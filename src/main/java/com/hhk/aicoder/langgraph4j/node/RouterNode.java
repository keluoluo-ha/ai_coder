
package com.hhk.aicoder.langgraph4j.node;

import com.hhk.aicoder.ai.AiCodeGenTypeRoutingService;
import com.hhk.aicoder.langgraph4j.state.WorkflowContext;
import com.hhk.aicoder.model.enums.CodeGenTypeEnum;
import com.hhk.aicoder.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class RouterNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 智能路由");

            CodeGenTypeEnum generationType;
            // TODO: 实际执行智能路由逻辑
            try {
                AiCodeGenTypeRoutingService aiCodeGenTypeRoutingService = SpringContextUtil.getBean(AiCodeGenTypeRoutingService.class);
                generationType = aiCodeGenTypeRoutingService.routeCodeGenType(context.getOriginalPrompt());
                log.info("AI智能路由完成，选择类型：{}({})",generationType.getValue(),generationType.getText());
            } catch (Exception e) {
                log.error(("AI智能路由失败，使用默认HTML"));
                generationType=CodeGenTypeEnum.HTML;
            }


            // 更新状态
            context.setCurrentStep("智能路由");
            context.setGenerationType(generationType);
            log.info("路由决策完成，选择类型: {}", generationType.getText());
            return WorkflowContext.saveContext(context);
        });
    }
}
