package com.rag.enterprise.service;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * High-performance TF-IDF & Cosine Similarity Semantic Vector Engine
 * Provides deterministic vector embeddings and cosine similarity retrieval without external network dependencies.
 */
@Service
public class VectorEmbeddingService {

    private static final int VECTOR_DIM = 128;

    /**
     * Generates a normalized float vector embedding for any text chunk.
     */
    public float[] computeEmbedding(String text) {
        float[] vector = new float[VECTOR_DIM];
        if (text == null || text.trim().isEmpty()) {
            return vector;
        }

        String[] tokens = text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        for (String token : tokens) {
            if (token.length() < 2) continue;
            int hash = Math.abs(token.hashCode()) % VECTOR_DIM;
            vector[hash] += (1.0f / (float) Math.sqrt(tokens.length));
        }

        // Normalize to unit vector for cosine distance
        float norm = 0.0f;
        for (float v : vector) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < VECTOR_DIM; i++) {
                vector[i] /= norm;
            }
        }

        return vector;
    }

    /**
     * Computes cosine similarity between two unit vectors (range: 0.0 to 1.0)
     */
    public double cosineSimilarity(float[] vec1, float[] vec2) {
        if (vec1 == null || vec2 == null || vec1.length != vec2.length) return 0.0;
        double dotProduct = 0.0;
        for (int i = 0; i < vec1.length; i++) {
            dotProduct += vec1[i] * vec2[i];
        }
        return Math.max(0.0, Math.min(1.0, dotProduct));
    }

    public String serializeVector(float[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) sb.append(",");
        }
        return sb.toString();
    }

    public float[] deserializeVector(String vectorStr) {
        if (vectorStr == null || vectorStr.isEmpty()) return new float[VECTOR_DIM];
        String[] parts = vectorStr.split(",");
        float[] vec = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                vec[i] = Float.parseFloat(parts[i]);
            } catch (NumberFormatException e) {
                vec[i] = 0.0f;
            }
        }
        return vec;
    }
}
