package com.rag.enterprise.service;

import com.rag.enterprise.dto.RagQueryRequest;
import com.rag.enterprise.dto.RagQueryResponse;
import com.rag.enterprise.guardrail.GuardrailService;
import com.rag.enterprise.model.AuditLogEntity;
import com.rag.enterprise.model.DocumentChunkEntity;
import com.rag.enterprise.repository.AuditLogRepository;
import com.rag.enterprise.repository.DocumentChunkRepository;
import com.rag.enterprise.service.hnsw.HnswIndex;
import com.rag.enterprise.service.hnsw.HnswVectorService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class RagSearchService {

    private final DocumentChunkRepository chunkRepository;
    private final VectorEmbeddingService vectorService;
    private final HnswVectorService hnswVectorService;
    private final LlmGatewayService llmService;
    private final GuardrailService guardrailService;
    private final AuditLogRepository auditLogRepository;

    public RagSearchService(DocumentChunkRepository chunkRepository,
                            VectorEmbeddingService vectorService,
                            HnswVectorService hnswVectorService,
                            LlmGatewayService llmService,
                            GuardrailService guardrailService,
                            AuditLogRepository auditLogRepository) {
        this.chunkRepository = chunkRepository;
        this.vectorService = vectorService;
        this.hnswVectorService = hnswVectorService;
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

        // 3. HNSW (Hierarchical Navigable Small World) Similarity Search
        int k = request.getTopK() > 0 ? request.getTopK() : 4;
        List<HnswIndex.Neighbor<Long>> hnswNeighbors = hnswVectorService.search(queryEmbedding, k * 2);

        List<ScoredChunk> scoredChunks = new ArrayList<>();
        for (HnswIndex.Neighbor<Long> neighbor : hnswNeighbors) {
            if (neighbor.similarity() > 0.05) {
                Optional<DocumentChunkEntity> chunkOpt = chunkRepository.findById(neighbor.id());
                chunkOpt.ifPresent(chunk -> scoredChunks.add(new ScoredChunk(chunk, neighbor.similarity())));
            }
        }

        // Limit to desired top-K
        int effectiveK = Math.min(k, scoredChunks.size());
        List<ScoredChunk> topKChunks = scoredChunks.subList(0, effectiveK);

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
