package org.mql.ia.rag.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Map;

@Component
public class ChromaDBInitializer implements CommandLineRunner {

    @Value("${chromadb.url}")
    private String chromaUrl;

    @Value("${chromadb.collection}")
    private String collectionName;

    private final WebClient webClient;

    public ChromaDBInitializer(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.build();
    }

    @Override
    public void run(String... args) {
        try {
            System.out.println("Test connexion ChromaDB (v2): " + chromaUrl);
            
            String response = webClient.get()
                .uri(chromaUrl + "/api/v2")
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            System.out.println("ChromaDB v2 connecté");
            
            try {
                webClient.post()
                    .uri(chromaUrl + "/api/v2/collections")
                    .bodyValue(Map.of(
                        "name", collectionName,
                        "metadata", Map.of("hnsw:space", "cosine")
                    ))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
                
                System.out.println("Collection créée: " + collectionName);
            } catch (Exception e) {
                System.out.println("Collection existe déjà: " + collectionName);
            }
            
        } catch (Exception e) {
            System.err.println("Erreur ChromaDB: " + e.getMessage());
            System.out.println("Essayez: docker restart chromadb");
        }
    }
}