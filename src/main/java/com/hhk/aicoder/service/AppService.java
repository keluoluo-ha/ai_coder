package com.hhk.aicoder.service;

import com.hhk.aicoder.model.dto.app.AppAddRequest;
import com.hhk.aicoder.model.dto.app.AppQueryRequest;
import com.hhk.aicoder.model.entity.User;
import com.hhk.aicoder.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import com.hhk.aicoder.model.entity.App;
import reactor.core.publisher.Flux;

import java.io.Serializable;
import java.util.List;

/**
 * 应用 服务层。
 *
 * @author hhk
 */
public interface AppService extends IService<App> {




    AppVO getAppVO(App app);

    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);

    List<AppVO> getAppVOList(List<App> appList);

    /**
     * 根据应用id和消息，生成代码并保存
     * @param appId
     * @param message
     * @param loginUser
     * @return
     */
    Flux<String> chatToGenCode(Long appId, String message, User loginUser);

    /**
     * 部署静态资源
     * @param appId
     * @param loginUser
     * @return
     */
    String deployApp(Long appId, User loginUser);


    /**
     * 删除应用
     * @param id
     * @return
     */
    boolean removeById(Serializable id);

    /**
     * 创建应用
     * @param appAddRequest
     * @param loginUser
     * @return
     */
    Long createApp(AppAddRequest appAddRequest,User loginUser);
}
