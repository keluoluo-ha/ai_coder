package com.hhk.aicoder.ai.tools;

import dev.langchain4j.agent.tool.Tool;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
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
    @Autowired
    private BaseTool[] tools;

    /**
     * 按工具方法名注册。展示层用 LangChain4j 的方法名查找，一个工具类可以有多个 @Tool。
     */
    @PostConstruct
    public void registerTools() {
        if (tools == null) {
            return;
        }
        for (BaseTool tool : tools) {
            if (tool.getToolName() != null) {
                toolMap.put(tool.getToolName(), tool);
            }
            Class<?> clazz = tool.getClass();
            while (clazz != null && clazz != Object.class) {
                for (Method method : clazz.getDeclaredMethods()) {
                    Tool annotation = method.getAnnotation(Tool.class);
                    if (annotation == null) {
                        continue;
                    }
                    String name = annotation.name();
                    if (name == null || name.isBlank()) {
                        name = method.getName();
                    }
                    toolMap.put(name, tool);
                }
                clazz = clazz.getSuperclass();
            }
        }
        log.info("registered tools: {}", toolMap.keySet());
    }

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
