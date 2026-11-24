package com.rag.enterprise.service.hnsw;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class HnswIndexTest {

    @Test
    void testInsertAndNearestNeighborRetrieval() {
        HnswIndex<String> index = new HnswIndex<>(8, 32, 16);

        float[] v1 = new float[]{1.0f, 0.0f, 0.0f};
        float[] v2 = new float[]{0.0f, 1.0f, 0.0f};
        float[] v3 = new float[]{0.0f, 0.0f, 1.0f};
        float[] vQuery = new float[]{0.95f, 0.05f, 0.0f};

        index.insert("node1", v1);
        index.insert("node2", v2);
        index.insert("node3", v3);

        assertEquals(3, index.size());

        List<HnswIndex.Neighbor<String>> results = index.search(vQuery, 2);
        assertFalse(results.isEmpty());
        assertEquals("node1", results.get(0).id());
        assertTrue(results.get(0).similarity() > 0.9);
    }

    @Test
    void testRemove() {
        HnswIndex<Integer> index = new HnswIndex<>(8, 32, 16);
        index.insert(1, new float[]{1.0f, 0.0f});
        index.insert(2, new float[]{0.0f, 1.0f});

        assertEquals(2, index.size());
        index.remove(1);
        assertEquals(1, index.size());

        List<HnswIndex.Neighbor<Integer>> results = index.search(new float[]{1.0f, 0.0f}, 1);
        assertEquals(1, results.size());
        assertEquals(2, results.get(0).id());
    }
}
