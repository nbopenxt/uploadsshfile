package com.openxt.uploadsshfile.store;

import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.model.SingleUploadTask;
import com.openxt.uploadsshfile.util.PluginPathManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * D-37 关窗快照存储层回归：JSON 回环、删除联动清引用（槽保留、引用置 null）、
 * 老配置缺字段 Gson 自愈为 null。
 * 测试 JVM 单一约定注入值 tmp/uploadsshfile-test-inject（D-29④ 自给化纪律；
 * 本类不另起第二值——异值会触发第二控制点拒绝连坐）。
 */
public class SingleUploadTaskSnapshotTest {

    private Path cfgFile;

    @Before
    public void setUp() throws Exception {
        Path root = Paths.get(System.getProperty("java.io.tmpdir"), "uploadsshfile-test-inject");
        Files.createDirectories(root);
        PluginPathManager.initialize(root, root);
        UnifiedConfigStore.resetInstance();
        cfgFile = root.resolve("uploadsshfile").resolve("plugin-config.json");
        Files.deleteIfExists(cfgFile);
    }

    @After
    public void tearDown() throws Exception {
        UnifiedConfigStore.resetInstance();
        Files.deleteIfExists(cfgFile);
    }

    @Test
    public void snapshotRoundTripThroughJson() {
        UnifiedConfigStore store = UnifiedConfigStore.getInstance();
        store.setSingleUploadTaskId("7000000000000000301");
        store.saveSingleUploadTask(List.of("E:\\a.txt", "D:\\dir b"), "srv-1", "path-1", "cmd-1", "AUTO");

        UnifiedConfigStore.resetInstance();
        UnifiedConfigStore reloaded = UnifiedConfigStore.getInstance();
        SingleUploadTask snap = reloaded.getSingleUploadTask();
        assertNotNull(snap);
        assertEquals(List.of("E:\\a.txt", "D:\\dir b"), snap.getFilePaths());
        assertEquals("srv-1", snap.getServerId());
        assertEquals("cmd-1", snap.getCommandConfigId());
        assertEquals("AUTO", snap.getTiming());
        assertEquals("7000000000000000301", reloaded.getSingleUploadTaskId());
    }

    @Test
    public void deleteServerClearsSnapshotRefsButKeepsSlot() {
        UnifiedConfigStore store = UnifiedConfigStore.getInstance();
        ServerConfig s = new ServerConfig();
        s.setId("srv-snap-1");
        s.setHost("h1");
        s.setUsername("u");
        PathConfig p = new PathConfig();
        p.setId("path-snap-1");
        p.setServerId("srv-snap-1");
        p.setRemotePath("/tmp/x");
        store.addServer(s);
        store.addPath(p);
        store.saveSingleUploadTask(List.of("E:\\a.txt"), s.getId(), p.getId(), null, "MANUAL");

        store.deleteServer(s.getId()); // 级联删路径

        UnifiedConfigStore.resetInstance();
        SingleUploadTask snap = UnifiedConfigStore.getInstance().getSingleUploadTask();
        assertNotNull("删除只清引用，不整体删槽", snap);
        assertNull(snap.getServerId());
        assertNull(snap.getPathId());
        assertEquals(List.of("E:\\a.txt"), snap.getFilePaths()); // 文件与时机保留
        assertEquals("MANUAL", snap.getTiming());
    }

    @Test
    public void deleteCommandConfigClearsSnapshotRef() {
        UnifiedConfigStore store = UnifiedConfigStore.getInstance();
        store.saveSingleUploadTask(List.of("E:\\a.txt"), null, null, "cmd-del", null);
        com.openxt.uploadsshfile.model.CommandConfig cc = new com.openxt.uploadsshfile.model.CommandConfig();
        cc.setId("cmd-del");
        cc.setServerId("srv-none");
        store.saveCommandConfig(cc);
        store.deleteCommandConfig("cmd-del");
        UnifiedConfigStore.resetInstance();
        SingleUploadTask snap = UnifiedConfigStore.getInstance().getSingleUploadTask();
        assertNotNull(snap);
        assertNull("被删命令组引用须从快照清除并持久化", snap.getCommandConfigId());
        assertEquals(List.of("E:\\a.txt"), snap.getFilePaths());
    }

    @Test
    public void legacyConfigWithoutSnapshotFieldSelfHeals() throws Exception {
        Files.createDirectories(cfgFile.getParent());
        Files.writeString(cfgFile, "{\"servers\": [], \"paths\": [], \"commandConfigs\": [], "
                + "\"batchTasks\": [], \"singleUploadTaskId\": \"old-id-1\"}");
        UnifiedConfigStore.resetInstance();
        UnifiedConfigStore store = UnifiedConfigStore.getInstance();
        assertNull("老配置缺快照字段＝null（Gson 自愈，R16 风格）", store.getSingleUploadTask());
        assertEquals("old-id-1", store.getSingleUploadTaskId());
        assertTrue(store.getBatchTasks().isEmpty());
    }

    @Test
    public void saveSnapshotOverwritesSingleSlot() {
        UnifiedConfigStore store = UnifiedConfigStore.getInstance();
        store.saveSingleUploadTask(List.of("E:\\first.txt"), "s1", "p1", null, "AUTO");
        store.saveSingleUploadTask(List.of("E:\\second.txt", "E:\\third.txt"), "s2", "p2", null, "MANUAL");
        SingleUploadTask snap = UnifiedConfigStore.getInstance().getSingleUploadTask();
        assertEquals(2, snap.getFilePaths().size());
        assertEquals("s2", snap.getServerId());
        assertEquals("MANUAL", snap.getTiming());
    }
}
