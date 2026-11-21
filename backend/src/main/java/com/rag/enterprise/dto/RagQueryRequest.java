package com.rag.enterprise.dto;

import java.util.List;

public class RagQueryRequest {
    private String query;
    private int topK = 4;
    private String userRole = "USER"; // USER, ADMIN

    public RagQueryRequest() {}

    public RagQueryRequest(String query) {
        this.query = query;
    }

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public int getTopK() { return topK; }
    public void setTopK(int topK) { this.topK = topK; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }
}
