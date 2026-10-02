package com.openxt.uploadsshfile.batch;

import com.openxt.uploadsshfile.config.ConfigManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 批处理任务配置管理器。
 * 持久化到 UnifiedPluginConfig.batchTasks。
 */
public class BatchTaskManager {

    private static BatchTaskManager instance;

    private final UnifiedConfigStore configStore;

    private BatchTaskManager() {
        this.configStore = UnifiedConfigStore.getInstance();
    }

    public static BatchTaskManager getInstance() {
        if (instance == null) {
            synchronized (BatchTaskManager.class) {
                if (instance == null) {
                    instance = new BatchTaskManager();
                }
            }
        }
        return instance;
    }

    /** 重置单例（用于测试隔离） */
    public static void resetInstance() {
        synchronized (BatchTaskManager.class) {
            instance = null;
        }
    }

    /** 获取所有批处理任务 */
    public List<BatchTask> listBatchTasks() {
        Logger.debug("BatchTaskManager", "listBatchTasks() called");
        List<BatchTask> tasks = configStore.getBatchTasks();
        return tasks != null ? new ArrayList<>(tasks) : new ArrayList<>();
    }

    /** 按 ID 获取 */
    public BatchTask getBatchTask(String id) {
        if (id == null) return null;
        return listBatchTasks().stream()
                .filter(t -> id.equals(t.getId()))
                .findFirst()
                .orElse(null);
    }

    /** 新增或更新批处理任务（ID 为空则新增并自动生成 ID，否则覆盖） */
    public void saveBatchTask(BatchTask task) {
        if (task == null) return;
        Logger.debug("BatchTaskManager", "saveBatchTask id=" + task.getId() + " name=" + task.getName());

        // ID 为空时自动生成
        if (task.getId() == null || task.getId().isEmpty()) {
            task.setId(UUID.randomUUID().toString());
        }

        List<BatchTask> tasks = listBatchTasks();
        boolean found = false;

        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).getId().equals(task.getId())) {
                tasks.set(i, task);
                found = true;
                break;
            }
        }

        if (!found) {
            tasks.add(task);
        }

        configStore.setBatchTasks(tasks);
        Logger.debug("BatchTaskManager", "saveBatchTask completed");
    }

    /** 复制已有批处理任务 → 生成新 UUID 的新任务 */
    public BatchTask copyBatchTask(String sourceId, String newName) {
        BatchTask source = getBatchTask(sourceId);
        if (source == null) return null;

        BatchTask copy = new BatchTask();
        copy.setId(UUID.randomUUID().toString());
        copy.setName(newName);
        copy.setCreateTime(System.currentTimeMillis());
        copy.setUpdateTime(System.currentTimeMillis());

        List<BatchSubTask> copiedSubTasks = new ArrayList<>();
        if (source.getSubTasks() != null) {
            for (BatchSubTask srcSub : source.getSubTasks()) {
                BatchSubTask newSub = new BatchSubTask();
                newSub.setId(UUID.randomUUID().toString());
                newSub.setServerId(srcSub.getServerId());
                newSub.setPathId(srcSub.getPathId());
                newSub.setCommandConfigId(srcSub.getCommandConfigId());
                newSub.setFilePaths(new ArrayList<>(srcSub.getFilePaths() != null ? srcSub.getFilePaths() : List.of()));
                newSub.setOrder(srcSub.getOrder());
                copiedSubTasks.add(newSub);
            }
        }
        copy.setSubTasks(copiedSubTasks);

        saveBatchTask(copy);
        return copy;
    }

    /** 删除批处理任务 */
    public void deleteBatchTask(String id) {
        if (id == null) return;
        List<BatchTask> tasks = listBatchTasks();
        tasks.removeIf(t -> id.equals(t.getId()));
        configStore.setBatchTasks(tasks);
    }

    /** 从失败的子任务列表创建新批处理任务 */
    public BatchTask createFromFailures(List<BatchSubTaskResult> failures, String newName) {
        if (failures == null || failures.isEmpty()) return null;

        BatchTask task = new BatchTask();
        task.setId(UUID.randomUUID().toString());
        task.setName(newName);

        List<BatchSubTask> subTasks = new ArrayList<>();
        int order = 0;

        // 从失败结果中恢复原始子任务配置
        List<BatchTask> allTasks = listBatchTasks();
        for (BatchSubTaskResult failure : failures) {
            if (!failure.isSuccess()) {
                // 查找原始子任务
                BatchSubTask original = findOriginalSubTask(allTasks, failure.getSubTaskId());
                if (original != null) {
                    BatchSubTask copy = new BatchSubTask();
                    copy.setId(UUID.randomUUID().toString());
                    copy.setServerId(original.getServerId());
                    copy.setPathId(original.getPathId());
                    copy.setCommandConfigId(original.getCommandConfigId());
                    copy.setFilePaths(new ArrayList<>(original.getFilePaths()));
                    copy.setOrder(order++);
                    subTasks.add(copy);
                }
            }
        }

        task.setSubTasks(subTasks);
        saveBatchTask(task);
        return task;
    }

    private BatchSubTask findOriginalSubTask(List<BatchTask> tasks, String subTaskId) {
        for (BatchTask task : tasks) {
            for (BatchSubTask sub : task.getSubTasks()) {
                if (sub.getId().equals(subTaskId)) {
                    return sub;
                }
            }
        }
        return null;
    }
}
