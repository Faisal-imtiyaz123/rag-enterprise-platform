package com.rag.enterprise.service.hnsw;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Hierarchical Navigable Small World (HNSW) Vector Index implementation in pure Java.
 *
 * Implements Malkov & Yashunin (2018): "Efficient and robust approximate nearest neighbor
 * search using Hierarchical Navigable Small World graphs".
 *
 * Designed for low-latency cosine similarity approximate nearest-neighbor retrieval.
 *
 * @param <T> Key type identifying the stored item (e.g. Long chunkId)
 */
public class HnswIndex<T> {

    public record Neighbor<T>(T id, double similarity) implements Comparable<Neighbor<T>> {
        @Override
        public int compareTo(Neighbor<T> o) {
            // Natural order: descending similarity (higher similarity comes first)
            return Double.compare(o.similarity, this.similarity);
        }
    }

    private static class Node<T> {
        final T id;
        final float[] vector;
        final int level;
        // Layer -> list of neighbor IDs
        final List<List<T>> connections;

        Node(T id, float[] vector, int level) {
            this.id = id;
            this.vector = vector;
            this.level = level;
            this.connections = new ArrayList<>(level + 1);
            for (int i = 0; i <= level; i++) {
                this.connections.add(new ArrayList<>());
            }
        }
    }

    private final int m;            // Max edges per node per layer (except layer 0 which is 2 * m)
    private final int maxM0;        // Max edges in layer 0
    private final int efConstruction; // Size of dynamic candidate list during construction
    private final int efSearch;     // Size of dynamic candidate list during search
    private final double mL;        // Normalization factor for level generation

    private final Map<T, Node<T>> nodes = new ConcurrentHashMap<>();
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();

    private volatile Node<T> entryPoint = null;
    private volatile int maxLevel = -1;

    public HnswIndex(int m, int efConstruction, int efSearch) {
        this.m = m;
        this.maxM0 = 2 * m;
        this.efConstruction = Math.max(efConstruction, m);
        this.efSearch = Math.max(efSearch, 10);
        this.mL = 1.0 / Math.log(m);
    }

    public HnswIndex() {
        this(16, 64, 32);
    }

    public int size() {
        return nodes.size();
    }

    public void clear() {
        rwLock.writeLock().lock();
        try {
            nodes.clear();
            entryPoint = null;
            maxLevel = -1;
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private int randomLevel() {
        double r = ThreadLocalRandom.current().nextDouble();
        if (r == 0) r = 0.0000001;
        return (int) (-Math.log(r) * mL);
    }

    /**
     * Cosine similarity between two unit vectors. Range: [0.0, 1.0]
     */
    private double similarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0.0;
        double dot = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
        }
        return Math.max(0.0, Math.min(1.0, dot));
    }

