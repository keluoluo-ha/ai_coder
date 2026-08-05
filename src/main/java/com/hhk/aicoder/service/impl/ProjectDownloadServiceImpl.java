package com.hhk.aicoder.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.core.util.ZipUtil;
import com.hhk.aicoder.exception.ErrorCode;
import com.hhk.aicoder.exception.ThrowUtils;
import com.hhk.aicoder.service.ProjectDownloadService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Set;


@Service
@Slf4j
public class ProjectDownloadServiceImpl implements ProjectDownloadService {
    /**
     * 需要过滤的文件和目录名称
     */
    private static final Set<String> IGNORED_NAMES = Set.of(
            "node_modules",
            ".git",
            "dist",
            "build",
            ".DS_Store",
            ".env",
            "target",
            ".mvn",
            ".idea",
            ".vscode"
    );

    /**
     * 需要过滤的文件扩展名
     */
    private static final Set<String> IGNORED_EXTENSIONS = Set.of(
            ".log",
            ".tmp",
            ".cache"
    );
    @Override
    public void downloadProject(String projectPath, String downloadFileName, HttpServletResponse httpServletResponse) {
        //校验参数
        ThrowUtils.throwIf(StrUtil.isBlank(projectPath), ErrorCode.PARAMS_ERROR,"projectPath不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(downloadFileName), ErrorCode.PARAMS_ERROR,"downloadName不能为空");
        File projectDir = new File(projectPath);
        ThrowUtils.throwIf(!projectDir.exists(), ErrorCode.PARAMS_ERROR,"文件不存在");
        ThrowUtils.throwIf(!projectDir.isDirectory(), ErrorCode.PARAMS_ERROR,"文件不是目录");
        log.info("开始下载项目:{}",projectPath);
        //设置http响应头
        httpServletResponse.setStatus(HttpServletResponse.SC_OK);
        httpServletResponse.setContentType("application/zip");
        httpServletResponse.addHeader("Content-Disposition",
                String.format("attachment; filename=\"%s.zip\"", downloadFileName));
        //过滤文件
        FileFilter fileFilter=file->isPathAllowed(file.toPath(), file.toPath());
        //利用hutool进行下载
        try {
            ZipUtil.zip(httpServletResponse.getOutputStream(), StandardCharsets.UTF_8,false,fileFilter,projectDir);
            log.info("项目下载完成：{}",downloadFileName);
        } catch (Exception e) {
            log.error("项目下载失败：{}",downloadFileName,e);
            ThrowUtils.throwIf(true, ErrorCode.SYSTEM_ERROR,"项目下载失败");
        }

    }

    /**
     * 过滤文件
     * @param projectRoot
     * @param fullPath
     * @return
     */
    private boolean isPathAllowed(Path projectRoot, Path fullPath) {
        Path relativizePath = projectRoot.relativize(fullPath);
        for (Path part:relativizePath){
            String partName = part.toString();
        if(IGNORED_NAMES.contains(partName)){
            return false;
        }
        if(IGNORED_EXTENSIONS.stream().anyMatch(partName::endsWith)){
            return false;
        }
        }
        return true;

    }
}


