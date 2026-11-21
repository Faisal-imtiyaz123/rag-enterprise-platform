package com.rag.enterprise.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String userRole; // ADMIN, USER, ANONYMOUS

    @Column(columnDefinition = "TEXT")
    private String userQuery;

    private boolean guardrailBlocked;

    private String guardrailReason;

    private long responseTimeMs;

    private LocalDateTime timestamp;

    public AuditLogEntity() {
        this.timestamp = LocalDateTime.now();
    }

    public AuditLogEntity(String userRole, String userQuery, boolean guardrailBlocked, String guardrailReason, long responseTimeMs) {
        this.userRole = userRole;
        this.userQuery = userQuery;
        this.guardrailBlocked = guardrailBlocked;
        this.guardrailReason = guardrailReason;
        this.responseTimeMs = responseTimeMs;
        this.timestamp = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUserRole() { return userRole; }
    public void setUserRole(String userRole) { this.userRole = userRole; }

    public String getUserQuery() { return userQuery; }
    public void setUserQuery(String userQuery) { this.userQuery = userQuery; }

    public boolean isGuardrailBlocked() { return guardrailBlocked; }
    public void setGuardrailBlocked(boolean guardrailBlocked) { this.guardrailBlocked = guardrailBlocked; }

    public String getGuardrailReason() { return guardrailReason; }
    public void setGuardrailReason(String guardrailReason) { this.guardrailReason = guardrailReason; }

    public long getResponseTimeMs() { return responseTimeMs; }
    public void setResponseTimeMs(long responseTimeMs) { this.responseTimeMs = responseTimeMs; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
