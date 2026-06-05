package com.openxt.uploadsshfile.batch;

import java.io.File;

/**
 * 简化的批处理进度模拟测试运行器。
 * 不依赖JUnit，可以直接通过java命令运行。
 */
public class SimpleTestRunner {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("批处理进度模拟测试 - 简化版");
        System.out.println("========================================\n");
        
        try {
            // 创建日志目录
            File logDir = new File("build/test-logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // 初始化模拟器
            String simulationLog = "build/test-logs/batch_progress_simulation.log";
            String validationReport = "build/test-logs/batch_progress_validation.log";
            
            BatchProgressSimulator simulator = new BatchProgressSimulator(simulationLog);
            
            // 运行模拟（先运行以计算总字节数）
            System.out.println("【开始运行模拟】");
            System.out.println("----------------------------------------");
            simulator.runSimulation();
            
            // 初始化验证器（在模拟运行后，获取正确的总字节数）
            BatchProgressValidator validator = new BatchProgressValidator(
                    validationReport, simulator.getBatchTotalBytes());
            
            // 验证进度
            System.out.println("\n【开始验证进度】");
            System.out.println("----------------------------------------");
            
            var events = simulator.getEvents();
            
            validator.validateBatchStart(simulator.getBatchTotalBytes());
            
            for (var event : events) {
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
            
            System.out.println("\n========================================");
            System.out.println("测试完成！");
            System.out.println("========================================");
            System.out.println("\n日志文件位置：");
            System.out.println("  - 模拟日志: " + simulationLog);
            System.out.println("  - 验证报告: " + validationReport);
            
            // 检查是否有失败项
            long failCount = validator.getResults().stream()
                    .filter(r -> r.status == BatchProgressValidator.ValidationResult.Status.FAIL)
                    .count();
            
            if (failCount > 0) {
                System.out.println("\n❌ 发现 " + failCount + " 个失败项，需要修复！");
                System.exit(1);
            } else {
                System.out.println("\n✅ 所有验证项均通过！");
                System.exit(0);
            }
            
        } catch (Exception e) {
            System.err.println("\n 测试执行失败：");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
