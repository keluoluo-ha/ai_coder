package com.hhk.aicoder.build;

import com.hhk.aicoder.core.builder.VueProjectBuilder;
import com.hhk.aicoder.mapper.AppMapper;
import com.hhk.aicoder.model.dto.app.BuildStatus;
import com.hhk.aicoder.service.ScreenshotService;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/** Same worker invariants apply to both Redis and RabbitMQ delivery adapters. */
class BuildWorkerIdempotencyTest {
    private final BuildQueueService queue = mock(BuildQueueService.class);
    private final BuildStatusService statuses = mock(BuildStatusService.class);
    private final VueProjectBuilder builder = mock(VueProjectBuilder.class);
    private final AppMapper mapper = mock(AppMapper.class);
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final RLock lock = mock(RLock.class);
    private final BuildWorker worker = new BuildWorker(queue, statuses, mock(ExecutorService.class),
            builder, mapper, redisson, mock(ScreenshotService.class));
    private final BuildTask task = new BuildTask("deploy-1", "/unused", 1L, "trace-1", 0);
    private final BuildQueueService.Delivery delivery = new BuildQueueService.Delivery("message-1", task);

    @Test
    void successfulRedeliveryIsAcknowledgedWithoutRebuilding() throws Exception {
        when(redisson.getLock("aicoder:build:lock:deploy-1")).thenReturn(lock);
        when(lock.tryLock(1, TimeUnit.SECONDS)).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        when(statuses.get("deploy-1")).thenReturn(BuildStatus.builder().status("SUCCESS").build());
        ReflectionTestUtils.invokeMethod(worker, "process", delivery);
        verify(queue).ack(delivery);
        verify(queue, never()).fail(any());
        verifyNoInteractions(builder, mapper);
        verify(lock).unlock();
    }

    @Test
    void contendedDeployKeyNeverStartsAnotherBuild() throws Exception {
        when(redisson.getLock("aicoder:build:lock:deploy-1")).thenReturn(lock);
        when(lock.tryLock(1, TimeUnit.SECONDS)).thenReturn(false);
        ReflectionTestUtils.invokeMethod(worker, "process", delivery);
        verifyNoInteractions(builder, mapper, queue, statuses);
        verify(lock, never()).unlock();
    }
}
