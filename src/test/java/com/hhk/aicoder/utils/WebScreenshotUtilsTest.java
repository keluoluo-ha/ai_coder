package com.hhk.aicoder.utils;

import com.hhk.aicoder.config.AiCoderProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.remote.RemoteWebDriver;

import java.io.File;
import java.nio.file.Files;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebScreenshotUtilsTest {

    @Mock
    private WebDriverPool pool;

    @Mock
    private RemoteWebDriver driver;

    private WebScreenshotUtils utils;
    private String lastPath;

    @BeforeEach
    void setUp() {
        utils = new WebScreenshotUtils();
        utils.setPool(pool);
        AiCoderProperties properties = new AiCoderProperties();
        utils.setAiCoderProperties(properties);
    }

    @AfterEach
    void tearDown() {
        if (lastPath != null) {
            File parent = new File(lastPath).getParentFile();
            if (parent != null && parent.exists()) {
                deleteRecursively(parent);
            }
        }
    }

    @Test
    void blankUrlReturnsPlaceholderWithoutBorrowing() throws Exception {
        lastPath = utils.saveWebPageScreenShot("  ");
        assertPlaceholder(lastPath);
        verify(pool, never()).borrow();
    }

    @Test
    void borrowTimeoutReturnsPlaceholderAndDoesNotThrow() throws Exception {
        when(pool.borrow()).thenThrow(new NoSuchElementException("Timeout waiting for idle object"));

        lastPath = utils.saveWebPageScreenShot("https://example.com");

        assertPlaceholder(lastPath);
        verify(pool, never()).invalidate(any());
        verify(pool, never()).release(any());
    }

    @Test
    void screenshotFailureInvalidatesDriverAndReturnsPlaceholder() throws Exception {
        when(pool.borrow()).thenReturn(driver);
        when(driver.executeScript("return document.readyState")).thenThrow(new RuntimeException("chrome died"));

        lastPath = utils.saveWebPageScreenShot("https://example.com");

        assertPlaceholder(lastPath);
        verify(pool).invalidate(driver);
        verify(pool, never()).release(driver);
    }

    @Test
    void successReleasesDriverAndWritesCompressedJpeg() throws Exception {
        when(pool.borrow()).thenReturn(driver);
        when(driver.executeScript("return document.readyState")).thenReturn("complete");
        when(driver.getScreenshotAs(OutputType.BYTES)).thenReturn(minimalPng());

        lastPath = utils.saveWebPageScreenShot("https://example.com");

        assertTrue(new File(lastPath).exists(), "截图文件应存在");
        assertTrue(lastPath.endsWith("_compressed.jpg"), lastPath);
        verify(pool).release(driver);
        verify(pool, never()).invalidate(driver);
    }

    private static void assertPlaceholder(String path) throws Exception {
        File file = new File(path);
        assertTrue(file.exists(), "占位图应落盘: " + path);
        assertTrue(path.contains("placeholder-screenshot.png"), path);
        byte[] bytes = Files.readAllBytes(file.toPath());
        assertTrue(bytes.length > 8, "占位图不应为空");
        assertTrue(bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47,
                "占位图应为 PNG");
        assertFalse(path.contains("src/main/resources"), "不能直接返回 classpath 路径");
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }

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
