package com.hhk.aicoder.langgraph4j;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowCancellationTest {

    @Test
    void checkpointThrowsAfterCancel() {
        WorkflowCancellation cancellation = new WorkflowCancellation();
        cancellation.cancel();
        assertTrue(cancellation.isCancelled());
        assertThrows(WorkflowCancelledException.class, cancellation::checkpoint);
    }

    @Test
    void cancelInterruptsBoundVirtualThread() throws Exception {
        WorkflowCancellation cancellation = new WorkflowCancellation();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean(false);
        Thread worker = Thread.ofVirtual().unstarted(() -> {
            started.countDown();
            try {
                Thread.sleep(30_000);
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            } finally {
                exited.countDown();
            }
        });
        cancellation.bind(worker);
        worker.start();
        assertTrue(started.await(2, TimeUnit.SECONDS));
        cancellation.cancel();
        assertTrue(exited.await(2, TimeUnit.SECONDS), "虚拟线程应被中断退出，不能变成僵尸");
        assertTrue(interrupted.get());
    }

    @Test
    void cancelCompletesCancelledMonoSoTakeUntilOtherStops() {
        WorkflowCancellation cancellation = new WorkflowCancellation();
        Flux<String> stream = Flux.interval(Duration.ofMillis(20))
                .map(i -> "tick-" + i)
                .takeUntilOther(cancellation.cancelledMono())
                .take(100);
        StepVerifier.create(stream)
                .thenAwait(Duration.ofMillis(50))
                .then(cancellation::cancel)
                .thenConsumeWhile(item -> item.startsWith("tick-"))
                .verifyComplete();
    }

    @Test
    void heartbeatMergesWhileWorkIsIdleThenStops() {
        Flux<String> work = Flux.concat(
                Flux.just("start"),
                Flux.<String>never().take(Duration.ofMillis(250)),
                Flux.just("end")
        );
        List<String> heartbeats = new ArrayList<>();
        StepVerifier.create(CodeGenWorkflow.mergeWithHeartbeat(work, Duration.ofMillis(80)))
                .thenConsumeWhile(item -> {
                    if (CodeGenWorkflow.HEARTBEAT_JSON.equals(item)) {
                        heartbeats.add(item);
                        return true;
                    }
                    return !"end".equals(item);
                })
                .expectNext("end")
                .verifyComplete();
        assertFalse(heartbeats.isEmpty(), "空闲期间应发出 heartbeat，避免代理断连");
    }
}
