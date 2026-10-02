package com.openxt.uploadsshfile.cli;

import com.openxt.uploadsshfile.batch.BatchTask;
import com.openxt.uploadsshfile.batch.BatchTaskManager;
import com.openxt.uploadsshfile.cliapi.CliRunner;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;

/**
 * 任务定位（1.0.8 / FR-10，设计文档 §3.1）：
 * 先比 {@code singleUploadTaskId}（单例任务），再遍历 {@code BatchTask.id}；
 * 不透明字符串匹配——存量 UUID 与雪花 ID 共存（R16）；未命中＝PARAM(2)。
 * 单任务上下文＝现成 {@code getLastSuccessful*()} 四项（D-09），任一为 null＝PARAM(2)
 * 并提示"请先在 IDEA 中成功执行一次单次上传"（跨机场景由 D-11 导出字段解决）。
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
            String serverId = store.getLastSuccessfulServerId();
            String pathId = store.getLastSuccessfulPathId();
            String cmdId = store.getLastSuccessfulCommandConfigId();
            String timing = store.getLastSuccessfulTiming();
            if (serverId == null || pathId == null) {
                r.error = "single-task context incomplete (server/path not remembered). "
                        + "Run a single upload successfully in IDEA once first.";
                return r;
            }
            CliRunner.SingleContext ctx = new CliRunner.SingleContext();
            ctx.serverId = serverId;
            ctx.pathId = pathId;
            ctx.commandConfigId = cmdId;   // 可 null＝GUI 当时未选命令组
            ctx.timing = timing;
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
