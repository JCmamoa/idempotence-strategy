package dev.local.dlq.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.local.dlq.config.RabbitConfig;
import dev.local.dlq.model.DlqMessage;
import dev.local.dlq.model.DlqStatus;
import dev.local.dlq.model.EventEnvelope;
import dev.local.dlq.repository.DlqMessageRepository;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class EventWorker {
    private final ObjectMapper objectMapper;
    private final RabbitTemplate rabbitTemplate;
    private final DlqMessageRepository messages;

    public EventWorker(ObjectMapper objectMapper, RabbitTemplate rabbitTemplate,
                       DlqMessageRepository messages) {
        this.objectMapper = objectMapper;
        this.rabbitTemplate = rabbitTemplate;
        this.messages = messages;
    }

    @RabbitListener(queues = RabbitConfig.MAIN_QUEUE)
    public void consume(Message message) {
        EventEnvelope event;
        try {
            event = objectMapper.readValue(message.getBody(), EventEnvelope.class);
            if (event.eventId() == null || event.eventId().isBlank()) {
                throw new IllegalArgumentException("eventId is required");
            }
        } catch (Exception exception) {
            throw new AmqpRejectAndDontRequeueException("Malformed event payload", exception);
        }

        if (event.simulateFailure()) {
            retryOrDeadLetter(message, event);
            return;
        }
        markSuccessfulReplay(event.eventId());
    }

    private void retryOrDeadLetter(Message message, EventEnvelope event) {
        int retryCount = numberHeader(message, "x-retry-count");
        String failure = "Simulated business rule failure";
        if (retryCount < 2) {
            int nextAttempt = retryCount + 1;
            String routingKey = "retry." + nextAttempt;
            Message retry = MessageBuilder.withBody(message.getBody())
                    .copyHeaders(message.getMessageProperties().getHeaders())
                    .setHeader("x-retry-count", nextAttempt)
                    .setHeader("x-exception-message", failure)
                    .build();
            rabbitTemplate.send(RabbitConfig.RETRY_EXCHANGE, routingKey, retry);
            return;
        }

        Message dead = MessageBuilder.withBody(message.getBody())
                .copyHeaders(message.getMessageProperties().getHeaders())
                .setHeader("x-retry-count", retryCount + 1)
                .setHeader("x-exception-message", failure)
                .build();
        rabbitTemplate.send(RabbitConfig.DLQ_EXCHANGE, RabbitConfig.DLQ_ROUTING_KEY, dead);
    }

    @RabbitListener(queues = RabbitConfig.DLQ_QUEUE)
    @Transactional
    public void recordDeadLetter(Message message) {
        String rawPayload = new String(message.getBody(), StandardCharsets.UTF_8);
        String eventId = eventId(rawPayload);
        int attempts = numberHeader(message, "x-retry-count");
        Object reasonHeader = message.getMessageProperties().getHeaders().get("x-exception-message");
        String reason = reasonHeader == null ? "Rejected malformed event" : reasonHeader.toString();

        List<DlqMessage> inProgress = messages.findAllByEventIdAndStatus(eventId, DlqStatus.REPROCESSING);
        inProgress.forEach(DlqMessage::markOpen);
        messages.saveAll(inProgress);
        messages.save(new DlqMessage(eventId, rawPayload, reason, attempts));
    }

    @Transactional
    public void markSuccessfulReplay(String eventId) {
        List<DlqMessage> inProgress = messages.findAllByEventIdAndStatus(eventId, DlqStatus.REPROCESSING);
        inProgress.forEach(DlqMessage::markReprocessed);
        messages.saveAll(inProgress);
    }

    private String eventId(String rawPayload) {
        try {
            EventEnvelope event = objectMapper.readValue(rawPayload, EventEnvelope.class);
            return event.eventId() == null || event.eventId().isBlank()
                    ? UUID.randomUUID().toString()
                    : event.eventId();
        } catch (Exception ignored) {
            return UUID.randomUUID().toString();
        }
    }

    private int numberHeader(Message message, String name) {
        Object value = message.getMessageProperties().getHeaders().get(name);
        if (value instanceof Number number) return number.intValue();
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
