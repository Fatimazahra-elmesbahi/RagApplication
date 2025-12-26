package org.mql.ia.rag.service;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChromaDBService {

    private final Map<String, DocumentEntry> store = new ConcurrentHashMap<>();

    private static class DocumentEntry {
        String content;
        Map<String, Object> metadata;
        List<Double> embedding;

        DocumentEntry(String content, List<Double> embedding, Map<String, Object> metadata) {
            this.content = content;
            this.embedding = embedding;
            this.metadata = metadata;
        }
    }

    public void addDocument(String id, String content, List<Double> embedding, Map<String, Object> metadata) {
        store.put(id, new DocumentEntry(content, embedding, metadata));
        System.out.println("Document ajouté (mock) : " + id);
    }

    public void addDocuments(List<String> ids, List<String> contents, 
                           List<List<Double>> embeddings, List<Map<String, Object>> metadatas) {
        for (int i = 0; i < ids.size(); i++) {
            store.put(ids.get(i), new DocumentEntry(contents.get(i), embeddings.get(i), metadatas.get(i)));
        }
        System.out.println("Documents ajoutés en batch : " + ids.size());
    }

    public List<Map<String, Object>> queryDocuments(List<Double> queryEmbedding, int topK) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, DocumentEntry> entry : store.entrySet()) {
            Map<String, Object> result = new HashMap<>();
            result.put("id", entry.getKey());
            result.put("content", entry.getValue().content);
            result.put("metadata", entry.getValue().metadata);
            result.put("distance", 0.0);
            results.add(result);
        }
        return results;
    }

    public void deleteDocument(String id) {
        store.remove(id);
        System.out.println("Document supprimé (mock) : " + id);
    }

    public void deleteDocumentsByMetadata(String key, String value) {
        List<String> toDelete = new ArrayList<>();
        for (Map.Entry<String, DocumentEntry> entry : store.entrySet()) {
            Map<String, Object> metadata = entry.getValue().metadata;
            if (metadata != null && value.equals(metadata.get(key))) {
                toDelete.add(entry.getKey());
            }
        }
        for (String id : toDelete) {
            store.remove(id);
        }
        System.out.println("Documents supprimés par metadata " + key + "=" + value + " : " + toDelete.size());
    }

    public List<Map<String, Object>> getDocumentsByUserAndDocId(String userId, String docId) {
        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, DocumentEntry> entry : store.entrySet()) {
            Map<String, Object> metadata = entry.getValue().metadata;
            if (metadata != null &&
                userId.equals(metadata.get("userId")) &&
                docId.equals(metadata.get("docId"))) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("id", entry.getKey());
                doc.put("content", entry.getValue().content);
                doc.put("metadata", metadata);
                results.add(doc);
            }
        }
        return results;
    }

    public List<Map<String, Object>> getDocumentsByUser(String userId) {
        List<Map<String, Object>> results = new ArrayList<>();
        Set<String> processedDocIds = new HashSet<>();

        for (Map.Entry<String, DocumentEntry> entry : store.entrySet()) {
            Map<String, Object> metadata = entry.getValue().metadata;
            if (metadata != null && userId.equals(metadata.get("userId"))) {
                String docId = (String) metadata.get("docId");
                if (docId != null && !processedDocIds.contains(docId)) {
                    processedDocIds.add(docId);
                    Map<String, Object> doc = new HashMap<>();
                    doc.put("id", docId);
                    doc.put("filename", metadata.get("filename"));
                    doc.put("userId", userId);
                    doc.put("chunkCount", getChunkCountForDoc(userId, docId));
                    results.add(doc);
                }
            }
        }
        return results;
    }

    private int getChunkCountForDoc(String userId, String docId) {
        int count = 0;
        for (Map.Entry<String, DocumentEntry> entry : store.entrySet()) {
            Map<String, Object> metadata = entry.getValue().metadata;
            if (metadata != null &&
                userId.equals(metadata.get("userId")) &&
                docId.equals(metadata.get("docId"))) {
                count++;
            }
        }
        return count;
    }

    private double cosineDistance(List<Double> a, List<Double> b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        return 1 - (dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }
}