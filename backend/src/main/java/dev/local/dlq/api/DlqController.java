package dev.local.dlq.api;

import dev.local.dlq.model.DlqMessage;
import dev.local.dlq.model.EventEnvelope;
import dev.local.dlq.model.ReplayAudit;
import dev.local.dlq.repository.DlqMessageRepository;
import dev.local.dlq.repository.ReplayAuditRepository;
import dev.local.dlq.service.EventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class DlqController {
    private final DlqMessageRepository messages;
    private final ReplayAuditRepository audit;
    private final EventPublisher publisher;

    public DlqController(DlqMessageRepository messages, ReplayAuditRepository audit,
                         EventPublisher publisher) {
        this.messages = messages;
        this.audit = audit;
        this.publisher = publisher;
    }

    @GetMapping("/dlq")
    public List<DlqMessage> listMessages() {
        return messages.findAllByOrderByCreatedAtDesc();
    }

    @PostMapping("/events")
    public Map<String, String> publishEvent(@RequestBody EventRequest request) {
        String eventId = UUID.randomUUID().toString();
        publisher.publish(new EventEnvelope(eventId, request.payload(), request.simulateFailure()));
        return Map.of("eventId", eventId, "status", "PUBLISHED");
    }

    @PostMapping("/dlq/{id}/reprocess")
    public Map<String, String> reprocess(@PathVariable UUID id, @RequestBody ReplayRequest request) {
        DlqMessage message = messages.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DLQ message not found"));
        if (message.getStatus().name().equals("REPROCESSING")) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Message is already being reprocessed");
        }
        if (request.reason() == null || request.reason().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Replay reason is required");
        }

        String payload = request.payload() == null ? message.getPayload() : request.payload();
        boolean simulateFailure = Boolean.TRUE.equals(request.simulateFailure());
        message.markReprocessing();
        messages.save(message);
        audit.save(new ReplayAudit(message.getEventId(), "REPROCESS_REQUESTED", "local-admin", request.reason()));
        publisher.publish(new EventEnvelope(message.getEventId(), payload, simulateFailure));
        return Map.of("eventId", message.getEventId(), "status", "REPROCESSING");
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
