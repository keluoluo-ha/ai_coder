package com.hhk.aicoder.mapper;

import com.hhk.aicoder.model.entity.AgentMemory;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Agent 长期记忆 映射层。
 *
 * @author hhk
 */
public interface AgentMemoryMapper {

    int insert(AgentMemory agentMemory);

    int updateIsActiveById(@Param("id") Long id, @Param("isActive") Integer isActive);

    List<AgentMemory> selectActiveByUserId(@Param("userId") Long userId);
}
