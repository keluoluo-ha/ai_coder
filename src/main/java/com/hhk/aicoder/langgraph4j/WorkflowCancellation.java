package com.hhk.aicoder.langgraph4j;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.nio.channels.InterruptibleChannel;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 工作流取消。客户端断开时由 Flux.doFinally / onDispose 置位：
 * 1. 中断持有的虚拟线程（打断 workflow.stream 迭代）
 * 2. 关闭 InterruptibleChannel（AI HTTP 若绑定则立刻释放连接）
 * 3. 完成 cancelledMono，让 CodeGeneratorNode 的 takeUntilOther 取消 blockLast
 * langgraph4j 节点默认跑在 ForkJoinPool，只 interrupt 虚拟线程拦不住 AI 调用。
 */
@Slf4j
public class WorkflowCancellation {

    private static final ThreadLocal<WorkflowCancellation> CURRENT = new ThreadLocal<>();

    //取消开关
    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    //取消信号播放器
    private final Sinks.Empty<Void> cancelSink = Sinks.empty();
    private final List<Runnable> cancelHooks = new CopyOnWriteArrayList<>();
    //记住干活的线程
    private volatile Thread worker;
    //网络连接
    private volatile InterruptibleChannel channel;

    public static void bindCurrent(WorkflowCancellation cancellation) {
        CURRENT.set(cancellation);
    }

    public static WorkflowCancellation current() {
        return CURRENT.get();
    }

    public static void clearCurrent() {
        CURRENT.remove();
    }

    public void bind(Thread worker) {
        this.worker = worker;
        if (cancelled.get() && worker != null) {
            worker.interrupt();
        }
    }

    public void bindChannel(InterruptibleChannel channel) {
        this.channel = channel;
        if (cancelled.get() && channel != null) {
            closeChannel(channel);
        }
    }

    public void onCancel(Runnable hook) {
        if (hook == null) {
            return;
        }
        if (cancelled.get()) {
            runQuietly(hook);
            return;
        }
        cancelHooks.add(hook);
        if (cancelled.get()) {
            runQuietly(hook);
        }
    }

    public boolean isCancelled() {
        return cancelled.get();
    }

    public Mono<Void> cancelledMono() {
        return cancelSink.asMono();
    }

    public void cancel() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        cancelSink.tryEmitEmpty();
        Thread current = worker;
        if (current != null) {
            current.interrupt();
        }
        InterruptibleChannel ch = channel;
        if (ch != null) {
            closeChannel(ch);
        }
        for (Runnable hook : cancelHooks) {
            runQuietly(hook);
        }
        cancelHooks.clear();
        log.info("工作流已发出取消, worker={}, interrupted={}",
                current == null ? "null" : current.getName(),
                current != null && current.isInterrupted());
    }

    public void checkpoint() {
        if (cancelled.get() || Thread.currentThread().isInterrupted()) {
            throw new WorkflowCancelledException("客户端已断开，工作流已取消");
        }
    }

    private static void closeChannel(InterruptibleChannel ch) {
        try {
            ch.close();
        } catch (Exception e) {
            log.warn("关闭可中断通道失败", e);
        }
    }

    private static void runQuietly(Runnable hook) {
        try {
            hook.run();
        } catch (Exception e) {
            log.warn("取消回调失败", e);
        }
    }
}
