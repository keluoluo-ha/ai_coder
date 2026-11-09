package com.hhk.aicoder.service.impl;

import com.hhk.aicoder.mapper.AgentMemoryMapper;
import com.hhk.aicoder.mapper.AppMapper;
import com.hhk.aicoder.model.entity.AgentMemory;
import com.hhk.aicoder.model.entity.App;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentMemoryServiceImplTest {

    @Mock
    private AgentMemoryMapper agentMemoryMapper;

    @Mock
    private AppMapper appMapper;

    @InjectMocks
    private AgentMemoryServiceImpl agentMemoryService;

    @Test
    void buildMemoryPromptIncludesActiveMemoriesAndRule() {
        when(agentMemoryMapper.selectActiveByUserId(7L)).thenReturn(List.of(
                AgentMemory.builder().id(3L).tag("PROJECT_CONVENTION").content("数据库统一用下划线命名").build()
        ));

        String prompt = agentMemoryService.buildMemoryPrompt(7L);

        assertTrue(prompt.startsWith("[id=3][项目约定] 数据库统一用下划线命名\n"));
        assertTrue(prompt.contains("调用 saveMemory 保存"));
        assertTrue(prompt.contains("先调用 deleteMemory 作废旧记忆"));
    }

    @Test
    void buildMemoryPromptIsEmptyWhenNoActiveMemory() {
        when(agentMemoryMapper.selectActiveByUserId(7L)).thenReturn(List.of());

        assertEquals("", agentMemoryService.buildMemoryPrompt(7L));
        assertEquals("", agentMemoryService.buildMemoryPrompt(null));
    }

    @Test
    void saveMemorySkipsDuplicateActiveRecord() {
        when(agentMemoryMapper.selectActiveByUserId(7L)).thenReturn(List.of(
                AgentMemory.builder().id(1L).tag("USER_PREFERENCE").content("用户偏好简洁的代码风格").build()
        ));

        agentMemoryService.saveMemory(7L, "用户偏好简洁的代码风格", "USER_PREFERENCE");

        verify(agentMemoryMapper, never()).insert(any());
    }

    @Test
    void saveMemoryInsertsNewPreference() {
        when(agentMemoryMapper.selectActiveByUserId(7L)).thenReturn(List.of());

        agentMemoryService.saveMemory(7L, " 用户偏好简洁的代码风格 ", "USER_PREFERENCE");

        ArgumentCaptor<AgentMemory> captor = ArgumentCaptor.forClass(AgentMemory.class);
        verify(agentMemoryMapper).insert(captor.capture());
        assertEquals(7L, captor.getValue().getUserId());
        assertEquals("用户偏好简洁的代码风格", captor.getValue().getContent());
        assertEquals("USER_PREFERENCE", captor.getValue().getTag());
        assertEquals(1, captor.getValue().getIsActive());
    }

    @Test
    void deactivateMemoryOnlyUpdatesOwnedActiveRecord() {
        when(agentMemoryMapper.selectActiveByUserId(7L)).thenReturn(List.of(
                AgentMemory.builder().id(3L).userId(7L).build()
        ));

        agentMemoryService.deactivateMemory(7L, 3L);
        verify(agentMemoryMapper).updateIsActiveById(3L, 0);

        assertThrows(IllegalArgumentException.class, () -> agentMemoryService.deactivateMemory(7L, 9L));
    }

    @Test
    void getUserIdByAppIdReadsAppUserId() {
        when(appMapper.selectOneById(11L)).thenReturn(App.builder().id(11L).userId(7L).build());

        assertEquals(7L, agentMemoryService.getUserIdByAppId(11L));
        assertEquals(null, agentMemoryService.getUserIdByAppId(null));
    }
}
