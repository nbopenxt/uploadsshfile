package com.openxt.uploadsshfile.util;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * D-26（SRS V2.8）回归防线：PluginPathManager 唯一控制点注入语义——
 * 未初始化 fail-fast 带指引、同值幂等合并（appStarted/菜单入口/projectOpened 三道保险
 * 共用同值调用不互斥）、异值拒绝（防第二控制点分叉）。
 */
public class PluginPathManagerTest {

    /**
     * 本类自测态（null/假值）不外泄：测试 JVM 里跨类约定单一注入值
     * tmp/uploadsshfile-test-inject（BatchExecutionOrchestratorTest 等既有语义），
     * @After 先 reset 再恢复该约定值——否则后续任何依赖注入态的类会被"未初始化"连坐
     * （D-29 实证：本类初版裸 reset 曾炸掉 KeywordMatcherTest/BatchExecutionOrchestratorTest）。
     */
    @Before
    public void resetInjectionState() {
        PluginPathManager.resetForTests();
    }

    @After
    public void restoreConventionInjection() {
        PluginPathManager.resetForTests();
        java.nio.file.Path root = java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"),
                "uploadsshfile-test-inject");
        PluginPathManager.initialize(root, root);
    }

    @Test
    public void getInstanceFailsFastBeforeInitialization() {
        try {
            PluginPathManager.getInstance();
            fail("expected IllegalStateException before initialize()");
        } catch (IllegalStateException e) {
            assertTrue("指引文案须指向 IdeBootstrap（D-26）",
                    e.getMessage().contains("IdeBootstrap"));
        }
    }

    @Test
    public void initializeIsIdempotentForSamePaths() {
        PluginPathManager.initialize(Paths.get("/tmp/cfg"), Paths.get("/tmp/log"));
        // 模拟多触发点重复注入：同值直接通过，不抛不重建
        PluginPathManager.initialize(Paths.get("/tmp/cfg"), Paths.get("/tmp/log"));
        assertNotNull(PluginPathManager.getInstance());
    }

    @Test
    public void initializeRejectsDifferentPaths() {
        PluginPathManager.initialize(Paths.get("/tmp/cfg"), Paths.get("/tmp/log"));
        try {
            PluginPathManager.initialize(Paths.get("/other/cfg"), Paths.get("/tmp/log"));
            fail("expected IllegalStateException on second control point");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("second control point"));
        }
    }

    @Test
    public void configAndLogPathsCarryPluginSubdir() {
        PluginPathManager.initialize(Paths.get("E:/fake/config"), Paths.get("E:/fake/log"));
        PluginPathManager pm = PluginPathManager.getInstance();
        assertEquals(Paths.get("E:/fake/config/uploadsshfile"), pm.getConfigPath());
        assertEquals(Paths.get("E:/fake/log/uploadsshfile"), pm.getLogPath());
    }
}
