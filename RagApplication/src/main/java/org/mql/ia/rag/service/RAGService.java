package org.mql.ia.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mql.ia.rag.model.QueryRequest;
import org.mql.ia.rag.model.QueryResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class RAGService {

    @Value("${lm.studio.url}")
    private String lmStudioUrl;

    @Value("${lm.studio.model}")
    private String modelName;

    private final EmbeddingService embeddingService;
    private final ChromaDBService chromaDBService;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public RAGService(EmbeddingService embeddingService,
                     ChromaDBService chromaDBService,
                     WebClient.Builder webClientBuilder) {
        this.embeddingService = embeddingService;
        this.chromaDBService = chromaDBService;
        this.webClient = webClientBuilder
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(16 * 1024 * 1024))
            .build();
        this.objectMapper = new ObjectMapper();
    }

    public QueryResponse processQuery(QueryRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            List<Double> queryEmbedding = embeddingService.generateEmbedding(request.getQuestion());
            
            List<Map<String, Object>> results = chromaDBService.queryDocuments(
                queryEmbedding, 
                request.getTopK()
            );
            
            if (results.isEmpty()) {
                QueryResponse response = new QueryResponse();
                response.setAnswer("Je n'ai pas trouvé d'informations pertinentes dans les documents pour répondre à cette question.");
                response.setSources(List.of());
                response.setResponseTime(System.currentTimeMillis() - startTime);
                response.setConfidence(0.0);
                return response;
            }
            
            String context = results.stream()
                .map(r -> (String) r.get("content"))
                .collect(Collectors.joining("\n\n"));
            
            String prompt = buildPrompt(context, request.getQuestion());
            String answer = generateAnswerStreaming(prompt);
            
            QueryResponse response = new QueryResponse();
            response.setAnswer(answer);
            response.setSources(extractSources(results));
            response.setResponseTime(System.currentTimeMillis() - startTime);
            response.setConfidence(calculateConfidence(results));
            
            return response;
            
        } catch (Exception e) {
            System.err.println("Erreur lors du traitement de la requête: " + e.getMessage());
            throw new RuntimeException("Erreur lors du traitement de la requête: " + e.getMessage(), e);
        }
    }

    private String buildPrompt(String context, String question) {
        return String.format("""
            Tu es un assistant intelligent qui répond aux questions basées sur le contexte fourni.
            
            Contexte:
            %s
            
            Question: %s
            
            Instructions:
            - Réponds en français
            - Base ta réponse uniquement sur le contexte fourni
            - Si le contexte ne contient pas l'information, dis-le clairement
            - Sois concis et précis
            
            Réponse:
            """, context, question);
    }

    private String generateAnswerStreaming(String prompt) {
        try {
            String response = webClient.post()
                .uri(lmStudioUrl + "/v1/chat/completions")
                .bodyValue(Map.of(
                    "model", modelName,
                    "messages", List.of(
                        Map.of("role", "system", "content", "Tu es un assistant qui répond de manière concise et précise."),
                        Map.of("role", "user", "content", prompt)
                    ),
                    "temperature", 0.3,
                    "max_tokens", 300,
                    "stream", false,
                    "top_p", 0.9
                ))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(Duration.ofSeconds(30))
                .block();

            JsonNode node = objectMapper.readTree(response);
            JsonNode choices = node.get("choices");

            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("Réponse LM Studio invalide");
            }

            return choices.get(0)
                .get("message")
                .get("content")
                .asText()
                .trim();

        } catch (Exception e) {
            System.err.println("Erreur de génération LM Studio: " + e.getMessage());
            return "Désolé, je n'ai pas pu générer une réponse. Veuillez réessayer.";
        }
    }

    private List<String> extractSources(List<Map<String, Object>> results) {
        return results.stream()
            .map(r -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> metadata = (Map<String, Object>) r.get("metadata");
                return (String) metadata.get("filename");
            })
            .distinct()
            .collect(Collectors.toList());
    }

    private double calculateConfidence(List<Map<String, Object>> results) {
        if (results.isEmpty()) return 0.0;
        double avgDistance = results.stream()
            .mapToDouble(r -> (Double) r.get("distance"))
            .average()
            .orElse(1.0);
        return Math.max(0, Math.min(100, (1 - avgDistance) * 100));
    }
}