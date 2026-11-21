package com.rag.enterprise;

import com.rag.enterprise.service.DocumentIngestionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RagEnterpriseApplication {

    public static void main(String[] args) {
        SpringApplication.run(RagEnterpriseApplication.class, args);
    }

    @Bean
    public CommandLineRunner initDefaultKnowledge(DocumentIngestionService ingestionService) {
        return args -> {
            // Seed verified enterprise sample documentation on startup
            ingestionService.ingestRawText(
                "Enterprise RAG Architectural Specification",
                """
                Enterprise Retrieval-Augmented Generation (RAG) Architecture Overview:
                The RAG platform employs a hybrid vector and semantic search pipeline designed for high-availability enterprise environments.
                1. Ingestion Pipeline: Documents (PDF, DOCX, TXT, Markdown) are processed into deterministic text chunks with 500-character windows and 100-character overlaps.
                2. Vector Embeddings: Text passages are tokenized and mapped to normalized 128-dimensional vector spaces using TF-IDF term weighting.
                3. Guardrailing: Prior to vector retrieval, every query undergoes a dual-layer security check. Layer 1 detects prompt injection, jailbreaks (e.g. DAN prompts), and system override attacks. Layer 2 identifies and masks PII including emails, phone numbers, SSNs, and credit card numbers.
                4. LLM Synthesis: Relevant passages are retrieved using cosine similarity scoring and passed to Google Gemini / OpenAI models with strict groundedness constraints.
                5. Audit Logging: Every interaction is recorded with latency metrics, user roles, and guardrail trigger flags.
                """,
                "Core system architecture and security specifications.",
                "system-bootstrap"
            );

            ingestionService.ingestRawText(
                "Corporate Security and Compliance Policy",
                """
                Corporate Security, Access Control, and Compliance Policy:
                - Role-Based Access Control (RBAC): Administrators must present a valid X-Admin-Key header for document management, text ingestion, and viewing compliance audit logs.
                - Document Deletion: Only administrators are authorized to purge indexed knowledge artifacts. Deletion recursively cascades across all document chunks.
                - Rate Limiting and SLA: User queries are prioritized with an SLA under 250ms for local vector retrieval and 1.5s for cloud LLM synthesis.
                - Data Privacy: All customer identifiable data (PII) is automatically masked before passing into external LLM gateways.
                """,
                "Company compliance, RBAC, and data privacy protocols.",
                "compliance-office"
            );
        };
    }
}
