package com.rag.enterprise.guardrail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class GuardrailService {

    @Value("${rag.guardrails.enabled:true}")
    private boolean enabled;

    @Value("${rag.guardrails.block-toxic:true}")
    private boolean blockToxic;

    @Value("${rag.guardrails.block-jailbreak:true}")
    private boolean blockJailbreak;

    @Value("${rag.guardrails.mask-pii:true}")
    private boolean maskPii;

    @Value("${rag.guardrails.max-query-length:500}")
    private int maxQueryLength;

    // Jailbreak injection patterns (Prompt injection attacks)
    private static final List<Pattern> JAILBREAK_PATTERNS = Arrays.asList(
            Pattern.compile("ignore (all|previous|prior) instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (all|previous) (rules|instructions)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now (DAN|unfiltered|jailbroken)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("act as an unfiltered (ai|model)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system prompt[:\\s]+reveal", Pattern.CASE_INSENSITIVE),
            Pattern.compile("bypass safety protocols", Pattern.CASE_INSENSITIVE),
            Pattern.compile("repeat your system instructions", Pattern.CASE_INSENSITIVE)
    );

    // Malicious or toxic intent keywords
    private static final Set<String> TOXIC_KEYWORDS = new HashSet<>(Arrays.asList(
            "ddos", "exploit", "keylogger", "ransomware", "malware",
            "sql injection", "make a bomb", "suicide", "credit card dump"
    ));

    // PII regex patterns: Email, Phone numbers, SSN, Credit card
    private static final Pattern EMAIL_PATTERN = Pattern.compile("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(\\+?[0-9]{1,3}[-\\s]?)?\\(?([0-9]{3})\\)?[-\\s]?([0-9]{3})[-\\s]?([0-9]{4})");
    private static final Pattern SSN_PATTERN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern CC_PATTERN = Pattern.compile("\\b(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|3[47][0-9]{13})\\b");

    public record GuardrailResult(boolean passed, String sanitizedText, String violationReason) {}

    public GuardrailResult validateAndSanitize(String query) {
        if (!enabled) {
            return new GuardrailResult(true, query, null);
        }

        if (query == null || query.trim().isEmpty()) {
            return new GuardrailResult(false, null, "Query cannot be empty.");
        }

        if (query.length() > maxQueryLength) {
            return new GuardrailResult(false, null, 
                "Query exceeds maximum allowed length of " + maxQueryLength + " characters.");
        }

        // 1. Check for prompt injection / jailbreak attempts
        if (blockJailbreak) {
            for (Pattern pattern : JAILBREAK_PATTERNS) {
                if (pattern.matcher(query).find()) {
                    return new GuardrailResult(false, null, 
                        "Security Guardrail: Potential prompt injection or system override detected.");
                }
            }
        }

        // 2. Check for toxic / high-risk keywords
        if (blockToxic) {
            String lower = query.toLowerCase();
            for (String keyword : TOXIC_KEYWORDS) {
                if (lower.contains(keyword)) {
                    return new GuardrailResult(false, null, 
                        "Safety Guardrail: Request contains prohibited or malicious topics (" + keyword + ").");
                }
            }
        }

        // 3. PII Detection and Masking
        String sanitized = query;
        if (maskPii) {
            sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("[REDACTED_EMAIL]");
            sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("[REDACTED_PHONE]");
            sanitized = SSN_PATTERN.matcher(sanitized).replaceAll("[REDACTED_SSN]");
            sanitized = CC_PATTERN.matcher(sanitized).replaceAll("[REDACTED_CARD]");
        }

        return new GuardrailResult(true, sanitized, null);
    }
}
