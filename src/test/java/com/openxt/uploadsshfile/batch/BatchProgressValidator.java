package com.openxt.uploadsshfile.batch;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * 批处理进度验证器。
 * 验证模拟测试中的进度计算是否正确，检测进度跳变等问题。
 */
public class BatchProgressValidator {
    
    private final List<ValidationResult> results = new ArrayList<>();
    private final PrintWriter logWriter;
    private long lastOverallPercent = -1;
    private long lastSubTaskPercent = -1;
    private int currentSubTaskIndex = -1;
    private long batchUploadedBytes = 0;
    private long currentFileUploaded = 0;
    private long batchTotalBytes = 0;
    
    /**
     * 验证结果
     */
    public static class ValidationResult {
        public enum Status {
            PASS, FAIL, WARN
        }
        
        public Status status;
        public String testName;
        public String message;
        public long timestamp;
        public long expectedValue;
        public long actualValue;
        
        @Override
        public String toString() {
            String statusStr = status == Status.PASS ? "✓" : (status == Status.FAIL ? "" : "⚠");
            return String.format("[%s] %s: %s", statusStr, testName, message);
        }
    }
    
    public BatchProgressValidator(String reportFilePath, long batchTotalBytes) throws IOException {
        this.batchTotalBytes = batchTotalBytes;
        this.logWriter = new PrintWriter(new FileWriter(reportFilePath));
        
        log("=== 批处理进度验证报告 ===");
        log("总字节数：" + formatSize(batchTotalBytes));
        log("");
    }
    
    /**
     * 验证批处理开始事件
     */
    public void validateBatchStart(long totalBytes) {
        boolean pass = totalBytes > 0;
        addResult(pass ? ValidationResult.Status.PASS : ValidationResult.Status.FAIL,
                "批处理总字节数",
                pass ? "总字节数 > 0" : "总字节数 <= 0",
                totalBytes, totalBytes);
    }
    
    /**
     * 验证子任务开始事件
     */
    public void validateSubTaskStart(int subTaskIndex, long subTaskTotalBytes) {
        // 检查子任务索引是否递增
        if (currentSubTaskIndex >= 0 && subTaskIndex != currentSubTaskIndex + 1) {
            addResult(ValidationResult.Status.FAIL,
                    "子任务索引连续性",
                    "子任务索引应递增，当前=" + subTaskIndex + ", 上一个=" + currentSubTaskIndex,
                    currentSubTaskIndex + 1, subTaskIndex);
        }
        
        // 检查子任务总字节数是否合理
        if (subTaskTotalBytes <= 0) {
            addResult(ValidationResult.Status.FAIL,
                    "子任务字节数",
                    "子任务总字节数应 > 0",
                    1, subTaskTotalBytes);
        }
        
        currentSubTaskIndex = subTaskIndex;
    }
    
    /**
     * 验证上传进度事件
     */
    public void validateUploadProgress(String fileName, int percent, long uploaded, long total) {
        // 验证百分比范围
        if (percent < 0 || percent > 100) {
            addResult(ValidationResult.Status.FAIL,
                    "进度百分比范围",
                    "百分比应在 0-100 之间，当前=" + percent,
                    50, percent);
        }
        
        // 验证上传字节数不超过总大小
        if (uploaded > total) {
            addResult(ValidationResult.Status.FAIL,
                    "上传字节数",
                    "已上传字节数不应超过总大小",
                    total, uploaded);
        }
        
        // 验证当前文件上传字节数递增
        if (currentFileUploaded > 0 && uploaded < currentFileUploaded) {
            addResult(ValidationResult.Status.WARN,
                    "文件上传字节数递减",
                    "文件 " + fileName + " 的上传字节数从 " + formatSize(currentFileUploaded) + 
                    " 减少到 " + formatSize(uploaded),
                    currentFileUploaded, uploaded);
        }
        
        // 计算并验证总进度
        long totalUploaded = batchUploadedBytes + uploaded;
        long overallPercent = batchTotalBytes > 0 ? (totalUploaded * 100 / batchTotalBytes) : 0;
        
        // 检查总进度是否跳变（下降）
        if (lastOverallPercent >= 0 && overallPercent < lastOverallPercent - 1) {
            addResult(ValidationResult.Status.FAIL,
                    "总任务进度跳变",
                    "总进度从 " + lastOverallPercent + "% 跳变到 " + overallPercent + "%（文件=" + fileName + "）",
                    lastOverallPercent, overallPercent);
        }
        
        lastOverallPercent = overallPercent;
        currentFileUploaded = uploaded;
    }
    
