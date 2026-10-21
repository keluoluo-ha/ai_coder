package com.hhk.aicoder.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 图片收集专用线程池
 *
 * 为什么要单独一个池：图片收集是 IO 密集型（外部搜索接口 + AI 生图），
 * 若沿用 CompletableFuture.supplyAsync 单参版本的默认池 ForkJoinPool.commonPool：
 *   1. 线程数仅 CPU 核数 - 1，并发度打不开；
 *   2. commonPool 是 JVM 全局共享，IO 阻塞会拖垮 parallelStream 等其它并行任务；
 *   3. 无法单独设置队列容量、拒绝策略与线程名，出问题难以定位。
 */
@Slf4j
@Configuration
public class ImageCollectExecutorConfig {

    public static final String BEAN_NAME = "imageCollectExecutor";

    /** 核心线程数：IO 密集型，线程多数时间在等网络响应，可取远大于 CPU 核数 */
    private static final int CORE_POOL_SIZE = 16;
    /** 最大线程数 */
    private static final int MAX_POOL_SIZE = 32;
    /** 空闲线程存活时间 */
    private static final long KEEP_ALIVE_SECONDS = 60L;
    /** 有界队列：防止 AI 规划出大量任务时无限堆积撑爆内存 */
    private static final int QUEUE_CAPACITY = 200;
    /** 单任务超时（秒），与 ImageCollectorNode#TASK_TIMEOUT_SECONDS 保持一致 */
    public static final long TASK_TIMEOUT_SECONDS = 30L;

    @Bean(name = BEAN_NAME, destroyMethod = "shutdown")
    public ThreadPoolExecutor imageCollectExecutor() {
        return createExecutor();
    }

    /**
     * 供非 Spring 环境（如 WorkflowApp#main）直接调用，保证任何场景下都不落回 commonPool
     */
    public static ThreadPoolExecutor createExecutor() {
        return new ThreadPoolExecutor(
                CORE_POOL_SIZE,
                MAX_POOL_SIZE,
                KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(QUEUE_CAPACITY),
                new NamedThreadFactory("image-collect"),
                // 队列满时由提交线程自己执行，形成回压，任务不会被静默丢弃
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * 自定义线程工厂：给线程命名，可用线程名验证任务确实跑在业务线程池上
     */
    static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger seq = new AtomicInteger(1);
        private final String prefix;

        NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, prefix + "-" + seq.getAndIncrement());
            t.setUncaughtExceptionHandler((thread, e) ->
                    log.error("线程 {} 执行异常", thread.getName(), e));
            return t;
        }
    }
}
