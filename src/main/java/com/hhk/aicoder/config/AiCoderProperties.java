package com.hhk.aicoder.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 高并发改造相关配置。单机开发默认 local，集群切换 cos。
 */
@Data
@ConfigurationProperties(prefix = "aicoder")
public class AiCoderProperties {

    private Static staticConfig = new Static();
    private Screenshot screenshot = new Screenshot();
    private Tmp tmp = new Tmp();
    private Build build = new Build();
    private Cache cache = new Cache();

    @Data
    public static class Static {
        /**
         * local：读本地 tmp/code_deploy；cos：读腾讯云 COS / CDN。
         */
        private String storage = "local";
        /**
         * COS 对象前缀，不含首尾斜杠。
         */
        private String cosPrefix = "static";
        /**
         * 可选 CDN 域名。为空时回退 cos.client.host。
         */
        private String cdnHost = "";
    }

    @Data
    public static class Screenshot {
        private int poolMaxTotal = 3;
        private long poolMaxWaitMillis = 5000;
        private long borrowTimeoutSeconds = 60;
        private String placeholderClasspath = "static/placeholder-screenshot.png";
    }

    @Data
    public static class Tmp {
        private long ttlHours = 24;
        private long maxBytes = 5L * 1024 * 1024 * 1024;
        private long targetBytes = 3L * 1024 * 1024 * 1024;
    }

    @Data
    public static class Cache {
        /** L1 Caffeine 最大条数 */
        private int l1MaxSize = 1000;
        /** L1 写后过期（分钟） */
        private int l1ExpireMinutes = 5;
        /** L2 Redis 基准 TTL（分钟），实际写入加 ±10% 抖动 */
        private int l2TtlMinutes = 5;
        /** 布隆过滤器预期元素数 */
        private long bloomExpectedInsertions = 1_000_000L;
        /** 布隆误判率 */
        private double bloomFalseProbability = 0.01;
        /** 单飞锁等待（秒） */
        private long singleflightWaitSeconds = 3;
    }

    @Data
    public static class Build {
        private String streamKey = "aicoder:build:tasks";
        private String group = "aicoder-builders";
        private String deadLetterStream = "aicoder:build:deadletter";
        private String screenshotStream = "aicoder:screenshot:tasks";
        private int maxRetry = 3;
        private int consumers = 4;
    }
}
