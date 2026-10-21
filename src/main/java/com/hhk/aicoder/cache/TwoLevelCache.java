package com.hhk.aicoder.cache;

import com.hhk.aicoder.config.AiCoderProperties;
import com.hhk.aicoder.config.CacheConfig;
import com.hhk.aicoder.utils.TraceIdHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * L1 Caffeine + L2 Redis。热 key 用 Redisson 锁单飞，避免 @Cacheable 并发回源。
 */
@Slf4j
@Component
public class TwoLevelCache {

    public static final String L1_STATIC_META = CacheConfig.L1_STATIC_META;

    private final com.github.benmanes.caffeine.cache.Cache<String, Object> l1;
    private final StringRedisTemplate redis;
    private final RedissonClient redissonClient;
    private final AiCoderProperties properties;

    public TwoLevelCache(StringRedisTemplate redis,
                         RedissonClient redissonClient,
                         AiCoderProperties properties) {
        this.l1 = CacheConfig.caffeine(properties).build();
        this.redis = redis;
        this.redissonClient = redissonClient;
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key, Class<T> type, Supplier<T> loader) {
        String fullKey = cacheName + ":" + key;
        Object local = l1.getIfPresent(fullKey);
        if (local != null) {
            return type.isInstance(local) ? type.cast(local) : null;
        }
        String redisKey = redisKey(cacheName, key);
        String cached = redis.opsForValue().get(redisKey);
        if (cached != null) {
            T value = decode(cached, type);
            if (value != null) {
                l1.put(fullKey, value);
                return value;
            }
        }
        return loadSingleFlight(cacheName, key, type, loader);
    }

    public void put(String cacheName, String key, Object value) {
        if (value == null) {
            return;
        }
        String fullKey = cacheName + ":" + key;
        l1.put(fullKey, value);
        redis.opsForValue().set(redisKey(cacheName, key), encode(value), jitteredTtl());
    }

    public void evict(String cacheName, String key) {
        l1.invalidate(cacheName + ":" + key);
        redis.delete(redisKey(cacheName, key));
    }

    public void putLocal(String cacheName, String key, Object value) {
        if (value == null) {
            return;
        }
        l1.put(cacheName + ":" + key, value);
    }

    @SuppressWarnings("unchecked")
    public <T> T getLocal(String cacheName, String key, Class<T> type) {
        Object local = l1.getIfPresent(cacheName + ":" + key);
        return type.isInstance(local) ? (T) local : null;
    }

    private <T> T loadSingleFlight(String cacheName, String key, Class<T> type, Supplier<T> loader) {
        String lockKey = "cache:lock:" + cacheName + ":" + key;
        RLock lock = redissonClient.getLock(lockKey);
        long waitSeconds = properties.getCache().getSingleflightWaitSeconds();
        boolean locked = false;
        try {
            locked = lock.tryLock(waitSeconds, waitSeconds + 2, TimeUnit.SECONDS);
            if (!locked) {
                String cached = redis.opsForValue().get(redisKey(cacheName, key));
                if (cached != null) {
                    return decode(cached, type);
                }
                log.warn("单飞未抢到锁且 L2 仍空, key={}, traceId={}", key, TraceIdHolder.current());
                return loader.get();
            }
            String cached = redis.opsForValue().get(redisKey(cacheName, key));
            if (cached != null) {
                T value = decode(cached, type);
                if (value != null) {
                    l1.put(cacheName + ":" + key, value);
                    return value;
                }
            }
            T loaded = loader.get();
            if (loaded != null) {
                put(cacheName, key, loaded);
            }
            return loaded;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loader.get();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private Duration jitteredTtl() {
        return CacheConfig.jitteredTtl(properties.getCache().getL2TtlMinutes());
    }

    private static String redisKey(String cacheName, String key) {
        return "cache:" + cacheName + ":" + key;
    }

    private static String encode(Object value) {
        return cn.hutool.json.JSONUtil.toJsonStr(value);
    }

    private static <T> T decode(String json, Class<T> type) {
        try {
            return cn.hutool.json.JSONUtil.toBean(json, type);
        } catch (Exception e) {
            return null;
        }
    }
}
