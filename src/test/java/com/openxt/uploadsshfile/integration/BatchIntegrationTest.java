package com.openxt.uploadsshfile.integration;

import com.openxt.uploadsshfile.TestConfig;
import com.openxt.uploadsshfile.TestConfigAssume;
import com.openxt.uploadsshfile.batch.*;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.importexport.*;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.KeywordRules;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import com.openxt.uploadsshfile.persistence.SecureStorage;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * 三期集成测试
 *
 * IT-01: 批处理任务：创建 → 保存 → 重启（模拟）→ 加载 → 验证
 * IT-02: 批处理任务：执行含真实 SFTP 连接的任务（需测试服务器）
 * IT-03: 导出 → 修改导出文件 → 导入到另一个实例 → 验证
 * IT-04: 密码加密导入后，在新实例中能成功 SFTP 连接（需测试服务器）
 * IT-05: 原有功能（配置 CRUD、密码存储）不受影响
 *
 * 注意: IT-02 和 IT-04 需要 TestConfig 中的真实 SSH 服务器
 */
public class BatchIntegrationTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path tempDir;
    private UnifiedConfigStore store;

    @Before
    public void setUp() throws Exception {
        tempDir = tempFolder.newFolder("batch-integration").toPath();
        Files.createDirectories(tempDir);
        UnifiedConfigStore.resetInstance();
        store = UnifiedConfigStore.createForTest(tempDir);
        BatchTaskManager.resetInstance();
    }

    @After
    public void tearDown() {
        BatchTaskManager.resetInstance();
        UnifiedConfigStore.resetInstance();
    }

    // ========== IT-01: 批处理持久化完整链路 ==========

    @Test
    public void testBatchTaskPersistenceRoundTrip() {
        // 准备数据
        setupServerAndPath();

        // 1. 创建批处理任务
        BatchTaskManager manager = BatchTaskManager.getInstance();
        BatchTask task = new BatchTask();
        task.setName("集成测试-部署任务");

        List<BatchSubTask> subs = new ArrayList<>();
        BatchSubTask sub = new BatchSubTask();
        ServerConfig server = store.getConfig().getServers().get(0);
        PathConfig path = store.getConfig().getPaths().get(0);
        sub.setServerId(server.getId());
        sub.setPathId(path.getId());
        sub.setFilePaths(List.of("/test/file1.txt", "/test/file2.txt"));
        sub.setOrder(1);
        subs.add(sub);
        task.setSubTasks(subs);

        // 2. 保存
        manager.saveBatchTask(task);
        String savedId = task.getId();
        assertNotNull(savedId);

        // 3. 模拟重启: 重新加载 store
        UnifiedConfigStore.resetInstance();
        store = UnifiedConfigStore.createForTest(tempDir);

        // 4. 重新加载后验证
        BatchTaskManager reloadedManager = BatchTaskManager.getInstance();
        BatchTask loaded = reloadedManager.getBatchTask(savedId);
        assertNotNull("任务应在重启后仍然存在", loaded);
        assertEquals("集成测试-部署任务", loaded.getName());
        assertEquals(1, loaded.getSubTasks().size());
        assertEquals(server.getId(), loaded.getSubTasks().get(0).getServerId());
        assertEquals(path.getId(), loaded.getSubTasks().get(0).getPathId());
        assertEquals(List.of("/test/file1.txt", "/test/file2.txt"),
            loaded.getSubTasks().get(0).getFilePaths());
    }

    @Test
    public void testBatchTaskCopyPersistsAfterReload() {
        BatchTaskManager manager = BatchTaskManager.getInstance();
        BatchTask original = new BatchTask();
        original.setName("原始任务");
        original.setSubTasks(new ArrayList<>());
        manager.saveBatchTask(original);

        BatchTask copy = manager.copyBatchTask(original.getId(), "复制任务");

        // 模拟重启
        UnifiedConfigStore.resetInstance();
        store = UnifiedConfigStore.createForTest(tempDir);
        BatchTaskManager reloadedManager = BatchTaskManager.getInstance();

        // 两任务都应存在
        assertNotNull(reloadedManager.getBatchTask(original.getId()));
        assertNotNull(reloadedManager.getBatchTask(copy.getId()));
        assertEquals(2, reloadedManager.listBatchTasks().size());
    }

    // ========== IT-02: 真实 SFTP 连接执行 ==========

    @Test
    public void testRealSftpBatchExecution() throws Exception {
        // 检查测试服务器是否已配置
        TestConfigAssume.assumeLinuxRemotePathConfigured();

        // 配置服务器和密码
        ServerConfig server = new ServerConfig();
        server.setName("集成测试-Linux");
        server.setHost(TestConfig.LINUX_HOST);
        server.setPort(TestConfig.LINUX_PORT);
        server.setUsername(TestConfig.LINUX_USER);
        server.setOsType("linux");
        store.getConfig().getServers().add(server);

        PathConfig path = new PathConfig();
        path.setServerId(server.getId());
        path.setRemotePath(TestConfig.LINUX_REMOTE_PATH);
        store.getConfig().getPaths().add(path);

        // 存储密码
        SecureStorage.getInstance().store(server.getId(), TestConfig.LINUX_PASSWORD);
        store.save();

        // 创建批处理任务
        BatchTask task = new BatchTask();
        task.setName("IT-SFTP-Test");

        BatchSubTask sub = new BatchSubTask();
        sub.setServerId(server.getId());
        sub.setPathId(path.getId());
        sub.setCommandConfigId(null); // 无命令组
        // 上传项目根目录的一个文件
        File testFile = new File("build.gradle.kts");
        sub.setFilePaths(testFile.exists()
            ? List.of(testFile.getAbsolutePath())
            : List.of("src/main/resources/META-INF/plugin.xml"));
        sub.setOrder(1);
        task.setSubTasks(new ArrayList<>(List.of(sub)));

        // 执行
        CountDownLatch latch = new CountDownLatch(1);
        List<BatchSubTaskResult> captured = new ArrayList<>();

        BatchExecutionOrchestrator orchestrator = new BatchExecutionOrchestrator();
        orchestrator.setListener(new BatchExecutionListener() {
            @Override public void onBatchStart(BatchTask t, int total) {}
            @Override public void onSubTaskStart(BatchSubTask st, int idx, int total) {}
            @Override public void onUploadProgress(String fileName, int percent, long uploaded, long total) {}
            @Override public void onSubTaskCompleted(BatchSubTaskResult r) {
                captured.add(r);
            }
            @Override public void onBatchCompleted(List<BatchSubTaskResult> all) {
                captured.clear();
                captured.addAll(all);
                latch.countDown();
            }
            @Override public void onBatchCancelled() {
                latch.countDown();
            }
        });

        orchestrator.execute(task);
        boolean done = latch.await(60, TimeUnit.SECONDS);

        assertTrue("批处理应在60秒内完成", done);
        assertFalse("应有执行结果", captured.isEmpty());
        BatchSubTaskResult result = captured.get(0);
        assertEquals("子任务应执行成功",
            BatchSubTaskResult.Status.SUCCESS, result.getStatus());
    }

    // ========== IT-03: 导出导入跨实例 ==========

    @Test
    public void testCrossInstanceExportImport() throws Exception {
        // 实例A: 准备数据
        setupServerAndPath();
        BatchTaskManager mgrA = BatchTaskManager.getInstance();
        BatchTask task = createSimpleTask("实例A任务");
        mgrA.saveBatchTask(task);

        // 导出
        File exportFile = new File(tempDir.toFile(), "cross-export.json");
        ConfigExporter exporter = new ConfigExporter(store, SecureStorage.getInstance());
        exporter.export(exportFile);

        // 实例B: 空配置
        Path otherDir = tempFolder.newFolder("instance-b").toPath();
        UnifiedConfigStore.resetInstance();
        UnifiedConfigStore storeB = UnifiedConfigStore.createForTest(otherDir);

        // 导入到实例B
        ConfigImporter importer = new ConfigImporter(storeB, SecureStorage.getInstance());
        ImportResult result = importer.import_(exportFile);

        assertNotNull(result);
        assertTrue("应导入服务器", result.getServersAdded() > 0);
        assertTrue("应导入路径", result.getPathsAdded() > 0);

        // 验证批处理任务已导入
        BatchTaskManager mgrB = BatchTaskManager.getInstance();
        List<BatchTask> tasksB = mgrB.listBatchTasks();
        assertEquals(1, tasksB.size());
        assertEquals("实例A任务", tasksB.get(0).getName());
    }

    // ========== IT-05: 原有功能不受影响 ==========

    @Test
    public void testExistingConfigCrudStillWorks() {
        // 服务器 CRUD
        ServerConfig server = new ServerConfig();
        server.setName("CRUD Test");
        server.setHost("10.0.0.99");
        server.setPort(22);
        server.setUsername("admin");
        store.getConfig().getServers().add(server);
        store.save();

        assertEquals(1, store.getConfig().getServers().size());
        assertEquals("CRUD Test", store.getConfig().getServers().get(0).getName());

        // 路径 CRUD
        PathConfig path = new PathConfig();
        path.setServerId(server.getId());
        path.setRemotePath("/data/test");
        store.getConfig().getPaths().add(path);
        store.save();

        assertEquals(1, store.getConfig().getPaths().size());
        assertEquals("/data/test", store.getConfig().getPaths().get(0).getRemotePath());

        // 密码存储
        SecureStorage.getInstance().store(server.getId(), "testpass");
        assertEquals("testpass", SecureStorage.getInstance().retrieve(server.getId()));

        // 关键字规则
        KeywordRules rules = store.getConfig().getKeywordRules();
        assertNotNull(rules.getFailKeywords());
        assertFalse(rules.getFailKeywords().isEmpty());
    }

    @Test
    public void testBatchTasksFieldNonNullInConfig() {
        UnifiedPluginConfig config = store.getConfig();
        assertNotNull("BatchTasks 不应为 null", config.getBatchTasks());
        assertTrue("BatchTasks 初始为空", config.getBatchTasks().isEmpty());
    }

    // ========== 辅助方法 ==========

    private void setupServerAndPath() {
        ServerConfig server = new ServerConfig();
        server.setName("Integration Server");
        server.setHost("192.168.1.100");
        server.setPort(22);
        server.setUsername("root");
        server.setOsType("linux");
        store.getConfig().getServers().add(server);

        PathConfig path = new PathConfig();
        path.setServerId(server.getId());
        path.setRemotePath("/opt/deploy");
        store.getConfig().getPaths().add(path);

        store.save();
    }

    private BatchTask createSimpleTask(String name) {
        BatchTask task = new BatchTask();
        task.setName(name);
        BatchSubTask sub = new BatchSubTask();
        ServerConfig srv = store.getConfig().getServers().get(0);
        PathConfig pth = store.getConfig().getPaths().get(0);
        sub.setServerId(srv.getId());
        sub.setPathId(pth.getId());
        sub.setFilePaths(List.of("test.txt"));
        sub.setOrder(1);
        task.setSubTasks(new ArrayList<>(List.of(sub)));
        return task;
    }
}