    /**
     * 验证文件完成事件
     */
    public void validateFileCompleted(String fileName, long fileSize, boolean success) {
        if (success) {
            batchUploadedBytes += fileSize;
            
            // 验证总进度不应跳变
            long overallPercent = batchTotalBytes > 0 ? (batchUploadedBytes * 100 / batchTotalBytes) : 0;
            
            if (lastOverallPercent >= 0 && overallPercent < lastOverallPercent - 1) {
                addResult(ValidationResult.Status.FAIL,
                        "文件完成后总进度跳变",
                        "文件 " + fileName + " 完成后，总进度从 " + lastOverallPercent + "% 跳变到 " + overallPercent + "%",
                        lastOverallPercent, overallPercent);
            }
            
            lastOverallPercent = overallPercent;
        } else {
            addResult(ValidationResult.Status.WARN,
                    "文件上传失败",
                    "文件 " + fileName + " 上传失败",
                    0, 0);
        }
        
        currentFileUploaded = 0;
    }
    
    /**
     * 验证子任务完成事件
     */
    public void validateSubTaskCompleted(int subTaskIndex, long uploadedBytes) {
        // 子任务完成时，已上传字节数应该累加
        if (uploadedBytes < batchUploadedBytes) {
            addResult(ValidationResult.Status.FAIL,
                    "子任务完成后字节数不一致",
                    "子任务 " + subTaskIndex + " 完成后，上报的已上传字节数 (" + formatSize(uploadedBytes) + 
                    ") 小于内部累计值 (" + formatSize(batchUploadedBytes) + ")",
                    batchUploadedBytes, uploadedBytes);
        }
        
        // 验证总进度计算
        long overallPercent = batchTotalBytes > 0 ? (batchUploadedBytes * 100 / batchTotalBytes) : 0;
        
        addResult(ValidationResult.Status.PASS,
                "子任务 " + subTaskIndex + " 完成",
                "累计已上传：" + formatSize(batchUploadedBytes) + "，总进度：" + overallPercent + "%",
                overallPercent, overallPercent);
    }
    
    /**
     * 验证批处理完成事件
     */
    public void validateBatchCompleted(long finalUploadedBytes) {
        // 验证最终上传字节数等于总字节数
        if (finalUploadedBytes != batchTotalBytes) {
            addResult(ValidationResult.Status.FAIL,
                    "批处理完成字节数",
                    "最终上传字节数 (" + formatSize(finalUploadedBytes) + 
                    ") 不等于总字节数 (" + formatSize(batchTotalBytes) + ")",
                    batchTotalBytes, finalUploadedBytes);
        } else {
            addResult(ValidationResult.Status.PASS,
                    "批处理完成字节数",
                    "最终上传字节数等于总字节数",
                    batchTotalBytes, finalUploadedBytes);
        }
        
        // 验证最终进度为 100%
        long overallPercent = batchTotalBytes > 0 ? (finalUploadedBytes * 100 / batchTotalBytes) : 0;
        if (overallPercent != 100) {
            addResult(ValidationResult.Status.FAIL,
                    "最终进度",
                    "最终进度应为 100%，实际为 " + overallPercent + "%",
                    100, overallPercent);
        } else {
            addResult(ValidationResult.Status.PASS,
                    "最终进度",
                    "最终进度为 100%",
                    100, 100);
        }
    }
    
    /**
     * 生成验证报告
     */
    public void generateReport() {
        log("");
        log("=== 验证结果汇总 ===");
        
        long passCount = results.stream().filter(r -> r.status == ValidationResult.Status.PASS).count();
        long failCount = results.stream().filter(r -> r.status == ValidationResult.Status.FAIL).count();
        long warnCount = results.stream().filter(r -> r.status == ValidationResult.Status.WARN).count();
        
        log("总验证项：" + results.size());
        log("通过：" + passCount);
        log("失败：" + failCount);
        log("警告：" + warnCount);
        log("");
        
        if (failCount > 0) {
            log("失败项详情：");
            for (ValidationResult result : results) {
                if (result.status == ValidationResult.Status.FAIL) {
                    log("  " + result);
                }
            }
            log("");
        }
        
        if (warnCount > 0) {
            log("警告项详情：");
            for (ValidationResult result : results) {
                if (result.status == ValidationResult.Status.WARN) {
                    log("  " + result);
                }
            }
            log("");
        }
        
        if (failCount == 0) {
            log("✓ 所有验证项均通过！");
        } else {
            log("✗ 存在 " + failCount + " 个失败项，需要修复！");
        }
        
        log("");
        log("=== 验证报告完成 ===");
        
        logWriter.flush();
        logWriter.close();
    }
    
    /**
     * 添加验证结果
     */
    private void addResult(ValidationResult.Status status, String testName, String message, 
                          long expectedValue, long actualValue) {
        ValidationResult result = new ValidationResult();
        result.status = status;
        result.testName = testName;
        result.message = message;
        result.timestamp = System.currentTimeMillis();
        result.expectedValue = expectedValue;
        result.actualValue = actualValue;
        
        results.add(result);
        log(result.toString());
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
     * 获取验证结果列表
     */
    public List<ValidationResult> getResults() {
        return results;
    }
}
