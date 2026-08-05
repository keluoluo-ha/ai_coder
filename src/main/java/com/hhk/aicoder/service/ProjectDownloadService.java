package com.hhk.aicoder.service;

import jakarta.servlet.http.HttpServletResponse;

import java.net.http.HttpResponse;

public interface ProjectDownloadService {


    /**
     * 下载文件
     * @param projectPath 文件路径
     * @param downloadName 文件名
     * @param httpResponse http
     */
    public void downloadProject(String projectPath,String downloadName, HttpServletResponse httpServletResponse);

}
