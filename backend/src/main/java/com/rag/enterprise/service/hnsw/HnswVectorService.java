package com.rag.enterprise.service.hnsw;

import com.rag.enterprise.model.DocumentChunkEntity;
import com.rag.enterprise.repository.DocumentChunkRepository;
import com.rag.enterprise.service.VectorEmbeddingService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service managing the in-memory HNSW index for high-speed similarity retrieval.
 */
@Service
public class HnswVectorService {

    private static final Logger log = LoggerFactory.getLogger(HnswVectorService.class);

    private final HnswIndex<Long> index;
    private final DocumentChunkRepository chunkRepository;
    private final VectorEmbeddingService vectorService;

    public HnswVectorService(DocumentChunkRepository chunkRepository,
                             VectorEmbeddingService vectorService) {
        this.chunkRepository = chunkRepository;
        this.vectorService = vectorService;
        // M=16, efConstruction=64, efSearch=32
        this.index = new HnswIndex<>(16, 64, 32);
    }

    @PostConstruct
    public void rebuildIndex() {
        log.info("Initializing HNSW vector index from stored chunks...");
        index.clear();
        List<DocumentChunkEntity> chunks = chunkRepository.findAll();
        for (DocumentChunkEntity chunk : chunks) {
            float[] vec = vectorService.deserializeVector(chunk.getVectorEmbedding());
            index.insert(chunk.getId(), vec);
        }
        log.info("HNSW vector index initialized with {} chunks.", index.size());
    }

    public void indexChunk(Long chunkId, float[] vector) {
        index.insert(chunkId, vector);
    }

    public void removeChunk(Long chunkId) {
        index.remove(chunkId);
    }

    public List<HnswIndex.Neighbor<Long>> search(float[] queryVector, int topK) {
        // If index is empty (e.g. after startup or before chunks loaded), return empty list
        if (index.size() == 0 && chunkRepository.count() > 0) {
            rebuildIndex();
        }
        return index.search(queryVector, topK);
    }

    public int getIndexSize() {
        return index.size();
    }
}
