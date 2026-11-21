package com.rag.enterprise.service;

import com.rag.enterprise.model.DocumentChunkEntity;
import com.rag.enterprise.model.DocumentEntity;
import com.rag.enterprise.repository.DocumentChunkRepository;
import com.rag.enterprise.repository.DocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class DocumentIngestionService {

    private static final int CHUNK_SIZE = 500; // ~500 chars per chunk
    private static final int CHUNK_OVERLAP = 100;

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final VectorEmbeddingService vectorService;

    public DocumentIngestionService(DocumentRepository documentRepository,
                                    DocumentChunkRepository chunkRepository,
                                    VectorEmbeddingService vectorService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.vectorService = vectorService;
    }

    @Transactional
    public DocumentEntity ingestFile(MultipartFile file, String description, String uploadedBy) throws Exception {
        String filename = file.getOriginalFilename();
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
        String extractedText = extractText(file);

        if (extractedText.trim().isEmpty()) {
            throw new IllegalArgumentException("Uploaded document contains no readable text.");
        }

        DocumentEntity doc = new DocumentEntity(filename, contentType, file.getSize(), description, uploadedBy);
        doc = documentRepository.save(doc);

        List<String> textChunks = chunkText(extractedText);
        doc.setChunkCount(textChunks.size());
        documentRepository.save(doc);

        for (int i = 0; i < textChunks.size(); i++) {
            String chunkText = textChunks.get(i);
            float[] embedding = vectorService.computeEmbedding(chunkText);
            String serialized = vectorService.serializeVector(embedding);

            DocumentChunkEntity chunkEntity = new DocumentChunkEntity(
                    doc.getId(),
                    doc.getFilename(),
                    i,
                    chunkText,
                    serialized
            );
            chunkRepository.save(chunkEntity);
        }

        return doc;
    }

    @Transactional
    public DocumentEntity ingestRawText(String title, String content, String description, String uploadedBy) {
        DocumentEntity doc = new DocumentEntity(title, "text/plain", (long) content.length(), description, uploadedBy);
        doc = documentRepository.save(doc);

        List<String> textChunks = chunkText(content);
        doc.setChunkCount(textChunks.size());
        documentRepository.save(doc);

        for (int i = 0; i < textChunks.size(); i++) {
            String chunkText = textChunks.get(i);
            float[] embedding = vectorService.computeEmbedding(chunkText);
            String serialized = vectorService.serializeVector(embedding);

            DocumentChunkEntity chunkEntity = new DocumentChunkEntity(
                    doc.getId(),
                    doc.getFilename(),
                    i,
                    chunkText,
                    serialized
            );
            chunkRepository.save(chunkEntity);
        }

        return doc;
    }

    private String extractText(MultipartFile file) throws Exception {
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        try (InputStream is = file.getInputStream()) {
            if (filename.endsWith(".pdf")) {
                try (PDDocument pdfDoc = PDDocument.load(is)) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    return stripper.getText(pdfDoc);
                }
            } else if (filename.endsWith(".docx")) {
                try (XWPFDocument docx = new XWPFDocument(is)) {
                    StringBuilder sb = new StringBuilder();
                    for (XWPFParagraph p : docx.getParagraphs()) {
                        sb.append(p.getText()).append("\n");
                    }
                    return sb.toString();
                }
            } else {
                // Default to UTF-8 text (txt, md, json, csv, etc.)
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private List<String> chunkText(String fullText) {
        List<String> chunks = new ArrayList<>();
        int length = fullText.length();
        int start = 0;

        while (start < length) {
            int end = Math.min(start + CHUNK_SIZE, length);
            String chunk = fullText.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end == length) break;
            start += (CHUNK_SIZE - CHUNK_OVERLAP);
        }
        return chunks;
    }

    public List<DocumentEntity> listAllDocuments() {
        return documentRepository.findAll();
    }

    @Transactional
    public void deleteDocument(Long id) {
        chunkRepository.deleteByDocumentId(id);
        documentRepository.deleteById(id);
    }
}
