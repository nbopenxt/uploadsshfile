package com.openxt.uploadsshfile.batch;

import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 批处理进度模拟测试。
 * 使用模拟器生成进度事件，验证进度计算的正确性。
 */
public class BatchProgressSimulationTest {
    
    private static final String LOG_DIR = "build/test-logs";
    private static final String SIMULATION_LOG = LOG_DIR + "/batch_progress_simulation.log";
    private static final String VALIDATION_REPORT = LOG_DIR + "/batch_progress_validation.log";
    
    private BatchProgressSimulator simulator;
    private BatchProgressValidator validator;
    
    @Before
    public void setUp() throws IOException {
        // 创建日志目录
        File logDir = new File(LOG_DIR);
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        
        // 初始化模拟器
        simulator = new BatchProgressSimulator(SIMULATION_LOG);
        
        // 注意：不在这里初始化验证器，因为此时总字节数还是0
        // 验证器将在测试方法中，运行模拟后再初始化
    }
    
    /**
     * 测试1：验证测试任务创建
     */
    @Test
    public void testTaskCreation() throws IOException {
        // Given
        BatchProgressSimulator testSimulator = new BatchProgressSimulator(SIMULATION_LOG);
        
        // When
        BatchTask task = testSimulator.getTestTask();
        
        // Then
        assertNotNull(task);
        assertEquals("模拟批处理测试", task.getName());
        assertEquals(4, task.getSubTasks().size());
        
        // 验证子任务配置
        assertEquals(1, task.getSubTasks().get(0).getFilePaths().size());
        assertEquals(1, task.getSubTasks().get(1).getFilePaths().size());
        assertEquals(1, task.getSubTasks().get(2).getFilePaths().size());
        assertEquals(2, task.getSubTasks().get(3).getFilePaths().size());
        
        System.out.println("✓ 测试任务创建验证通过");
    }
    
    /**
     * 测试2：验证总字节数计算
     */
    @Test
    public void testTotalBytesCalculation() {
        // Given
        long expectedMinBytes = 3000L * 1024 * 1024; // 至少 3GB
        
        // When - 先运行模拟以计算总字节数
        simulator.runSimulation();
        long actualBytes = simulator.getBatchTotalBytes();
        
        // Then
        assertTrue("总字节数应大于 " + formatSize(expectedMinBytes) + "，实际为: " + formatSize(actualBytes),
                   actualBytes > expectedMinBytes);
        
        System.out.println("✓ 总字节数计算验证通过: " + formatSize(actualBytes));
    }
    
    /**
     * 测试3：运行完整模拟并验证进度
     */
    @Test
    public void testFullSimulation() {
        // When
        simulator.runSimulation();
        
        // Then
        List<BatchProgressSimulator.ProgressEvent> events = simulator.getEvents();
        assertFalse(events.isEmpty());
        
        System.out.println("\n=== 模拟事件统计 ===");
        System.out.println("总事件数：" + events.size());
        
        long batchStartCount = events.stream().filter(e -> e.type == BatchProgressSimulator.EventType.BATCH_START).count();
        long subTaskStartCount = events.stream().filter(e -> e.type == BatchProgressSimulator.EventType.SUBTASK_START).count();
        long fileStartCount = events.stream().filter(e -> e.type == BatchProgressSimulator.EventType.FILE_START).count();
        long progressCount = events.stream().filter(e -> e.type == BatchProgressSimulator.EventType.UPLOAD_PROGRESS).count();
        long fileCompleteCount = events.stream().filter(e -> e.type == BatchProgressSimulator.EventType.FILE_COMPLETED).count();
        long subTaskCompleteCount = events.stream().filter(e -> e.type == BatchProgressSimulator.EventType.SUBTASK_COMPLETED).count();
        long batchCompleteCount = events.stream().filter(e -> e.type == BatchProgressSimulator.EventType.BATCH_COMPLETED).count();
        
        System.out.println("BATCH_START: " + batchStartCount);
        System.out.println("SUBTASK_START: " + subTaskStartCount);
        System.out.println("FILE_START: " + fileStartCount);
        System.out.println("UPLOAD_PROGRESS: " + progressCount);
        System.out.println("FILE_COMPLETED: " + fileCompleteCount);
        System.out.println("SUBTASK_COMPLETED: " + subTaskCompleteCount);
        System.out.println("BATCH_COMPLETED: " + batchCompleteCount);
        
        assertEquals(1, batchStartCount);
        assertEquals(4, subTaskStartCount);
        assertEquals(4, subTaskCompleteCount);
        assertEquals(1, batchCompleteCount);
        
        System.out.println("\n✓ 完整模拟测试通过");
    }
    
