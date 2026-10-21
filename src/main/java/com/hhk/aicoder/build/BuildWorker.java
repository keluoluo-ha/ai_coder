package com.hhk.aicoder.build;

import cn.hutool.core.io.FileUtil;
import com.hhk.aicoder.config.WorkflowExecutorConfig;
import com.hhk.aicoder.constant.AppConstant;
import com.hhk.aicoder.core.builder.VueProjectBuilder;
import com.hhk.aicoder.mapper.AppMapper;
import com.hhk.aicoder.model.entity.App;
import com.hhk.aicoder.service.ScreenshotService;
import com.hhk.aicoder.utils.TraceIdHolder;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class BuildWorker implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(BuildWorker.class);
    private final BuildQueueService queue;
    private final BuildStatusService statuses;
    private final ExecutorService executor;
    private final VueProjectBuilder builder;
    private final AppMapper mapper;
    private final RedissonClient redisson;
    private final ScreenshotService screenshots;
    private volatile boolean running;
    private final List<Thread> consumers = new ArrayList<>();
    public BuildWorker(BuildQueueService queue, BuildStatusService statuses,
            @Qualifier(WorkflowExecutorConfig.BUILD) ExecutorService executor,
            VueProjectBuilder builder, AppMapper mapper, RedissonClient redisson, ScreenshotService screenshots) {
        this.queue=queue; this.statuses=statuses; this.executor=executor; this.builder=builder;
        this.mapper=mapper; this.redisson=redisson; this.screenshots=screenshots;
    }
    public synchronized void start() {
        if (running) return;
        running=true;
        int count=((ThreadPoolExecutor)executor).getCorePoolSize();
        for (int i=0;i<count;i++) {
            String name="build-consumer-"+UUID.randomUUID();
            consumers.add(Thread.ofVirtual().name(name).start(() -> consume(name)));
        }
    }
    private void consume(String name) {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                var delivery=queue.poll(name);
                if (delivery!=null) executor.submit(() -> process(delivery)).get();
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            catch (Exception e) {
                log.error("Build consumer failure; unacked deliveries remain recoverable",e);
                try { Thread.sleep(1000); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            }
        }
    }
    private void process(BuildQueueService.Delivery delivery) {
        BuildTask task=delivery.task();
        TraceIdHolder.set(task.traceId());
        var taskLock=redisson.getLock("aicoder:build:lock:"+task.deployKey());
        boolean locked=false;
        try {
            locked=taskLock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) return; // Remains pending, recovered later.
            var old=statuses.get(task.deployKey());
            if (old!=null && "SUCCESS".equals(old.getStatus())) { queue.ack(delivery); return; }
            statuses.save(task,"RUNNING","???",null);
            App app=mapper.selectOneById(task.appId());
            if (app==null) throw new IllegalStateException("Application no longer exists");
            File source=new File(task.projectPath()).getCanonicalFile();
            File expected=new File(AppConstant.CODE_OUTPUT_ROOT_DIR,app.getCodeGenType()+"_"+task.appId()).getCanonicalFile();
            if (!source.equals(expected) || !source.isDirectory()) throw new IllegalStateException("Invalid project path");
            if (!task.deployKey().matches("[a-zA-Z0-9_-]+")) throw new IllegalStateException("Invalid deploy key");
            var sourceLock=redisson.getLock("tmp:lock:"+source.getName());
            var deployLock=redisson.getLock("tmp:lock:"+task.deployKey());
            sourceLock.lock();
            try {
                if ("vue_project".equals(app.getCodeGenType())) {
                    if (!builder.buildProject(source.getPath())) throw new IllegalStateException("npm build failed");
                    source=new File(source,"dist");
                    if (!source.isDirectory()) throw new IllegalStateException("dist missing");
                }
                deployLock.lock();
                try { FileUtil.copyContent(source,new File(AppConstant.CODE_DEPLOY_ROOT_DIR,task.deployKey()),true); }
                finally { deployLock.unlock(); }
            } finally { sourceLock.unlock(); }
            App update=new App(); update.setId(task.appId()); update.setDeployKey(task.deployKey());
            update.setDeployedTime(LocalDateTime.now());
            if (mapper.update(update)<=0) throw new IllegalStateException("Deployment DB update failed");
            String url=AppConstant.CODE_DEPLOY_HOST+"/"+task.deployKey()+"/";
            statuses.save(task,"SUCCESS","????",url);
            queue.ack(delivery);
            Thread.startVirtualThread(() -> {
                try {
                    App cover=new App(); cover.setId(task.appId());
                    cover.setCover(screenshots.generateAndUploadScreenshot(url)); mapper.update(cover);
                } catch (Exception e) { log.warn("Screenshot failed, traceId={}",task.traceId(),e); }
            });
        } catch (Exception e) {
            log.error("Build failed, appId={}, traceId={}, retry={}",task.appId(),task.traceId(),task.retryCount(),e);
            // Persist status before republishing so a fast retry cannot be overwritten by this attempt.
            statuses.save(task,"RETRYING","?????????",null);
            if (!queue.fail(delivery)) statuses.save(task,"FAILED","?????????traceId="+task.traceId(),null);
        } finally {
            if (locked && taskLock.isHeldByCurrentThread()) taskLock.unlock();
            TraceIdHolder.clear();
        }
    }
    public synchronized void stop() {
        running=false;
        consumers.forEach(Thread::interrupt);
        consumers.clear();
    }
    public boolean isRunning() { return running; }
}
