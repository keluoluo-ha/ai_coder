package com.hhk.aicoder.service.impl;

import cn.hutool.core.io.FileUtil;
import com.hhk.aicoder.exception.ErrorCode;
import com.hhk.aicoder.exception.ThrowUtils;
import com.hhk.aicoder.manager.CosManager;
import com.hhk.aicoder.service.ScreenshotService;
import com.hhk.aicoder.utils.WebScreenshotUtils;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
public class ScreenshotServiceImpl implements ScreenshotService {


    @Resource
    private CosManager cosManager;

    @Override
    public String generateAndUploadScreenshot(String webUrl) {
        //参数校验
        ThrowUtils.throwIf(StringUtils.isBlank(webUrl), ErrorCode.PARAMS_ERROR, "url不能为空");
        log.info("开始生成网页截图，URL:{}",webUrl);
        // 生成本地网页截图
        String localScreenShotPath = WebScreenshotUtils.saveWebPageScreenShot(webUrl);
        ThrowUtils.throwIf(StringUtils.isBlank(localScreenShotPath), ErrorCode.PARAMS_ERROR, "生成网页截图失败");

        try {
            log.info("开始上传网页截图，URL:{},本地路径:{}",webUrl,localScreenShotPath);
            //上传到cos存储
            String screenShotToCosPath = uploadScreenShotToCos(localScreenShotPath);
            ThrowUtils.throwIf(StringUtils.isBlank(screenShotToCosPath), ErrorCode.SYSTEM_ERROR, "上传网页截图失败");
            log.info("上传网页截图成功，URL:{},本地路径:{},COS路径:{}",webUrl,localScreenShotPath,screenShotToCosPath);
            return screenShotToCosPath;
        }  finally {
             //清理本地文件
            cleanupLocalFile(localScreenShotPath);
        }

    }

    private String uploadScreenShotToCos(String localScreenShotPath) {
        //参数校验
        if(localScreenShotPath==null){
            return null;
        }
        File screenshotFile = new File(localScreenShotPath);
        if(!screenshotFile.exists()){
            log.error("本地网页截图文件不存在，路径:{}",localScreenShotPath);
            return null;
        }
        String fileName = UUID.randomUUID().toString().substring(0, 8) + "_compressed.jpg";
        //生成COS对象键
        String cosKey = generateScreenKey(fileName);
        return cosManager.uploadFile(cosKey, screenshotFile);
    }

    /**
     * 生成COS对象键
     * @param fileName
     */
    private String generateScreenKey(String fileName) {
        String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        return String.format("/screenshots/%s/%s", datePath, fileName);
    }

    /**
     * 清理本地文件
     *
     * @param localFilePath 本地文件路径
     */
    private void cleanupLocalFile(String localFilePath) {
        File localFile = new File(localFilePath);
        if (localFile.exists()) {
            File parentDir = localFile.getParentFile();
            FileUtil.del(parentDir);
            log.info("本地截图文件已清理: {}", localFilePath);
        }
    }
}