    /**
     * 测试4：验证进度计算的连续性
     */
    @Test
    public void testProgressContinuity() throws IOException {
        // 先运行模拟
        simulator.runSimulation();
        
        // 运行模拟后初始化验证器（获取正确的总字节数）
        validator = new BatchProgressValidator(VALIDATION_REPORT, simulator.getBatchTotalBytes());
        
        List<BatchProgressSimulator.ProgressEvent> events = simulator.getEvents();
        
        validator.validateBatchStart(simulator.getBatchTotalBytes());
        
        for (BatchProgressSimulator.ProgressEvent event : events) {
            switch (event.type) {
                case SUBTASK_START:
                    validator.validateSubTaskStart(event.subTaskIndex, event.totalBytes);
                    break;
                    
                case UPLOAD_PROGRESS:
                    validator.validateUploadProgress(event.fileName, event.percent, 
                            event.uploadedBytes, event.totalBytes);
                    break;
                    
                case FILE_COMPLETED:
                    validator.validateFileCompleted(event.fileName, event.fileSize, event.success);
                    break;
                    
                case SUBTASK_COMPLETED:
                    validator.validateSubTaskCompleted(event.subTaskIndex, event.uploadedBytes);
                    break;
                    
                case BATCH_COMPLETED:
                    validator.validateBatchCompleted(event.uploadedBytes);
                    break;
            }
        }
        
        validator.generateReport();
        
        System.out.println("\n✓ 进度连续性验证完成");
    }
    
    /**
     * 测试5：验证子任务切换时的进度处理
     */
    @Test
    public void testSubTaskTransition() {
        simulator.runSimulation();
        
        List<BatchProgressSimulator.ProgressEvent> events = simulator.getEvents();
        
        boolean foundSubTask2Start = false;
        BatchProgressSimulator.ProgressEvent lastFileCompleteBeforeSubTask2 = null;
        BatchProgressSimulator.ProgressEvent subTask2StartEvent = null;
        
        for (int i = 0; i < events.size(); i++) {
            BatchProgressSimulator.ProgressEvent event = events.get(i);
            
            if (event.type == BatchProgressSimulator.EventType.SUBTASK_START && event.subTaskIndex == 2) {
                foundSubTask2Start = true;
                subTask2StartEvent = event;
                
                // 查找子任务2开始之前的最后一个 FILE_COMPLETED 事件
                for (int j = i - 1; j >= 0; j--) {
                    if (events.get(j).type == BatchProgressSimulator.EventType.FILE_COMPLETED) {
                        lastFileCompleteBeforeSubTask2 = events.get(j);
                        break;
                    }
                }
                break;
            }
        }
        
        assertTrue("应该找到子任务2的开始事件", foundSubTask2Start);
        assertNotNull("子任务2开始前应该有文件完成事件", lastFileCompleteBeforeSubTask2);
        assertNotNull("应该找到子任务2的开始事件对象", subTask2StartEvent);
        
        System.out.println("\n=== 子任务切换验证 ===");
        System.out.println("子任务1最后完成文件：" + lastFileCompleteBeforeSubTask2.fileName);
        System.out.println("子任务1最后完成文件大小：" + formatSize(lastFileCompleteBeforeSubTask2.fileSize));
        System.out.println("子任务2开始，总大小：" + formatSize(subTask2StartEvent.totalBytes));
        
        System.out.println("\n✓ 子任务切换验证通过");
    }
    
