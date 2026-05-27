package com.openxt.uploadsshfile.model;

/**
 * 校验结果
 * 用于黑名单校验
 * 
 * <b>状态说明</b>：
 * <ul>
 *   <li><b>allowed</b> - 允许执行</li>
 *   <li><b>blocked</b> - 强制拦截，不允许执行</li>
 *   <li><b>caution</b> - 需用户确认，可选择继续或取消</li>
 * </ul>
 */
public class ValidationResult {
    
    /** 状态枚举 */
    public enum Status {
        ALLOWED,    // 允许执行
        BLOCKED,    // 强制拦截
        CAUTION     // 需确认
    }
    
    private final Status status;
    private final String reason;
    
    private ValidationResult(Status status, String reason) {
        this.status = status;
        this.reason = reason;
    }
    
    /**
     * 创建被拦截的结果（不允许执行）
     */
    public static ValidationResult blocked(String reason) {
        return new ValidationResult(Status.BLOCKED, reason);
    }
    
    /**
     * 创建允许的结果
     */
    public static ValidationResult allowed() {
        return new ValidationResult(Status.ALLOWED, null);
    }
    
    /**
     * 创建需确认的结果（提示用户风险，可选择继续）
     */
    public static ValidationResult caution(String reason) {
        return new ValidationResult(Status.CAUTION, reason);
    }
    
    public boolean isBlocked() {
        return status == Status.BLOCKED;
    }
    
    public boolean isAllowed() {
        return status == Status.ALLOWED;
    }
    
    public boolean isCaution() {
        return status == Status.CAUTION;
    }
    
    public Status getStatus() {
        return status;
    }
    
    public String getReason() {
        return reason;
    }
    
    @Override
    public String toString() {
        return "ValidationResult{status=" + status + ", reason='" + reason + "'}";
    }
}
