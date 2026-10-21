package com.hhk.aicoder.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hhk.aicoder.ai.AiCodeGenTypeRoutingService;
import com.hhk.aicoder.ai.AiCodeGenTypeRoutingServiceFactory;
import com.hhk.aicoder.cache.AppIdBloomFilter;
import com.hhk.aicoder.common.ResultUtils;
import com.hhk.aicoder.constant.AppConstant;
import com.hhk.aicoder.job.TmpDirLock;
import com.hhk.aicoder.core.builder.VueProjectBuilder;
import com.hhk.aicoder.core.handler.StreamHandlerExecutor;
import com.hhk.aicoder.langgraph4j.CodeGenWorkflow;
import com.hhk.aicoder.exception.BusinessException;
import com.hhk.aicoder.exception.ErrorCode;
import com.hhk.aicoder.exception.ThrowUtils;
import com.hhk.aicoder.model.dto.app.AppAddRequest;
import com.hhk.aicoder.model.dto.app.AppQueryRequest;
import com.hhk.aicoder.model.entity.User;
import com.hhk.aicoder.model.enums.ChatHistoryMessageTypeEnum;
import com.hhk.aicoder.model.enums.CodeGenTypeEnum;
import com.hhk.aicoder.model.vo.AppVO;
import com.hhk.aicoder.model.vo.UserVO;
import com.hhk.aicoder.monitor.MonitorContext;
import com.hhk.aicoder.monitor.MonitorContextHolder;
import com.hhk.aicoder.service.ChatHistoryService;
import com.hhk.aicoder.service.ScreenshotService;
import com.hhk.aicoder.service.UserService;
import com.hhk.aicoder.utils.TraceIdHolder;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.spring.service.impl.ServiceImpl;
import com.hhk.aicoder.model.entity.App;
import com.hhk.aicoder.mapper.AppMapper;
import com.hhk.aicoder.service.AppService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.io.File;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.hutool.poi.excel.sax.ElementName.c;

/**
 * 应用 服务层实现。
 *
 * @author hhk
 */
@Service
@Slf4j
public class AppServiceImpl extends ServiceImpl<AppMapper, App>  implements AppService{


    @Resource
    private AiCodeGenTypeRoutingServiceFactory aiCodeGenTypeRoutingServiceFactory;
    @Resource
    private UserService userService;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ScreenshotService screenshotService;
    @Resource
    private AppIdBloomFilter appIdBloomFilter;


    @Override
    public App getAppByIdGuarded(Long appId) {
        if (appId == null || appId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if (!appIdBloomFilter.mightContain(appId)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR);
        return app;
    }

    @Override
    public AppVO getAppVO(App app) {
        if (app == null) {
            return null;
        }
        AppVO appVO = new AppVO();
        BeanUtil.copyProperties(app, appVO);
        // 关联查询用户信息
        Long userId = app.getUserId();
        if (userId != null) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            appVO.setUser(userVO);
        }
        return appVO;
    }

