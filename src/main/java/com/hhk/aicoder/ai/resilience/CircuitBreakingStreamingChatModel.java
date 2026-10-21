package com.hhk.aicoder.ai.resilience;

import com.hhk.aicoder.exception.ServiceBusyException;
import dev.langchain4j.model.ModelProvider;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ChatRequestParameters;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;

/**
 * 装饰 StreamingChatModel：请求前 acquirePermission，完成/失败回写熔断器。
 * 打开时立刻 onError(ServiceBusyException)，不打到 OpenAI。
 */
@Slf4j
@RequiredArgsConstructor
public class CircuitBreakingStreamingChatModel implements StreamingChatModel {

    private final StreamingChatModel delegate;
    private final OpenAiCircuitBreaker openAiCircuitBreaker;

    @Override
    public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
        try {
            openAiCircuitBreaker.acquirePermission();
        } catch (ServiceBusyException busy) {
            handler.onError(busy);
            return;
        }
        long started = System.nanoTime();
        delegate.doChat(chatRequest, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                handler.onPartialResponse(partialResponse);
            }

            @Override
            public void onPartialToolExecutionRequest(int index, dev.langchain4j.agent.tool.ToolExecutionRequest partialToolExecutionRequest) {
                handler.onPartialToolExecutionRequest(index, partialToolExecutionRequest);
            }

            @Override
            public void onCompleteToolExecutionRequest(int index, dev.langchain4j.agent.tool.ToolExecutionRequest completeToolExecutionRequest) {
                handler.onCompleteToolExecutionRequest(index, completeToolExecutionRequest);
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                openAiCircuitBreaker.onSuccess(started);
                handler.onCompleteResponse(completeResponse);
            }

            @Override
            public void onError(Throwable error) {
                openAiCircuitBreaker.onError(started, error);
                handler.onError(error instanceof ServiceBusyException ? error : sanitize(error));
            }
        });
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
    public Set<dev.langchain4j.model.chat.Capability> supportedCapabilities() {
        return delegate.supportedCapabilities();
    }

    private static Throwable sanitize(Throwable error) {
        log.warn("OpenAI 流式调用失败，降级为前端兜底文案: {}", error.toString());
        return new RuntimeException("生成失败，请重试", error);
    }
}
