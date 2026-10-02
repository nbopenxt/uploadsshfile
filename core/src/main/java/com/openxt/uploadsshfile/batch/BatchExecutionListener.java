package com.openxt.uploadsshfile.batch;

import java.util.List;

/**
 * 批处理任务执行监听器。
 * BatchExecutionOrchestrator 通过此回调向 UI 层报告执行进度。
 */
public interface BatchExecutionListener {

    /** 批处理任务开始执行 */
    void onBatchStart(BatchTask task, int totalSubTasks);

    /** 子任务即将开始 */
    void onSubTaskStart(BatchSubTask subTask, int index, int total);

    /** 当前子任务内某个顶层文件/目录开始上传 */
    default void onFileStart(String fileName, int fileIndex, int totalFiles) {}

    /** 单个文件上传进度 */
    void onUploadProgress(String fileName, int percent, long uploaded, long total);

    /** 当前子任务内某个顶层文件/目录上传完成 */
    default void onFileCompleted(String fileName, long size, boolean success, String errorMessage) {}

    /** 子任务执行完成（成功或失败） */
    void onSubTaskCompleted(BatchSubTaskResult result);

    /** 全部子任务执行完成 */
    void onBatchCompleted(List<BatchSubTaskResult> allResults);

    /** 用户取消执行 */
    void onBatchCancelled();

    /** 日志输出 */
    default void onLog(String message) {}
}
