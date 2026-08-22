package com.hhk.aicoder.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.hhk.aicoder.ai.AiCodeGenTypeRoutingService;
import com.hhk.aicoder.ai.AiCodeGenTypeRoutingServiceFactory;
import com.hhk.aicoder.common.ResultUtils;
import com.hhk.aicoder.constant.AppConstant;
import com.hhk.aicoder.core.AiCodeGeneratorFacade;
import com.hhk.aicoder.core.builder.VueProjectBuilder;
import com.hhk.aicoder.core.handler.StreamHandlerExecutor;
import com.hhk.aicoder.exception.BusinessException;
import com.hhk.aicoder.exception.ErrorCode;
import com.hhk.aicoder.exception.ThrowUtils;
import com.hhk.aicoder.model.dto.app.AppAddRequest;
import com.hhk.aicoder.model.dto.app.AppQueryRequest;
import com.hhk.aicoder.model.entity.User;
import com.hhk.aicoder.model.enums.CodeGenTypeEnum;
import com.hhk.aicoder.model.vo.AppVO;
import com.hhk.aicoder.model.vo.UserVO;
import com.hhk.aicoder.service.ChatHistoryService;
import com.hhk.aicoder.service.ScreenshotService;
import com.hhk.aicoder.service.UserService;
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
    private AiCodeGeneratorFacade aiCodeGeneratorFacade;
    @Resource
    private ChatHistoryService chatHistoryService;
    @Resource
    private StreamHandlerExecutor streamHandlerExecutor;
    @Resource
    private VueProjectBuilder vueProjectBuilder;
    @Resource
    private ScreenshotService screenshotService;


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
        chatHistoryService.addChatMessage(appId,message,appCodeGenType,loginUser.getId());
        //调用代码生成器生成代码
        Flux<String> contentflux = aiCodeGeneratorFacade.generateAndSaveCodeStream(message, codeGenTypeEnum, appId);
        //保存代码保存ai对话历史记录
        return streamHandlerExecutor.doExecute(contentflux, chatHistoryService, appId, loginUser,codeGenTypeEnum);

    }

    @Override
    public String deployApp(Long appId, User loginUser) {
        // 1. 参数校验
        ThrowUtils.throwIf(appId == null || appId <= 0, ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        ThrowUtils.throwIf(loginUser == null, ErrorCode.NOT_LOGIN_ERROR, "用户未登录");
        // 2. 查询应用信息
        App app = this.getById(appId);
        ThrowUtils.throwIf(app == null, ErrorCode.NOT_FOUND_ERROR, "应用不存在");
        // 3. 验证用户是否有权限部署该应用，仅本人可以部署
        if (!app.getUserId().equals(loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权限部署该应用");
        }
        // 4. 检查是否已有 deployKey
        String deployKey = app.getDeployKey();
        // 没有则生成 6 位 deployKey（大小写字母 + 数字）
        if (StrUtil.isBlank(deployKey)) {
            deployKey = RandomUtil.randomString(6);
        }
        // 5. 获取代码生成类型，构建源目录路径
        String codeGenType = app.getCodeGenType();
        String sourceDirName = codeGenType + "_" + appId;
        String sourceDirPath = AppConstant.CODE_OUTPUT_ROOT_DIR + File.separator + sourceDirName;
        // 6. 检查源目录是否存在
        File sourceDir = new File(sourceDirPath);
        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "应用代码不存在，请先生成代码");
        }

        //vue项目特殊处理 需要提前执行构建
        CodeGenTypeEnum codeGenTypeEnum = CodeGenTypeEnum.getEnumByValue(codeGenType);
        if(codeGenTypeEnum == CodeGenTypeEnum.VUE_PROJECT) {
            
            boolean res = vueProjectBuilder.buildProjectAsync(sourceDirPath);
            ThrowUtils.throwIf(!res, ErrorCode.SYSTEM_ERROR, "构建失败");
            //检查dist目录是否存在
            File distDir = new File(sourceDirPath, "dist");
            ThrowUtils.throwIf(!distDir.exists() || !distDir.isDirectory(), ErrorCode.SYSTEM_ERROR, "构建失败，dist目录不存在");
            sourceDir = distDir;
            log.info("构建成功");

        }
        // 7. 复制文件到部署目录
        String deployDirPath = AppConstant.CODE_DEPLOY_ROOT_DIR + File.separator + deployKey;
        try {
            FileUtil.copyContent(sourceDir, new File(deployDirPath), true);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "部署失败：" + e.getMessage());
        }
        // 8. 更新应用的 deployKey 和部署时间
        App updateApp = new App();
        updateApp.setId(appId);
        updateApp.setDeployKey(deployKey);
        updateApp.setDeployedTime(LocalDateTime.now());
        boolean updateResult = this.updateById(updateApp);
        ThrowUtils.throwIf(!updateResult, ErrorCode.OPERATION_ERROR, "更新应用部署信息失败");
        // 9. 返回可访问的 URL
        String deployUrl = String.format("%s/%s/", AppConstant.CODE_DEPLOY_HOST, deployKey);
        //10. 异步生成封面截图并且更新应用界面
        generateAppScreenShotAsync(appId,deployUrl);
        return deployUrl;
    }

    private void generateAppScreenShotAsync(Long appId, String deployUrl) {
        Thread.startVirtualThread(()->{
            //调用截图服务生成截图上传
            String coverPath = screenshotService.generateAndUploadScreenshot(deployUrl);
            //更新应用页面字段
            App app = new App();
            app.setId(appId);
            app.setCover(coverPath);
            boolean res = this.updateById(app);
            ThrowUtils.throwIf(!res, ErrorCode.OPERATION_ERROR, "更新应用封面失败");


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
        return app.getId();
    }

}
