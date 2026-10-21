package com.hhk.aicoder.core.builder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VueProjectBuilderTest {

    private final VueProjectBuilder builder = new VueProjectBuilder();

    @Test
    void buildProjectReturnsFalseWhenDirectoryMissing() {
        assertFalse(builder.buildProject("tmp/definitely-not-a-vue-project"));
    }

    @Test
    void buildProjectReturnsFalseWhenPackageJsonMissing(@TempDir Path dir) {
        assertFalse(builder.buildProject(dir.toString()));
    }

    /**
     * 回归：异步方法在构建完成前就返回 true。deployApp 不能再用它，必须调同步 buildProject。
     * 不写 package.json，避免后台虚拟线程真的拉起 npm。
     */
    @Test
    void buildProjectAsyncReturnsTrueBeforeDistExists(@TempDir Path dir) {
        boolean returned = builder.buildProjectAsync(dir.toString());
        assertTrue(returned, "fire-and-forget 立刻返回 true");
        assertFalse(new File(dir.toFile(), "dist").exists(),
                "异步刚返回时 dist 还不存在");
    }
}
