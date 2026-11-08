package dev.local.dlq.api;

public record ReplayRequest(String payload, Boolean simulateFailure, String reason) {
}
