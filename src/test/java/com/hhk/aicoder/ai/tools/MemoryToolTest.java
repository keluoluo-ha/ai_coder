package com.hhk.aicoder.ai.tools;

import cn.hutool.json.JSONObject;
import com.hhk.aicoder.service.AgentMemoryService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MemoryToolTest {

    @Test
    void executedResultUsesMemoryFormat() {
        MemoryTool tool = new MemoryTool();
        JSONObject saved = new JSONObject();
        saved.set("content", "用户偏好简洁的代码风格");
        assertEquals("[长期记忆] 已保存：用户偏好简洁的代码风格", tool.generateToolExecutedResult(saved));

        JSONObject deleted = new JSONObject();
        deleted.set("memoryId", 3);
        assertEquals("[长期记忆] 已作废：3", tool.generateToolExecutedResult(deleted));
    }

    @Test
    void missingUserReturnsErrorAndDoesNotThrow() {
        AgentMemoryService agentMemoryService = mock(AgentMemoryService.class);
        when(agentMemoryService.getUserIdByAppId(1L)).thenReturn(null);
        MemoryTool tool = new MemoryTool();
        ReflectionTestUtils.setField(tool, "agentMemoryService", agentMemoryService);

        assertDoesNotThrow(() -> {
            assertEquals("保存长期记忆失败: 无法根据应用解析用户",
                    tool.saveMemory("简洁风格", "USER_PREFERENCE", 1L));
            assertEquals("作废长期记忆失败: 无法根据应用解析用户",
                    tool.deleteMemory(3L, 1L));
        });
    }

    @Test
    void toolManagerRegistersBothMemoryMethods() {
        MemoryTool memoryTool = new MemoryTool();
        ToolManager toolManager = new ToolManager();
        ReflectionTestUtils.setField(toolManager, "tools", new BaseTool[]{memoryTool});
        toolManager.registerTools();

        assertEquals(memoryTool, toolManager.getTools("saveMemory"));
        assertEquals(memoryTool, toolManager.getTools("deleteMemory"));
    }
}
