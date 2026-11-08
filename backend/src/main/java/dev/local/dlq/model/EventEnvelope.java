package dev.local.dlq.model;

public record EventEnvelope(String eventId, String payload, boolean simulateFailure) {
}
