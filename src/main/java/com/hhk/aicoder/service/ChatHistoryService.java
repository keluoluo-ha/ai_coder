package com.hhk.aicoder.service;

import com.hhk.aicoder.model.dto.chathistory.ChatHistoryQueryRequest;
import com.hhk.aicoder.model.entity.User;
import com.mybatisflex.core.paginate.Page;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.hhk.aicoder.model.entity.ChatHistory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import java.time.LocalDateTime;

/**
 * 对话历史 服务层。
 *
 * @author hhk
 */
public interface ChatHistoryService extends IService<ChatHistory> {

    /**
     * 添加对话消息
     * @param appId
     * @param message
     * @param messageType
     * @param userId
     * @return
     */
    public boolean addChatMessage(Long appId, String message, String messageType, Long userId);

    /**
     * 根据应用ID删除对话消息
     * @param appId
     * @return
     */
    public boolean deleteByAppId(Long appId);

    /**
     * 获取查询对象
     * @param chatHistoryQueryRequest
     * @return
     */
    public QueryWrapper getQueryWrapper(ChatHistoryQueryRequest chatHistoryQueryRequest) ;


    /**
     * 游标查询对象
     * @param appId
     * @param pageSize
     * @param lastCreateTime
     * @param loginUser
     * @return
     */
    public Page<ChatHistory> listAppChatHistoryByPage(Long appId, int pageSize,
                                                      LocalDateTime lastCreateTime,
                                                      User loginUser);


    /**
     * 从数据库中加载对话历史到记忆中
     * @param appId
     * @param chatMemory
     * @return
     */
    int loadChatHitstoryToMemory(Long appId, MessageWindowChatMemory chatMemory,int maxcount);




}
