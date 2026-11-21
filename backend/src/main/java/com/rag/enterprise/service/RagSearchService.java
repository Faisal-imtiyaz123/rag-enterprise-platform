package com.rag.enterprise.service;

import com.rag.enterprise.dto.RagQueryRequest;
import com.rag.enterprise.dto.RagQueryResponse;
import com.rag.enterprise.guardrail.GuardrailService;
import com.rag.enterprise.model.AuditLogEntity;
import com.rag.enterprise.model.DocumentChunkEntity;
import com.rag.enterprise.repository.AuditLogRepository;
import com.rag.enterprise.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagSearchService {

    private final DocumentChunkRepository chunkRepository;
    private final VectorEmbeddingService vectorService;
    private final LlmGatewayService llmService;
    private final GuardrailService guardrailService;
    private final AuditLogRepository auditLogRepository;

    public RagSearchService(DocumentChunkRepository chunkRepository,
                            VectorEmbeddingService vectorService,
                            LlmGatewayService llmService,
                            GuardrailService guardrailService,
                            AuditLogRepository auditLogRepository) {
        this.chunkRepository = chunkRepository;
        this.vectorService = vectorService;
        this.llmService = llmService;
        this.guardrailService = guardrailService;
        this.auditLogRepository = auditLogRepository;
    }

    public RagQueryResponse executeQuery(RagQueryRequest request) {
        long startTime = System.currentTimeMillis();

        // 1. Guardrail Validation & PII Masking
        GuardrailService.GuardrailResult guardrailResult = guardrailService.validateAndSanitize(request.getQuery());
        if (!guardrailResult.passed()) {
            auditLogRepository.save(new AuditLogEntity(
                    request.getUserRole(),
                    request.getQuery(),
                    true,
                    guardrailResult.violationReason(),
                    System.currentTimeMillis() - startTime
            ));
            return RagQueryResponse.blocked(guardrailResult.violationReason());
        }

        String sanitizedQuery = guardrailResult.sanitizedText();

        // 2. Compute Query Embedding
        float[] queryEmbedding = vectorService.computeEmbedding(sanitizedQuery);

        // 3. Dense Cosine Retrieval
        List<DocumentChunkEntity> allChunks = chunkRepository.findAll();
        List<ScoredChunk> scoredChunks = new ArrayList<>();

        for (DocumentChunkEntity chunk : allChunks) {
            float[] chunkVec = vectorService.deserializeVector(chunk.getVectorEmbedding());
            double score = vectorService.cosineSimilarity(queryEmbedding, chunkVec);
            if (score > 0.05) { // Similarity threshold
                scoredChunks.add(new ScoredChunk(chunk, score));
            }
        }

        // Sort descending by relevance score
        scoredChunks.sort((a, b) -> Double.compare(b.score, a.score));
        int k = Math.min(request.getTopK() > 0 ? request.getTopK() : 4, scoredChunks.size());
        List<ScoredChunk> topKChunks = scoredChunks.subList(0, k);

        // 4. Extract contexts for LLM Synthesis
        List<String> contexts = topKChunks.stream()
                .map(sc -> sc.chunk.getContent())
                .collect(Collectors.toList());

        String generatedAnswer = llmService.generateRAGResponse(sanitizedQuery, contexts);

        // 5. Package Citations
        List<RagQueryResponse.RetrievedCitation> citations = topKChunks.stream()
                .map(sc -> new RagQueryResponse.RetrievedCitation(
                        sc.chunk.getDocumentName(),
                        sc.chunk.getChunkIndex(),
                        Math.round(sc.score * 1000.0) / 1000.0,
                        sc.chunk.getContent().length() > 200 ? sc.chunk.getContent().substring(0, 200) + "..." : sc.chunk.getContent()
                ))
                .collect(Collectors.toList());

        long elapsed = System.currentTimeMillis() - startTime;

        // 6. Audit Logging
        auditLogRepository.save(new AuditLogEntity(
                request.getUserRole(),
                sanitizedQuery,
                false,
                null,
                elapsed
        ));

        RagQueryResponse response = new RagQueryResponse();
        response.setGuardrailPassed(true);
        response.setAnswer(generatedAnswer);
        response.setCitations(citations);
        response.setExecutionTimeMs(elapsed);
        return response;
    }

    private record ScoredChunk(DocumentChunkEntity chunk, double score) {}
}
