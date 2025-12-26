package org.mql.ia.rag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    @Value("${lm.studio.url}")
    private String lmStudioUrl;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    public EmbeddingService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
        this.objectMapper = new ObjectMapper();
    }

    public List<Double> generateEmbedding(String text) {
        try {
            System.out.println("Génération embedding (LM Studio)...");
            
            String response = webClient.post()
                .uri(lmStudioUrl + "/embeddings")
                .bodyValue(Map.of(
                    "input", text,
                    "model", "text-embedding-nomic-embed-text-v1.5"
                ))
                .retrieve()
                .bodyToMono(String.class)
                .block();

            System.out.println("Reponse LM Studio reçue");

            JsonNode node = objectMapper.readTree(response);
            JsonNode embeddingNode = node.get("data").get(0).get("embedding");

            List<Double> embedding = new ArrayList<>();
            embeddingNode.forEach(e -> embedding.add(e.asDouble()));
            
            System.out.println("Embdding généré: " + embedding.size() + " dimensions");
            return embedding;
            
        } catch (Exception e) {
            System.err.println("Erreur embedding LM Studio: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Erreur génération embedding: " + e.getMessage(), e);
        }
    }
}