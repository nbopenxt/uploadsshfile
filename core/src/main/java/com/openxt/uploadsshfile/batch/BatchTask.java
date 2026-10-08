package com.openxt.uploadsshfile.batch;

import com.openxt.uploadsshfile.model.TaskIdGenerator;

import java.util.ArrayList;
import java.util.List;

/**
 * 批处理任务
 * 包含多个子任务的独立任务（非模板），可复制已有任务来创建新任务
 */
public class BatchTask {
    private String id;
    private String name;
    private long createTime;
    private long updateTime;
    private List<BatchSubTask> subTasks;

    public BatchTask() {
        // D-25（FR-04）：新建默认雪花（纯数字、无 '-'）；存量 UUID 不迁移（R16）
        this.id = TaskIdGenerator.nextId();
        this.name = "";
        this.createTime = System.currentTimeMillis();
        this.updateTime = this.createTime;
        this.subTasks = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }

    public long getUpdateTime() { return updateTime; }
    public void setUpdateTime(long updateTime) { this.updateTime = updateTime; }

    public List<BatchSubTask> getSubTasks() { return subTasks; }
    public void setSubTasks(List<BatchSubTask> subTasks) { this.subTasks = subTasks; }

    @Override
    public String toString() {
        return name != null ? name : "BatchTask-" + id.substring(0, 8);
    }
}
