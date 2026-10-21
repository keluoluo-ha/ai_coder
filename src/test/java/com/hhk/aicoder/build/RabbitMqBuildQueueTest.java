package com.hhk.aicoder.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class RabbitMqBuildQueueTest {
    private final ObjectMapper json = new ObjectMapper();
    private final RabbitProperties properties = new RabbitProperties();
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);
    private final BuildTask task = new BuildTask("deploy-1", "/tmp/project", 1L, "trace-1", 0);

    interface Listener { void receive(Message message, Channel channel) throws Exception; }

    @Test
    void topologyIsDurableAndRoutesRejectedMessagesToDlq() {
        var topology = new RabbitMqBuildQueueConfiguration().buildQueueTopology();
        var queues = topology.getDeclarablesByType(Queue.class);
        assertThat(queues).hasSize(2).allMatch(Queue::isDurable);
        Queue business = queues.stream().filter(q -> q.getName().equals("aicoder.build.queue")).findFirst().orElseThrow();
        assertThat(business.getArguments())
                .containsEntry("x-dead-letter-exchange", "aicoder.build.dlx")
                .containsEntry("x-dead-letter-routing-key", "dlq")
                .containsEntry("x-single-active-consumer", true);
        assertThat(topology.getDeclarablesByType(DirectExchange.class)).hasSize(2).allMatch(DirectExchange::isDurable);
        assertThat(topology.getDeclarablesByType(Binding.class)).anySatisfy(binding -> {
            assertThat(binding.getExchange()).isEqualTo("aicoder.build.exchange");
            assertThat(binding.getDestination()).isEqualTo("aicoder.build.queue");
            assertThat(binding.getRoutingKey()).isEqualTo("build-task");
        }).anySatisfy(binding -> {
            assertThat(binding.getExchange()).isEqualTo("aicoder.build.dlx");
            assertThat(binding.getDestination()).isEqualTo("aicoder.build.dlq");
            assertThat(binding.getRoutingKey()).isEqualTo("dlq");
        });
    }

    @Test
    void publishesPersistentJson() throws Exception {
        confirmPublish(true, null);
        var queue = new RabbitMqBuildQueueService(rabbit, json, properties);
        queue.offer(task);
        var captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rabbit).send(eq("aicoder.build.exchange"), eq("build-task"), captor.capture(), any(CorrelationData.class));
        assertThat(captor.getValue().getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(json.readValue(captor.getValue().getBody(), BuildTask.class)).isEqualTo(task);
    }

    @Test
    void successAcknowledgesOnlyAfterWorkerCompletes() throws Exception {
        var queue = new RabbitMqBuildQueueService(rabbit, json, properties);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var listener = executor.submit(() -> { queue.onMessage(message(), channel); return null; });
            var delivery = queue.poll("worker");
            assertThat(delivery).isNotNull();
            verifyNoInteractions(channel);
            queue.ack(delivery);
            listener.get(5, TimeUnit.SECONDS);
            verify(channel).basicAck(42L, false);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    @Test
    void retriesThreeAttemptsThenNacksWithoutRequeue() throws Exception {
        var retry = properties.getListener().getSimple().getRetry();
        retry.setEnabled(true);
        retry.setMaxAttempts(3);
        retry.setInitialInterval(Duration.ofMillis(1));
        var queue = new RabbitMqBuildQueueService(rabbit, json, properties);
        ProxyFactory factory = new ProxyFactory((Listener) queue::onMessage);
        factory.addAdvice(RetryInterceptorBuilder.stateless().maxAttempts(3).backOffOptions(1, 2, 10).build());
        Listener proxy = (Listener) factory.getProxy();
        try (var executor = Executors.newSingleThreadExecutor()) {
            var listener = executor.submit(() -> { proxy.receive(message(), channel); return null; });
            for (int i = 0; i < 3; i++) {
                var delivery = queue.poll("worker");
                assertThat(delivery).isNotNull();
                assertThat(delivery.task().retryCount()).isEqualTo(i);
                assertThat(queue.fail(delivery)).isEqualTo(i < 2);
            }
            listener.get(5, TimeUnit.SECONDS);
            verify(channel).basicNack(42L, false, false);
            verify(channel, never()).basicAck(anyLong(), anyBoolean());
            verifyNoInteractions(rabbit); // retry never republishes or requeues the message
        }
    }

    @Test
    void publishNackIsReportedToCaller() {
        confirmPublish(false, null);
        var queue = new RabbitMqBuildQueueService(rabbit, json, properties);
        assertThatThrownBy(() -> queue.offer(task)).isInstanceOf(AmqpException.class).hasMessageContaining("NACK");
    }

    @Test
    void unroutableMessageIsFailureEvenWhenBrokerConfirms() throws Exception {
        confirmPublish(true, new ReturnedMessage(message(), 312, "NO_ROUTE", "aicoder.build.exchange", "build-task"));
        var queue = new RabbitMqBuildQueueService(rabbit, json, properties);
        assertThatThrownBy(() -> queue.offer(task)).isInstanceOf(AmqpException.class).hasMessageContaining("NO_ROUTE");
    }

    @Test
    void publishTimeoutDoesNotAutomaticallyRepublish() {
        var queue = new RabbitMqBuildQueueService(rabbit, json, properties);
        ReflectionTestUtils.setField(queue, "confirmTimeoutMillis", 1L);
        assertThatThrownBy(() -> queue.offer(task)).isInstanceOf(AmqpException.class).hasMessageContaining("confirmation unavailable");
        verify(rabbit, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void duplicateAcknowledgementIsRejectedLocally() throws Exception {
        var queue = new RabbitMqBuildQueueService(rabbit, json, properties);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var listener = executor.submit(() -> { queue.onMessage(message(), channel); return null; });
            var delivery = queue.poll("worker");
            queue.ack(delivery);
            listener.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> queue.ack(delivery)).isInstanceOf(IllegalStateException.class);
            verify(channel, times(1)).basicAck(42L, false);
        }
    }

    @Test
    void ackIoFailureIsNotReportedAsSuccessfulSettlement() throws Exception {
        doThrow(new java.io.IOException("channel closed")).when(channel).basicAck(42L, false);
        var queue = new RabbitMqBuildQueueService(rabbit, json, properties);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var listener = executor.submit(() -> { queue.onMessage(message(), channel); return null; });
            var delivery = queue.poll("worker");
            queue.ack(delivery);
            assertThatThrownBy(() -> listener.get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
            verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        }
    }

    private void confirmPublish(boolean ack, ReturnedMessage returned) {
        doAnswer(invocation -> {
            CorrelationData correlation = invocation.getArgument(3);
            correlation.setReturned(returned);
            correlation.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "broker rejected"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    private Message message() throws Exception {
        MessageProperties metadata = new MessageProperties();
        metadata.setDeliveryTag(42L);
        return new Message(json.writeValueAsBytes(task), metadata);
    }
}
