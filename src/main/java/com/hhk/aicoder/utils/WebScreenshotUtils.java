package com.hhk.aicoder.utils;

import cn.hutool.core.img.ImgUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.RandomUtil;
import com.hhk.aicoder.config.AiCoderProperties;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 网页截图。WebDriver 从池中借出，借不到或超时返回占位图，不向调用方抛异常。
 */
@Slf4j
@Component
public class WebScreenshotUtils {

    private static final float COMPRESS_QUALITY = 0.5f;

    @Resource
    private AiCoderProperties aiCoderProperties;

    @Resource
    private WebDriverPool pool;

    /**
     * 测试注入，避免拉起真实 Chrome。
     */
    public void setPool(WebDriverPool webDriverPool) {
        this.pool = webDriverPool;
    }

    public void setAiCoderProperties(AiCoderProperties aiCoderProperties) {
        this.aiCoderProperties = aiCoderProperties;
    }

    public String saveWebPageScreenShot(String url) {
        if (StringUtils.isBlank(url)) {
            log.error("截图地址不能为空, traceId={}", TraceIdHolder.current());
            return copyPlaceholder();
        }
        RemoteWebDriver driver = null;
        boolean broken = false;
        try {
            driver = pool.borrow();
            String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator
                    + "screenshot" + File.separator + UUID.randomUUID().toString().substring(0, 8);
            FileUtil.mkdir(rootPath);
            String imageSavePath = rootPath + File.separator + RandomUtil.randomString(5) + ".png";
            driver.get(url);
            waitForPageLoad(driver);
            byte[] screenshotAs = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
            FileUtil.writeBytes(screenshotAs, imageSavePath);
            String compressedImagePath = rootPath + File.separator + RandomUtil.randomNumbers(5) + "_compressed.jpg";
            ImgUtil.compress(FileUtil.file(imageSavePath), FileUtil.file(compressedImagePath), COMPRESS_QUALITY);
            FileUtil.del(imageSavePath);
            log.info("截图成功，路径：{}, traceId={}", compressedImagePath, TraceIdHolder.current());
            return compressedImagePath;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            broken = true;
            log.warn("截图被中断，降级返回占位图, url={}, traceId={}", url, TraceIdHolder.current(), e);
            return copyPlaceholder();
        } catch (Exception e) {
            broken = true;
            log.warn("截图失败或借出超时，降级返回占位图, url={}, traceId={}", url, TraceIdHolder.current(), e);
            return copyPlaceholder();
        } finally {
            if (driver != null) {
                if (broken) {
                    pool.invalidate(driver);
                } else {
                    pool.release(driver);
                }
            }
        }
    }

    private void waitForPageLoad(RemoteWebDriver webDriver) throws InterruptedException {
        WebDriverWait webDriverWait = new WebDriverWait(webDriver, Duration.ofSeconds(30));
        webDriverWait.until(driver ->
                "complete".equals(((JavascriptExecutor) driver).executeScript("return document.readyState")));
        TimeUnit.SECONDS.sleep(2);
    }

    /**
     * 占位图复制到独立目录，调用方按原路径清理时不会删掉 classpath 资源。
     */
    String copyPlaceholder() {
        String rootPath = System.getProperty("user.dir") + File.separator + "tmp" + File.separator
                + "screenshot" + File.separator + "placeholder-" + UUID.randomUUID().toString().substring(0, 8);
        FileUtil.mkdir(rootPath);
        String target = rootPath + File.separator + "placeholder-screenshot.png";
        String classpath = aiCoderProperties == null
                ? "static/placeholder-screenshot.png"
                : aiCoderProperties.getScreenshot().getPlaceholderClasspath();
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(classpath)) {
            if (in == null) {
                log.warn("classpath 占位图不存在: {}, 写入最小 PNG, traceId={}", classpath, TraceIdHolder.current());
                FileUtil.writeBytes(minimalPng(), target);
            } else {
                FileUtil.writeFromStream(in, target);
            }
        } catch (Exception e) {
            log.warn("读取占位图失败, traceId={}", TraceIdHolder.current(), e);
            FileUtil.writeBytes(minimalPng(), target);
        }
        return target;
    }

    /**
     * 1x1 透明 PNG，仅作 classpath 资源缺失时的最后兜底。
     */
    private static byte[] minimalPng() {
        return new byte[]{
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52,
                0x00, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x01,
                0x08, 0x06, 0x00, 0x00, 0x00, 0x1F, 0x15, (byte) 0xC4,
                (byte) 0x89, 0x00, 0x00, 0x00, 0x0A, 0x49, 0x44, 0x41,
                0x54, 0x78, (byte) 0x9C, 0x63, 0x00, 0x01, 0x00, 0x00,
                0x05, 0x00, 0x01, 0x0D, 0x0A, 0x2D, (byte) 0xB4, 0x00,
                0x00, 0x00, 0x00, 0x49, 0x45, 0x4E, 0x44, (byte) 0xAE,
                0x42, 0x60, (byte) 0x82
        };
    }
}
