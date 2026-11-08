package dev.local.dlq.api;

public record EventRequest(String payload, boolean simulateFailure) {
}
