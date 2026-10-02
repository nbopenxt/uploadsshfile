package com.openxt.uploadsshfile.model;

/**
 * 执行摘要
 * 记录命令执行的统计信息
 */
public class ExecutionSummary {
    
    private int total;
    private int successCount;
    private int failedCount;
    private int blockedCount;
    private long startTime;
    private long endTime;
    
    public ExecutionSummary() {
    }
    
    public ExecutionSummary(int total, int successCount, int failedCount) {
        this.total = total;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.startTime = System.currentTimeMillis();
        this.endTime = System.currentTimeMillis();
    }
    
    public ExecutionSummary(int total, int successCount, int failedCount, int blockedCount) {
        this.total = total;
        this.successCount = successCount;
        this.failedCount = failedCount;
        this.blockedCount = blockedCount;
    }
    
    public int getTotal() {
        return total;
    }
    
    public void setTotal(int total) {
        this.total = total;
    }
    
    public int getSuccessCount() {
        return successCount;
    }
    
    public void setSuccessCount(int successCount) {
        this.successCount = successCount;
    }
    
    public int getFailedCount() {
        return failedCount;
    }
    
    public void setFailedCount(int failedCount) {
        this.failedCount = failedCount;
    }
    
    public int getBlockedCount() {
        return blockedCount;
    }
    
    public void setBlockedCount(int blockedCount) {
        this.blockedCount = blockedCount;
    }
    
    public long getStartTime() {
        return startTime;
    }
    
    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }
    
    public long getEndTime() {
        return endTime;
    }
    
    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }
    
    public long getDurationMs() {
        return endTime - startTime;
    }
    
    @Override
    public String toString() {
        return "ExecutionSummary{" +
                "total=" + total +
                ", success=" + successCount +
                ", failed=" + failedCount +
                ", blocked=" + blockedCount +
                '}';
    }
}
