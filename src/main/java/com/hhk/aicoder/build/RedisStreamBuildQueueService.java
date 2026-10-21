package com.hhk.aicoder.build;

import com.hhk.aicoder.config.AiCoderProperties;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "aicoder.build.queue", name = "type", havingValue = "redis", matchIfMissing = true)
public class RedisStreamBuildQueueService implements BuildQueueService {
    private final StreamOperations<String, String, String> streams;
    private final AiCoderProperties.Build cfg;
    private volatile boolean ready;
    public RedisStreamBuildQueueService(StringRedisTemplate redis, AiCoderProperties properties) {
        streams = redis.opsForStream(); cfg = properties.getBuild();
    }
    private synchronized void initialize() {
        if (ready) return;
        // Ensure the stream exists before XGROUP CREATE. Bootstrap entries are ACKed by poll.
        streams.add(cfg.getStreamKey(), Map.of("bootstrap", "true"));
        try { streams.createGroup(cfg.getStreamKey(), ReadOffset.from("0-0"), cfg.getGroup()); }
        catch (RuntimeException e) {
            Throwable t = e;
            while (t.getCause() != null) t = t.getCause();
            if (t.getMessage() == null || !t.getMessage().contains("BUSYGROUP")) throw e;
        }
        ready = true;
    }
    public void offer(BuildTask task) {
        initialize();
        if (streams.add(cfg.getStreamKey(), task.fields()) == null) throw new IllegalStateException("XADD failed");
    }
    public Delivery poll(String consumer) {
        initialize();
        // Recover deliveries abandoned by a crashed process. Idle threshold exceeds npm's 8min timeout.
        var pending = streams.pending(cfg.getStreamKey(), cfg.getGroup(), Range.unbounded(), 100);
        if (pending != null) for (var message : pending) {
            if (message.getElapsedTimeSinceLastDelivery().compareTo(Duration.ofMinutes(15)) >= 0) {
                var claimed = streams.claim(cfg.getStreamKey(), cfg.getGroup(), consumer,
                        Duration.ofMinutes(15), message.getId());
                Delivery recovered = delivery(claimed);
                if (recovered != null) return recovered;
            }
        }
        return delivery(streams.read(Consumer.from(cfg.getGroup(), consumer),
                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                StreamOffset.create(cfg.getStreamKey(), ReadOffset.lastConsumed())));
    }
    private Delivery delivery(List<MapRecord<String, String, String>> records) {
        if (records == null || records.isEmpty()) return null;
        var record = records.getFirst();
        if (record.getValue().containsKey("bootstrap")) {
            streams.acknowledge(cfg.getStreamKey(), cfg.getGroup(), record.getId());
            return null;
        }
        return new Delivery(record.getId().getValue(), BuildTask.from(record.getValue()));
    }
    public void ack(Delivery delivery) {
        streams.acknowledge(cfg.getStreamKey(), cfg.getGroup(), delivery.id());
    }
    public boolean fail(Delivery delivery) {
        boolean retry = delivery.task().retryCount() < cfg.getMaxRetry();
        // Write replacement BEFORE ACK: at-least-once delivery, never ACK a lost retry/deadletter.
        if (retry) offer(delivery.task().retry());
        else if (streams.add(cfg.getDeadLetterStream(), delivery.task().fields()) == null)
            throw new IllegalStateException("Deadletter XADD failed");
        ack(delivery);
        return retry;
    }
}
