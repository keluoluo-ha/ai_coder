package com.hhk.aicoder.job;

import com.hhk.aicoder.config.AiCoderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TmpDirCleanerTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private TmpDirCleaner cleaner;

    @Test
    void expiredDirectoryIsDeletedWhenLockAcquired(@TempDir Path root) throws Exception {
        AiCoderProperties properties = new AiCoderProperties();
        properties.getTmp().setTtlHours(1);
        properties.getTmp().setMaxBytes(Long.MAX_VALUE);
        ReflectionTestUtils.setField(cleaner, "properties", properties);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);

        Path oldDir = root.resolve("old-app");
        Files.createDirectories(oldDir);
        Files.writeString(oldDir.resolve("index.html"), "x");
        oldDir.toFile().setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25));
        oldDir.resolve("index.html").toFile().setLastModified(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(25));

        cleaner.cleanRoot(root);

        assertFalse(Files.exists(oldDir), "超 TTL 的目录应被删除");
    }

    @Test
    void inUseDirectoryIsSkippedWhenLockBusy(@TempDir Path root) throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).thenReturn(false);

        Path busy = root.resolve("busy-app");
        Files.createDirectories(busy);
        Files.writeString(busy.resolve("a.txt"), "keep");

        boolean deleted = ReflectionTestUtils.invokeMethod(cleaner, "tryDelete", busy);

        assertFalse(Boolean.TRUE.equals(deleted));
        assertTrue(Files.exists(busy), "抢不到 tmp:lock:{dirName} 时不得删除");
    }
}
