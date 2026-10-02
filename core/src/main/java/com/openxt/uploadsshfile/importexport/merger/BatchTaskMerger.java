package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.batch.BatchSubTask;
import com.openxt.uploadsshfile.batch.BatchTask;
import com.openxt.uploadsshfile.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 批处理任务合并器。
 * 比对依据：name
 * 子任务中的 serverId/pathId/commandConfigId 全部做 ID 重映射。
 */
public class BatchTaskMerger implements ConfigMerger<List<BatchTask>> {

    private final MergeCounts counts = new MergeCounts();

    @Override
    public List<BatchTask> merge(List<BatchTask> exported, List<BatchTask> current, RemapContext ctx) {
        List<BatchTask> result = new ArrayList<>(current);

        for (BatchTask exportedTask : exported) {
            BatchTask match = findMatch(result, exportedTask.getName());
            if (match != null) {
                // 覆盖 match 的属性，子任务 ID 重映射
                match.setName(exportedTask.getName());
                match.setUpdateTime(System.currentTimeMillis());
                match.setSubTasks(remapSubTasks(exportedTask.getSubTasks(), ctx));
                ctx.putBatchTask(exportedTask.getId(), match.getId());
                counts.incrementUpdated();
                Logger.debug("BatchTaskMerger", "Updated batch task: " + exportedTask.getName());
            } else {
                BatchTask newTask = new BatchTask();
                newTask.setId(UUID.randomUUID().toString());
                newTask.setName(exportedTask.getName());
                newTask.setCreateTime(System.currentTimeMillis());
                newTask.setUpdateTime(System.currentTimeMillis());
                newTask.setSubTasks(remapSubTasks(exportedTask.getSubTasks(), ctx));
                result.add(newTask);
                ctx.putBatchTask(exportedTask.getId(), newTask.getId());
                counts.incrementAdded();
                Logger.debug("BatchTaskMerger", "Added batch task: " + newTask.getName());
            }
        }

        return result;
    }

    private List<BatchSubTask> remapSubTasks(List<BatchSubTask> subTasks, RemapContext ctx) {
        List<BatchSubTask> newSubs = new ArrayList<>();
        if (subTasks == null) return newSubs;

        for (BatchSubTask src : subTasks) {
            BatchSubTask newSub = new BatchSubTask();
            newSub.setId(UUID.randomUUID().toString());

            String newServerId = ctx.resolveServerId(src.getServerId());
            newSub.setServerId(newServerId != null ? newServerId : src.getServerId());

            String newPathId = ctx.resolvePathId(src.getPathId());
            newSub.setPathId(newPathId != null ? newPathId : src.getPathId());

            if (src.getCommandConfigId() != null) {
                String newCmdId = ctx.resolveCommandConfigId(src.getCommandConfigId());
                newSub.setCommandConfigId(newCmdId != null ? newCmdId : src.getCommandConfigId());
            }

            newSub.setFilePaths(new ArrayList<>(src.getFilePaths() != null ? src.getFilePaths() : List.of()));
            newSub.setOrder(src.getOrder());
            newSubs.add(newSub);
        }
        return newSubs;
    }

    private BatchTask findMatch(List<BatchTask> current, String name) {
        for (BatchTask t : current) {
            if (t.getName() != null && t.getName().equals(name)) {
                return t;
            }
        }
        return null;
    }

    @Override
    public MergeCounts getMergeCounts() { return counts; }
}
