package dev.local.dlq.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dlq_messages")
public class DlqMessage {
    @Id
    private UUID id;

    @Column(nullable = false)
    private String eventId;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Column(nullable = false)
    private String failureReason;

    @Column(nullable = false)
    private int attempts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DlqStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant reprocessedAt;

    protected DlqMessage() {
    }

    public DlqMessage(String eventId, String payload, String failureReason, int attempts) {
        this.eventId = eventId;
        this.payload = payload;
        this.failureReason = failureReason;
        this.attempts = attempts;
        this.status = DlqStatus.OPEN;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEventId() { return eventId; }
    public String getPayload() { return payload; }
    public String getFailureReason() { return failureReason; }
    public int getAttempts() { return attempts; }
    public DlqStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getReprocessedAt() { return reprocessedAt; }

    public void markReprocessing() {
        status = DlqStatus.REPROCESSING;
        reprocessedAt = Instant.now();
    }

    public void markReprocessed() {
        status = DlqStatus.REPROCESSED;
        reprocessedAt = Instant.now();
    }

    public void markOpen() {
        status = DlqStatus.OPEN;
        reprocessedAt = null;
    }
}
