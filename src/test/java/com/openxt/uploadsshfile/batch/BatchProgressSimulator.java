package com.openxt.uploadsshfile.batch;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 批处理进度模拟器。
 * 生成模拟的上传进度事件序列，用于测试 BatchProgressDialog 的进度计算逻辑。
 */
public class BatchProgressSimulator {
    
    private final BatchTask testTask;
    private final List<ProgressEvent> events = new ArrayList<>();
    private final PrintWriter logWriter;
    private long currentTime = 0;
    private long batchTotalBytes = 0;
    private long batchUploadedBytes = 0;
    private int currentSubTaskIndex = 0;
    private long currentFileUploaded = 0;
    private long currentFileTotal = 0;
    private long batchStartTime = 0;
    private final Random random = new Random(42); // 固定种子保证可重复性
    
    /**
     * 进度事件类型
     */
    public enum EventType {
        BATCH_START,
        SUBTASK_START,
        FILE_START,
        UPLOAD_PROGRESS,
        FILE_COMPLETED,
        SUBTASK_COMPLETED,
        BATCH_COMPLETED
    }
    
    /**
     * 进度事件
     */
    public static class ProgressEvent {
        public EventType type;
        public long timestamp;
        public int subTaskIndex;
        public String fileName;
        public int percent;
        public long uploadedBytes;
        public long totalBytes;
        public long fileSize;
        public boolean success;
        public String errorMessage;
        
        @Override
        public String toString() {
            return String.format("[%d] %s (subTask=%d, file=%s, percent=%d%%, uploaded=%s, total=%s)",
                    timestamp, type, subTaskIndex, fileName, percent, 
                    formatSize(uploadedBytes), formatSize(totalBytes));
        }
    }
    
    public BatchProgressSimulator(String logFilePath) throws IOException {
        this.testTask = createTestTask();
        this.logWriter = new PrintWriter(new FileWriter(logFilePath));
        
        log("=== 批处理进度模拟测试 ===");
        log("任务：" + testTask.getName());
        log("子任务数：" + testTask.getSubTasks().size());
        log("");
    }
    
    /**
     * 创建测试任务
     */
    private BatchTask createTestTask() {
        BatchTask task = new BatchTask();
        task.setName("模拟批处理测试");
        
        // 子任务1：1个300MB文件
        BatchSubTask subTask1 = new BatchSubTask();
        subTask1.setOrder(1);
        subTask1.getFilePaths().add("mock://large_file_1.bin?size=300MB");
        
        // 子任务2：1个文件夹，含200个1-10MB文件（平均5.5MB）
        BatchSubTask subTask2 = new BatchSubTask();
        subTask2.setOrder(2);
        subTask2.getFilePaths().add("mock://folder_2/?count=200&avgSize=5.5MB");
        
        // 子任务3：1个300MB文件
        BatchSubTask subTask3 = new BatchSubTask();
        subTask3.setOrder(3);
        subTask3.getFilePaths().add("mock://large_file_3.bin?size=300MB");
        
        // 子任务4：2个文件夹，各含200个1-10MB文件
        BatchSubTask subTask4 = new BatchSubTask();
        subTask4.setOrder(4);
        subTask4.getFilePaths().add("mock://folder_4a/?count=200&avgSize=5.5MB");
        subTask4.getFilePaths().add("mock://folder_4b/?count=200&avgSize=5.5MB");
        
        task.getSubTasks().add(subTask1);
        task.getSubTasks().add(subTask2);
        task.getSubTasks().add(subTask3);
        task.getSubTasks().add(subTask4);
        
        return task;
    }
    
