package com.hhk.aicoder.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolExecutorOrderingTest {

    private final ThreadPoolTaskExecutor executor = new ToolExecutorConfig().toolExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdown();
    }

    @Test
    void thenComposeKeepsWriteReadModifyOrder() throws Exception {
        List<String> order = new CopyOnWriteArrayList<>();
        CountDownLatch startedSecond = new CountDownLatch(1);

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null)
                .thenCompose(ignored -> CompletableFuture.runAsync(() -> {
                    order.add("FileWrite");
                    sleep(80);
                }, executor))
                .thenCompose(ignored -> CompletableFuture.runAsync(() -> {
                    startedSecond.countDown();
                    order.add("FileRead");
                    sleep(40);
                }, executor))
                .thenCompose(ignored -> CompletableFuture.runAsync(() -> order.add("Modify"), executor));

        assertTrue(chain.get(2, TimeUnit.SECONDS) == null);
        assertTrue(startedSecond.await(2, TimeUnit.SECONDS));
        assertEquals(List.of("FileWrite", "FileRead", "Modify"), new ArrayList<>(order));
    }

    @Test
    void poolUsesToolExecPrefixAndCallerRuns() {
        assertEquals("tool-exec-", executor.getThreadNamePrefix());
        assertEquals(8, executor.getCorePoolSize());
        assertEquals(16, executor.getMaxPoolSize());
        assertEquals(100, executor.getQueueCapacity());
        assertTrue(executor.getThreadPoolExecutor().getRejectedExecutionHandler()
                instanceof java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }
}
