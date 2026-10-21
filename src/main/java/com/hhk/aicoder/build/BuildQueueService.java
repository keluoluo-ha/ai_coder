package com.hhk.aicoder.build;

/** Broker-neutral transport; P4 can replace the Redis implementation. */
public interface BuildQueueService {
    record Delivery(String id, BuildTask task) {}
    void offer(BuildTask task);
    Delivery poll(String consumer);
    void ack(Delivery delivery);
    boolean fail(Delivery delivery);
}
