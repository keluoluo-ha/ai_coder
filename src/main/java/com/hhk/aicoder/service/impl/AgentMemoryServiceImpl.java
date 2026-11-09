package com.hhk.aicoder.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hhk.aicoder.mapper.AgentMemoryMapper;
import com.hhk.aicoder.mapper.AppMapper;
import com.hhk.aicoder.model.entity.AgentMemory;
import com.hhk.aicoder.model.entity.App;
import com.hhk.aicoder.model.enums.MemoryTagEnum;
import com.hhk.aicoder.service.AgentMemoryService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Agent 长期记忆 服务层实现。
 *
 * @author hhk
 */
@Service
@Slf4j
public class AgentMemoryServiceImpl implements AgentMemoryService {

    private static final int MAX_CONTENT_LENGTH = 512;

    private static final String MEMORY_RULE = """
            以上是关于该用户的长期记忆。当用户表达固定偏好/约定/重要事实时，调用 saveMemory 保存；
            当本次对话的新说法与某条记忆矛盾时，先调用 deleteMemory 作废旧记忆，再保存新记忆；
            临时性、一次性的要求不要保存。""";

    @Resource
    private AgentMemoryMapper agentMemoryMapper;

    @Resource
    private AppMapper appMapper;

    @Override
    public String buildMemoryPrompt(Long userId) {
        if (userId == null || userId <= 0) {
            return "";
        }
        List<AgentMemory> memories = agentMemoryMapper.selectActiveByUserId(userId);
        if (memories == null || memories.isEmpty()) {
            return "";
        }
        StringBuilder prompt = new StringBuilder();
        for (AgentMemory memory : memories) {
            prompt.append("[id=").append(memory.getId()).append("][")
                    .append(tagText(memory.getTag())).append("] ")
                    .append(memory.getContent())
                    .append('\n');
        }
        prompt.append(MEMORY_RULE);
        return prompt.toString();
    }

    @Override
    public void saveMemory(Long userId, String content, String tag) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户不存在，无法保存长期记忆");
        }
        String normalizedContent = StrUtil.trim(content);
        if (StrUtil.isBlank(normalizedContent)) {
            throw new IllegalArgumentException("记忆内容不能为空");
        }
        if (normalizedContent.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("记忆内容不能超过512字");
        }
        MemoryTagEnum tagEnum = MemoryTagEnum.getEnumByValue(StrUtil.trim(tag));
        if (tagEnum == null) {
            throw new IllegalArgumentException("不支持的记忆标签: " + tag);
        }
        List<AgentMemory> activeMemories = agentMemoryMapper.selectActiveByUserId(userId);
        if (activeMemories != null) {
            for (AgentMemory memory : activeMemories) {
                if (tagEnum.getValue().equals(memory.getTag())
                        && normalizedContent.equals(memory.getContent())) {
                    log.info("跳过重复长期记忆, userId={}, tag={}", userId, tagEnum.getValue());
                    return;
                }
            }
        }
        AgentMemory agentMemory = AgentMemory.builder()
                .userId(userId)
                .content(normalizedContent)
                .tag(tagEnum.getValue())
                .isActive(1)
                .build();
        agentMemoryMapper.insert(agentMemory);
        log.info("保存长期记忆, userId={}, id={}, tag={}", userId, agentMemory.getId(), tagEnum.getValue());
    }

    @Override
    public void deactivateMemory(Long userId, Long memoryId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("用户不存在，无法作废长期记忆");
        }
        if (memoryId == null || memoryId <= 0) {
            throw new IllegalArgumentException("记忆ID不能为空");
        }
        List<AgentMemory> activeMemories = agentMemoryMapper.selectActiveByUserId(userId);
        boolean owned = activeMemories != null && activeMemories.stream()
                .anyMatch(memory -> memoryId.equals(memory.getId()));
        if (!owned) {
            throw new IllegalArgumentException("记忆不存在或无权作废");
        }
        agentMemoryMapper.updateIsActiveById(memoryId, 0);
        log.info("作废长期记忆, userId={}, memoryId={}", userId, memoryId);
    }

    @Override
    public Long getUserIdByAppId(Long appId) {
        if (appId == null || appId <= 0) {
            return null;
        }
        App app = appMapper.selectOneById(appId);
        if (app == null) {
            return null;
        }
        return app.getUserId();
    }

    private String tagText(String tag) {
        MemoryTagEnum tagEnum = MemoryTagEnum.getEnumByValue(tag);
        return tagEnum == null ? tag : tagEnum.getText();
    }
}
