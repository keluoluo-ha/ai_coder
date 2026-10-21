package com.hhk.aicoder.cache;

import com.hhk.aicoder.config.AiCoderProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoLevelCacheTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Test
    void localHitDoesNotTouchRedis() {
        TwoLevelCache cache = new TwoLevelCache(redis, redissonClient, new AiCoderProperties());
        cache.putLocal("good_app_page", "k1", "from-l1");

        String value = cache.get("good_app_page", "k1", String.class, () -> "from-db");

        assertEquals("from-l1", value);
        verify(valueOperations, never()).get(anyString());
    }

    @Test
    void missLoadsOnceAndWritesL2() throws Exception {
        when(redis.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any())).thenReturn(true);
        when(lock.isHeldByCurrentThread()).thenReturn(true);
        TwoLevelCache cache = new TwoLevelCache(redis, redissonClient, new AiCoderProperties());
        AtomicInteger loads = new AtomicInteger();

        String value = cache.get("good_app_page", "k2", String.class, () -> {
            loads.incrementAndGet();
            return "from-db";
        });

        assertEquals("from-db", value);
        assertEquals(1, loads.get());
        verify(valueOperations).set(anyString(), anyString(), any(Duration.class));
        verify(lock).unlock();
    }
}
