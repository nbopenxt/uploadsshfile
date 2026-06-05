package com.openxt.uploadsshfile.batch;

/**
 * 子任务执行结果（内存对象，不持久化）
 */
public class BatchSubTaskResult {

    public enum Status { SUCCESS, FAILED }

    private String subTaskId;
    private String taskDescription;
    private Status status;
    private String errorMessage;
    private long durationMs;

    public BatchSubTaskResult() {
    }

    public String getSubTaskId() { return subTaskId; }
    public void setSubTaskId(String subTaskId) { this.subTaskId = subTaskId; }

    public String getTaskDescription() { return taskDescription; }
    public void setTaskDescription(String taskDescription) { this.taskDescription = taskDescription; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) { this.durationMs = durationMs; }

    public boolean isSuccess() { return status == Status.SUCCESS; }
}
