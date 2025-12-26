package org.mql.ia.rag.controller;

import org.mql.ia.rag.model.Document;
import org.mql.ia.rag.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("userId") Long userId) {

        System.out.println("Réception upload:");
        System.out.println("   - Fichier: " + file.getOriginalFilename());
        System.out.println("   - Taille: " + file.getSize() + " bytes");
        System.out.println("   - UserId: " + userId);

        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Le fichier est vide"));
            }

            if (!file.getOriginalFilename().endsWith(".txt")) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Seuls les fichiers .txt sont acceptés"));
            }

            Document document = documentService.uploadDocument(file, userId);
            return ResponseEntity.ok(document);

        } catch (Exception e) {
            System.err.println("Err upload: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}