package org.mql.ia.rag.service;

import org.mql.ia.rag.model.Document;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private final EmbeddingService embeddingService;
    private final ChromaDBService chromaDBService;
    private final ExecutorService executorService;

    public DocumentService(EmbeddingService embeddingService, 
                          ChromaDBService chromaDBService) {
        this.embeddingService = embeddingService;
        this.chromaDBService = chromaDBService;
        this.executorService = Executors.newFixedThreadPool(4);
    }

    public Document uploadDocument(MultipartFile file, Long userId) throws IOException {
        String content = new String(file.getBytes());
        List<String> chunks = chunkText(content, 500, 50);
        String docId = UUID.randomUUID().toString();
        
        List<String> ids = new ArrayList<>();
        List<String> contents = new ArrayList<>();
        List<List<Double>> embeddings = new ArrayList<>();
        List<Map<String, Object>> metadatas = new ArrayList<>();
        
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < chunks.size(); i++) {
            final int index = i;
            final String chunk = chunks.get(i);
            
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                String chunkId = docId + "_chunk_" + index;
                List<Double> embedding = embeddingService.generateEmbedding(chunk);
                
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("filename", file.getOriginalFilename());
                metadata.put("userId", userId.toString());
                metadata.put("chunkIndex", String.valueOf(index));
                metadata.put("docId", docId);
                
                synchronized (ids) {
                    ids.add(chunkId);
                    contents.add(chunk);
                    embeddings.add(embedding);
                    metadatas.add(metadata);
                }
            }, executorService);
            
            futures.add(future);
        }
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        chromaDBService.addDocuments(ids, contents, embeddings, metadatas);
        
        Document document = new Document();
        document.setId(docId);
        document.setFilename(file.getOriginalFilename());
        document.setContent(content);
        document.setUserId(userId);
        document.setChunkCount(chunks.size());
        
        return document;
    }

    public List<Map<String, Object>> getDocumentsByUser(Long userId) {
        return chromaDBService.getDocumentsByUser(userId.toString());
    }

    public void deleteDocument(String docId, Long userId) {
        chromaDBService.deleteDocumentsByMetadata("docId", docId);
        System.out.println("Document supprimé: " + docId + " pour utilisateur: " + userId);
    }

    private List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        String[] words = text.split("\\s+");

        int start = 0;
        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);

            StringBuilder chunk = new StringBuilder();
            for (int i = start; i < end; i++) {
                chunk.append(words[i]).append(" ");
            }

            String chunkText = chunk.toString().trim();
            if (!chunkText.isEmpty()) {
                chunks.add(chunkText);
            }

            start += (chunkSize - overlap);
        }

        return chunks;
    }
}