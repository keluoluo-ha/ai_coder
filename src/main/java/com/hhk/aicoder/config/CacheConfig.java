package com.hhk.aicoder.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.CompositeCacheManager;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * L1 Caffeine（进程内、微秒）+ L2 Redis（跨节点）。
 * good_app_page 热列表走 {@link com.hhk.aicoder.cache.TwoLevelCache} 单飞，不走 @Cacheable 默认并发回源。
 */
@Slf4j
@Configuration
public class CacheConfig {

    /**
     * 一级缓存 放不变的静态数据
     */
    public static final String L1_STATIC_META = "static_meta";
    /**
     * 系统配置 放系统配置开关
     */
    public static final String L1_SYS_CONFIG = "sys_config";
    /**
     * redis分布式缓存 放热门应用列表
     */
    public static final String L2_GOOD_APP_PAGE = "good_app_page";
    /**
     * 布隆过滤器
     */
    public static final String BLOOM_APP_ID = "aicoder:bloom:appId";

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        return new StringRedisTemplate(factory);
    }

    @Bean
    public CaffeineCache staticMetaCache(AiCoderProperties properties) {
        return new CaffeineCache(L1_STATIC_META, caffeine(properties).build());
    }

    @Bean
    public CaffeineCache sysConfigCache(AiCoderProperties properties) {
        return new CaffeineCache(L1_SYS_CONFIG, caffeine(properties).build());
    }

    @Bean
    public CacheManager l1CacheManager(CaffeineCache staticMetaCache, CaffeineCache sysConfigCache) {
        SimpleCacheManager manager = new SimpleCacheManager();
        manager.setCaches(List.of(staticMetaCache, sysConfigCache));
        return manager;
    }

    @Bean
    public RedisCacheManager redisCacheManager(RedisConnectionFactory redisConnectionFactory,
                                               AiCoderProperties properties) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(jitteredTtl(properties.getCache().getL2TtlMinutes()))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration(L2_GOOD_APP_PAGE, defaultConfig)
                .build();
    }

    /**
     * 兼容仍使用 @Cacheable 的入口：先 L1 再 L2。热列表请走 TwoLevelCache。
     */
    @Bean
    @Primary
    public CacheManager cacheManager(CacheManager l1CacheManager, RedisCacheManager redisCacheManager) {
        CompositeCacheManager composite = new CompositeCacheManager(l1CacheManager, redisCacheManager);
        composite.setFallbackToNoOpCache(false);
        return composite;
    }

    @Bean
    public RBloomFilter<Long> appIdBloomFilter(RedissonClient redissonClient, AiCoderProperties properties) {
        RBloomFilter<Long> bloom = redissonClient.getBloomFilter(BLOOM_APP_ID);
        AiCoderProperties.Cache cache = properties.getCache();
        boolean created = bloom.tryInit(cache.getBloomExpectedInsertions(), cache.getBloomFalseProbability());
        log.info("appId 布隆过滤器初始化, created={}, expected={}, fpp={}",
                created, cache.getBloomExpectedInsertions(), cache.getBloomFalseProbability());
        return bloom;
    }

    public static Caffeine<Object, Object> caffeine(AiCoderProperties properties) {
        AiCoderProperties.Cache cache = properties.getCache();
        return Caffeine.newBuilder()
                .maximumSize(cache.getL1MaxSize())
                .expireAfterWrite(cache.getL1ExpireMinutes(), TimeUnit.MINUTES)
                .recordStats();
    }

    /**
     * 5min ± 10%（约 ±30s），打散同时过期，避免缓存雪崩。
     */
    public static Duration jitteredTtl(int baseMinutes) {
        long baseSeconds = Math.max(1, baseMinutes) * 60L;
        long jitter = Math.round(baseSeconds * 0.10);
        long delta = ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
        return Duration.ofSeconds(Math.max(30L, baseSeconds + delta));
    }
}
