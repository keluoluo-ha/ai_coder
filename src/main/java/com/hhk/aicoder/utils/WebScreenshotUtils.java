package com.hhk.aicoder.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IORuntimeException;
import cn.hutool.core.util.RandomUtil;
import com.hhk.aicoder.exception.BusinessException;
import com.hhk.aicoder.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Configuration
public class WebScreenshotUtils {

    private static final WebDriver webDriver;

    static {
        final int DEFAULT_WIDTH = 1600;
        final int DEFAULT_HEIGHT = 900;
        webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @PreDestroy
    public void destroy() {
        webDriver.quit();
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            // 自动管理 ChromeDriver
            WebDriverManager.chromedriver().setup();
            // 配置 Chrome 选项
            ChromeOptions options = new ChromeOptions();
            // 无头模式 通过--headless启动，该参数告诉浏览器在后台运行
            options.addArguments("--headless");
            // 禁用GPU（在某些环境下避免问题）
            options.addArguments("--disable-gpu");
            // 禁用沙盒模式（Docker环境需要）
            options.addArguments("--no-sandbox");
            // 禁用开发者shm使用
            options.addArguments("--disable-dev-shm-usage");
            // 设置窗口大小
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            // 禁用扩展
            options.addArguments("--disable-extensions");
            // 设置用户代理
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            // 创建驱动
            WebDriver driver = new ChromeDriver(options);
            // 设置页面加载超时
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            // 设置隐式等待
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 保存截图
     * @param image
     * @param path
     */
    private static void saveImage(byte[] image, String path) {
        try {
            FileUtil.writeBytes(image, path);
        } catch (IORuntimeException e) {
            log.error("保存截图失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 压缩截图
     * @param path
     * @param outputPath
     */
    private static void compressImage(String path, String outputPath) {
        final float COMPRESS_QUALITY= 0.5f;
        try {
            ImgUtil.compress(
                    FileUtil.file(path),
                    FileUtil.file(outputPath),
                    COMPRESS_QUALITY
            );
        } catch (IORuntimeException e) {
            log.error("压缩截图失败", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 等待页面加载完成
     * @param webDriver
     */
    private static void waitForPageLoad(WebDriver webDriver) {
        try {
            WebDriverWait webDriverWait = new WebDriverWait(webDriver, Duration.ofSeconds(30));
            webDriverWait.until(driver -> ((JavascriptExecutor) driver).executeScript("return document.readyState").equals("complete"));
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            log.error("等待页面加载失败", e);
            throw new RuntimeException(e);
        }
    }
    
    
    public static String saveWebPageScreenShot(String url) {
        //参数校验
        if(StringUtils.isBlank(url)) {
            log.error("截图地址不能为空");
            return null;
        }
        //截图
        try {
            //创建截图目录
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator + "screenshot" + File.separator + "screenshots" + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            final String IMAGE_SUFFIX=".png";
            String imageSavePath = rootPath + File.separator + RandomUtil.randomString(5) + IMAGE_SUFFIX;
            //访问网页
            webDriver.get(url);
            //等待页面加载完成
            waitForPageLoad(webDriver);
            //生成截图
            byte[] screenshotAs = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
            //保存到文件
            saveImage(screenshotAs,imageSavePath);
            //压缩图片质量
            final String COMPRESSION_SUFFIX = "_compressed.jpg";
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + COMPRESSION_SUFFIX;
            compressImage(imageSavePath, compressedImagePath);
            log.info("截图成功，截图路径：{}", compressedImagePath);
            FileUtil.del(imageSavePath);
            //返回文件路径
            return compressedImagePath;
        } catch (Exception e) {
          log.error("截图失败", e);

          return null;
        }
    }
    

}
