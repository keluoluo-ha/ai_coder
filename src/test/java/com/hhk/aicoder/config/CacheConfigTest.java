package com.hhk.aicoder.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheConfigTest {

    @Test
    void jitteredTtlStaysWithinPlusMinusTenPercent() {
        long base = 5 * 60L;
        long min = Math.round(base * 0.90);
        long max = Math.round(base * 1.10);
        for (int i = 0; i < 200; i++) {
            Duration ttl = CacheConfig.jitteredTtl(5);
            long seconds = ttl.getSeconds();
            assertTrue(seconds >= min && seconds <= max,
                    "TTL 应在 5min ±10% 内, actual=" + seconds);
        }
    }
}
