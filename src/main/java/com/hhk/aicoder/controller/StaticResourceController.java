package com.hhk.aicoder.controller;

import cn.hutool.crypto.digest.DigestUtil;
import com.hhk.aicoder.cache.TwoLevelCache;
import com.hhk.aicoder.config.CacheConfig;
import com.hhk.aicoder.constant.AppConstant;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerMapping;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

/**
 * 静态资源访问。白名单扩展名带 Cache-Control + ETag，If-None-Match 命中 304 不读文件。
 */
@RestController
@RequestMapping("/static")
public class StaticResourceController {

    private static final String PREVIEW_ROOT_DIR = AppConstant.CODE_DEPLOY_ROOT_DIR;

    private static final Set<String> CACHEABLE_EXT = Set.of(
            "html", "css", "js", "json", "png", "jpg", "jpeg", "svg", "ico", "woff2"
    );

    @Resource
    private TwoLevelCache twoLevelCache;

    /**
     * 访问格式：http://localhost:8080/api/static/{deployKey}[/{fileName}]
     */
    @GetMapping("/{deployKey}/**")
    public ResponseEntity<org.springframework.core.io.Resource> serveStaticResource(
            @PathVariable String deployKey,
            HttpServletRequest request) {
        try {
            String resourcePath = (String) request.getAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE);
            resourcePath = resourcePath.substring(("/static/" + deployKey).length());
            if (resourcePath.isEmpty()) {
                HttpHeaders headers = new HttpHeaders();
                headers.add("Location", request.getRequestURI() + "/");
                return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
            }
            if (resourcePath.equals("/")) {
                resourcePath = "/index.html";
            }
            if (resourcePath.contains("..")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            File file = resolveSafeFile(deployKey, resourcePath);
            if (file == null || !file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }

            StaticFileMeta meta = twoLevelCache.getLocal(
                    CacheConfig.L1_STATIC_META, file.getAbsolutePath(), StaticFileMeta.class);
            if (meta == null || meta.getMtime() != file.lastModified() || meta.getSize() != file.length()) {
                meta = StaticFileMeta.from(file);
                twoLevelCache.putLocal(CacheConfig.L1_STATIC_META, file.getAbsolutePath(), meta);
            }

            String ifNoneMatch = request.getHeader(HttpHeaders.IF_NONE_MATCH);
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.CONTENT_TYPE, getContentTypeWithCharset(file.getName()));
            headers.setETag("\"" + meta.getEtag() + "\"");
            applyCacheControl(headers, file.getName());

            if (ifNoneMatch != null && etagMatches(ifNoneMatch, meta.getEtag())) {
                return new ResponseEntity<>(headers, HttpStatus.NOT_MODIFIED);
            }

            org.springframework.core.io.Resource resource = new FileSystemResource(file);
            return new ResponseEntity<>(resource, headers, HttpStatus.OK);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    static boolean isCacheableExtension(String fileName) {
        String ext = extension(fileName);
        return ext != null && CACHEABLE_EXT.contains(ext);
    }

    static void applyCacheControl(HttpHeaders headers, String fileName) {
        if (isCacheableExtension(fileName)) {
            headers.setCacheControl("public, max-age=86400");
        } else {
            headers.setCacheControl("no-cache");
        }
    }

    static boolean etagMatches(String ifNoneMatch, String etag) {
        if (ifNoneMatch == null || etag == null) {
            return false;
        }
        String expected = "\"" + etag + "\"";
        for (String token : ifNoneMatch.split(",")) {
            String candidate = token.trim();
            if ("*".equals(candidate) || expected.equals(candidate) || etag.equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private File resolveSafeFile(String deployKey, String resourcePath) {
        File root = new File(PREVIEW_ROOT_DIR, deployKey);
        File file = new File(root, resourcePath);
        Path rootPath = root.toPath().toAbsolutePath().normalize();
        Path filePath = file.toPath().toAbsolutePath().normalize();
        if (!filePath.startsWith(rootPath)) {
            return null;
        }
        return file;
    }

    private static String extension(String fileName) {
        if (fileName == null) {
            return null;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private String getContentTypeWithCharset(String fileName) {
        String ext = extension(fileName);
        if (ext == null) {
            return "application/octet-stream";
        }
        return switch (ext) {
            case "html" -> "text/html; charset=UTF-8";
            case "css" -> "text/css; charset=UTF-8";
            case "js" -> "application/javascript; charset=UTF-8";
            case "json" -> "application/json; charset=UTF-8";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "svg" -> "image/svg+xml";
            case "ico" -> "image/x-icon";
            case "woff2" -> "font/woff2";
            default -> "application/octet-stream";
        };
    }

    @Data
    public static class StaticFileMeta {
        private String etag;
        private long mtime;
        private long size;

        public static StaticFileMeta from(File file) {
            StaticFileMeta meta = new StaticFileMeta();
            meta.setMtime(file.lastModified());
            meta.setSize(file.length());
            meta.setEtag(DigestUtil.md5Hex(file.getName() + ":" + file.lastModified() + ":" + file.length()));
            return meta;
        }
    }
}
