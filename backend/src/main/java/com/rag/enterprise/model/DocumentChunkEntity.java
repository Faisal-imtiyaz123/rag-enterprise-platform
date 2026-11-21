package com.rag.enterprise.model;

import jakarta.persistence.*;

@Entity
@Table(name = "document_chunks")
public class DocumentChunkEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private String documentName;

    private int chunkIndex;

    @Lob
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String vectorEmbedding; // Comma-separated or serialized floats

    public DocumentChunkEntity() {}

    public DocumentChunkEntity(Long documentId, String documentName, int chunkIndex, String content, String vectorEmbedding) {
        this.documentId = documentId;
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.content = content;
        this.vectorEmbedding = vectorEmbedding;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public String getDocumentName() { return documentName; }
    public void setDocumentName(String documentName) { this.documentName = documentName; }

    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getVectorEmbedding() { return vectorEmbedding; }
    public void setVectorEmbedding(String vectorEmbedding) { this.vectorEmbedding = vectorEmbedding; }
}
