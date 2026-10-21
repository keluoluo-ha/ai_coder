package com.hhk.aicoder.langgraph4j.node;

import com.hhk.aicoder.config.ImageCollectExecutorConfig;
import com.hhk.aicoder.langgraph4j.ai.ImageCollectionPlanService;
import com.hhk.aicoder.langgraph4j.model.ImageCollectionPlan;
import com.hhk.aicoder.langgraph4j.model.ImageResource;
import com.hhk.aicoder.langgraph4j.state.WorkflowContext;
import com.hhk.aicoder.langgraph4j.tools.ImageSearchTool;
import com.hhk.aicoder.langgraph4j.tools.LogoGeneratorTool;
import com.hhk.aicoder.langgraph4j.tools.MermaidDiagramTool;
import com.hhk.aicoder.langgraph4j.tools.UndrawIllustrationTool;
import com.hhk.aicoder.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.bsc.langgraph4j.action.AsyncNodeAction.node_async;

@Slf4j
public class ImageCollectorNode {

    /** 单任务超时：外部搜索/生图接口不可靠，避免 join() 无限等待拖死整个工作流 */
    private static final long TASK_TIMEOUT_SECONDS =
            ImageCollectExecutorConfig.TASK_TIMEOUT_SECONDS;

    public static AsyncNodeAction<MessagesState<String>> create() {
        return create(resolveExecutor());
    }

    public static AsyncNodeAction<MessagesState<String>> create(Executor executor) {
        return node_async(state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            context.checkpoint();
            log.info("执行节点: 图片收集，线程池: {}", executor);
            String originalPrompt = context.getOriginalPrompt();
            ArrayList<ImageResource> collectedImages = new ArrayList<>();
            StringBuilder imageListStrBuilder = new StringBuilder();
            try {
                // 1. 获取图片收集计划
                ImageCollectionPlanService planService =
                        SpringContextUtil.getBean(ImageCollectionPlanService.class);
                ImageCollectionPlan plan = planService.planImageCollection(originalPrompt);
                log.info("获取到图片收集计划，开始并发执行");

                // 2. 并发执行各种图片收集任务（全部投递到独立业务线程池）
                List<CompletableFuture<List<ImageResource>>> futures = new ArrayList<>();

                // 内容图片搜索
                if (plan.getContentImageTasks() != null) {
                    ImageSearchTool imageSearchTool = SpringContextUtil.getBean(ImageSearchTool.class);
                    for (ImageCollectionPlan.ImageSearchTask task : plan.getContentImageTasks()) {
                        futures.add(submit(executor,
                                () -> imageSearchTool.searchContentImages(task.query())));
                    }
                }
                // 插画图片搜索
                if (plan.getIllustrationTasks() != null) {
                    UndrawIllustrationTool undrawIllustrationTool =
                            SpringContextUtil.getBean(UndrawIllustrationTool.class);
                    for (ImageCollectionPlan.IllustrationTask task : plan.getIllustrationTasks()) {
                        futures.add(submit(executor,
                                () -> undrawIllustrationTool.searchIllustrations(task.query())));
                    }
                }
                // 架构图生成
                if (plan.getDiagramTasks() != null) {
                    MermaidDiagramTool diagramTool = SpringContextUtil.getBean(MermaidDiagramTool.class);
                    for (ImageCollectionPlan.DiagramTask task : plan.getDiagramTasks()) {
                        futures.add(submit(executor,
                                () -> diagramTool.generateMermaidDiagram(task.mermaidCode(), task.description())));
                    }
                }
                // Logo 生成
                if (plan.getLogoTasks() != null) {
                    LogoGeneratorTool logoGeneratorTool = SpringContextUtil.getBean(LogoGeneratorTool.class);
                    for (ImageCollectionPlan.LogoTask task : plan.getLogoTasks()) {
                        futures.add(submit(executor,
                                () -> logoGeneratorTool.generateLogos(task.description())));
                    }
                }

                // 3. 汇聚（每个任务已独立降级，此处不会抛出）
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                for (CompletableFuture<List<ImageResource>> future : futures) {
                    List<ImageResource> imageResources = future.join();
                    if (imageResources == null || imageResources.isEmpty()) {
                        continue;
                    }
                    collectedImages.addAll(imageResources);
                    for (ImageResource imageResource : imageResources) {
                        imageListStrBuilder.append(imageResource.getCategory().getValue())
                                .append(",\"")
                                .append(imageResource.getDescription())
                                .append("\",")
                                .append(imageResource.getUrl())
                                .append("\n");
                    }
                }
                log.info("并发图片收集完成，共收集 {} 个素材", collectedImages.size());
            } catch (Exception e) {
                log.error("图片收集失败", e);
            }

            context.setCurrentStep("图片收集");
            context.setImageList(collectedImages);
            context.setImageListStr(imageListStrBuilder.toString());
            return WorkflowContext.saveContext(context);
        });
    }

    /**
     * 统一提交：指定业务线程池 + 单任务超时 + 失败降级为空结果，
     * 保证单个任务失败/超时不会连带丢弃其它任务的结果
     */
    private static CompletableFuture<List<ImageResource>> submit(
            Executor executor, Supplier<List<ImageResource>> task) {
        return CompletableFuture.supplyAsync(task, executor)
                .orTimeout(TASK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle((result, ex) -> {
                    if (ex != null) {
                        log.error("单个图片收集任务失败或超时，已降级为空结果", ex);
                        return List.<ImageResource>of();
                    }
                    return result == null ? List.<ImageResource>of() : result;
                });
    }

    /**
     * 优先从 Spring 容器取线程池；非 Spring 环境（如 WorkflowApp#main）兜底为默认业务线程池
     */
    private static Executor resolveExecutor() {
        try {
            return SpringContextUtil.getBean(ImageCollectExecutorConfig.BEAN_NAME, Executor.class);
        } catch (Exception e) {
            log.warn("未从 Spring 容器获取到 {}，使用默认业务线程池",
                    ImageCollectExecutorConfig.BEAN_NAME);
            return ImageCollectExecutorConfig.createExecutor();
        }
    }
}
