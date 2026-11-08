package dev.local.dlq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class RabbitConfig {
    public static final String MAIN_EXCHANGE = "events.exchange";
    public static final String MAIN_QUEUE = "events.main";
    public static final String RETRY_EXCHANGE = "events.retry.exchange";
    public static final String RETRY_QUEUE_1 = "events.retry.1";
    public static final String RETRY_QUEUE_2 = "events.retry.2";
    public static final String DLQ_EXCHANGE = "events.dlq.exchange";
    public static final String DLQ_QUEUE = "events.dlq";
    public static final String EVENTS_ROUTING_KEY = "events";
    public static final String DLQ_ROUTING_KEY = "dead";

    @Bean
    DirectExchange mainExchange() {
        return new DirectExchange(MAIN_EXCHANGE);
    }

    @Bean
    DirectExchange retryExchange() {
        return new DirectExchange(RETRY_EXCHANGE);
    }

    @Bean
    DirectExchange dlqExchange() {
        return new DirectExchange(DLQ_EXCHANGE);
    }

    @Bean
    Queue mainQueue() {
        return QueueBuilder.durable(MAIN_QUEUE)
                .deadLetterExchange(DLQ_EXCHANGE)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    Binding mainBinding(Queue mainQueue, DirectExchange mainExchange) {
        return BindingBuilder.bind(mainQueue).to(mainExchange).with(EVENTS_ROUTING_KEY);
    }

    @Bean
    Queue retryQueue1(@Value("${app.retry.delay-1}") long delay) {
        return retryQueue(RETRY_QUEUE_1, delay);
    }

    @Bean
    Queue retryQueue2(@Value("${app.retry.delay-2}") long delay) {
        return retryQueue(RETRY_QUEUE_2, delay);
    }

    private Queue retryQueue(String name, long delay) {
        return QueueBuilder.durable(name)
                .withArguments(Map.of(
                        "x-message-ttl", delay,
                        "x-dead-letter-exchange", MAIN_EXCHANGE,
                        "x-dead-letter-routing-key", EVENTS_ROUTING_KEY))
                .build();
    }

    @Bean
    Binding retryBinding1(Queue retryQueue1, DirectExchange retryExchange) {
        return BindingBuilder.bind(retryQueue1).to(retryExchange).with("retry.1");
    }

    @Bean
    Binding retryBinding2(Queue retryQueue2, DirectExchange retryExchange) {
        return BindingBuilder.bind(retryQueue2).to(retryExchange).with("retry.2");
    }

    @Bean
    Queue dlqQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    Binding dlqBinding(Queue dlqQueue, DirectExchange dlqExchange) {
        return BindingBuilder.bind(dlqQueue).to(dlqExchange).with(DLQ_ROUTING_KEY);
    }

    @Bean
    Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                  Jackson2JsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        template.setMandatory(true);
        return template;
    }
}
