package com.hhk.aicoder.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 独立的有界节点线程池；与工具池、图片收集池隔离。 */
@Configuration
public class WorkflowExecutorConfig {
    public static final String CODE_GEN = "codeGenExecutor";
    public static final String QUALITY_CHECK = "qualityCheckExecutor";
    public static final String BUILD = "buildExecutor";
    public static final int BUILD_CORE_SIZE = 4;

    @Bean(name = CODE_GEN, destroyMethod = "shutdown")
    public ExecutorService codeGenExecutor() {
        return createExecutor(4, "code-gen-");
    }

    @Bean(name = QUALITY_CHECK, destroyMethod = "shutdown")
    public ExecutorService qualityCheckExecutor() {
        return createExecutor(2, "quality-check-");
    }

    @Bean(name = BUILD, destroyMethod = "shutdown")
    public ExecutorService buildExecutor() {
        return createExecutor(BUILD_CORE_SIZE, "build-");
    }

    public static ThreadPoolExecutor createExecutor(int core, String prefix) {
        return new ThreadPoolExecutor(core, core, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50),
                Thread.ofPlatform().name(prefix, 1).factory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
