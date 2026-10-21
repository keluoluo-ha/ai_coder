package com.hhk.aicoder.job;

import com.hhk.aicoder.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * tmp 目录占用锁。构建/部署持有，清理任务抢不到则跳过，避免删正在用的目录。
 */
@Slf4j
public final class TmpDirLock {

    private TmpDirLock() {
    }

    public static String lockName(String dirName) {
        return "tmp:lock:" + dirName;
    }

    public static String lockName(File dir) {
        return lockName(dir.getName());
    }

    public static <T> T withLock(File dir, long waitSeconds, long leaseSeconds, Supplier<T> action) {
        RedissonClient client;
        try {
            client = SpringContextUtil.getBean(RedissonClient.class);
        } catch (IllegalStateException | org.springframework.beans.BeansException e) {
            return action.get();
        }
        RLock lock = client.getLock(lockName(dir));
        boolean locked = false;
        try {
            locked = lock.tryLock(waitSeconds, leaseSeconds, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("未能获取 tmp 目录锁 {}", lockName(dir));
            }
            return action.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return action.get();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
