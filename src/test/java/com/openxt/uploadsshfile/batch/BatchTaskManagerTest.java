package com.openxt.uploadsshfile.batch;

import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * BatchTaskManager 单元测试
 * UT-01: 新增/查询/更新/删除批处理任务
 * UT-02: 复制任务：验证新 UUID、子任务完整复制
 * UT-03: 从失败创建任务：仅含失败子任务
 */
public class BatchTaskManagerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path tempDir;
    private BatchTaskManager manager;

    @Before
    public void setUp() throws Exception {
        tempDir = tempFolder.newFolder("batch-test").toPath();
        Files.createDirectories(tempDir);
        UnifiedConfigStore.resetInstance();
        UnifiedConfigStore.createForTest(tempDir);
        BatchTaskManager.resetInstance();
        manager = BatchTaskManager.getInstance();
    }

    @After
    public void tearDown() {
        BatchTaskManager.resetInstance();
        UnifiedConfigStore.resetInstance();
    }

    // ========== UT-01: 新增/查询/更新/删除 ==========

    @Test
    public void testSaveAndGetBatchTask() {
        BatchTask task = createTestTask("测试任务1", 2);
        manager.saveBatchTask(task);

        // 验证 ID 已生成
        assertNotNull(task.getId());
        assertFalse(task.getId().isEmpty());

        // 查询
        BatchTask loaded = manager.getBatchTask(task.getId());
        assertNotNull(loaded);
        assertEquals("测试任务1", loaded.getName());
        assertEquals(2, loaded.getSubTasks().size());
    }

    @Test
    public void testListBatchTasks() {
        BatchTask task1 = createTestTask("任务A", 1);
        BatchTask task2 = createTestTask("任务B", 2);
        manager.saveBatchTask(task1);
        manager.saveBatchTask(task2);

        List<BatchTask> all = manager.listBatchTasks();
        assertEquals(2, all.size());
    }

    @Test
    public void testUpdateBatchTask() {
        BatchTask task = createTestTask("原始名称", 1);
        manager.saveBatchTask(task);

        // 更新名称
        task.setName("更新后名称");
        manager.saveBatchTask(task);

        BatchTask loaded = manager.getBatchTask(task.getId());
        assertEquals("更新后名称", loaded.getName());
    }

    @Test
    public void testDeleteBatchTask() {
        BatchTask task = createTestTask("待删除", 1);
        manager.saveBatchTask(task);
        assertEquals(1, manager.listBatchTasks().size());

        manager.deleteBatchTask(task.getId());
        assertEquals(0, manager.listBatchTasks().size());
        assertNull(manager.getBatchTask(task.getId()));
    }

    @Test
    public void testSaveWithoutIdAutoGenerates() {
        BatchTask task = createTestTask("无ID任务", 1);
        task.setId(null); // 显式置空
        manager.saveBatchTask(task);

        assertNotNull(task.getId());
        BatchTask loaded = manager.getBatchTask(task.getId());
        assertNotNull(loaded);
    }

    // ========== UT-02: 复制任务 ==========

    @Test
    public void testCopyBatchTask() {
        BatchTask original = createTestTask("原始任务", 3);
        manager.saveBatchTask(original);

        BatchTask copy = manager.copyBatchTask(original.getId(), "复制任务");
        assertNotNull(copy);

        // 验证新 UUID
        assertNotEquals(original.getId(), copy.getId());

        // 验证子任务完整复制（新 UUID）
        assertEquals(original.getSubTasks().size(), copy.getSubTasks().size());
        for (int i = 0; i < original.getSubTasks().size(); i++) {
            assertNotEquals(
                original.getSubTasks().get(i).getId(),
                copy.getSubTasks().get(i).getId()
            );
            assertEquals(
                original.getSubTasks().get(i).getServerId(),
                copy.getSubTasks().get(i).getServerId()
            );
            assertEquals(
                original.getSubTasks().get(i).getFilePaths(),
                copy.getSubTasks().get(i).getFilePaths()
            );
        }

        // 验证 name 正确
        assertEquals("复制任务", copy.getName());

        // 验证原始任务不受影响
        BatchTask originalLoaded = manager.getBatchTask(original.getId());
        assertEquals("原始任务", originalLoaded.getName());
        assertEquals(3, originalLoaded.getSubTasks().size());
    }

    @Test
    public void testCopyBatchTaskPreservesLists() {
        BatchTask original = createTestTask("复杂任务", 2);
        // 第一个子任务：多文件
        original.getSubTasks().get(0).setFilePaths(List.of("a.txt", "b.txt", "c.txt"));
        original.getSubTasks().get(0).setOrder(10);
        // 第二个子任务：有命令组
        original.getSubTasks().get(1).setCommandConfigId("cmd-123");
        original.getSubTasks().get(1).setOrder(20);
        manager.saveBatchTask(original);

        BatchTask copy = manager.copyBatchTask(original.getId(), "副本");

        // 验证 order 保留
        assertEquals(10, copy.getSubTasks().get(0).getOrder());
        assertEquals(20, copy.getSubTasks().get(1).getOrder());

        // 验证文件列表完整复制
        assertEquals(3, copy.getSubTasks().get(0).getFilePaths().size());
        assertTrue(copy.getSubTasks().get(0).getFilePaths().contains("a.txt"));

        // 验证命令组 ID 保留
        assertEquals("cmd-123", copy.getSubTasks().get(1).getCommandConfigId());
    }

    // ========== UT-03: 从失败创建 ==========

    @Test
    public void testCreateFromFailures() {
        // 先保存一个批处理任务（含子任务），供 findOriginalSubTask 查询
        BatchTask existingTask = createTestTask("已存在任务", 2);
        existingTask.getSubTasks().get(0).setId("sub-1");
        existingTask.getSubTasks().get(1).setId("sub-2");
        manager.saveBatchTask(existingTask);

        // 创建模拟的失败结果列表
        List<BatchSubTaskResult> failures = new ArrayList<>();

        BatchSubTaskResult r1 = new BatchSubTaskResult();
        r1.setSubTaskId("sub-1");
        r1.setStatus(BatchSubTaskResult.Status.FAILED);
        r1.setTaskDescription("上传到 server1:/opt/app");
        r1.setErrorMessage("Connection refused");
        r1.setDurationMs(1500);
        failures.add(r1);

        BatchSubTaskResult r2 = new BatchSubTaskResult();
        r2.setSubTaskId("sub-2");
        r2.setStatus(BatchSubTaskResult.Status.FAILED);
        r2.setTaskDescription("上传到 server2:/var/www");
        r2.setErrorMessage("File not found: /tmp/missing.txt");
        r2.setDurationMs(500);
        failures.add(r2);

        BatchTask newTask = manager.createFromFailures(failures, "失败重试任务");
        assertNotNull(newTask);

        // 验证包含失败子任务信息
        assertEquals(2, newTask.getSubTasks().size());
    }

    @Test
    public void testCreateFromFailuresEmptyList() {
        // createFromFailures 对空列表返回 null
        BatchTask task = manager.createFromFailures(new ArrayList<>(), "空失败任务");
        assertNull(task);
    }

    // ========== 辅助方法 ==========

    private BatchTask createTestTask(String name, int subTaskCount) {
        BatchTask task = new BatchTask();
        task.setName(name);
        task.setCreateTime(System.currentTimeMillis());
        task.setUpdateTime(System.currentTimeMillis());

        List<BatchSubTask> subs = new ArrayList<>();
        for (int i = 0; i < subTaskCount; i++) {
            BatchSubTask sub = new BatchSubTask();
            sub.setServerId("server-" + (i + 1));
            sub.setPathId("path-" + (i + 1));
            sub.setFilePaths(new ArrayList<>(List.of("file" + (i + 1) + ".txt")));
            sub.setOrder(i + 1);
            subs.add(sub);
        }
        task.setSubTasks(subs);
        return task;
    }
}