    /**
     * Inserts an item and its vector into the HNSW graph.
     */
    public void insert(T id, float[] vector) {
        if (id == null || vector == null) return;

        rwLock.writeLock().lock();
        try {
            if (nodes.containsKey(id)) {
                // If it already exists, replace by removing or updating vector
                remove(id);
            }

            int insertLevel = randomLevel();
            Node<T> newNode = new Node<>(id, vector, insertLevel);
            nodes.put(id, newNode);

            Node<T> currObj = entryPoint;
            if (currObj == null) {
                entryPoint = newNode;
                maxLevel = insertLevel;
                return;
            }

            float[] qVec = vector;
            int currLevel = maxLevel;

            // 1. Traverse layers from top down to insertLevel + 1 to find the closest entry point
            while (currLevel > insertLevel) {
                Node<T> next = findGreedyNearestAtLayer(currObj, qVec, currLevel);
                currObj = next;
                currLevel--;
            }

            // 2. From min(maxLevel, insertLevel) down to 0, search and connect neighbors
            int topConnectLevel = Math.min(maxLevel, insertLevel);
            for (int level = topConnectLevel; level >= 0; level--) {
                List<Neighbor<T>> candidates = searchLayer(currObj, qVec, efConstruction, level);

                int maxConn = (level == 0) ? maxM0 : m;
                List<Neighbor<T>> selected = selectNeighbors(candidates, maxConn);

                for (Neighbor<T> neighbor : selected) {
                    Node<T> neighborNode = nodes.get(neighbor.id);
                    if (neighborNode != null) {
                        connectBidirectional(newNode, neighborNode, level, maxConn);
                    }
                }

                if (!candidates.isEmpty()) {
                    currObj = nodes.get(candidates.get(0).id);
                }
            }

            if (insertLevel > maxLevel) {
                maxLevel = insertLevel;
                entryPoint = newNode;
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Removes an item from the index.
     */
    public void remove(T id) {
        rwLock.writeLock().lock();
        try {
            Node<T> node = nodes.remove(id);
            if (node == null) return;

            // Clean up connections
            for (int l = 0; l <= node.level; l++) {
                for (T neighborId : node.connections.get(l)) {
                    Node<T> neighborNode = nodes.get(neighborId);
                    if (neighborNode != null && l <= neighborNode.level) {
                        neighborNode.connections.get(l).remove(id);
                    }
                }
            }

            if (entryPoint != null && entryPoint.id.equals(id)) {
                if (nodes.isEmpty()) {
                    entryPoint = null;
                    maxLevel = -1;
                } else {
                    // Pick any remaining node with the highest level as new entry point
                    Node<T> newEntry = null;
                    int highest = -1;
                    for (Node<T> n : nodes.values()) {
                        if (n.level > highest) {
                            highest = n.level;
                            newEntry = n;
                        }
                    }
                    entryPoint = newEntry;
                    maxLevel = highest;
                }
            }
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void connectBidirectional(Node<T> nodeA, Node<T> nodeB, int level, int maxConn) {
        List<T> connsA = nodeA.connections.get(level);
        if (!connsA.contains(nodeB.id)) {
            connsA.add(nodeB.id);
            if (connsA.size() > maxConn) {
                shrinkConnections(nodeA, level, maxConn);
            }
        }

        List<T> connsB = nodeB.connections.get(level);
        if (!connsB.contains(nodeA.id)) {
            connsB.add(nodeA.id);
            if (connsB.size() > maxConn) {
                shrinkConnections(nodeB, level, maxConn);
            }
        }
    }

    private void shrinkConnections(Node<T> node, int level, int maxConn) {
        List<T> conns = node.connections.get(level);
        if (conns.size() <= maxConn) return;

        List<Neighbor<T>> scored = new ArrayList<>(conns.size());
        for (T neighborId : conns) {
            Node<T> neighbor = nodes.get(neighborId);
            if (neighbor != null) {
                double sim = similarity(node.vector, neighbor.vector);
                scored.add(new Neighbor<>(neighborId, sim));
            }
        }
        scored.sort(Comparator.naturalOrder()); // highest similarity first

        conns.clear();
        for (int i = 0; i < Math.min(maxConn, scored.size()); i++) {
            conns.add(scored.get(i).id);
        }
    }

    private Node<T> findGreedyNearestAtLayer(Node<T> start, float[] queryVec, int level) {
        Node<T> curr = start;
        double maxSim = similarity(curr.vector, queryVec);
        boolean changed = true;

        while (changed) {
            changed = false;
            List<T> neighbors = curr.connections.get(level);
            for (T nId : neighbors) {
                Node<T> nNode = nodes.get(nId);
                if (nNode != null) {
                    double sim = similarity(nNode.vector, queryVec);
                    if (sim > maxSim) {
                        maxSim = sim;
                        curr = nNode;
                        changed = true;
                    }
                }
            }
        }
        return curr;
    }

    /**
     * Search layer using best-first search maintaining candidate set of size ef.
     */
    private List<Neighbor<T>> searchLayer(Node<T> entry, float[] queryVec, int ef, int level) {
        Set<T> visited = new HashSet<>();
        // Max-heap of candidates to explore (ordered by distance/similarity): peek = lowest similarity among best
        PriorityQueue<Neighbor<T>> candidates = new PriorityQueue<>(Comparator.comparingDouble(Neighbor::similarity));
        // Result set: keep the ef best elements
        PriorityQueue<Neighbor<T>> nearest = new PriorityQueue<>(Comparator.comparingDouble(Neighbor::similarity));

        double entrySim = similarity(entry.vector, queryVec);
        Neighbor<T> entryNeighbor = new Neighbor<>(entry.id, entrySim);

        visited.add(entry.id);
        candidates.add(entryNeighbor);
        nearest.add(entryNeighbor);

        while (!candidates.isEmpty()) {
            Neighbor<T> curr = candidates.poll();
            Neighbor<T> worstNearest = nearest.peek();

            if (worstNearest != null && curr.similarity < worstNearest.similarity && nearest.size() >= ef) {
                break;
            }

            Node<T> currNode = nodes.get(curr.id);
            if (currNode == null || level >= currNode.connections.size()) continue;

            for (T neighborId : currNode.connections.get(level)) {
                if (visited.add(neighborId)) {
                    Node<T> neighborNode = nodes.get(neighborId);
                    if (neighborNode != null) {
                        double sim = similarity(neighborNode.vector, queryVec);
                        Neighbor<T> nObj = new Neighbor<>(neighborId, sim);

                        if (nearest.size() < ef || sim > Objects.requireNonNull(nearest.peek()).similarity) {
                            candidates.add(nObj);
                            nearest.add(nObj);
                            if (nearest.size() > ef) {
                                nearest.poll();
                            }
                        }
                    }
                }
            }
        }

        List<Neighbor<T>> result = new ArrayList<>(nearest);
        result.sort(Comparator.naturalOrder()); // descending similarity
        return result;
    }

    private List<Neighbor<T>> selectNeighbors(List<Neighbor<T>> candidates, int max) {
        if (candidates.size() <= max) return candidates;
        return new ArrayList<>(candidates.subList(0, max));
    }

    /**
     * Search top-K approximate nearest neighbors for a query vector.
     */
    public List<Neighbor<T>> search(float[] queryVec, int k) {
        rwLock.readLock().lock();
        try {
            if (entryPoint == null || nodes.isEmpty()) {
                return Collections.emptyList();
            }

            Node<T> currObj = entryPoint;
            int currLevel = maxLevel;

            // 1. Greedy descent down to layer 1
            while (currLevel > 0) {
                currObj = findGreedyNearestAtLayer(currObj, queryVec, currLevel);
                currLevel--;
            }

            // 2. Perform search at layer 0 with efSearch
            int ef = Math.max(efSearch, k);
            List<Neighbor<T>> candidates = searchLayer(currObj, queryVec, ef, 0);

            // 3. Return top-K
            int resultSize = Math.min(k, candidates.size());
            return new ArrayList<>(candidates.subList(0, resultSize));
        } finally {
            rwLock.readLock().unlock();
        }
    }
}
