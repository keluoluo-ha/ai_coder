package com.hhk.aicoder.langgraph4j.node;

import com.hhk.aicoder.langgraph4j.ai.ImageCollectionPlanService;
import com.hhk.aicoder.langgraph4j.ai.ImageCollectionService;

import com.hhk.aicoder.langgraph4j.model.ImageCollectionPlan;
import com.hhk.aicoder.langgraph4j.model.ImageResource;
import com.hhk.aicoder.langgraph4j.state.WorkflowContext;
import com.hhk.aicoder.langgraph4j.tools.ImageSearchTool;
import com.hhk.aicoder.langgraph4j.tools.MermaidDiagramTool;
import com.hhk.aicoder.langgraph4j.tools.UndrawIllustrationTool;
import com.hhk.aicoder.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;


import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ImageCollectorNode {
    public static AsyncNodeAction<MessagesState<String>> create() {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 图片收集");
            // 实际执行图片收集逻辑
            String originalPrompt = context.getOriginalPrompt();
            ArrayList<ImageResource> collectedImages = new ArrayList<>();
            String imageListStr="";
            try {
                //1.获取图片收集计划
                ImageCollectionPlanService planService = SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(originalPrompt);
                log.info("获取到图片收集计划，开始并发执行");
                //2.并发执行各种图片收集任务
                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();
                //并发执行内容图片搜索
                if (plan.getContentImageTasks() != null) {
                    ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);
                    for (ImageCollectionPlan.ImageSearchTask task:plan.getContentImageTasks()){
                                futures.add(CompletableFuture.supplyAsync(()->
                                    imageSearchTool.searchContentImages(task.query())
                                ));
                    }
                }
                //并发执行插画图片搜索
                if (plan.getIllustrationTasks() != null) {
                    UndrawIllustrationTool undrawIllustrationTool = SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    for (ImageCollectionPlan.IllustrationTask task:plan.getIllustrationTasks()){
                        futures.add(CompletableFuture.supplyAsync(()->
                                undrawIllustrationTool.searchIllustrations(task.query())
                        ));
                    }
                }
                //并发执行架构图生成
                if (plan.getDiagramTasks() != null) {
                    MermaidDiagramTool diagramTool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                    for (ImageCollectionPlan.DiagramTask task:plan.getDiagramTasks()){
                        futures.add(CompletableFuture.supplyAsync(()->
                                diagramTool.generateMermaidDiagram(task.mermaidCode(),task.description())
                        ));
                    }
                }
                //并发执行logo生成
                if (plan.getLogoTasks() != null) {
                    ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);
                    for (ImageCollectionPlan.ImageSearchTask task:plan.getContentImageTasks()){
                        futures.add(CompletableFuture.supplyAsync(()->
                                imageSearchTool.searchContentImages(task.query())
                        ));
                    }
                }
                CompletableFuture<Void> allTasks = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
                allTasks.join();
                for (CompletableFuture<List<ImageResource>> future:futures){
                    List<ImageResource> imageResources = future.get();
                    if(imageResources!=null){
                        collectedImages.addAll(imageResources);
                    }
                    log.info("并发图片收集完成");
                }
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
