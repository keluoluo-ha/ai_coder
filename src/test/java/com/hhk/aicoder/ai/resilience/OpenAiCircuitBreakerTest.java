package com.hhk.aicoder.ai.resilience;

import com.hhk.aicoder.exception.ServiceBusyException;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiCircuitBreakerTest {

    @Test
    void acquirePermissionThrowsServiceBusyWhenOpen() {
        OpenAiCircuitBreaker breaker = new OpenAiCircuitBreaker();
        breaker.get().transitionToOpenState();
        ServiceBusyException ex = assertThrows(ServiceBusyException.class, breaker::acquirePermission);
        assertEquals("服务繁忙", ex.getMessage());
    }

    @Test
    void streamingDecoratorDoesNotCallDelegateWhenOpen() {
        OpenAiCircuitBreaker breaker = new OpenAiCircuitBreaker();
        breaker.get().transitionToOpenState();
        AtomicInteger calls = new AtomicInteger();
        StreamingChatModel delegate = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                calls.incrementAndGet();
                handler.onCompleteResponse(null);
            }
        };
        AtomicReference<Throwable> error = new AtomicReference<>();
        new CircuitBreakingStreamingChatModel(delegate, breaker)
                .doChat(request(), new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                    }

                    @Override
                    public void onError(Throwable err) {
                        error.set(err);
                    }
                });
        assertEquals(0, calls.get(), "熔断打开时不得打到 OpenAI");
        assertInstanceOf(ServiceBusyException.class, error.get());
        assertEquals("服务繁忙", error.get().getMessage());
    }

    @Test
    void streamingDecoratorSanitizesUpstreamErrors() {
        OpenAiCircuitBreaker breaker = new OpenAiCircuitBreaker();
        StreamingChatModel delegate = new StreamingChatModel() {
            @Override
            public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
                handler.onError(new RuntimeException("openai 500 internal, request-id=abc"));
            }
        };
        AtomicReference<Throwable> error = new AtomicReference<>();
        new CircuitBreakingStreamingChatModel(delegate, breaker)
                .doChat(request(), new StreamingChatResponseHandler() {
                    @Override
                    public void onPartialResponse(String partialResponse) {
                    }

                    @Override
                    public void onCompleteResponse(ChatResponse completeResponse) {
                    }

                    @Override
                    public void onError(Throwable err) {
                        error.set(err);
                    }
                });
        assertEquals("生成失败，请重试", error.get().getMessage());
        assertTrue(error.get().getCause().getMessage().contains("openai 500"));
    }

    private static ChatRequest request() {
        return ChatRequest.builder().messages(UserMessage.from("hi")).build();
    }
}
