package com.hhk.aicoder.ai;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hhk.aicoder.ai.tools.*;
import com.hhk.aicoder.model.enums.CodeGenTypeEnum;
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

import java.time.Duration;

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
        //根据类型切换大模型
        return switch (codeGenTypeEnum){
            case VUE_PROJECT -> {
               StreamingChatModel reasoningStreamChatModel= SpringContextUtil.getBean("reasoningStreamChatModel",StreamingChatModel.class);
               yield AiServices.builder(AiCodeGeneratorService.class)
                       .streamingChatModel(reasoningStreamingChatModel)
                       .chatMemoryProvider(memoryId->messageWindowChatMemory)
                       .tools(toolManager.getAllTool())
                       .hallucinatedToolNameStrategy(toolExecutionRequest -> ToolExecutionResultMessage.from(toolExecutionRequest,"Error:there is no tool called"+toolExecutionRequest))
                       .build();
            }
            case HTML,MULTI_FILE -> {
                StreamingChatModel openAistreamChatModel= SpringContextUtil.getBean("streamChatModelPrototype",StreamingChatModel.class);
                yield AiServices.builder(AiCodeGeneratorService.class)
                        .streamingChatModel(openAistreamChatModel)
                        .chatMemoryProvider(memoryId->messageWindowChatMemory)
                        .build();
            }
            default -> throw new IllegalArgumentException("unsupported codeGenTypeEnum:"+codeGenTypeEnum);
        };

    }

    }


