package com.hhk.aicoder.ai.tools;

import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Component
public class ToolManager {
    /**
     * 工具名称到工具实例的映射
     */
    private final Map<String,BaseTool> toolMap=new HashMap<>();

    /**
     * 自动注入所有工具
     */
    @Resource
    private BaseTool[] tools;

    /**
     * 根据工具名称获取工具
     * @param toolName
     * @return
     */
    public BaseTool getTools(String toolName){
        return toolMap.get(toolName);
    }

    /**
     * 获取已注册的工具集合
     * @return
     */
    public BaseTool[] getAllTool(){
        return tools;
    }


}
