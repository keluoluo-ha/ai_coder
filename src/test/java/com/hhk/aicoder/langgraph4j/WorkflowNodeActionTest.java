package com.hhk.aicoder.langgraph4j;

import com.hhk.aicoder.config.WorkflowExecutorConfig;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowNodeActionTest {

    private final ExecutorService executor = WorkflowExecutorConfig.createExecutor(2, "node-test-");

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void nodeRunsOnProvidedExecutor() throws Exception {
        AsyncNodeAction<MessagesState<String>> node = WorkflowNodeAction.node_async(
                state -> Map.of("thread", Thread.currentThread().getName()), executor);

        Map<String, Object> result = node.apply(new MessagesState<>(Map.of()))
                .get(2, TimeUnit.SECONDS);

        assertTrue(((String) result.get("thread")).startsWith("node-test-"));
    }

    @Test
    void nodeExceptionIsPropagatedThroughFuture() {
        AsyncNodeAction<MessagesState<String>> node = WorkflowNodeAction.node_async(state -> {
            throw new IllegalStateException("node failed");
        }, executor);
        Exception e = assertThrows(Exception.class,
                () -> node.apply(new MessagesState<>(Map.of())).get(2, TimeUnit.SECONDS));
        assertTrue(e.getCause() instanceof IllegalStateException);
        assertEquals("node failed", e.getCause().getMessage());
    }
}
