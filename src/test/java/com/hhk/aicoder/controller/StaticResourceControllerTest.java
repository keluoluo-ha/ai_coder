package com.hhk.aicoder.controller;

import cn.hutool.crypto.digest.DigestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpHeaders;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaticResourceControllerTest {

    @Test
    void whitelistUsesOneDayCacheOthersNoCache() {
        HttpHeaders cached = new HttpHeaders();
        StaticResourceController.applyCacheControl(cached, "index.html");
        assertEquals("public, max-age=86400", cached.getCacheControl());

        HttpHeaders raw = new HttpHeaders();
        StaticResourceController.applyCacheControl(raw, "notes.txt");
        assertEquals("no-cache", raw.getCacheControl());
        assertTrue(StaticResourceController.isCacheableExtension("app.js"));
        assertTrue(StaticResourceController.isCacheableExtension("font.woff2"));
        assertFalse(StaticResourceController.isCacheableExtension("secret.map"));
    }

    @Test
    void etagIsMd5OfNameMtimeSizeAndIfNoneMatchHits(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("index.html");
        Files.writeString(file, "<html></html>");
        File f = file.toFile();
        StaticResourceController.StaticFileMeta meta = StaticResourceController.StaticFileMeta.from(f);
        String expected = DigestUtil.md5Hex(f.getName() + ":" + f.lastModified() + ":" + f.length());
        assertEquals(expected, meta.getEtag());
        assertTrue(StaticResourceController.etagMatches("\"" + expected + "\"", meta.getEtag()));
        assertTrue(StaticResourceController.etagMatches(expected, meta.getEtag()));
        assertFalse(StaticResourceController.etagMatches("\"other\"", meta.getEtag()));
    }
}
