package dev.local.dlq.service;

import dev.local.dlq.config.RabbitConfig;
import dev.local.dlq.model.EventEnvelope;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventPublisher {
    private final RabbitTemplate rabbitTemplate;

    public EventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(EventEnvelope event) {
        rabbitTemplate.convertAndSend(
                RabbitConfig.MAIN_EXCHANGE,
                RabbitConfig.EVENTS_ROUTING_KEY,
                event);
    }
}
