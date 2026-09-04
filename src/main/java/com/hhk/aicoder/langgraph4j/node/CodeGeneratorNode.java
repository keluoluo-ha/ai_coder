package com.hhk.aicoder.langgraph4j.node;

import com.hhk.aicoder.constant.AppConstant;
import com.hhk.aicoder.core.AiCodeGeneratorFacade;
import com.hhk.aicoder.langgraph4j.model.QualityResult;
import com.hhk.aicoder.langgraph4j.state.WorkflowContext;
import com.hhk.aicoder.model.enums.CodeGenTypeEnum;
import com.hhk.aicoder.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class CodeGeneratorNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 代码生成");
            String userMessage = buildUserMessage(context);
            CodeGenTypeEnum generationType = context.getGenerationType();
            // 获取 AI 代码生成外观服务
            AiCodeGeneratorFacade codeGeneratorFacade = SpringContextUtil.getBean(AiCodeGeneratorFacade.class);
            log.info("开始生成代码，类型: {} ({})", generationType.getValue(), generationType.getText());
            Long appId = context.getAppId();
            if (appId == null) {
                throw new com.hhk.aicoder.exception.BusinessException(com.hhk.aicoder.exception.ErrorCode.PARAMS_ERROR, "appId 不能为空");
            }
            // 调用流式代码生成
            // 注意：不把 token 转发到 SSE 下游 —— 聊天气泡只显示工作流步骤状态，
            // 原始 AI 文本（规划段、代码、JSON 数据等）会刷屏且对用户无价值。
            Flux<String> codeStream = codeGeneratorFacade.generateAndSaveCodeStream(userMessage, generationType, appId);
            // 非阻塞异步：订阅生成流，流完成/失败时再 complete 节点 Future，
            // 不用 blockLast 阻塞等待，也没有固定超时，避免把正常的长耗时生成误杀。
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            codeStream
                    .doOnComplete(() -> {
                        // 根据类型设置生成目录
                        String generatedCodeDir = String.format("%s/%s_%s", AppConstant.CODE_OUTPUT_ROOT_DIR, generationType.getValue(), appId);
                        log.info("AI 代码生成完成，生成目录: {}", generatedCodeDir);
                        // 更新状态
                        context.setCurrentStep("代码生成");
                        context.setGeneratedCodeDir(generatedCodeDir);
                        future.complete(WorkflowContext.saveContext(context));
                    })
                    .doOnError(future::completeExceptionally)
                    .subscribe();
            return future;
        };
    }
    /**
     * 构造用户消息，如果存在质检失败结果则添加错误修复信息
     */
    private static String buildUserMessage(WorkflowContext context) {
        String userMessage = context.getEnhancedPrompt();
        // 检查是否存在质检失败结果
        QualityResult qualityResult = context.getQualityResult();
        if (isQualityCheckFailed(qualityResult)) {
            // 直接将错误修复信息作为新的提示词（起到了修改的作用）
            userMessage = buildErrorFixPrompt(qualityResult);
        }
        return userMessage;
    }

    /**
     * 判断质检是否失败
     */
    private static boolean isQualityCheckFailed(QualityResult qualityResult) {
        return qualityResult != null &&
                !qualityResult.getIsValid() &&
                qualityResult.getErrors() != null &&
                !qualityResult.getErrors().isEmpty();
    }

    /**
     * 构造错误修复提示词
     */
    private static String buildErrorFixPrompt(QualityResult qualityResult) {
        StringBuilder errorInfo = new StringBuilder();
        errorInfo.append("\n\n## 上次生成的代码存在以下问题，请修复：\n");
        // 添加错误列表
        qualityResult.getErrors().forEach(error ->
                errorInfo.append("- ").append(error).append("\n"));
        // 添加修复建议（如果有）
        if (qualityResult.getSuggestions() != null && !qualityResult.getSuggestions().isEmpty()) {
            errorInfo.append("\n## 修复建议：\n");
            qualityResult.getSuggestions().forEach(suggestion ->
                    errorInfo.append("- ").append(suggestion).append("\n"));
        }
        errorInfo.append("\n请根据上述问题和建议重新生成代码，确保修复所有提到的问题。");
        return errorInfo.toString();
    }

}
