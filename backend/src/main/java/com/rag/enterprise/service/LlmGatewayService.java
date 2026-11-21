package com.rag.enterprise.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class LlmGatewayService {

    @Value("${rag.llm.provider:GEMINI}")
    private String provider;

    @Value("${rag.llm.gemini.api-key:}")
    private String geminiApiKey;

    @Value("${rag.llm.gemini.model:gemini-2.5-flash-lite}")
    private String geminiModel;

    @Value("${rag.llm.openai.api-key:}")
    private String openaiApiKey;

    @Value("${rag.llm.openai.model:gpt-4o-mini}")
    private String openaiModel;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generateRAGResponse(String userQuery, List<String> retrievedContexts) {
        StringBuilder contextBlock = new StringBuilder();
        for (int i = 0; i < retrievedContexts.size(); i++) {
            contextBlock.append("[").append(i + 1).append("] ").append(retrievedContexts.get(i)).append("\n\n");
        }

        String systemPrompt = """
            You are an Enterprise Knowledge Assistant.
            Answer the user's inquiry strictly and accurately using ONLY the provided verified context passages below.
            Cite the sources using numbers like [1], [2] whenever referencing facts.
            If the context does not contain enough information to answer the question, politely say:
            "I could not find sufficient information in the ingested enterprise documents to answer this question."
            Do not hallucinate or make assumptions beyond the text.
            """;

        String userPrompt = "Context Passages:\n" + contextBlock + "\nUser Question: " + userQuery;

        // Try configured cloud LLM; fall back to hybrid deterministic synthesis if offline or key absent
        try {
            if ("GEMINI".equalsIgnoreCase(provider) && geminiApiKey != null && !geminiApiKey.isBlank()) {
                return callGemini(systemPrompt + "\n\n" + userPrompt);
            } else if ("OPENAI".equalsIgnoreCase(provider) && openaiApiKey != null && !openaiApiKey.isBlank()) {
                return callOpenAI(systemPrompt, userPrompt);
            }
        } catch (Exception e) {
            System.err.println("Cloud LLM invocation failed, falling back to local synthesis engine: " + e.getMessage());
        }

        return fallbackSynthesis(userQuery, retrievedContexts);
    }

    private String callGemini(String prompt) throws Exception {
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + geminiModel + ":generateContent?key=" + geminiApiKey;

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
    }

    private String callOpenAI(String systemPrompt, String userPrompt) throws Exception {
        String url = "https://api.openai.com/v1/chat/completions";

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        );

        Map<String, Object> requestBody = Map.of(
                "model", openaiModel,
                "messages", messages,
                "temperature", 0.2
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

        JsonNode root = objectMapper.readTree(response.getBody());
        return root.path("choices").get(0).path("message").path("content").asText();
    }

    /**
     * Resilient Fallback Engine: Extractive synthesis with context citation
     */
    private String fallbackSynthesis(String userQuery, List<String> retrievedContexts) {
        if (retrievedContexts.isEmpty()) {
            return "I could not find sufficient information in the ingested enterprise documents to answer this question.";
        }

        StringBuilder response = new StringBuilder();
        response.append("Based on the verified enterprise documents:\n\n");
        for (int i = 0; i < retrievedContexts.size(); i++) {
            String snippet = retrievedContexts.get(i).trim();
            if (snippet.length() > 300) {
                snippet = snippet.substring(0, 300) + "...";
            }
            response.append("• ").append(snippet).append(" [Source ").append(i + 1).append("]\n\n");
        }
        response.append("Summary: Key details addressing \"").append(userQuery).append("\" have been retrieved from the ingested knowledge base above.");
        return response.toString();
    }
}
