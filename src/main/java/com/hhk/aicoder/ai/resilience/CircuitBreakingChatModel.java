package com.hhk.aicoder.ai.resilience;

import com.hhk.aicoder.exception.ServiceBusyException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.Capability;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * 同步 ChatModel 熔断包装，覆盖路由模型等非流式调用。
 */
@Slf4j
@RequiredArgsConstructor
public class CircuitBreakingChatModel implements ChatModel {

    private final ChatModel delegate;
    private final OpenAiCircuitBreaker openAiCircuitBreaker;

    @Override
    public ChatResponse doChat(ChatRequest chatRequest) {
        openAiCircuitBreaker.acquirePermission();
        long started = System.nanoTime();
        try {
            ChatResponse response = delegate.doChat(chatRequest);
            openAiCircuitBreaker.onSuccess(started);
            return response;
        } catch (ServiceBusyException e) {
            throw e;
        } catch (RuntimeException e) {
            openAiCircuitBreaker.onError(started, e);
            log.warn("OpenAI 同步调用失败，降级: {}", e.toString());
            throw new RuntimeException("生成失败，请重试", e);
        }
    }

    @Override
    public ChatRequestParameters defaultRequestParameters() {
        return delegate.defaultRequestParameters();
    }

    @Override
    public List<ChatModelListener> listeners() {
        return delegate.listeners();
    }

    @Override
    public ModelProvider provider() {
        return delegate.provider();
    }

    @Override
    public Set<Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }
}
