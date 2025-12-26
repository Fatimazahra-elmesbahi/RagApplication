package org.mql.ia.rag.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.scheduling.annotation.Async;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class LangChainRAGService {

    private final EmbeddingModel embeddingModel;
    private final ChatLanguageModel chatModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChromaDBService chromaDBService;
    private final ExecutorService executorService;

    // Cache pour éviter de re-générer les mêmes embeddings
    private final Map<String, List<String>> userDocuments = new ConcurrentHashMap<>();

    public LangChainRAGService(ChromaDBService chromaDBService,
                               @Value("${lm.studio.url}") String lmStudioUrl,
                               @Value("${embedding.model.url}") String embeddingModelUrl,
                               @Value("${lm.studio.model}") String chatModelName,
                               @Value("${embedding.model.name}") String embeddingModelName) {
        
        this.embeddingModel = OpenAiEmbeddingModel.builder()
            .baseUrl(embeddingModelUrl)
            .apiKey("not-needed")
            .modelName(embeddingModelName)
            .timeout(java.time.Duration.ofSeconds(60))
            .build();

        this.chatModel = OpenAiChatModel.builder()
            .baseUrl(lmStudioUrl + "/v1")
            .apiKey("not-needed")
            .modelName(chatModelName)
            .temperature(0.7)
            .maxTokens(500)
            .timeout(java.time.Duration.ofSeconds(120))
            .build();

        this.embeddingStore = new ChromaDBEmbeddingStore(chromaDBService);
        this.chromaDBService = chromaDBService;

        this.executorService = Executors.newFixedThreadPool(4);
    }


    public Map<String, Object> uploadDocument(MultipartFile file, Long userId) 
            throws IOException {

        String content = new String(file.getBytes());
        String docId = UUID.randomUUID().toString();

        // Split du document
        Document document = Document.from(content);
        var splitter = DocumentSplitters.recursive(500, 50);
        List<TextSegment> segments = splitter.split(document);

        List<TextSegment> segmentsWithMetadata = new ArrayList<>();
        
        for (int i = 0; i < segments.size(); i++) {
            Map<String, String> meta = new HashMap<>();
            meta.put("filename", file.getOriginalFilename());
            meta.put("userId", userId.toString());
            meta.put("chunkIndex", String.valueOf(i));
            meta.put("docId", docId);

            Metadata metadata = Metadata.from(meta);
            TextSegment segment = TextSegment.from(
                segments.get(i).text(), 
                metadata
            );
            segmentsWithMetadata.add(segment);
        }

        try {
            List<Embedding> embeddings = batchEmbed(segmentsWithMetadata);
            
            embeddingStore.addAll(embeddings, segmentsWithMetadata);
            
        } catch (Exception e) {
            throw new IOException("Erreur lors de la génération des embeddings", e);
        }

        userDocuments.computeIfAbsent(userId.toString(), k -> new ArrayList<>())
            .add(docId);

        return Map.of(
            "id", docId,
            "filename", file.getOriginalFilename(),
            "chunkCount", segments.size(),
            "uploadedAt", System.currentTimeMillis()
        );
    }

    private List<Embedding> batchEmbed(List<TextSegment> segments) {
        List<Embedding> embeddings = new ArrayList<>();
        
        int batchSize = 10;
        for (int i = 0; i < segments.size(); i += batchSize) {
            int end = Math.min(i + batchSize, segments.size());
            List<TextSegment> batch = segments.subList(i, end);
            
            for (TextSegment segment : batch) {
                Embedding embedding = embeddingModel.embed(segment).content();
                embeddings.add(embedding);
            }
        }
        
        return embeddings;
    }

    public Map<String, Object> query(String question, int topK, Long userId) {

        long start = System.currentTimeMillis();

        Embedding queryEmbedding = embeddingModel.embed(question).content();
        
        List<EmbeddingMatch<TextSegment>> matches =
            embeddingStore.findRelevant(queryEmbedding, topK, 0.5);

        matches = matches.stream()
            .filter(m -> {
                String segmentUserId = m.embedded().metadata().get("userId");
                return segmentUserId != null && segmentUserId.equals(userId.toString());
            })
            .collect(Collectors.toList());

        if (matches.isEmpty()) {
            return Map.of(
                "answer", "Aucune information trouvée dans vos documents.",
                "sources", List.of(),
                "responseTime", System.currentTimeMillis() - start,
                "confidence", 0.0
            );
        }

        String context = matches.stream()
            .map(m -> m.embedded().text())
            .collect(Collectors.joining("\n\n"));

        String prompt = String.format("""
            Tu es un assistant qui répond aux questions en te basant UNIQUEMENT 
            sur le contexte fourni. Ne réponds pas avec des informations externes.
            
            Contexte:
            %s
            
            Question:
            %s
            
            Instructions:
            - Réponds en français
            - Sois précis et concis
            - Si l'information n'est pas dans le contexte, dis-le clairement
            - Cite les parties pertinentes du contexte si possible
            
            Réponse:
            """, context, question);

        String answer;
        try {
            answer = chatModel.generate(prompt);
        } catch (Exception e) {
            answer = "Erreur lors de la génération de la réponse: " + e.getMessage();
        }

        List<String> sources = matches.stream()
            .map(m -> m.embedded().metadata().get("filename"))
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());

        double confidence = matches.stream()
            .mapToDouble(EmbeddingMatch::score)
            .average()
            .orElse(0) * 100;

        return Map.of(
            "answer", answer,
            "sources", sources,
            "responseTime", System.currentTimeMillis() - start,
            "confidence", confidence,
            "chunksUsed", matches.size()
        );
    }

    public void deleteDocument(String docId, Long userId) {
        List<String> userDocs = userDocuments.get(userId.toString());
        if (userDocs == null || !userDocs.contains(docId)) {
            throw new IllegalArgumentException(
                "Document non trouvé ou n'appartient pas à cet utilisateur"
            );
        }

        chromaDBService.deleteDocumentsByMetadata("docId", docId);

        userDocs.remove(docId);
    }

    public List<Map<String, Object>> getUserDocuments(Long userId) {
        return chromaDBService.getDocumentsByUser(userId.toString());
    }

    public void cleanup() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(60, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }

    private static class ChromaDBEmbeddingStore implements EmbeddingStore<TextSegment> {

        private final ChromaDBService chromaDB;

        public ChromaDBEmbeddingStore(ChromaDBService chromaDB) {
            this.chromaDB = chromaDB;
        }

        @Override
        public String add(Embedding embedding, TextSegment segment) {
            String id = UUID.randomUUID().toString();
            List<Double> vector = embedding.vectorAsList().stream()
                    .map(Float::doubleValue)
                    .collect(Collectors.toList());
            
            Map<String, Object> meta = new HashMap<>();
            segment.metadata().asMap().forEach(meta::put);
            
            chromaDB.addDocument(id, segment.text(), vector, meta);
            return id;
        }

        @Override
        public void add(String id, Embedding embedding) {
            throw new UnsupportedOperationException(
                "add(String, Embedding) not supported – TextSegment is required"
            );
        }

        @Override
        public String add(Embedding embedding) {
            throw new UnsupportedOperationException(
                "add(Embedding) not supported – TextSegment is required"
            );
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings) {
            throw new UnsupportedOperationException(
                "addAll(List<Embedding>) not supported – use addAll(embeddings, segments)"
            );
        }

        @Override
        public List<String> addAll(List<Embedding> embeddings, List<TextSegment> segments) {
            if (embeddings.size() != segments.size()) {
                throw new IllegalArgumentException(
                    "Embeddings and segments must have the same size"
                );
            }
            
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < embeddings.size(); i++) {
                ids.add(add(embeddings.get(i), segments.get(i)));
            }
            return ids;
        }

        @Override
        public List<EmbeddingMatch<TextSegment>> findRelevant(
                Embedding referenceEmbedding, int maxResults, double minScore) {

            List<Double> queryVector = referenceEmbedding.vectorAsList().stream()
                    .map(Float::doubleValue)
                    .collect(Collectors.toList());

            List<Map<String, Object>> results = chromaDB.queryDocuments(
                queryVector, 
                maxResults
            );

            return results.stream()
                    .map(r -> {
                        String text = (String) r.get("content");
                        Double distance = (Double) r.get("distance");

                        @SuppressWarnings("unchecked")
                        Map<String, Object> metaObj = (Map<String, Object>) r.get("metadata");
                        Map<String, String> meta = metaObj == null ? new HashMap<>() :
                                metaObj.entrySet().stream()
                                        .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                e -> String.valueOf(e.getValue())
                                        ));

                        Metadata metadata = Metadata.from(meta);
                        TextSegment segment = TextSegment.from(text, metadata);
                        double score = Math.max(0, 1.0 - distance);

                        return new EmbeddingMatch<>(
                                score,
                                (String) r.get("id"),
                                Embedding.from(new float[0]),
                                segment
                        );
                    })
                    .filter(m -> m.score() >= minScore)
                    .sorted((a, b) -> Double.compare(b.score(), a.score()))
                    .collect(Collectors.toList());
        }

        @Override
        public List<EmbeddingMatch<TextSegment>> findRelevant(
                Embedding referenceEmbedding, int maxResults) {
            return findRelevant(referenceEmbedding, maxResults, 0.0);
        }
    }
}