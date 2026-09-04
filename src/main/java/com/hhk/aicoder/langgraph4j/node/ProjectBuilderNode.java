package com.hhk.aicoder.langgraph4j.node;

import com.hhk.aicoder.core.builder.VueProjectBuilder;
import com.hhk.aicoder.langgraph4j.state.WorkflowContext;
import com.hhk.aicoder.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.bsc.langgraph4j.prebuilt.MessagesState;

import java.io.File;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class ProjectBuilderNode {

    public static AsyncNodeAction<MessagesState<String>> create() {
        return state -> {
            WorkflowContext context = WorkflowContext.getContext(state);
            log.info("执行节点: 项目构建");
            String generatedCodeDir = context.getGeneratedCodeDir();

            // 非阻塞异步：把 npm install + build 放到独立虚拟线程里执行，
            // 完成后 complete 节点 Future，不阻塞工作流驱动线程。
            // 注意：这里仍会等待构建完成（Future 未 complete 前工作流不会推进），
            // 因为右侧预览要读取 dist/index.html，构建不能 fire-and-forget。
            CompletableFuture<Map<String, Object>> future = new CompletableFuture<>();
            Thread.ofVirtual().name("vue-build-" + System.currentTimeMillis()).start(() -> {
                String buildResultDir;
                try {
                    VueProjectBuilder vueBuilder = SpringContextUtil.getBean(VueProjectBuilder.class);
                    boolean buildSuccess = vueBuilder.buildProject(generatedCodeDir);
                    if (buildSuccess) {
                        buildResultDir = generatedCodeDir + File.separator + "dist";
                        log.info("Vue 项目构建成功，dist 目录: {}", buildResultDir);
                    } else {
                        buildResultDir = generatedCodeDir;
                        log.error("Vue 项目构建失败，回退原路径: {}", generatedCodeDir);
                    }
                } catch (Exception e) {
                    log.error("Vue 项目构建异常: {}", e.getMessage(), e);
                    buildResultDir = generatedCodeDir; // 异常时返回原路径
                }

                // 更新状态
                context.setCurrentStep("项目构建");
                context.setBuildResultDir(buildResultDir);
                log.info("项目构建节点完成，最终目录: {}", buildResultDir);
                future.complete(WorkflowContext.saveContext(context));
            });
            return future;
        };
    }
}
