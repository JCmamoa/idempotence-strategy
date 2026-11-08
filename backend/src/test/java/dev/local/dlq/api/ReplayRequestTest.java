package dev.local.dlq.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReplayRequestTest {
    @Test
    void carriesAnOptionalPayloadAndReason() {
        ReplayRequest request = new ReplayRequest("{\"fixed\":true}", false, "Corrected invalid payload");

        assertEquals("{\"fixed\":true}", request.payload());
        assertEquals(false, request.simulateFailure());
        assertEquals("Corrected invalid payload", request.reason());
    }
}
