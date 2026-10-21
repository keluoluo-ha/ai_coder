package com.hhk.aicoder.langgraph4j;

import cn.hutool.json.JSONUtil;
import com.hhk.aicoder.config.WorkflowExecutorConfig;
import com.hhk.aicoder.exception.BusinessException;
import com.hhk.aicoder.exception.ErrorCode;
import com.hhk.aicoder.langgraph4j.model.QualityResult;
import com.hhk.aicoder.langgraph4j.node.*;
import com.hhk.aicoder.langgraph4j.state.WorkflowContext;
import com.hhk.aicoder.model.enums.CodeGenTypeEnum;
import com.hhk.aicoder.monitor.MonitorContext;
import com.hhk.aicoder.monitor.MonitorContextHolder;
import com.hhk.aicoder.utils.TraceIdHolder;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphRepresentation;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.NodeOutput;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import org.bsc.langgraph4j.prebuilt.MessagesStateGraph;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Map;

import static org.bsc.langgraph4j.StateGraph.END;
import static org.bsc.langgraph4j.StateGraph.START;
import static org.bsc.langgraph4j.action.AsyncEdgeAction.edge_async;

@Slf4j
public class CodeGenWorkflow {

    public static final String HEARTBEAT_JSON = "{\"type\":\"heartbeat\"}";
    public static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);

    /**
     * 创建完整的工作流
     */
    public CompiledGraph<MessagesState<String>> createWorkflow() {
        try {
            java.util.concurrent.ExecutorService codeGenExecutor = WorkflowNodeAction.executor(WorkflowExecutorConfig.CODE_GEN);
            java.util.concurrent.ExecutorService qualityCheckExecutor = WorkflowNodeAction.executor(WorkflowExecutorConfig.QUALITY_CHECK);
            java.util.concurrent.ExecutorService buildExecutor = WorkflowNodeAction.executor(WorkflowExecutorConfig.BUILD);
            return new MessagesStateGraph<String>()
                    // 添加节点 - 使用完整实现的节点
                    .addNode("image_collector", ImageCollectorNode.create())
                    .addNode("prompt_enhancer", PromptEnhancerNode.create())
                    .addNode("router", RouterNode.create())
                    .addNode("code_quality_check", CodeQualityCheckNode.create(qualityCheckExecutor))
                    .addNode("code_generator", CodeGeneratorNode.create(codeGenExecutor))
                    .addNode("project_builder", ProjectBuilderNode.create(buildExecutor))
                    // 添加边
                    .addEdge(START, "image_collector")
                    .addEdge("image_collector", "prompt_enhancer")
                    .addEdge("prompt_enhancer", "router")
                    .addEdge("router", "code_generator")
                    .addEdge("code_generator","code_quality_check")
                    .addConditionalEdges("code_quality_check",
                            edge_async(this::routeAfterQualityCheck), Map.of(
                                    "build","project_builder",
                                    "skip_build",END,
                                    "fail","code_generator"
                            ))
                    .addEdge("project_builder", END)

                    // 编译工作流
                    .compile();
        } catch (GraphStateException e) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "工作流创建失败");
        }
    }

    private String routeBuildOrSkip(MessagesState<String> stringMessagesState) {
        WorkflowContext context = WorkflowContext.getContext(stringMessagesState);
        CodeGenTypeEnum generationType = context.getGenerationType();
        if(generationType==CodeGenTypeEnum.HTML||generationType==CodeGenTypeEnum.MULTI_FILE){
            return "skip_build";
        }
        //VUE_PROJECT需要构建
        return "build";
    }

    /**
     * 执行工作流
     */
    public WorkflowContext executeWorkflow(String originalPrompt, Long appId) {
        CompiledGraph<MessagesState<String>> workflow = createWorkflow();

        // 初始化 WorkflowContext
        WorkflowContext initialContext = WorkflowContext.builder()
                .originalPrompt(originalPrompt)
                .appId(appId)
                .currentStep("初始化")
                .build();

        GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
        log.info("工作流图:\n{}", graph.content());
        log.info("开始执行代码生成工作流");

        WorkflowContext finalContext = null;
        int stepCounter = 1;
        for (NodeOutput<MessagesState<String>> step : workflow.stream(
                Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
            log.info("--- 第 {} 步完成 ---", stepCounter);
            // 显示当前状态
            WorkflowContext currentContext = WorkflowContext.getContext(step.state());
            if (currentContext != null) {
                finalContext = currentContext;
                log.info("当前步骤上下文: {}", currentContext);
            }
            stepCounter++;
        }
        log.info("代码生成工作流执行完成！");
        return finalContext;
    }
    private String routeAfterQualityCheck(MessagesState<String> state) {
        WorkflowContext context = WorkflowContext.getContext(state);
        QualityResult qualityResult = context.getQualityResult();
        // 如果质检失败，重新生成代码
        if (qualityResult == null || !qualityResult.getIsValid()) {
            log.error("代码质检失败，需要重新生成代码");
            return "fail";
        }
        // 质检通过，使用原有的构建路由逻辑
        log.info("代码质检通过，继续后续流程");
        return routeBuildOrSkip(state);
    }

    /**
     * 执行工作流（Flux 流式输出版本）。
     * 每 15s 合并一条 type=heartbeat，避免代理/网关空闲断连。
     * 客户端断开时 cancel 虚拟线程 + 取消标志，打断 blockLast 的 AI 调用。
     */
    public Flux<String> executeWorkflowWithFlux(String originalPrompt, Long appId) {
        WorkflowCancellation cancellation = new WorkflowCancellation();
        String parentTraceId = TraceIdHolder.current();
        Flux<String> work = Flux.create(sink -> {
            Thread worker = Thread.ofVirtual()
                    .name("codegen-wf-" + appId + "-" + System.currentTimeMillis())
                    .unstarted(() -> {
                        WorkflowCancellation.bindCurrent(cancellation);
                        TraceIdHolder.set(parentTraceId);
                        MonitorContextHolder.setContext(MonitorContext.builder()
                                .appId(appId == null ? null : appId.toString())
                                .build());
                        try {
                            cancellation.checkpoint();
                            CompiledGraph<MessagesState<String>> workflow = createWorkflow();
                            WorkflowContext initialContext = WorkflowContext.builder()
                                    .originalPrompt(originalPrompt)
                                    .appId(appId)
                                    .currentStep("初始化")
                                    .cancellation(cancellation)
                                    .build();
                            sink.next(formatSseEvent("workflow_start", Map.of(
                                    "message", "开始执行代码生成工作流",
                                    "originalPrompt", originalPrompt,
                                    "appId", appId
                            )));
                            GraphRepresentation graph = workflow.getGraph(GraphRepresentation.Type.MERMAID);
                            log.info("工作流图:\n{}", graph.content());

                            int stepCounter = 1;
                            for (NodeOutput<MessagesState<String>> step : workflow.stream(
                                    Map.of(WorkflowContext.WORKFLOW_CONTEXT_KEY, initialContext))) {
                                cancellation.checkpoint();
                                log.info("--- 第 {} 步完成 ---", stepCounter);
                                WorkflowContext currentContext = WorkflowContext.getContext(step.state());
                                if (currentContext != null) {
                                    currentContext.setCancellation(cancellation);
                                    sink.next(formatSseEvent("step_completed", Map.of(
                                            "stepNumber", stepCounter,
                                            "currentStep", currentContext.getCurrentStep()
                                    )));
                                    log.info("当前步骤上下文: {}", currentContext);
                                }
                                stepCounter++;
                            }
                            cancellation.checkpoint();
                            sink.next(formatSseEvent("workflow_completed", Map.of(
                                    "message", "代码生成工作流执行完成！"
                            )));
                            log.info("代码生成工作流执行完成！");
                            sink.complete();
                        } catch (WorkflowCancelledException e) {
                            log.info("工作流已取消, appId={}, traceId={}", appId, TraceIdHolder.current());
                            if (!sink.isCancelled()) {
                                sink.complete();
                            }
                        } catch (Exception e) {
                            if (cancellation.isCancelled() || Thread.currentThread().isInterrupted()) {
                                log.info("工作流在取消过程中退出, appId={}, error={}", appId, e.getMessage());
                                if (!sink.isCancelled()) {
                                    sink.complete();
                                }
                                return;
                            }
                            log.error("工作流执行失败, traceId={}", TraceIdHolder.current(), e);
                            String frontend = frontendMessage(e);
                            sink.next(formatSseEvent("workflow_error", Map.of(
                                    "error", frontend,
                                    "message", frontend,
                                    "traceId", TraceIdHolder.current()
                            )));
                            sink.error(e instanceof com.hhk.aicoder.exception.ServiceBusyException
                                    ? e
                                    : new RuntimeException(frontend, e));
                        } finally {
                            WorkflowCancellation.clearCurrent();
                            MonitorContextHolder.clearContext();
                            TraceIdHolder.clear();
                        }
                    });
            cancellation.bind(worker);
            worker.start();
            sink.onDispose(cancellation::cancel);
            sink.onCancel(cancellation::cancel);
        });
        return mergeWithHeartbeat(work, HEARTBEAT_INTERVAL)
                .doFinally(signalType -> {
                    cancellation.cancel();
                    MonitorContextHolder.clearContext();
                    log.info("工作流 Flux 结束, signal={}, appId={}, traceId={}",
                            signalType, appId, TraceIdHolder.current());
                });
    }

    /**
     * 业务流与心跳合并。心跳在业务流结束时自动停止，避免泄漏 interval。
     */
    static Flux<String> mergeWithHeartbeat(Flux<String> source, Duration interval) {
        Flux<String> shared = source.publish().refCount(1);
        Flux<String> heartbeat = Flux.interval(interval)
                .map(tick -> HEARTBEAT_JSON)
                .takeUntilOther(shared.ignoreElements());
        //        就是把两条流合并成一条
        return Flux.merge(shared, heartbeat);
    }

    private static String frontendMessage(Throwable e) {
        if (e instanceof com.hhk.aicoder.exception.ServiceBusyException) {
            return com.hhk.aicoder.exception.ServiceBusyException.DEFAULT_MESSAGE;
        }
        if (e instanceof com.hhk.aicoder.exception.BusinessException businessException) {
            return businessException.getMessage();
        }
        return "生成失败，请重试";
    }

    public static boolean isHeartbeat(String chunk) {
        if (chunk == null) {
            return false;
        }
        String trimmed = chunk.trim();
        return HEARTBEAT_JSON.equals(trimmed)
                || trimmed.contains("\"type\":\"heartbeat\"");
    }

    /**
     * 格式化 SSE 事件的辅助方法
     */
    private String formatSseEvent(String eventType, Object data) {
        try {
            String jsonData = JSONUtil.toJsonStr(data);
            return "event: " + eventType + "\ndata: " + jsonData + "\n\n";
        } catch (Exception e) {
            log.error("格式化 SSE 事件失败: {}", e.getMessage(), e);
            return "event: error\ndata: {\"error\":\"格式化失败\"}\n\n";
        }
    }


}