    /**
     * 测试6：验证大文件上传进度
     */
    @Test
    public void testLargeFileUpload() {
        simulator.runSimulation();
        
        List<BatchProgressSimulator.ProgressEvent> events = simulator.getEvents();
        
        long largeFileProgressCount = events.stream()
                .filter(e -> e.type == BatchProgressSimulator.EventType.UPLOAD_PROGRESS)
                .filter(e -> e.totalBytes >= 300L * 1024 * 1024)
                .count();
        
        assertTrue(largeFileProgressCount > 0);
        
        System.out.println("\n✓ 大文件上传进度验证通过");
    }
    
    /**
     * 测试7：验证小文件批量上传
     */
    @Test
    public void testSmallFilesBatchUpload() {
        simulator.runSimulation();
        
        List<BatchProgressSimulator.ProgressEvent> events = simulator.getEvents();
        
        long smallFileCount = events.stream()
                .filter(e -> e.type == BatchProgressSimulator.EventType.FILE_COMPLETED)
                .filter(e -> e.fileSize <= 10L * 1024 * 1024)
                .count();
        
        assertTrue(smallFileCount >= 600);
        
        System.out.println("\n=== 小文件批量上传验证 ===");
        System.out.println("小文件总数：" + smallFileCount);
        
        System.out.println("\n✓ 小文件批量上传验证通过");
    }
    
    /**
     * 测试8：验证所有文件都成功完成
     */
    @Test
    public void testAllFilesCompletedSuccessfully() {
        simulator.runSimulation();
        
        List<BatchProgressSimulator.ProgressEvent> events = simulator.getEvents();
        
        long successCount = events.stream()
                .filter(e -> e.type == BatchProgressSimulator.EventType.FILE_COMPLETED)
                .filter(e -> e.success)
                .count();
        
        long failCount = events.stream()
                .filter(e -> e.type == BatchProgressSimulator.EventType.FILE_COMPLETED)
                .filter(e -> !e.success)
                .count();
        
        assertEquals(0, failCount);
        assertTrue(successCount > 0);
        
        System.out.println("\n=== 文件完成情况验证 ===");
        System.out.println("成功：" + successCount);
        System.out.println("失败：" + failCount);
        
        System.out.println("\n✓ 文件完成情况验证通过");
    }
    
    /**
     * 测试9：生成完整的模拟日志文件
     */
    @Test
    public void testLogGeneration() throws IOException {
        simulator.runSimulation();
        
        // 初始化验证器并生成报告
        validator = new BatchProgressValidator(VALIDATION_REPORT, simulator.getBatchTotalBytes());
        validator.generateReport();
        
        File simulationLogFile = new File(SIMULATION_LOG);
        assertTrue("模拟日志文件应该存在", simulationLogFile.exists());
        assertTrue("模拟日志文件不应为空", simulationLogFile.length() > 0);
        
        File validationReportFile = new File(VALIDATION_REPORT);
        assertTrue("验证报告文件应该存在", validationReportFile.exists());
        assertTrue("验证报告文件不应为空", validationReportFile.length() > 0);
        
        System.out.println("\n=== 日志文件验证 ===");
        System.out.println("模拟日志：" + SIMULATION_LOG + " (" + formatSize(simulationLogFile.length()) + ")");
        System.out.println("验证报告：" + VALIDATION_REPORT + " (" + formatSize(validationReportFile.length()) + ")");
        
        System.out.println("\n✓ 日志文件生成验证通过");
    }
    
    /**
     * 格式化文件大小
     */
    private static String formatSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
