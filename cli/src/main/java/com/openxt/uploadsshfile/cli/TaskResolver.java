package com.openxt.uploadsshfile.cli;

import com.openxt.uploadsshfile.batch.BatchTask;
import com.openxt.uploadsshfile.batch.BatchTaskManager;
import com.openxt.uploadsshfile.cliapi.CliRunner;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;

/**
 * 任务定位（1.0.8 / FR-10，设计文档 §3.1）：
 * 先比 {@code singleUploadTaskId}（单例任务），再遍历 {@code BatchTask.id}；
 * 不透明字符串匹配——存量 UUID 与雪花 ID 共存（R16）；未命中＝PARAM(2)。
 * D-38 起单任务上下文与文件清单＝**关窗快照优先**（D-37 落盘，含 filePaths），
 * 快照缺失项回落 {@code lastSuccessful*} 成功记忆（D-09 旧语义保留、并存不互斥）；
 * 服务器/路径引用仍为 null＝PARAM(2)；快照无文件清单＝PARAM(2) 点名"先在 GUI 开窗关窗保存清单"
 * （旧提示"先成功执行一次"语义升级——现在关窗即算保存，无需先上传）。
 */
public final class TaskResolver {

    public static final class Resolution {
        public boolean isBatch;
        public BatchTask batchTask;
        public CliRunner.SingleContext single;
        public String error; // 非 null＝未命中/上下文不全（Main 映射 PARAM(2)）
    }

    private TaskResolver() {
    }

    public static Resolution resolve(String taskId) {
        Resolution r = new Resolution();
        UnifiedConfigStore store = UnifiedConfigStore.getInstance();

        String singleId = store.getSingleUploadTaskId();
        if (singleId != null && singleId.equals(taskId)) {
            com.openxt.uploadsshfile.model.SingleUploadTask snap = store.getSingleUploadTask();
            // 快照优先、回落成功记忆（与 UploadDialog effective* 同口径）
            String serverId = snap != null && snap.getServerId() != null
                    ? snap.getServerId() : store.getLastSuccessfulServerId();
            String pathId = snap != null && snap.getPathId() != null
                    ? snap.getPathId() : store.getLastSuccessfulPathId();
            String cmdId = snap != null && snap.getCommandConfigId() != null
                    ? snap.getCommandConfigId() : store.getLastSuccessfulCommandConfigId();
            String timing = snap != null && snap.getTiming() != null
                    ? snap.getTiming() : store.getLastSuccessfulTiming();
            if (serverId == null || pathId == null) {
                r.error = "single-task context incomplete (server/path not remembered). "
                        + "Right-click files in IDEA, pick server/path in the Upload dialog and close it once.";
                return r;
            }
            if (snap == null || snap.getFilePaths().isEmpty()) {
                r.error = "single-task snapshot has no saved files. "
                        + "Right-click files in IDEA, open the Upload dialog and close it once to save the list.";
                return r;
            }
            CliRunner.SingleContext ctx = new CliRunner.SingleContext();
            ctx.serverId = serverId;
            ctx.pathId = pathId;
            ctx.commandConfigId = cmdId;   // 可 null＝GUI 当时未选命令组
            ctx.timing = timing;
            ctx.filePaths = new java.util.ArrayList<>(snap.getFilePaths()); // D-38：文件唯一来源＝关窗快照
            ctx.taskIdForLock = taskId;    // M4：.info 展示用
            r.single = ctx;
            return r;
        }

        BatchTask batch = BatchTaskManager.getInstance().getBatchTask(taskId);
        if (batch != null) {
            r.isBatch = true;
            r.batchTask = batch;
            return r;
        }

        r.error = "unknown task id: " + taskId;
        return r;
    }
}
