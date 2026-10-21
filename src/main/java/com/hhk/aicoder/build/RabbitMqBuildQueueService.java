package com.hhk.aicoder.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.retry.support.RetrySynchronizationManager;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Bridges a retry-enabled AMQP listener to the unchanged poll/ack/fail worker. */
@Service
@ConditionalOnProperty(prefix = "aicoder.build.queue", name = "type", havingValue = "rabbitmq")
public class RabbitMqBuildQueueService implements BuildQueueService {
    static final String EXCHANGE = "aicoder.build.exchange";
    static final String QUEUE = "aicoder.build.queue";
    static final String ROUTING_KEY = "build-task";
    static final String DLX = "aicoder.build.dlx";
    static final String DLQ = "aicoder.build.dlq";

    private final RabbitTemplate rabbit;
    private final ObjectMapper json;
    private final int maxAttempts;
    @Value("${aicoder.build.queue.confirm-timeout-millis:10000}")
    private long confirmTimeoutMillis = 10000;
    private final LinkedBlockingQueue<Delivery> available = new LinkedBlockingQueue<>();
    private final Map<String, Attempt> pending = new ConcurrentHashMap<>();

    public RabbitMqBuildQueueService(RabbitTemplate rabbit, ObjectMapper json, RabbitProperties properties) {
        this.rabbit = rabbit;
        this.json = json;
        var retry = properties.getListener().getSimple().getRetry();
        this.maxAttempts = retry.isEnabled() ? retry.getMaxAttempts() : 1;
    }

    @Override
    public void offer(BuildTask task) {
        try {
            MessageProperties properties = new MessageProperties();
            properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
            properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
            properties.setMessageId(UUID.randomUUID().toString());
            CorrelationData correlation = new CorrelationData(properties.getMessageId());
            rabbit.send(EXCHANGE, ROUTING_KEY, new Message(json.writeValueAsBytes(task), properties), correlation);
            var confirm = correlation.getFuture().get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) throw new AmqpException("Build publish NACK: " + confirm.getReason());
            // RabbitTemplate populates the return before completing the confirm future.
            if (correlation.getReturned() != null) {
                throw new AmqpException("Build message was not routed: " + correlation.getReturned().getReplyText());
            }
        } catch (IOException e) {
            throw new AmqpIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AmqpException("Interrupted while confirming build publish", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            // A timeout is ambiguous: the broker may already own the message. Never auto-publish
            // another copy here; redelivery/deduplication remains the worker's responsibility.
            throw new AmqpException("Build publish confirmation unavailable", e);
        }
    }

    // Invoked again by Spring Retry for each attempt, on the listener thread.
    void onMessage(Message message, Channel channel) throws Exception {
        BuildTask original = json.readValue(message.getBody(), BuildTask.class);
        var context = RetrySynchronizationManager.getContext();
        int retryCount = context == null ? 0 : context.getRetryCount();
        BuildTask task = new BuildTask(original.deployKey(), original.projectPath(), original.appId(),
                original.traceId(), retryCount);
        Delivery delivery = new Delivery(UUID.randomUUID().toString(), task);
        Attempt attempt = new Attempt(retryCount + 1 < maxAttempts, new CompletableFuture<>());
        pending.put(delivery.id(), attempt);
        available.add(delivery);
        try {
            // Bound abandoned work (for example, the worker could not acquire its deployKey lock).
            boolean success = attempt.outcome().get(15, TimeUnit.MINUTES);
            long tag = message.getMessageProperties().getDeliveryTag();
            if (success) channel.basicAck(tag, false);
            else if (attempt.retryable()) throw new BuildAttemptFailedException();
            else channel.basicNack(tag, false, false);
        } catch (IOException e) {
            // A failed settlement must not rerun the build on this delivery/channel.
            // Closed channels are recovered by the broker; open ones are rejected without requeue.
            throw new AmqpRejectAndDontRequeueException("Build settlement failed", true, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            // Do not start another in-process attempt during container shutdown.
            throw new AmqpRejectAndDontRequeueException("Build listener interrupted", true, e);
        } finally {
            pending.remove(delivery.id(), attempt);
            available.remove(delivery);
        }
    }

    @Override
    public Delivery poll(String consumer) {
        try {
            return available.poll(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    @Override
    public void ack(Delivery delivery) {
        complete(delivery, true);
    }

    @Override
    public boolean fail(Delivery delivery) {
        return complete(delivery, false).retryable();
    }

    private Attempt complete(Delivery delivery, boolean success) {
        Attempt attempt = pending.get(delivery.id());
        if (attempt == null || !attempt.outcome().complete(success)) {
            throw new IllegalStateException("Unknown or completed AMQP attempt: " + delivery.id());
        }
        return attempt;
    }

    private record Attempt(boolean retryable, CompletableFuture<Boolean> outcome) {}
    private static class BuildAttemptFailedException extends RuntimeException {
        BuildAttemptFailedException() { super("Build attempt failed; retry through listener advice"); }
    }
}
