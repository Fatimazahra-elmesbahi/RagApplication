package org.mql.ia.rag.controller;

import org.mql.ia.rag.service.LangChainRAGService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.List;
import java.util.Arrays;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "${app.cors.allowed-origins}")
public class LangChainController {

    private final LangChainRAGService ragService;
    
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    private static final List<String> ALLOWED_MIME_TYPES = List.of(
    "text/plain"
    );

    public LangChainController(LangChainRAGService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/documents/upload-langchain")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        
        try {
            validateFile(file);
            Long userId = extractUserIdFromAuth(authentication);
            Map<String, Object> result = ragService.uploadDocument(file, userId);
            return ResponseEntity.ok(result);
            
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("Upload error: " + e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Erreur lors du traitement du fichier"));
        }
    }

    @PostMapping("/query-langchain")
    public ResponseEntity<?> queryDocuments(
            @Valid @RequestBody QueryRequest request,
            Authentication authentication) {

        try {
            Long userId = extractUserIdFromAuth(authentication);
            
            if (request.getQuestion() == null || request.getQuestion().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "La question ne peut pas être vide"));
            }
            
            if (request.getQuestion().length() > 1000) {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Question trop longue (max 1000 caractères)"));
            }
            
            Map<String, Object> response = ragService.query(
                request.getQuestion(), 
                request.getTopK(),
                userId
            );
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            System.err.println("Query error: " + e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Erreur lors du traitement de la requête"));
        }
    }

    @DeleteMapping("/documents/{docId}")
    public ResponseEntity<?> deleteDocument(
            @PathVariable String docId,
            Authentication authentication) {
        
        try {
            Long userId = extractUserIdFromAuth(authentication);
            ragService.deleteDocument(docId, userId);
            return ResponseEntity.ok(Map.of("message", "Document supprimé"));
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/documents")
    public ResponseEntity<?> getUserDocuments(Authentication authentication) {
        try {
            Long userId = extractUserIdFromAuth(authentication);
            List<Map<String, Object>> docs = ragService.getUserDocuments(userId);
            return ResponseEntity.ok(docs);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Le fichier est vide");
        }
        
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                "Fichier trop volumineux (max 10 MB)"
            );
        }
        
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_MIME_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(
                "Type de fichier non autorisé. Accepté: " + ALLOWED_MIME_TYPES
            );
        }
        
        String filename = file.getOriginalFilename();
        if (filename == null || filename.contains("..") || filename.contains("/")) {
            throw new IllegalArgumentException("Nom de fichier invalide");
        }
    }

    private Long extractUserIdFromAuth(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return 1L;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof Long) {
            return (Long) principal;
        }

        throw new IllegalArgumentException("Impossible d'extraire userId");
    }

    public static class QueryRequest {
        @NotBlank(message = "La question est obligatoire")
        private String question;
        
        @Min(value = 1, message = "topK doit être >= 1")
        @Max(value = 10, message = "topK doit être <= 10")
        private int topK = 3;

        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public int getTopK() { return topK; }
        public void setTopK(int topK) { this.topK = topK; }
    }
}