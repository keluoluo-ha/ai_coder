package com.hhk.aicoder.job;

import com.hhk.aicoder.config.AiCoderProperties;
import com.hhk.aicoder.constant.AppConstant;
import com.hhk.aicoder.utils.TraceIdHolder;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 每小时扫 tmp/code_output、tmp/code_deploy。
 * 超 24h 删整目录；总容量超 5GB 按最旧优先淘汰到 3GB。
 * 删除前抢 Redisson 锁 tmp:lock:{dirName}，避免删正在构建/预览的目录。
 */
@Slf4j
@Component
public class TmpDirCleaner {

    @Resource
    private AiCoderProperties properties;

    @Resource
    private RedissonClient redissonClient;

    @Scheduled(fixedDelay = 3_600_000)
    public void clean() {
        TraceIdHolder.current();
        try {
            cleanRoot(Path.of(AppConstant.CODE_OUTPUT_ROOT_DIR));
            cleanRoot(Path.of(AppConstant.CODE_DEPLOY_ROOT_DIR));
        } finally {
            TraceIdHolder.clear();
        }
    }

    void cleanRoot(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return;
        }
        AiCoderProperties.Tmp cfg = properties.getTmp();
        long ttlMillis = Duration.ofHours(Math.max(1, cfg.getTtlHours())).toMillis();
        long now = System.currentTimeMillis();
        List<DirStat> dirs = listChildDirs(root);
        int expiredDeleted = 0;
        for (DirStat dir : dirs) {
            if (now - dir.lastModified >= ttlMillis && tryDelete(dir.path)) {
                expiredDeleted++;
            }
        }
        dirs = listChildDirs(root);
        long total = dirs.stream().mapToLong(d -> d.size).sum();
        int capacityDeleted = 0;
        if (total > cfg.getMaxBytes()) {
            dirs.sort(Comparator.comparingLong((DirStat d) -> d.lastModified));
            for (DirStat dir : dirs) {
                if (total <= cfg.getTargetBytes()) {
                    break;
                }
                if (tryDelete(dir.path)) {
                    total -= dir.size;
                    capacityDeleted++;
                }
            }
        }
        log.info("tmp 清理完成, root={}, expiredDeleted={}, capacityDeleted={}, remainBytes={}, traceId={}",
                root, expiredDeleted, capacityDeleted, Math.max(0, total), TraceIdHolder.current());
    }

    private boolean tryDelete(Path dir) {
        String lockName = TmpDirLock.lockName(dir.getFileName().toString());
        RLock lock = redissonClient.getLock(lockName);
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
            if (!locked) {
                log.info("跳过正在使用的目录 {}, lock={}", dir, lockName);
                return false;
            }
            deleteRecursively(dir);
            log.info("已删除过期/超额目录 {}, traceId={}", dir, TraceIdHolder.current());
            return true;
        } catch (Exception e) {
            log.warn("删除目录失败 {}, traceId={}", dir, TraceIdHolder.current(), e);
            return false;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private static List<DirStat> listChildDirs(Path root) {
        List<DirStat> result = new ArrayList<>();
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(path -> {
                try {
                    result.add(stat(path));
                } catch (IOException e) {
                    log.warn("统计目录失败 {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("列出目录失败 {}", root, e);
        }
        return result;
    }

    static DirStat stat(Path dir) throws IOException {
        final long[] size = {0L};
        final long[] mtime = {dir.toFile().lastModified()};
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                size[0] += attrs.size();
                mtime[0] = Math.max(mtime[0], attrs.lastModifiedTime().toMillis());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exc) {
                mtime[0] = Math.max(mtime[0], directory.toFile().lastModified());
                return FileVisitResult.CONTINUE;
            }
        });
        return new DirStat(dir, size[0], mtime[0]);
    }

    static void deleteRecursively(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        Files.walkFileTree(dir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exc) throws IOException {
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    record DirStat(Path path, long size, long lastModified) {
    }
}
