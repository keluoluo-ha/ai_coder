package com.hhk.aicoder.core.handler;

import com.hhk.aicoder.langgraph4j.CodeGenWorkflow;
import com.hhk.aicoder.model.entity.User;
import com.hhk.aicoder.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SimpleTextStreamHandlerTest {

    @Mock
    private ChatHistoryService chatHistoryService;

    @Test
    void heartbeatIsForwardedButNotPersistedAsChatHistory() {
        User user = new User();
        user.setId(7L);
        Flux<String> origin = Flux.just("hello", CodeGenWorkflow.HEARTBEAT_JSON, "world");

        Flux<String> handled = new SimpleTextStreamHandler().handle(origin, chatHistoryService, 1L, user);

        StepVerifier.create(handled)
                .expectNext("hello")
                .expectNext(CodeGenWorkflow.HEARTBEAT_JSON)
                .expectNext("world")
                .verifyComplete();

        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(chatHistoryService).addChatMessage(anyLong(), content.capture(), anyString(), anyLong());
        assertFalse(content.getValue().contains("heartbeat"), "心跳不能写入对话历史");
    }
}
