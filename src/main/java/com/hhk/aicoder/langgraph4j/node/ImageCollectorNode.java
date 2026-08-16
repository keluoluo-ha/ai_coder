package com.hhk.aicoder.langgraph4j.node;

import com.hhk.aicoder.langgraph4j.ai.ImageCollectionService;
import com.hhk.aicoder.langgraph4j.model.ImageCategoryEnum;
import com.hhk.aicoder.langgraph4j.model.ImageResource;
import com.hhk.aicoder.langgraph4j.state.WorkflowContext;
import com.hhk.aicoder.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.Arrays;
import java.util.List;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ImageCollectorNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 图片收集");
            
            // 实际执行图片收集逻辑
            String originalPrompt = context.getOriginalPrompt();
            String imageListStr="";
            try {
                ImageCollectionService imageCollectionService = SpringContextUtil.getBean(ImageCollectionService.class);
                imageListStr = imageCollectionService.collectImages(originalPrompt);
            } catch (Exception e) {
                log.error("图片收集失败:{}",e.getMessage());
            }

            // 更新状态
            context.setCurrentStep("图片收集");
            context.setImageListStr(imageListStr);
            return WorkflowContext.saveContext(context);
        });
    }
}
