package com.hhk.aicoder.langgraph4j;

import com.hhk.aicoder.utils.SpringContextUtil;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.action.NodeAction;
import org.bsc.langgraph4j.state.AgentState;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

/** LangGraph4j 1.6 的 node_async 没有 Executor 重载，在此显式指定执行器。 */
public final class WorkflowNodeAction {
    private WorkflowNodeAction() {
    }

    public static ExecutorService executor(String beanName) {
        // 未配置时明确失败，不偷偷回退到 commonPool 或创建无人管理的线程池。
        return SpringContextUtil.getBean(beanName, ExecutorService.class);
    }

    public static <S extends AgentState> AsyncNodeAction<S> node_async(
            NodeAction<S> action, ExecutorService executor) {
        Objects.requireNonNull(executor, "executor");
        return state -> CompletableFuture.supplyAsync(() -> {
            try {
                return action.apply(state);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }
}
