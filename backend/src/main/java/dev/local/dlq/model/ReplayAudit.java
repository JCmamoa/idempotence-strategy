package dev.local.dlq.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "replay_audit")
public class ReplayAudit {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String actor;

    @Column(nullable = false, columnDefinition = "text")
    private String details;

    @Column(nullable = false)
    private Instant createdAt;

    protected ReplayAudit() {
    }

    public ReplayAudit(String eventId, String action, String actor, String details) {
        this.eventId = eventId;
        this.action = action;
        this.actor = actor;
        this.details = details;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }
}