    @Override
    public QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest) {
        if (appQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = appQueryRequest.getId();
        String appName = appQueryRequest.getAppName();
        String cover = appQueryRequest.getCover();
        String initPrompt = appQueryRequest.getInitPrompt();
        String codeGenType = appQueryRequest.getCodeGenType();
        String deployKey = appQueryRequest.getDeployKey();
        Integer priority = appQueryRequest.getPriority();
        Long userId = appQueryRequest.getUserId();
        String sortField = appQueryRequest.getSortField();
        String sortOrder = appQueryRequest.getSortOrder();
        return QueryWrapper.create()
                .eq("id", id)
                .like("appName", appName)
                .like("cover", cover)
                .like("initPrompt", initPrompt)
                .eq("codeGenType", codeGenType)
                .eq("deployKey", deployKey)
                .eq("priority", priority)
                .eq("userId", userId)
                .orderBy(sortField, "ascend".equals(sortOrder));
    }

    @Override
    public List<AppVO> getAppVOList(List<App> appList) {
        if (CollUtil.isEmpty(appList)) {
            return new ArrayList<>();
        }
        // 批量获取用户信息，避免 N+1 查询问题
        Set<Long> userIds = appList.stream()
                .map(App::getUserId)
                .collect(Collectors.toSet());
        Map<Long, UserVO> userVOMap = userService.listByIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, userService::getUserVO));
        return appList.stream().map(app -> {
            AppVO appVO = getAppVO(app);
            UserVO userVO = userVOMap.get(app.getUserId());
            appVO.setUser(userVO);
            return appVO;
        }).collect(Collectors.toList());
    }

    @Override
    public Flux<String> chatToGenCode(Long appId, String message, User loginUser) {
        //参数检验
        if (appId<=0||appId==null){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        //权限检验
        App app = this.getById(appId);
        if (app == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用不存在");
        }
        //只有本人可以生成代码
        if(!app.getUserId().equals(loginUser.getId())){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "只有本人可以生成代码");
        }
        //获取app代码类型
        String codeGenType = app.getCodeGenType();
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if (codeGenTypeEnum == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型错误");
        }
        //保存用户聊天记录
        String appCodeGenType = app.getCodeGenType();
        if(codeGenType==null)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "代码生成类型错误");
        chatHistoryService.addChatMessage(appId,message, ChatHistoryMessageTypeEnum.USER.getValue(),loginUser.getId());
        MonitorContextHolder.setContext(MonitorContext.builder()
                .userId(loginUser.getId().toString())
                .appId(appId.toString())
                .build());
        // LangGraph：搜图 + 提示词增强后，再按应用已有类型流式生成
        Flux<String> contentflux = new CodeGenWorkflow().executeWorkflowWithFlux(message, appId);
        //保存代码保存ai对话历史记录；客户端断开时 doFinally 同步清 ThreadLocal，避免泄漏
        return streamHandlerExecutor.doExecute(contentflux, chatHistoryService, appId, loginUser,codeGenTypeEnum)
                .doFinally(signalType -> MonitorContextHolder.clearContext());

    }

    @Resource
    private com.hhk.aicoder.build.BuildQueueService buildQueue;
    @Resource
    private com.hhk.aicoder.build.BuildStatusService buildStatuses;
    @Resource
    private org.redisson.api.RedissonClient buildRedisson;

    @Override
    public com.hhk.aicoder.model.dto.app.BuildStatus deployApp(Long appId, User loginUser) {
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR);
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR);
        var lock=buildRedisson.getLock("aicoder:deploy:submit:"+appId);
        lock.lock();
        try {
            App app=this.getById(appId);
            ThrowUtils.throwIf(app==null,ErrorCode.NOT_FOUND_ERROR);
            ThrowUtils.throwIf(!loginUser.getId().equals(app.getUserId()),ErrorCode.NO_AUTH_ERROR);
            String key=app.getDeployKey();
            if (StrUtil.isBlank(key)) key=RandomUtil.randomString(16);
            var existing=buildStatuses.get(key);
            if(existing!=null && java.util.Set.of("PENDING","RUNNING","RETRYING").contains(existing.getStatus())) return existing;
            File project=new File(AppConstant.CODE_OUTPUT_ROOT_DIR,app.getCodeGenType()+"_"+appId);
            ThrowUtils.throwIf(!project.isDirectory(),ErrorCode.SYSTEM_ERROR,"Application code missing");
            App update=new App(); update.setId(appId); update.setDeployKey(key);
            ThrowUtils.throwIf(!this.updateById(update),ErrorCode.OPERATION_ERROR);
            var task=new com.hhk.aicoder.build.BuildTask(key,project.getAbsolutePath(),appId,TraceIdHolder.current(),0);
            buildStatuses.save(task,"PENDING","????",null);
            try { buildQueue.offer(task); }
            catch (RuntimeException e) {
                buildStatuses.save(task,"FAILED","????????",null);
                log.error("Build enqueue failed, traceId={}",task.traceId(),e);
                throw new com.hhk.aicoder.exception.ServiceBusyException();
            }
            return com.hhk.aicoder.model.dto.app.BuildStatus.builder().appId(appId).deployKey(key)
                    .status("PENDING").message("????").traceId(task.traceId()).updatedAt(System.currentTimeMillis()).build();
        } finally { lock.unlock(); }
    }

    /**
     * 列目录名，构建失败时便于对照 npm 实际产物。
     */
    private static String listDirNames(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            return "DIR_MISSING";
        }
        String[] names = dir.list();
        if (names == null || names.length == 0) {
            return "EMPTY";
        }
        return String.join(",", names);
    }

    private void generateAppScreenShotAsync(Long appId, String deployUrl) {
        Thread.startVirtualThread(() -> {
            try {
                log.info("开始生成应用截图，appId={}, deployUrl={}", appId, deployUrl);
                // 调用截图服务生成截图上传
                String coverPath = screenshotService.generateAndUploadScreenshot(deployUrl);
                log.info("截图生成完成，appId={}, coverPath={}", appId, coverPath);
                // 更新应用页面字段
                App app = new App();
                app.setId(appId);
                app.setCover(coverPath);
                boolean res = this.updateById(app);
                ThrowUtils.throwIf(!res, ErrorCode.OPERATION_ERROR, "更新应用封面失败");
                log.info("应用封面更新成功，appId={}", appId);
            } catch (Exception e) {
                log.error("应用截图生成或封面更新失败，appId={}, deployUrl={}, error={}", appId, deployUrl, e.getMessage(), e);
            }
        });


    }


    @Override
    public boolean removeById(Serializable id) {
        if(id==null) return false;
        Long appId = Long.valueOf(id.toString());
        if(appId<0) return false;
        try{
            chatHistoryService.deleteByAppId(appId);
        }catch (Exception e){
            log.error("删除聊天记录失败",e);
        }
        return super.removeById(id);

    }

    @Override
    public Long createApp(AppAddRequest appAddRequest, User loginUser) {
        String initPrompt = appAddRequest.getInitPrompt();
        // 构造入库对象
        App app = new App();
        BeanUtil.copyProperties(appAddRequest, app);
        // 5位Long，范围 10000‑99999
        long appId = 10000L + (long) (Math.random() * 90000);
        app.setId(appId);
        app.setUserId(loginUser.getId());
        // 应用名称暂时为 initPrompt 前 12 位
        app.setAppName(initPrompt.substring(0, Math.min(initPrompt.length(), 12)));
        //类型由路由ai决定
        AiCodeGenTypeRoutingService codeGenTypeRoutingService = aiCodeGenTypeRoutingServiceFactory.aiCodeGenTypeRoutingService();
        CodeGenTypeEnum codeGenTypeEnum = codeGenTypeRoutingService.routeCodeGenType(initPrompt);
        app.setCodeGenType(codeGenTypeEnum.getValue());
        // 插入数据库
        boolean result = this.save(app);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        appIdBloomFilter.add(app.getId());
        return app.getId();
    }

}
