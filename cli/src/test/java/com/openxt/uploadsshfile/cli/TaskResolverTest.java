package com.openxt.uploadsshfile.cli;

import com.openxt.uploadsshfile.model.SingleUploadTask;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * D-38 任务定位回归：单任务文件清单/上下文＝**关窗快照优先**、回落 lastSuccessful*，
 * 快照无文件＝PARAM 点名（提示先在 GUI 开窗关窗保存）。批任务分支由
 * CliRunnerBatchTest（root 模块）间接覆盖，此处只钉 unknown id 兜底。
 * 测试 JVM 约定注入值 tmp/uploadsshfile-test-inject（D-29④ 全测试类自给化）。
 */
public class TaskResolverTest {

    private static UnifiedConfigStore store;

    @BeforeClass
    public static void injectPaths() throws Exception {
        java.nio.file.Path root = Paths.get(System.getProperty("java.io.tmpdir"), "uploadsshfile-test-inject");
        java.nio.file.Files.createDirectories(root);
        com.openxt.uploadsshfile.util.PluginPathManager.initialize(root, root);
        store = UnifiedConfigStore.getInstance();
    }

    @After
    public void restoreSlots() {
        store.setSingleUploadTaskId(null);
        store.saveSingleUploadTask(List.of(), null, null, null, null); // 清引用（快照保留空清单）
        store.saveLastSuccessfulSelection(null, null, null, null);
    }

    @Test
    public void singleTaskResolvesFilesFromSnapshot() {
        String id = "7000000000000000101";
        store.setSingleUploadTaskId(id);
        store.saveSingleUploadTask(List.of("E:\\a.txt", "E:\\dir"), "srv-1", "path-1", null, "AUTO");
        TaskResolver.Resolution r = TaskResolver.resolve(id);
        assertNull(r.error);
        assertTrue(!r.isBatch);
        assertEquals("srv-1", r.single.serverId);
        assertEquals("path-1", r.single.pathId);
        assertEquals("AUTO", r.single.timing);
        assertEquals(2, r.single.filePaths.size());
        assertEquals("E:\\a.txt", r.single.filePaths.get(0));
    }

    @Test
    public void snapshotMissingContextFallsBackToLastSuccessful() {
        String id = "7000000000000000102";
        store.setSingleUploadTaskId(id);
        store.saveSingleUploadTask(List.of("E:\\a.txt"), null, null, null, null); // 快照引用被删除联动清空
        store.saveLastSuccessfulSelection("srv-old", "path-old", null, "MANUAL");
        TaskResolver.Resolution r = TaskResolver.resolve(id);
        assertNull(r.error);
        assertEquals("srv-old", r.single.serverId);
        assertEquals("path-old", r.single.pathId);
        assertEquals("MANUAL", r.single.timing);
    }

    @Test
    public void snapshotWithoutFilesIsParamError() {
        String id = "7000000000000000103";
        store.setSingleUploadTaskId(id);
        store.saveSingleUploadTask(List.of(), "srv-1", "path-1", null, "MANUAL");
        TaskResolver.Resolution r = TaskResolver.resolve(id);
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("no saved files"));
    }

    @Test
    public void lastSuccessfulContextWithoutAnyFilesRejected() {
        // D-38 收紧：即便旧"成功记忆"齐全，快照不存在/无文件清单也不允许单任务跑（防传错东西）
        String id = "7000000000000000104";
        store.setSingleUploadTaskId(id);
        store.saveLastSuccessfulSelection("srv-old", "path-old", null, "AUTO");
        SingleUploadTask snap = store.getSingleUploadTask(); // 上一用例 @After 留空清单槽；null 槽同样应拒
        assertTrue(snap == null || snap.getFilePaths().isEmpty());
        TaskResolver.Resolution r = TaskResolver.resolve(id);
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("no saved files"));
    }

    @Test
    public void unknownIdRejected() {
        TaskResolver.Resolution r = TaskResolver.resolve("9999999999999999999");
        assertNotNull(r.error);
        assertTrue(r.error, r.error.contains("unknown task id"));
    }
}
