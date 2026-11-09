package com.hhk.aicoder.ai;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hhk.aicoder.ai.guardrial.PromptSafetyInputGuardrail;
import com.hhk.aicoder.ai.resilience.CircuitBreakingStreamingChatModel;
import com.hhk.aicoder.ai.resilience.OpenAiCircuitBreaker;
import com.hhk.aicoder.ai.tools.*;
import cn.hutool.core.util.StrUtil;
import com.hhk.aicoder.model.enums.CodeGenTypeEnum;
import com.hhk.aicoder.service.AgentMemoryService;
import com.hhk.aicoder.service.ChatHistoryService;
import com.hhk.aicoder.utils.SpringContextUtil;
import dev.langchain4j.community.store.memory.chat.redis.RedisChatMemoryStore;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * @author hhk
 */
@Configuration
@Slf4j
public class AiCodeGeneratorServiceFactory {

    @Resource(name = "openAiChatModel")
    private ChatModel chatModel;


    @Resource
    private StreamingChatModel openAiStreamingChatModel;

    @Resource
    private RedisChatMemoryStore redisChatMemoryStore;

    @Resource
    private ChatHistoryService chatHistoryService;

    @Resource
    private StreamingChatModel reasoningStreamingChatModel;

    @Resource
    private ToolManager toolManager;

    @Resource
    private OpenAiCircuitBreaker openAiCircuitBreaker;

    @Resource
    private AgentMemoryService agentMemoryService;

    private static final Map<CodeGenTypeEnum, String> BASE_SYSTEM_PROMPTS = Map.of(
            CodeGenTypeEnum.HTML, readPrompt("prompt/codegen-html-system-prompt.txt"),
            CodeGenTypeEnum.MULTI_FILE, readPrompt("prompt/codegen-multi-file-system-prompt.txt"),
            CodeGenTypeEnum.VUE_PROJECT, readPrompt("prompt/codegen-vue-project-system-prompt.txt")
    );

    private final Cache<String,AiCodeGeneratorService> serviceCache= Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .expireAfterAccess(Duration.ofMinutes(10))
            .removalListener(((key, value, cause) ->
                    log.info("aiCodeGeneratorService for appId:{} is removed, cause:{}",key,cause)
            ))
            .build();


    /**
     * 创建AI代码生成服务
     * @return
     */
    @Bean
    public AiCodeGeneratorService aiCodeGeneratorService() {
        return getAiCodeGeneratorService(0L, CodeGenTypeEnum.HTML);
    }


    /**
     * 获取AI代码生成器服务
     * @param appId 应用ID
     * @param codeGenTypeEnum 代码生成类型枚举
     * @return AiCodeGeneratorService AI代码生成器服务实例
     */
    public AiCodeGeneratorService getAiCodeGeneratorService(long appId,CodeGenTypeEnum codeGenTypeEnum) {
    // 根据应用ID和代码生成类型构建缓存键
        String cacheKey = buildCacheKey(appId, codeGenTypeEnum);
    // 从缓存中获取服务，如果不存在则创建新的服务实例并缓存
        return serviceCache.get(cacheKey, key->createAiCodeGeneratorService(appId, codeGenTypeEnum));
    }

    /**
     * 构建缓存key
     * @param appId
     * @param codeGenTypeEnum
     * @return
     */
    private String buildCacheKey(long appId, CodeGenTypeEnum codeGenTypeEnum) {
        return appId+"_"+codeGenTypeEnum;
    }


    /**
     * 根据appId创建对话记忆模块的服务
     * @param appId
     * @return
     */
    private AiCodeGeneratorService createAiCodeGeneratorService(long appId, CodeGenTypeEnum codeGenTypeEnum) {
        log.info("create aiCodeGeneratorService for appId:{}",appId);
        //根据appId构建独立的对话记忆
        MessageWindowChatMemory messageWindowChatMemory = MessageWindowChatMemory.builder()
                .id(appId)
                .chatMemoryStore(redisChatMemoryStore)
                .maxMessages(20)
                .build();
        //加载历史对话
        chatHistoryService.loadChatHitstoryToMemory(appId,messageWindowChatMemory,20);
        String systemMessage = composeSystemPrompt(codeGenTypeEnum, resolveMemoryPrompt(appId));
        //根据类型切换大模型
        // 注意：流式服务不要配置 outputGuardrails。
        // LangChain4j 会把所有 token 先缓冲，校验完再一次性吐出，前端就会「思考很久 → 突然全文出现」。
        return switch (codeGenTypeEnum){
            case VUE_PROJECT -> {
               StreamingChatModel reasoningStreamChatModel= wrapStreaming(
                       SpringContextUtil.getBean("reasoningStreamingChatModel",StreamingChatModel.class));
               yield AiServices.builder(AiCodeGeneratorService.class)
                       .streamingChatModel(reasoningStreamChatModel)
                       .chatMemoryProvider(memoryId->messageWindowChatMemory)
                       .systemMessageProvider(memoryId -> systemMessage)
                       .tools(toolManager.getAllTool())
                       .inputGuardrails(new PromptSafetyInputGuardrail())
                       .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(toolExecutionRequest,"Error:there is no tool called"+toolExecutionRequest))
                       .build();
            }
            case HTML,MULTI_FILE -> {
                StreamingChatModel openAistreamChatModel= wrapStreaming(
                        SpringContextUtil.getBean("streamingChatModelPrototype",StreamingChatModel.class));
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .streamingChatModel(openAistreamChatModel)
                        .inputGuardrails(new PromptSafetyInputGuardrail())
                        .chatMemoryProvider(memoryId->messageWindowChatMemory)
                        .systemMessageProvider(memoryId -> systemMessage)
                        .build();
            }
            default -> throw new IllegalArgumentException("unsupported codeGenTypeEnum:"+codeGenTypeEnum);
        };

    }

    private String resolveMemoryPrompt(long appId) {
        if (appId <= 0) {
            return "";
        }
        Long userId = agentMemoryService.getUserIdByAppId(appId);
        if (userId == null) {
            return "";
        }
        String memoryPrompt = agentMemoryService.buildMemoryPrompt(userId);
        return memoryPrompt == null ? "" : memoryPrompt;
    }

    /**
     * systemMessageProvider 会覆盖接口上的 @SystemMessage，因此这里带上原来的提示词文件。
     * HTML 与 MULTI_FILE 共用一个 builder，但服务按代码生成类型分别缓存，提示词按类型取。
     */
    private String composeSystemPrompt(CodeGenTypeEnum codeGenTypeEnum, String memoryPromptBlock) {
        String basePrompt = BASE_SYSTEM_PROMPTS.get(codeGenTypeEnum);
        if (StrUtil.isBlank(memoryPromptBlock)) {
            return basePrompt;
        }
        return basePrompt + "\n\n" + memoryPromptBlock;
    }

    private static String readPrompt(String classpath) {
        try (InputStream in = AiCodeGeneratorServiceFactory.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in == null) {
                throw new IllegalStateException("系统提示词不存在: " + classpath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取系统提示词失败: " + classpath, e);
        }
    }

    private StreamingChatModel wrapStreaming(StreamingChatModel model) {
        if (model instanceof CircuitBreakingStreamingChatModel) {
            return model;
        }
        return new CircuitBreakingStreamingChatModel(model, openAiCircuitBreaker);
    }

    }


