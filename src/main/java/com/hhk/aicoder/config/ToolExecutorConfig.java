package com.hhk.aicoder.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 工具执行独立线程池。OpenAI 回调线程只负责收 token，
 * FileWrite / FileRead / Modify 等 IO 提交到这里，避免堵死流式回调。
 */
@Slf4j
@Configuration
public class ToolExecutorConfig {

    public static final String BEAN_NAME = "toolExecutor";

    @Bean(name = BEAN_NAME, destroyMethod = "shutdown")
    public ThreadPoolTaskExecutor toolExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("tool-exec-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        log.info("toolExecutor 已创建, core=8, max=16, queue=100, reject=CallerRuns");
        return executor;
    }

    /**
     * 非 Spring / 单测兜底，避免 vendor handler 在容器未就绪时落到 ForkJoinPool。
     */
    public static Executor fallback() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("tool-exec-fallback-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
