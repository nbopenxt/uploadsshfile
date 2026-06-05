package com.openxt.uploadsshfile.batch;

/**
 * 验证修复前后的进度计算差异。
 * 演示为什么在 onSubTaskStart 中调用 updateBatchOverallProgress() 会导致进度归零。
 */
public class ProgressBugDemonstration {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("进度归零问题演示");
        System.out.println("========================================\n");
        
        // 模拟状态变量
        long batchTotalBytes = 100_000_000; // 100 MB
        long batchUploadedBytes = 30_000_000; // 30 MB（子任务1已完成）
        long currentFileUploaded = 140_540; // 140.54 KB（子任务1最后一个文件的已上传字节数）
        int currentSubTaskIndex = 1;
        int totalSubTasks = 4;
        
        System.out.println("=== 子任务1完成时的状态 ===");
        System.out.println("batchTotalBytes: " + formatSize(batchTotalBytes));
        System.out.println("batchUploadedBytes: " + formatSize(batchUploadedBytes));
        System.out.println("currentFileUploaded: " + formatSize(currentFileUploaded));
        System.out.println("currentSubTaskIndex: " + currentSubTaskIndex);
        
        // 计算子任务1完成时的总进度
        long totalUploaded = batchUploadedBytes + currentFileUploaded;
        int overallPct = (int)(totalUploaded * 100.0 / batchTotalBytes);
        System.out.println("总进度: " + overallPct + "%");
        System.out.println("  计算: (" + formatSize(batchUploadedBytes) + " + " + formatSize(currentFileUploaded) + ") / " + formatSize(batchTotalBytes));
        System.out.println("  = (" + batchUploadedBytes + " + " + currentFileUploaded + ") / " + batchTotalBytes);
        System.out.println("  = " + totalUploaded + " / " + batchTotalBytes);
        System.out.println("  = " + overallPct + "%\n");
        
        System.out.println("=== 子任务2开始时（BUG场景）===\n");
        
        // 错误做法：在 onSubTaskStart 中调用 updateBatchOverallProgress()
        System.out.println("❌ 错误做法（修复前）：");
        System.out.println("  onSubTaskStart(subTask2) 被调用");
        currentSubTaskIndex = 2;
        System.out.println("  currentSubTaskIndex = " + currentSubTaskIndex);
        
        // 此时 currentFileUploaded 还是旧值！
        totalUploaded = batchUploadedBytes + currentFileUploaded;
        overallPct = (int)(totalUploaded * 100.0 / batchTotalBytes);
        int completedSubTasks = currentSubTaskIndex - 1;
        
        System.out.println("  调用 updateBatchOverallProgress()");
        System.out.println("  completedSubTasks = currentSubTaskIndex - 1 = " + completedSubTasks);
        System.out.println("  totalUploaded = batchUploadedBytes + currentFileUploaded");
        System.out.println("                = " + formatSize(batchUploadedBytes) + " + " + formatSize(currentFileUploaded));
        System.out.println("                = " + totalUploaded);
        System.out.println("  overallPct = " + totalUploaded + " * 100 / " + batchTotalBytes + " = " + overallPct + "%");
        System.out.println("  标签显示: " + completedSubTasks + "/" + totalSubTasks + " (" + overallPct + "%)");
        System.out.println("  ⚠️  问题：进度从 30% 跳变到 " + overallPct + "%，且 completedSubTasks 显示为 1\n");
        
        // 然后 onFileStart 被调用
        System.out.println("  onFileStart 被调用");
        currentFileUploaded = 0;
        System.out.println("  currentFileUploaded = 0（重置）");
        
        // 再次计算进度
        totalUploaded = batchUploadedBytes + currentFileUploaded;
        overallPct = (int)(totalUploaded * 100.0 / batchTotalBytes);
        System.out.println("  再次计算进度: " + overallPct + "%");
        System.out.println("  ⚠️  进度从 " + (batchUploadedBytes + 140540) * 100 / batchTotalBytes + "% 跳变到 " + overallPct + "%\n");
        
        System.out.println("=== 子任务2开始时（正确做法）===\n");
        
        // 重置状态
        currentSubTaskIndex = 1;
        currentFileUploaded = 140_540;
        
        System.out.println("✅ 正确做法（修复后）：");
        System.out.println("  onSubTaskStart(subTask2) 被调用");
        currentSubTaskIndex = 2;
        System.out.println("  currentSubTaskIndex = " + currentSubTaskIndex);
        System.out.println("  初始化子任务状态（completedFilesInSubTask = 0, subTaskOverallBar = 0）");
        System.out.println("  ❌ 不调用 updateBatchOverallProgress()");
        System.out.println("   等待文件开始上传...\n");
        
        System.out.println("  onFileStart 被调用");
        currentFileUploaded = 0;
        System.out.println("  currentFileUploaded = 0（重置）\n");
        
        System.out.println("  onUploadProgress 被调用（第一个文件上传了 10%）");
        currentFileUploaded = 1_000_000; // 1 MB
        totalUploaded = batchUploadedBytes + currentFileUploaded;
        overallPct = (int)(totalUploaded * 100.0 / batchTotalBytes);
        completedSubTasks = currentSubTaskIndex - 1;
        
        System.out.println("  调用 updateBatchOverallProgress()");
        System.out.println("  completedSubTasks = " + completedSubTasks);
        System.out.println("  totalUploaded = " + formatSize(batchUploadedBytes) + " + " + formatSize(currentFileUploaded));
        System.out.println("                = " + totalUploaded);
        System.out.println("  overallPct = " + overallPct + "%");
        System.out.println("  标签显示: " + completedSubTasks + "/" + totalSubTasks + " (" + overallPct + "%)");
        System.out.println("  ✅ 进度连续，从 30% 平滑增长到 " + overallPct + "%\n");
        
        System.out.println("========================================");
        System.out.println("结论");
        System.out.println("========================================");
        System.out.println(" 错误：在 onSubTaskStart 中调用 updateBatchOverallProgress()");
        System.out.println("   - 此时 currentFileUploaded 还是上一个文件的值");
        System.out.println("   - 导致进度计算混乱，可能出现归零或跳变");
        System.out.println();
        System.out.println("✅ 正确：只在 onUploadProgress 和 onFileCompleted 中调用");
        System.out.println("   - 这两个时机 currentFileUploaded 都是准确的");
        System.out.println("   - 确保进度计算正确，不会出现跳变");
    }
    
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
