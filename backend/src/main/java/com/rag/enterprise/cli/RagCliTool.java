package com.rag.enterprise.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/**
 * Enterprise RAG Interactive Command-Line Tool
 * Allows querying documents, checking health, inspecting audit logs, and uploading documents directly from the terminal.
 */
public class RagCliTool {

    private static final String DEFAULT_BASE_URL = "http://localhost:8080/api";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static void main(String[] args) {
        String baseUrl = System.getenv("RAG_API_URL") != null ? System.getenv("RAG_API_URL") : DEFAULT_BASE_URL;
        String adminKey = System.getenv("RAG_ADMIN_KEY") != null ? System.getenv("RAG_ADMIN_KEY") : "adminkey123";

        // Check if single command mode passed via arguments
        if (args.length > 0) {
            handleCliArg(args, baseUrl, adminKey);
            return;
        }

        // Interactive Shell Mode
        printBanner();
        Scanner scanner = new Scanner(System.in);

        while (true) {
            System.out.print("\n\u001B[36mrag-cli>\u001B[0m ");
            if (!scanner.hasNextLine()) break;
            String line = scanner.nextLine().trim();

            if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                System.out.println("Exiting RAG CLI. Goodbye!");
                break;
            } else if (line.equalsIgnoreCase("help")) {
                printHelp();
            } else if (line.equalsIgnoreCase("docs") || line.equalsIgnoreCase("documents")) {
                listDocuments(baseUrl);
            } else if (line.equalsIgnoreCase("health")) {
                checkHealth(baseUrl);
            } else if (line.equalsIgnoreCase("audit")) {
                viewAuditLogs(baseUrl, adminKey);
            } else if (line.startsWith("ask ") || line.startsWith("query ")) {
                String query = line.substring(line.indexOf(' ') + 1).trim();
                askQuery(baseUrl, query);
            } else if (line.startsWith("upload ")) {
                String filePath = line.substring(7).trim();
                uploadFile(baseUrl, adminKey, filePath);
            } else if (!line.isEmpty()) {
                // Default input treated as direct RAG query
                askQuery(baseUrl, line);
            }
        }
    }

    private static void handleCliArg(String[] args, String baseUrl, String adminKey) {
        String cmd = args[0];
        if (cmd.equalsIgnoreCase("--help") || cmd.equalsIgnoreCase("-h")) {
            printHelp();
        } else if (cmd.equalsIgnoreCase("docs")) {
            listDocuments(baseUrl);
        } else if (cmd.equalsIgnoreCase("health")) {
            checkHealth(baseUrl);
        } else if (cmd.equalsIgnoreCase("audit")) {
            viewAuditLogs(baseUrl, adminKey);
        } else if (cmd.equalsIgnoreCase("ask") && args.length > 1) {
            String query = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            askQuery(baseUrl, query);
        } else if (cmd.equalsIgnoreCase("upload") && args.length > 1) {
            uploadFile(baseUrl, adminKey, args[1]);
        } else {
            // Treat full argument list as query
            askQuery(baseUrl, String.join(" ", args));
        }
    }

    private static void printBanner() {
        System.out.println("\u001B[32m===============================================================\u001B[0m");
        System.out.println("\u001B[1m\u001B[34m       ENTERPRISE RAG PLATFORM - INTERACTIVE CLI\u001B[0m");
        System.out.println("  Semantically search ingested knowledge with security guardrails");
        System.out.println("\u001B[32m===============================================================\u001B[0m");
        System.out.println("Commands:");
        System.out.println("  ask <question>   - Run a RAG query with citations");
        System.out.println("  docs             - List all ingested documents");
        System.out.println("  upload <path>    - Ingest PDF, DOCX, or text file (Admin)");
        System.out.println("  audit            - View compliance audit log (Admin)");
        System.out.println("  health           - Check API and guardrail status");
        System.out.println("  exit             - Exit CLI");
    }

    private static void printHelp() {
        System.out.println("Usage:");
        System.out.println("  Interactive Mode: java -cp target/rag-enterprise-backend-1.0.0.jar com.rag.enterprise.cli.RagCliTool");
        System.out.println("  Direct Query:     rag-cli ask \"What is the architecture overview?\"");
        System.out.println("  Direct Upload:    rag-cli upload /path/to/handbook.pdf");
    }

    private static void askQuery(String baseUrl, String question) {
        System.out.println("\u001B[33m[Querying RAG Engine...]\u001B[0m");
        try {
            URL url = new URL(baseUrl + "/query");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            String jsonPayload = MAPPER.writeValueAsString(new QueryPayload(question, 4, "USER"));
            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();
            JsonNode root = MAPPER.readTree(is);

            boolean passed = root.path("guardrailPassed").asBoolean(true);
            long latency = root.path("executionTimeMs").asLong(0);

            if (!passed) {
                System.out.println("\u001B[31m[BLOCKED BY GUARDRAILS]:\u001B[0m " + root.path("guardrailViolation").asText());
                return;
            }

            System.out.println("\n\u001B[1m\u001B[32m=== ANSWER ===\u001B[0m");
            System.out.println(root.path("answer").asText());

            JsonNode citations = root.path("citations");
            if (citations.isArray() && citations.size() > 0) {
                System.out.println("\n\u001B[34m--- Verified Citations (" + citations.size() + ") ---\u001B[0m");
                for (int i = 0; i < citations.size(); i++) {
                    JsonNode c = citations.get(i);
                    System.out.printf("[%d] %s (chunk #%d, similarity: %.3f)\n    Snippet: \"%s\"\n",
                            i + 1,
                            c.path("documentName").asText(),
                            c.path("chunkIndex").asInt(),
                            c.path("relevanceScore").asDouble(),
                            c.path("snippet").asText());
                }
            }
            System.out.println("\u001B[90m(Response latency: " + latency + "ms)\u001B[0m");

        } catch (Exception e) {
            System.err.println("\u001B[31mError connecting to backend: " + e.getMessage() + "\u001B[0m");
        }
    }

    private static void listDocuments(String baseUrl) {
        try {
            URL url = new URL(baseUrl + "/documents");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            JsonNode docs = MAPPER.readTree(conn.getInputStream());
            System.out.println("\n\u001B[34m=== INGESTED ENTERPRISE DOCUMENTS (" + docs.size() + ") ===\u001B[0m");
            for (JsonNode doc : docs) {
                System.out.printf("• [ID: %d] %s (%s, %d chunks, uploaded by %s)\n  Description: %s\n",
                        doc.path("id").asLong(),
                        doc.path("filename").asText(),
                        doc.path("contentType").asText(),
                        doc.path("chunkCount").asInt(),
                        doc.path("uploadedBy").asText(),
                        doc.path("description").asText());
            }
        } catch (Exception e) {
            System.err.println("Error listing documents: " + e.getMessage());
        }
    }

    private static void viewAuditLogs(String baseUrl, String adminKey) {
        try {
            URL url = new URL(baseUrl + "/admin/audit-logs");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Admin-Key", adminKey);

            if (conn.getResponseCode() == 401) {
                System.out.println("\u001B[31mUnauthorized: Invalid X-Admin-Key.\u001B[0m");
                return;
            }

            JsonNode logs = MAPPER.readTree(conn.getInputStream());
            System.out.println("\n\u001B[35m=== COMPLIANCE AUDIT LOGS (" + logs.size() + ") ===\u001B[0m");
            for (JsonNode log : logs) {
                boolean blocked = log.path("guardrailBlocked").asBoolean();
                String status = blocked ? "\u001B[31mBLOCKED\u001B[0m" : "\u001B[32mPASSED\u001B[0m";
                System.out.printf("[%s] [%s] %s (Time: %dms) - Query: \"%s\"\n",
                        log.path("timestamp").asText(),
                        log.path("userRole").asText(),
                        status,
                        log.path("responseTimeMs").asLong(),
                        log.path("userQuery").asText());
                if (blocked) {
                    System.out.println("    Violation: " + log.path("guardrailReason").asText());
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching audit logs: " + e.getMessage());
        }
    }

    private static void checkHealth(String baseUrl) {
        try {
            URL url = new URL(baseUrl + "/health");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            JsonNode root = MAPPER.readTree(conn.getInputStream());
            System.out.println("\u001B[32mBackend Status: " + root.path("status").asText() +
                    " | Guardrails: " + root.path("guardrails").asText() +
                    " | Version: " + root.path("version").asText() + "\u001B[0m");
        } catch (Exception e) {
            System.err.println("\u001B[31mBackend is not reachable at " + baseUrl + ": " + e.getMessage() + "\u001B[0m");
        }
    }

    private static void uploadFile(String baseUrl, String adminKey, String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("\u001B[31mFile does not exist: " + filePath + "\u001B[0m");
            return;
        }

        System.out.println("Uploading " + file.getName() + " (" + file.length() + " bytes)...");
        String boundary = "===" + System.currentTimeMillis() + "===";
        try {
            URL url = new URL(baseUrl + "/admin/upload");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("X-Admin-Key", adminKey);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            conn.setDoOutput(true);

            try (OutputStream output = conn.getOutputStream();
                 PrintWriter writer = new PrintWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8), true)) {

                // File part
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"file\"; filename=\"").append(file.getName()).append("\"\r\n");
                writer.append("Content-Type: application/octet-stream\r\n\r\n").flush();

                try (FileInputStream inputStream = new FileInputStream(file)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                    output.flush();
                }

                writer.append("\r\n");
                // Description part
                writer.append("--").append(boundary).append("\r\n");
                writer.append("Content-Disposition: form-data; name=\"description\"\r\n\r\n");
                writer.append("Uploaded via RAG CLI Tool\r\n");

                writer.append("--").append(boundary).append("--\r\n").flush();
            }

            int status = conn.getResponseCode();
            if (status == 201) {
                JsonNode res = MAPPER.readTree(conn.getInputStream());
                System.out.println("\u001B[32mSuccessfully ingested document: ID " + res.path("id").asLong() +
                        " with " + res.path("chunkCount").asInt() + " text chunks!\u001B[0m");
            } else {
                System.err.println("\u001B[31mUpload failed (Status " + status + ")\u001B[0m");
            }

        } catch (Exception e) {
            System.err.println("Error uploading file: " + e.getMessage());
        }
    }

    private record QueryPayload(String query, int topK, String userRole) {}
}
