package com.hhk.aicoder.service;

/**
 * Agent 长期记忆。
 *
 * @author hhk
 */
public interface AgentMemoryService {

    /**
     * 拼进 system prompt 的记忆块。没有生效记忆时返回空字符串。
     * 格式：[id=3][项目约定] 数据库统一用下划线命名
     */
    String buildMemoryPrompt(Long userId);

    /**
     * 保存一条长期记忆。同用户、同标签、同内容且仍生效时跳过，不重复插入。
     */
    void saveMemory(Long userId, String content, String tag);

    /**
     * 软删：把 is_active 置 0。只能作废当前用户自己的生效记录。
     */
    void deactivateMemory(Long userId, Long memoryId);

    /**
     * 根据应用 id 查所属用户。
     */
    Long getUserIdByAppId(Long appId);
}
