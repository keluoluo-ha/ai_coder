package com.hhk.aicoder.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Opt-in real broker test. The dedicated vhost must be created before running. */
@EnabledIfEnvironmentVariable(named = "P4_RABBITMQ_ACCEPTANCE", matches = "true")
class RabbitMqBrokerAcceptanceTest {
    @Test
    void realBrokerRoutesRetriesDeadlettersAndRecoversUnackedDelivery() throws Exception {
        var connection = new CachingConnectionFactory("localhost", 5672);
        connection.setUsername(System.getenv().getOrDefault("RABBITMQ_USERNAME", "guest"));
        connection.setPassword(System.getenv().getOrDefault("RABBITMQ_PASSWORD", "guest"));
        connection.setVirtualHost("p4-acceptance");
        connection.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        connection.setPublisherReturns(true);
        var rabbit = new RabbitTemplate(connection);
        rabbit.setMandatory(true);
        var admin = new RabbitAdmin(connection);
        var configuration = new RabbitMqBuildQueueConfiguration();
        var topology = configuration.buildQueueTopology();
        try {
            topology.getDeclarablesByType(Exchange.class).forEach(admin::declareExchange);
            topology.getDeclarablesByType(Queue.class).forEach(admin::declareQueue);
            topology.getDeclarablesByType(Binding.class).forEach(admin::declareBinding);
            // Only the explicitly named acceptance vhost is purged, never application queues.
            admin.purgeQueue(RabbitMqBuildQueueService.QUEUE, false);
            admin.purgeQueue(RabbitMqBuildQueueService.DLQ, false);
            var properties = new RabbitProperties();
            var retry = properties.getListener().getSimple().getRetry();
            retry.setEnabled(true);
            retry.setMaxAttempts(3);
            retry.setInitialInterval(Duration.ofMillis(10));
            retry.setMultiplier(2);
            var queue = new RabbitMqBuildQueueService(rabbit, new ObjectMapper(), properties);
            var task = new BuildTask("acceptance", "/test-only", 1L, "p4-acceptance", 0);

            queue.offer(task);
            // A real channel close must make an unacknowledged delivery available again.
            var raw = connection.createConnection().createChannel(false);
            var first = raw.basicGet(RabbitMqBuildQueueService.QUEUE, false);
            assertThat(first).isNotNull();
            assertThat(first.getProps().getDeliveryMode()).isEqualTo(2);
            raw.abort();

            var container = configuration.buildRabbitListenerContainer(connection,
                    new SimpleRabbitListenerContainerFactoryConfigurer(properties), properties, queue);
            container.start();
            try {
                var recovered = awaitDelivery(queue);
                queue.ack(recovered);
                queue.offer(task);
                for (int i = 0; i < 3; i++) {
                    var delivery = awaitDelivery(queue);
                    assertThat(delivery.task().retryCount()).isEqualTo(i);
                    assertThat(queue.fail(delivery)).isEqualTo(i < 2);
                }
                Message dead = rabbit.receive(RabbitMqBuildQueueService.DLQ, 10000);
                assertThat(dead).isNotNull();
                assertThat(dead.getMessageProperties().getHeaders()).containsKey("x-death");
                assertThat(admin.getQueueInfo(RabbitMqBuildQueueService.QUEUE).getMessageCount()).isZero();
            } finally {
                container.stop();
                container.destroy();
            }
        } finally {
            connection.destroy();
        }
    }

    private BuildQueueService.Delivery awaitDelivery(RabbitMqBuildQueueService queue) {
        for (int i = 0; i < 10; i++) {
            var delivery = queue.poll("acceptance");
            if (delivery != null) return delivery;
        }
        throw new AssertionError("Timed out waiting for broker delivery");
    }
}