    /**
     * 运行模拟
     */
    public void runSimulation() {
        calculateTotalBytes();
        log("总字节数：" + formatSize(batchTotalBytes));
        log("");
        
        // 触发批处理开始
        currentTime = System.currentTimeMillis();
        batchStartTime = currentTime;
        
        ProgressEvent batchStartEvent = new ProgressEvent();
        batchStartEvent.type = EventType.BATCH_START;
        batchStartEvent.timestamp = currentTime;
        batchStartEvent.totalBytes = batchTotalBytes;
        events.add(batchStartEvent);
        
        log("--- 批处理开始 ---");
        log("总字节数：" + formatSize(batchTotalBytes));
        log("");
        
        // 执行每个子任务
        for (int i = 0; i < testTask.getSubTasks().size(); i++) {
            BatchSubTask subTask = testTask.getSubTasks().get(i);
            executeSubTask(subTask, i + 1);
        }
        
        // 触发批处理完成
        // 修正：由于随机文件大小会导致实际字节数与预设值有偏差，将总字节数更新为实际值
        batchTotalBytes = batchUploadedBytes;
        
        ProgressEvent batchCompleteEvent = new ProgressEvent();
        batchCompleteEvent.type = EventType.BATCH_COMPLETED;
        batchCompleteEvent.timestamp = currentTime;
        batchCompleteEvent.totalBytes = batchTotalBytes;
        batchCompleteEvent.uploadedBytes = batchUploadedBytes;
        events.add(batchCompleteEvent);
        
        log("");
        log("--- 批处理完成 ---");
        log("总上传字节：" + formatSize(batchUploadedBytes));
        log("总字节数：" + formatSize(batchTotalBytes));
        log("完成百分比：" + (batchTotalBytes > 0 ? (batchUploadedBytes * 100 / batchTotalBytes) : 0) + "%");
        log("");
        log("=== 模拟测试完成 ===");
        
        logWriter.flush();
        logWriter.close();
    }
    
    /**
     * 计算总字节数
     */
    private void calculateTotalBytes() {
        batchTotalBytes = 0;
        for (BatchSubTask subTask : testTask.getSubTasks()) {
            batchTotalBytes += calculateSubTaskBytes(subTask);
        }
    }
    
    /**
     * 计算子任务字节数
     */
    private long calculateSubTaskBytes(BatchSubTask subTask) {
        long totalBytes = 0;
        for (String filePath : subTask.getFilePaths()) {
            totalBytes += parseMockFileSize(filePath);
        }
        return totalBytes;
    }
    
    /**
     * 解析模拟文件大小
     */
    private long parseMockFileSize(String mockPath) {
        if (mockPath.contains("size=")) {
            // 单个文件大小
            String sizeStr = mockPath.substring(mockPath.indexOf("size=") + 5);
            return parseSize(sizeStr);
        } else if (mockPath.contains("count=") && mockPath.contains("avgSize=")) {
            // 文件夹，包含多个文件
            String countStr = mockPath.substring(mockPath.indexOf("count=") + 6, mockPath.indexOf("&"));
            String avgSizeStr = mockPath.substring(mockPath.indexOf("avgSize=") + 8);
            int count = Integer.parseInt(countStr);
            long avgSize = parseSize(avgSizeStr);
            return count * avgSize;
        }
        return 0;
    }
    
    /**
     * 解析文件大小字符串（如 "300MB", "5.5MB"）
     */
    private long parseSize(String sizeStr) {
        if (sizeStr.endsWith("MB")) {
            double mb = Double.parseDouble(sizeStr.substring(0, sizeStr.length() - 2));
            return (long) (mb * 1024 * 1024);
        } else if (sizeStr.endsWith("KB")) {
            double kb = Double.parseDouble(sizeStr.substring(0, sizeStr.length() - 2));
            return (long) (kb * 1024);
        }
        return Long.parseLong(sizeStr);
    }
    
    /**
     * 执行子任务
     */
    private void executeSubTask(BatchSubTask subTask, int index) {
        currentSubTaskIndex = index;
        long subTaskBytes = calculateSubTaskBytes(subTask);
        
        // 子任务开始
        ProgressEvent subTaskStartEvent = new ProgressEvent();
        subTaskStartEvent.type = EventType.SUBTASK_START;
        subTaskStartEvent.timestamp = currentTime;
        subTaskStartEvent.subTaskIndex = index;
        subTaskStartEvent.totalBytes = subTaskBytes;
        events.add(subTaskStartEvent);
        
        log("--- 子任务 " + index + " 开始 ---");
        log("子任务总大小：" + formatSize(subTaskBytes));
        
        // 处理每个文件路径
        int fileIndex = 0;
        for (String filePath : subTask.getFilePaths()) {
            fileIndex += simulateFileUpload(filePath, fileIndex + 1);
        }
        
        // 子任务完成
        ProgressEvent subTaskCompleteEvent = new ProgressEvent();
        subTaskCompleteEvent.type = EventType.SUBTASK_COMPLETED;
        subTaskCompleteEvent.timestamp = currentTime;
        subTaskCompleteEvent.subTaskIndex = index;
        subTaskCompleteEvent.uploadedBytes = batchUploadedBytes;
        events.add(subTaskCompleteEvent);
        
        log("子任务 " + index + " 完成，累计已上传：" + formatSize(batchUploadedBytes));
        log("当前总进度：" + (batchTotalBytes > 0 ? (batchUploadedBytes * 100 / batchTotalBytes) : 0) + "%");
        log("");
    }
    
