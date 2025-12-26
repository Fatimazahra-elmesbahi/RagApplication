package org.mql.ia.rag.model;

import java.time.LocalDateTime;

public class Document {
    private String id;
    private String filename;
    private String content;
    private Long userId;
    private LocalDateTime uploadedAt;
    private int chunkCount;

    public Document() {
        this.uploadedAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getFilename() {
        return filename;
    }

    public String getContent() {
        return content;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public void setChunkCount(int chunkCount) {
        this.chunkCount = chunkCount;
    }
}