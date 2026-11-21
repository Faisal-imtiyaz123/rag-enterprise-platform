package com.rag.enterprise.dto;

import java.util.List;

public class RagQueryResponse {
    private String answer;
    private boolean guardrailPassed;
    private String guardrailViolation;
    private List<RetrievedCitation> citations;
    private long executionTimeMs;

    public record RetrievedCitation(String documentName, int chunkIndex, double relevanceScore, String snippet) {}

    public RagQueryResponse() {}

    public static RagQueryResponse blocked(String violationReason) {
        RagQueryResponse res = new RagQueryResponse();
        res.setGuardrailPassed(false);
        res.setGuardrailViolation(violationReason);
        res.setAnswer("Query blocked by security guardrails: " + violationReason);
        res.setCitations(List.of());
        return res;
    }

    // Getters and Setters
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }

    public boolean isGuardrailPassed() { return guardrailPassed; }
    public void setGuardrailPassed(boolean guardrailPassed) { this.guardrailPassed = guardrailPassed; }

    public String getGuardrailViolation() { return guardrailViolation; }
    public void setGuardrailViolation(String guardrailViolation) { this.guardrailViolation = guardrailViolation; }

    public List<RetrievedCitation> getCitations() { return citations; }
    public void setCitations(List<RetrievedCitation> citations) { this.citations = citations; }

    public long getExecutionTimeMs() { return executionTimeMs; }
    public void setExecutionTimeMs(long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
}