    /**
     * 模拟文件上传
     * @return 上传的文件数量
     */
    private int simulateFileUpload(String mockPath, int startFileIndex) {
        int fileCount = 0;
        
        if (mockPath.contains("count=")) {
            // 文件夹，包含多个文件
            String countStr = mockPath.substring(mockPath.indexOf("count=") + 6, mockPath.indexOf("&"));
            int count = Integer.parseInt(countStr);
            
            for (int i = 0; i < count; i++) {
                long fileSize = generateRandomFileSize(mockPath);
                simulateSingleFileUpload("file_" + String.format("%03d", startFileIndex + i) + ".bin", fileSize);
                fileCount++;
            }
        } else {
            // 单个文件
            long fileSize = parseMockFileSize(mockPath);
            String fileName = mockPath.substring(mockPath.lastIndexOf('/') + 1).split("\\?")[0];
            simulateSingleFileUpload(fileName, fileSize);
            fileCount++;
        }
        
        return fileCount;
    }
    
    /**
     * 生成随机文件大小（1-10MB范围内）
     */
    private long generateRandomFileSize(String mockPath) {
        String avgSizeStr = mockPath.substring(mockPath.indexOf("avgSize=") + 8);
        long avgSize = parseSize(avgSizeStr);
        // 在平均大小的 0.5x 到 1.5x 范围内随机
        long minSize = avgSize / 2;
        long maxSize = avgSize * 3 / 2;
        return minSize + random.nextInt((int)(maxSize - minSize));
    }
    
    /**
     * 模拟单个文件上传
     */
    private void simulateSingleFileUpload(String fileName, long fileSize) {
        // 文件开始
        ProgressEvent fileStartEvent = new ProgressEvent();
        fileStartEvent.type = EventType.FILE_START;
        fileStartEvent.timestamp = currentTime;
        fileStartEvent.subTaskIndex = currentSubTaskIndex;
        fileStartEvent.fileName = fileName;
        fileStartEvent.totalBytes = fileSize;
        events.add(fileStartEvent);
        
        currentFileUploaded = 0;
        currentFileTotal = fileSize;
        
        // 模拟上传进度（每隔10%报告一次）
        for (int percent = 0; percent <= 100; percent += 10) {
            currentTime += 100; // 每次进度更新间隔100ms
            
            long uploaded = fileSize * percent / 100;
            currentFileUploaded = uploaded;
            
            ProgressEvent progressEvent = new ProgressEvent();
            progressEvent.type = EventType.UPLOAD_PROGRESS;
            progressEvent.timestamp = currentTime;
            progressEvent.subTaskIndex = currentSubTaskIndex;
            progressEvent.fileName = fileName;
            progressEvent.percent = percent;
            progressEvent.uploadedBytes = uploaded;
            progressEvent.totalBytes = fileSize;
            events.add(progressEvent);
            
            // 计算并记录进度
            long totalUploaded = batchUploadedBytes + currentFileUploaded;
            int overallPct = batchTotalBytes > 0 ? (int)(totalUploaded * 100 / batchTotalBytes) : 0;
            
            if (percent % 50 == 0 || percent == 100) {
                log("  " + percent + "% (" + formatSize(uploaded) + ") - 总进度：" + overallPct + "%");
            }
        }
        
        // 文件完成
        batchUploadedBytes += fileSize;
        currentFileUploaded = 0;
        currentTime += 50;
        
        ProgressEvent fileCompleteEvent = new ProgressEvent();
        fileCompleteEvent.type = EventType.FILE_COMPLETED;
        fileCompleteEvent.timestamp = currentTime;
        fileCompleteEvent.subTaskIndex = currentSubTaskIndex;
        fileCompleteEvent.fileName = fileName;
        fileCompleteEvent.fileSize = fileSize;
        fileCompleteEvent.success = true;
        events.add(fileCompleteEvent);
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
    
    /**
     * 记录日志
     */
    private void log(String message) {
        logWriter.println(message);
        System.out.println(message);
    }
    
    /**
     * 获取生成的事件列表
     */
    public List<ProgressEvent> getEvents() {
        return events;
    }
    
    /**
     * 获取测试任务
     */
    public BatchTask getTestTask() {
        return testTask;
    }
    
    /**
     * 获取总字节数
     */
    public long getBatchTotalBytes() {
        return batchTotalBytes;
    }
}
