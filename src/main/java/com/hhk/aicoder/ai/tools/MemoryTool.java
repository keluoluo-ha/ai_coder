package com.hhk.aicoder.ai.tools;

import cn.hutool.json.JSONObject;
import com.hhk.aicoder.service.AgentMemoryService;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 长期记忆工具。
 * 由模型决定何时保存或作废，失败时返回错误字符串，不中断对话。
 */
@Slf4j
@Component
public class MemoryTool extends BaseTool {

    @Resource
    private AgentMemoryService agentMemoryService;

    @Tool("当用户明确说出自己的偏好、项目约定或重要事实时，保存为长期记忆。临时性、一次性的要求不要保存")
    public String saveMemory(
            @P("记忆内容，一句话，100字以内") String content,
            @P("标签：USER_PREFERENCE / PROJECT_CONVENTION / IMPORTANT_FACT") String tag,
            @ToolMemoryId Long appId) {
        try {
            Long userId = agentMemoryService.getUserIdByAppId(appId);
            if (userId == null) {
                return "保存长期记忆失败: 无法根据应用解析用户";
            }
            agentMemoryService.saveMemory(userId, content, tag);
            log.info("保存长期记忆成功, appId={}, userId={}", appId, userId);
            return "已保存长期记忆: " + content;
        } catch (Exception e) {
            String errorMessage = "保存长期记忆失败: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Tool("当新说法与已有长期记忆矛盾时，先作废旧记忆。记忆ID可从系统提示词的记忆列表中获取")
    public String deleteMemory(
            @P("要作废的记忆ID") Long memoryId,
            @ToolMemoryId Long appId) {
        try {
            Long userId = agentMemoryService.getUserIdByAppId(appId);
            if (userId == null) {
                return "作废长期记忆失败: 无法根据应用解析用户";
            }
            agentMemoryService.deactivateMemory(userId, memoryId);
            log.info("作废长期记忆成功, appId={}, userId={}, memoryId={}", appId, userId, memoryId);
            return "已作废长期记忆: " + memoryId;
        } catch (Exception e) {
            String errorMessage = "作废长期记忆失败: " + e.getMessage();
            log.error(errorMessage, e);
            return errorMessage;
        }
    }

    @Override
    public String getToolName() {
        return "saveMemory";
    }

    @Override
    public String getDisplayName() {
        return "保存长期记忆";
    }

    @Override
    public String generateToolExecutedResult(JSONObject arguments) {
        Long memoryId = arguments.getLong("memoryId");
        if (memoryId != null) {
            return "[长期记忆] 已作废：" + memoryId;
        }
        String content = arguments.getStr("content");
        return "[长期记忆] 已保存：" + content;
    }
}
