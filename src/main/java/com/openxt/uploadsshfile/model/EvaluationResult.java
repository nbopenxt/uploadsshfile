package com.openxt.uploadsshfile.model;

/**
 * 评估结果
 * 用于关键词匹配评估
 */
public class EvaluationResult {
    
    private final boolean success;
    private final String reason;
    private final int exitCode;
    
    private EvaluationResult(boolean success, String reason, int exitCode) {
        this.success = success;
        this.reason = reason;
        this.exitCode = exitCode;
    }
    
    /**
     * 创建成功结果
     */
    public static EvaluationResult success(int exitCode) {
        return new EvaluationResult(true, null, exitCode);
    }
    
    /**
     * 创建失败结果
     */
    public static EvaluationResult failed(String reason, int exitCode) {
        return new EvaluationResult(false, reason, exitCode);
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getReason() {
        return reason;
    }
    
    public int getExitCode() {
        return exitCode;
    }
    
    @Override
    public String toString() {
        if (success) {
            return "EvaluationResult{success=true, exitCode=" + exitCode + '}';
        }
        return "EvaluationResult{success=false, reason='" + reason + '\'' + ", exitCode=" + exitCode + '}';
    }
}
