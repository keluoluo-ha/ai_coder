package com.hhk.aicoder.utils;

import com.hhk.aicoder.config.AiCoderProperties;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RemoteWebDriver 对象池。禁止 static 单例：并发截图会互相覆盖当前 URL。
 * 借出超过 {@code borrowTimeoutSeconds} 未归还时兜底销毁，防止 Chrome 进程泄漏。
 */
@Slf4j
@Component
public class WebDriverPool {


    /**
     * 对象池
     */
    private final GenericObjectPool<RemoteWebDriver> pool;
    /**
     * 超时时间
     */
    private final long borrowTimeoutSeconds;
    /**
     * 登记表
     */
    private final Map<RemoteWebDriver, Long> borrowedAt = new ConcurrentHashMap<>();
    /**
     * 这是一个定时任务调度器。构造方法里用它启动了一个任务，每 15 秒执行一次 evictLeakedDrivers()，去检查 borrowedAt 里有没有超时的记录。
     */
    private final ScheduledExecutorService sweeper;
    /**
     * 关门标志
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Autowired
    public WebDriverPool(AiCoderProperties properties) {
        this(properties.getScreenshot().getPoolMaxTotal(),
                properties.getScreenshot().getPoolMaxWaitMillis(),
                properties.getScreenshot().getBorrowTimeoutSeconds());
    }

    public WebDriverPool(int maxTotal, long maxWaitMillis, long borrowTimeoutSeconds) {
        this.borrowTimeoutSeconds = borrowTimeoutSeconds;
        GenericObjectPoolConfig<RemoteWebDriver> config = new GenericObjectPoolConfig<>();
        config.setMaxTotal(maxTotal);
        config.setMaxIdle(maxTotal);
        config.setMinIdle(0);
        config.setBlockWhenExhausted(true);
        config.setMaxWait(Duration.ofMillis(maxWaitMillis));
        config.setTestOnBorrow(true);
        // 2.11 / 2.12 都支持 millis；Duration 重载在部分传递依赖版本上缺失
        config.setTimeBetweenEvictionRunsMillis(15_000L);
        this.pool = new GenericObjectPool<>(new ChromeDriverFactory(), config);

        AbandonedConfig abandonedConfig = new AbandonedConfig();
        abandonedConfig.setRemoveAbandonedOnBorrow(true);
        abandonedConfig.setRemoveAbandonedOnMaintenance(true);
        abandonedConfig.setRemoveAbandonedTimeout((int) Math.max(1L, borrowTimeoutSeconds));
        abandonedConfig.setLogAbandoned(true);
        this.pool.setAbandonedConfig(abandonedConfig);

        this.sweeper = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "webdriver-pool-sweeper");
            t.setDaemon(true);
            return t;
        });
        this.sweeper.scheduleAtFixedRate(this::evictLeakedDrivers, 15, 15, TimeUnit.SECONDS);
        log.info("WebDriver 池已创建, maxTotal={}, maxWaitMillis={}, borrowTimeoutSeconds={}",
                maxTotal, maxWaitMillis, borrowTimeoutSeconds);
    }

    public RemoteWebDriver borrow() throws Exception {
        RemoteWebDriver driver = pool.borrowObject();
        borrowedAt.put(driver, System.currentTimeMillis());
        return driver;
    }

    public void release(RemoteWebDriver driver) {
        if (driver == null) {
            return;
        }
        Long start = borrowedAt.remove(driver);
        if (start != null && System.currentTimeMillis() - start > borrowTimeoutSeconds * 1000L) {
            log.warn("WebDriver 借出超过 {}s 未归还，兜底销毁防泄漏, traceId={}",
                    borrowTimeoutSeconds, TraceIdHolder.current());
            invalidate(driver);
            return;
        }
        try {
            pool.returnObject(driver);
        } catch (Exception e) {
            log.warn("归还 WebDriver 失败，销毁实例, traceId={}", TraceIdHolder.current(), e);
            invalidate(driver);
        }
    }

    public void invalidate(RemoteWebDriver driver) {
        if (driver == null) {
            return;
        }
        borrowedAt.remove(driver);
        try {
            pool.invalidateObject(driver);
        } catch (Exception e) {
            log.warn("销毁 WebDriver 失败", e);
            quitQuietly(driver);
        }
    }

    public int getNumActive() {
        return pool.getNumActive();
    }

    public int getMaxTotal() {
        return pool.getMaxTotal();
    }

    @PreDestroy
    public void destroy() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        sweeper.shutdownNow();
        for (RemoteWebDriver driver : new ArrayList<>(borrowedAt.keySet())) {
            quitQuietly(driver);
        }
        borrowedAt.clear();
        pool.close();
        log.info("WebDriver 池已关闭");
    }

    private void evictLeakedDrivers() {
        if (closed.get()) {
            return;
        }
        long deadline = System.currentTimeMillis() - borrowTimeoutSeconds * 1000L;
        for (Map.Entry<RemoteWebDriver, Long> entry : borrowedAt.entrySet()) {
            Long start = entry.getValue();
            if (start != null && start < deadline) {
                log.warn("WebDriver 借出超过 {}s 未归还，兜底销毁防泄漏, traceId={}",
                        borrowTimeoutSeconds, TraceIdHolder.current());
                invalidate(entry.getKey());
            }
        }
    }

    private static void quitQuietly(RemoteWebDriver driver) {
        try {
            driver.quit();
        } catch (Exception ignored) {
            // 进程已退出时忽略
        }
    }

    /**
     * 工厂只负责创建，不缓存实例。启动参数按开发机约定带 --no-sandbox。
     */
    static final class ChromeDriverFactory extends BasePooledObjectFactory<RemoteWebDriver> {

        @Override
        public RemoteWebDriver create() {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless=new");
            options.addArguments("--disable-gpu");
            options.addArguments("--single-process");
            options.addArguments("--no-zygote");
            // 开发机 / Docker 需要；生产有独立 user namespace 时可去掉
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-extensions");
            options.addArguments("--window-size=1600,900");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            RemoteWebDriver driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(5));
            return driver;
        }

        @Override
        public PooledObject<RemoteWebDriver> wrap(RemoteWebDriver driver) {
            return new DefaultPooledObject<>(driver);
        }

        @Override
        public boolean validateObject(PooledObject<RemoteWebDriver> pooled) {
            try {
                return pooled.getObject().getSessionId() != null;
            } catch (Exception e) {
                return false;
            }
        }

        @Override
        public void destroyObject(PooledObject<RemoteWebDriver> pooled) {
            quitQuietly(pooled.getObject());
        }
    }
}
