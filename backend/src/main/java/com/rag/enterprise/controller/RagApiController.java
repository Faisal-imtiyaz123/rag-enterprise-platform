package com.rag.enterprise.controller;

import com.rag.enterprise.dto.RagQueryRequest;
import com.rag.enterprise.dto.RagQueryResponse;
import com.rag.enterprise.model.AuditLogEntity;
import com.rag.enterprise.model.DocumentEntity;
import com.rag.enterprise.repository.AuditLogRepository;
import com.rag.enterprise.service.DocumentIngestionService;
import com.rag.enterprise.service.RagSearchService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class RagApiController {

    @Value("${rag.admin.api-key:adminkey123}")
    private String adminApiKey;

    private final DocumentIngestionService ingestionService;
    private final RagSearchService searchService;
    private final AuditLogRepository auditLogRepository;

    public RagApiController(DocumentIngestionService ingestionService,
                            RagSearchService searchService,
                            AuditLogRepository auditLogRepository) {
        this.ingestionService = ingestionService;
        this.searchService = searchService;
        this.auditLogRepository = auditLogRepository;
    }

    // ==========================================
    // USER RAG QUERY ENDPOINT
    // ==========================================
    @PostMapping("/query")
    public ResponseEntity<RagQueryResponse> query(@RequestBody RagQueryRequest request) {
        RagQueryResponse response = searchService.executeQuery(request);
        return ResponseEntity.ok(response);
    }

    // ==========================================
    // PUBLIC DOCUMENT LIST (User Knowledge Base View)
    // ==========================================
    @GetMapping("/documents")
    public ResponseEntity<List<DocumentEntity>> listDocuments() {
        return ResponseEntity.ok(ingestionService.listAllDocuments());
    }

    // ==========================================
    // ADMIN ENDPOINTS (Guarded by X-Admin-Key)
    // ==========================================
    @PostMapping(value = "/admin/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadDocument(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false, defaultValue = "") String description,
            @RequestParam(value = "uploadedBy", required = false, defaultValue = "admin") String uploadedBy) {

        if (!validateAdmin(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Valid X-Admin-Key header required."));
        }

        try {
            DocumentEntity doc = ingestionService.ingestFile(file, description, uploadedBy);
            return ResponseEntity.status(HttpStatus.CREATED).body(doc);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Failed to ingest document: " + e.getMessage()));
        }
    }

    @PostMapping("/admin/ingest-text")
    public ResponseEntity<?> ingestRawText(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestBody Map<String, String> body) {

        if (!validateAdmin(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Valid X-Admin-Key header required."));
        }

        String title = body.getOrDefault("title", "Untitled Document");
        String content = body.getOrDefault("content", "");
        String description = body.getOrDefault("description", "Directly ingested text");
        String uploadedBy = body.getOrDefault("uploadedBy", "admin");

        if (content.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Content cannot be empty."));
        }

        DocumentEntity doc = ingestionService.ingestRawText(title, content, description, uploadedBy);
        return ResponseEntity.status(HttpStatus.CREATED).body(doc);
    }

    @DeleteMapping("/admin/documents/{id}")
    public ResponseEntity<?> deleteDocument(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @PathVariable Long id) {

        if (!validateAdmin(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Valid X-Admin-Key header required."));
        }

        ingestionService.deleteDocument(id);
        return ResponseEntity.ok(Map.of("message", "Document deleted successfully."));
    }

    @GetMapping("/admin/audit-logs")
    public ResponseEntity<?> getAuditLogs(
            @RequestHeader(value = "X-Admin-Key", required = false) String key) {

        if (!validateAdmin(key)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Unauthorized: Valid X-Admin-Key header required."));
        }

        return ResponseEntity.ok(auditLogRepository.findAll());
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "RAG Enterprise Platform",
                "guardrails", "ACTIVE",
                "version", "1.0.0"
        ));
    }

    private boolean validateAdmin(String key) {
        return adminApiKey.equals(key);
    }
}
