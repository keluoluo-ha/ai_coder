package com.hhk.aicoder.build;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.policy.SimpleRetryPolicy;
import java.util.Map;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "aicoder.build.queue", name = "type", havingValue = "rabbitmq")
public class RabbitMqBuildQueueConfiguration {
    @Bean
    Declarables buildQueueTopology() {
        DirectExchange exchange = new DirectExchange(RabbitMqBuildQueueService.EXCHANGE, true, false);
        DirectExchange dlx = new DirectExchange(RabbitMqBuildQueueService.DLX, true, false);
        Queue queue = QueueBuilder.durable(RabbitMqBuildQueueService.QUEUE)
                .deadLetterExchange(RabbitMqBuildQueueService.DLX)
                .deadLetterRoutingKey("dlq")
                .withArgument("x-single-active-consumer", true)
                .build();
        Queue dlq = QueueBuilder.durable(RabbitMqBuildQueueService.DLQ).build();
        return new Declarables(exchange, dlx, queue, dlq,
                BindingBuilder.bind(queue).to(exchange).with(RabbitMqBuildQueueService.ROUTING_KEY),
                BindingBuilder.bind(dlq).to(dlx).with("dlq"));
    }

    @Bean
    SimpleMessageListenerContainer buildRabbitListenerContainer(
            ConnectionFactory connectionFactory,
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            RabbitProperties properties, RabbitMqBuildQueueService queue) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        configurer.configure(factory, connectionFactory);
        // Enforce ordering even if environment-wide listener defaults enable parallelism.
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        factory.setConcurrentConsumers(1);
        factory.setMaxConcurrentConsumers(1);
        factory.setPrefetchCount(1);
        factory.setDefaultRequeueRejected(false);
        var retry = properties.getListener().getSimple().getRetry();
        if (retry.isEnabled()) {
            factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                    .retryPolicy(new SimpleRetryPolicy(retry.getMaxAttempts(),
                            Map.of(Exception.class, true, AmqpRejectAndDontRequeueException.class, false), true))
                    .backOffOptions(retry.getInitialInterval().toMillis(), retry.getMultiplier(),
                            retry.getMaxInterval().toMillis())
                    .recoverer((message, cause) -> {
                        // rejectManual=true is essential: the default recoverer is insufficient
                        // for malformed messages/timeouts with MANUAL acknowledgement.
                        throw new AmqpRejectAndDontRequeueException("Build retries exhausted", true, cause);
                    }).build());
        }
        SimpleMessageListenerContainer container = factory.createListenerContainer();
        container.setQueueNames(RabbitMqBuildQueueService.QUEUE);
        container.setMessageListener((ChannelAwareMessageListener) queue::onMessage);
        return container;
    }
}
