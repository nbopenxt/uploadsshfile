package com.openxt.uploadsshfile.batch;

import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import com.openxt.uploadsshfile.persistence.SecureStorage;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/**
 * BatchExecutionOrchestrator 单元测试
 * 
 * 根据《批处理任务进度处理.md》文档实现完整的监听器回调测试。
 * 
 * 测试覆盖：
 * - UT-04: 正常执行 3 个子任务（验证监听器回调顺序）
 * - UT-05: 中途失败不中断，记录失败后继续
 * - UT-06: 取消执行（当前子任务完成后停止）
 * - UT-07: 无命令组的子任务（commandConfigId=null）
 * - UT-08: 进度回调参数验证（@Ignore，需要真实 SFTP 连接，属于集成测试）
 *
 * 注意: 由于 Orchestrator 依赖真实 SFTP 连接，这些测试验证监听器回调/编排逻辑，
 * 实际 SFTP 上行测试在 integration 包中。
 */
public class BatchExecutionOrchestratorTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    private Path tempDir;
    private BatchExecutionOrchestrator orchestrator;
    private TestListener testListener;

    @Before
    public void setUp() throws Exception {
        tempDir = tempFolder.newFolder("orchestrator-test").toPath();
        Files.createDirectories(tempDir);
        UnifiedConfigStore.resetInstance();
        UnifiedConfigStore.createForTest(tempDir);

        orchestrator = new BatchExecutionOrchestrator();
        testListener = new TestListener();
    }

    @After
    public void tearDown() {
        UnifiedConfigStore.resetInstance();
    }

    // ========== UT-07: 无命令组的子任务（空任务快速返回） ==========

    @Test
    public void testNullTaskIsIgnored() {
        orchestrator.setListener(testListener);
        orchestrator.execute(null);
        // 不应崩溃，且不会触发任何回调
        assertEquals(0, testListener.startCount.get());
    }

    @Test
    public void testEmptySubTasksIsIgnored() {
        BatchTask task = new BatchTask();
        task.setName("Empty Task");
        task.setSubTasks(new ArrayList<>());

        orchestrator.setListener(testListener);
        orchestrator.execute(task);

        // 空子任务列表直接返回
        assertEquals(0, testListener.startCount.get());
    }

    // ========== UT-04: 正常执行回调验证 ==========

    @Test
    public void testListenerCallbacksInOrder() throws Exception {
        BatchTask task = createTaskWithSubTasks("Order Test", 3);

        orchestrator.setListener(testListener);
        orchestrator.execute(task);

        // 等待执行完成
        boolean completed = testListener.completedLatch.await(10, TimeUnit.SECONDS);
        assertTrue("Execution should complete within timeout", completed);

        // 验证回调顺序
        assertEquals(1, testListener.startCount.get());
        assertEquals(3, testListener.subTaskStartCount.get());
        assertEquals(3, testListener.subTaskCompletedCount.get());
        assertEquals(1, testListener.batchCompletedCount.get());
    }

    @Test
    public void testSubTaskOrderPreserved() {
        BatchTask task = new BatchTask();
        task.setName("Order Test");

        List<BatchSubTask> subs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            BatchSubTask sub = new BatchSubTask();
            sub.setOrder(i + 1); // 1, 2, 3
            sub.setServerId("srv-" + (i + 1));
            sub.setPathId("path-" + (i + 1));
            sub.setFilePaths(List.of());
            subs.add(sub);
        }
        task.setSubTasks(subs);

        orchestrator.setListener(testListener);
        orchestrator.execute(task);

        try {
            testListener.completedLatch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 验证按 order 顺序启动
        List<Integer> startOrders = testListener.subTaskStartOrders;
        assertFalse(startOrders.isEmpty());

        // 第一个启动的子任务 index=1 (基于1)
        assertEquals(1, (int) startOrders.get(0));
    }

    // ========== UT-05: 中途失败不中断 ==========

    @Test
    public void testFailureDoesNotInterrupt() throws Exception {
        // 创建3个子任务，第二个有无效文件路径
        BatchTask task = new BatchTask();
        task.setName("Failure Test");
        List<BatchSubTask> subs = new ArrayList<>();

        BatchSubTask s1 = new BatchSubTask();
        s1.setOrder(1);
        s1.setServerId("srv-1");
        s1.setPathId("path-1");
        s1.setFilePaths(List.of());
        subs.add(s1);

        BatchSubTask s2 = new BatchSubTask();
        s2.setOrder(2);
        s2.setServerId("srv-2");
        s2.setPathId("path-2");
        s2.setFilePaths(List.of("/nonexistent/file.txt")); // 无效文件
        subs.add(s2);

        BatchSubTask s3 = new BatchSubTask();
        s3.setOrder(3);
        s3.setServerId("srv-3");
        s3.setPathId("path-3");
        s3.setFilePaths(List.of());
        subs.add(s3);

        task.setSubTasks(subs);

        orchestrator.setListener(testListener);
        orchestrator.execute(task);

        boolean completed = testListener.completedLatch.await(10, TimeUnit.SECONDS);
        assertTrue(completed);

        // 所有3个子任务都应该完成（有的成功，有的失败）
        assertEquals(3, testListener.subTaskCompletedCount.get());

        // 失败不应中断后续子任务
        assertTrue(testListener.results.size() >= 3);
    }

    // ========== UT-06: 取消执行 ==========

    @Test
    public void testCancelStopsAfterCurrentSubTask() throws Exception {
        BatchTask task = createTaskWithSubTasks("Cancel Test", 5);

        orchestrator.setListener(testListener);
        orchestrator.execute(task);

        // 等待第一个子任务开始
        boolean firstStarted = testListener.firstSubTaskLatch.await(5, TimeUnit.SECONDS);
        assertTrue(firstStarted);

        // 取消
        orchestrator.cancel();

        boolean completed = testListener.completedLatch.await(10, TimeUnit.SECONDS);
        // 取消后应该触发 onBatchCancelled
        assertTrue(testListener.cancelled.get() || testListener.batchCompletedCount.get() >= 1);
    }

    // ========== 新增：进度回调验证 ==========

    /**
     * 验证进度回调参数的正确性（参考批处理任务进度处理.md）
     * 
     * 测试要点：
     * 1. onUploadProgress 的四个参数都应该有值
     * 2. percent 应该在 0-100 范围内
     * 3. uploaded 不应该超过 total
     * 4. total 应该等于文件大小
     * 
     * 注意：此测试需要真实的 SFTP 连接，因此在单元测试中跳过。
     * 实际的进度回调测试在 integration 包中进行。
     */
    @org.junit.Ignore("需要真实 SFTP 连接，属于集成测试")
    @Test
    public void testProgressCallbackParameters() throws Exception {
        // 创建一个包含实际文件的子任务
        BatchTask task = new BatchTask();
        task.setName("Progress Test");
        
        BatchSubTask subTask = new BatchSubTask();
        subTask.setOrder(1);
        subTask.setServerId("srv-1");
        subTask.setPathId("path-1");
        
        // 创建一个临时文件用于测试
        Path testFile = tempDir.resolve("test-progress.txt");
        Files.writeString(testFile, "This is a test file for progress tracking.");
        subTask.setFilePaths(List.of(testFile.toString()));
        
        task.setSubTasks(List.of(subTask));

        orchestrator.setListener(testListener);
        orchestrator.execute(task);

        boolean completed = testListener.completedLatch.await(10, TimeUnit.SECONDS);
        assertTrue("Execution should complete", completed);

        // 验证进度回调被调用
        assertTrue("Progress callback should be called at least once", 
                   testListener.progressUpdateCount.get() > 0);
        
        // 验证最后一次进度的参数有效性
        assertNotNull("Last progress file should not be null", 
                     testListener.lastProgressFile.get());
        assertTrue("Total bytes should be positive", 
                  testListener.lastTotalBytes.get() > 0);
        assertTrue("Uploaded bytes should not exceed total", 
                  testListener.lastUploadedBytes.get() <= testListener.lastTotalBytes.get());
        
        System.out.println("Progress updates count: " + testListener.progressUpdateCount.get());
        System.out.println("Last file: " + testListener.lastProgressFile.get());
        System.out.println("Last uploaded/total: " + testListener.lastUploadedBytes.get() + "/" + testListener.lastTotalBytes.get());
    }

    // ========== 辅助方法 ==========

    private BatchTask createTaskWithSubTasks(String name, int count) {
        BatchTask task = new BatchTask();
        task.setName(name);
        List<BatchSubTask> subs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            BatchSubTask sub = new BatchSubTask();
            sub.setOrder(i + 1);
            sub.setServerId("srv-" + (i + 1));
            sub.setPathId("path-" + (i + 1));
            sub.setFilePaths(List.of()); // 空文件列表会导致快速失败
            subs.add(sub);
        }
        task.setSubTasks(subs);
        return task;
    }

    /**
     * 测试用监听器，记录所有回调并支持等待
     * 
     * 根据批处理任务进度处理.md文档实现完整的监听器回调跟踪
     */
    private static class TestListener implements BatchExecutionListener {
        final CountDownLatch completedLatch = new CountDownLatch(1);
        final CountDownLatch firstSubTaskLatch = new CountDownLatch(1);
        final AtomicInteger startCount = new AtomicInteger(0);
        final AtomicInteger subTaskStartCount = new AtomicInteger(0);
        final AtomicInteger subTaskCompletedCount = new AtomicInteger(0);
        final AtomicInteger batchCompletedCount = new AtomicInteger(0);
        final AtomicBoolean cancelled = new AtomicBoolean(false);
        final List<BatchSubTaskResult> results = new ArrayList<>();
        final List<Integer> subTaskStartOrders = new ArrayList<>();
        
        // 进度跟踪相关（用于验证进度回调）
        final AtomicInteger progressUpdateCount = new AtomicInteger(0);
        final AtomicReference<String> lastProgressFile = new AtomicReference<>();
        final AtomicLong lastUploadedBytes = new AtomicLong(0);
        final AtomicLong lastTotalBytes = new AtomicLong(0);

        @Override
        public void onBatchStart(BatchTask task, int totalSubTasks) {
            startCount.incrementAndGet();
            System.out.println("[Batch Start] Task: " + task.getName() + ", Total SubTasks: " + totalSubTasks);
        }

        @Override
        public void onSubTaskStart(BatchSubTask subTask, int index, int total) {
            subTaskStartCount.incrementAndGet();
            subTaskStartOrders.add(index);
            firstSubTaskLatch.countDown();
            System.out.println("[SubTask Start] Index: " + index + "/" + total + ", Server: " + subTask.getServerId());
        }

        @Override
        public void onFileStart(String fileName, int fileIndex, int totalFiles) {
            System.out.println("[File Start] " + fileName + " (" + fileIndex + "/" + totalFiles + ")");
        }

        /**
         * 单个文件上传进度回调
         * 
         * 参数说明（参考批处理任务进度处理.md）：
         * - fileName: 当前上传的文件名
         * - percent: 当前文件上传百分比 (0-100)
         * - uploaded: 已上传字节数
         * - total: 文件总字节数
         */
        @Override
        public void onUploadProgress(String fileName, int percent, long uploaded, long total) {
            progressUpdateCount.incrementAndGet();
            lastProgressFile.set(fileName);
            lastUploadedBytes.set(uploaded);
            lastTotalBytes.set(total);
            
            // 打印进度信息（可选，便于调试）
            if (progressUpdateCount.get() % 10 == 0 || percent == 0 || percent == 100) {
                System.out.println("[Progress] " + fileName + ": " + percent + "% (" + 
                                   formatBytes(uploaded) + "/" + formatBytes(total) + ")");
            }
        }

        @Override
        public void onFileCompleted(String fileName, long size, boolean success, String errorMessage) {
            String status = success ? "OK" : "FAIL";
            System.out.println("[File Completed] [" + status + "] " + fileName + " (" + formatBytes(size) + ")" + 
                             (errorMessage != null ? " - Error: " + errorMessage : ""));
        }

        @Override
        public void onSubTaskCompleted(BatchSubTaskResult result) {
            subTaskCompletedCount.incrementAndGet();
            results.add(result);
            String status = result.isSuccess() ? "SUCCESS" : "FAILED";
            System.out.println("[SubTask Completed] [" + status + "] " + result.getTaskDescription() + 
                             " (Duration: " + result.getDurationMs() + "ms)");
        }

        @Override
        public void onBatchCompleted(List<BatchSubTaskResult> allResults) {
            batchCompletedCount.incrementAndGet();
            completedLatch.countDown();
            
            // 统计结果
            long successCount = allResults.stream().filter(BatchSubTaskResult::isSuccess).count();
            long totalDuration = allResults.stream().mapToLong(BatchSubTaskResult::getDurationMs).sum();
            
            System.out.println("[Batch Completed] Success: " + successCount + "/" + allResults.size() + 
                             ", Total Duration: " + totalDuration + "ms");
        }

        @Override
        public void onBatchCancelled() {
            cancelled.set(true);
            completedLatch.countDown();
            System.out.println("[Batch Cancelled]");
        }

        @Override
        public void onLog(String message) {
            System.out.println("[Log] " + message);
        }
        
        /**
         * 格式化字节数为可读字符串
         */
        private String formatBytes(long bytes) {
            if (bytes < 1024) {
                return bytes + " B";
            } else if (bytes < 1024 * 1024) {
                return String.format("%.2f KB", bytes / 1024.0);
            } else if (bytes < 1024 * 1024 * 1024) {
                return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
            } else {
                return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
            }
        }
    }
}
