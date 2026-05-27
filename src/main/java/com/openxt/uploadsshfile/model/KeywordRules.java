package com.openxt.uploadsshfile.model;

import java.util.ArrayList;
import java.util.List;

/**
 * 关键词规则配置
 * 只配置失败关键词，用于在 exitcode=0 时判断是否存在隐式失败
 * 
 * 判断优先级：
 * 1. 命令返回值（exit code != 0 → 失败）
 * 2. 输出中是否包含失败关键词
 */
public class KeywordRules {
    
    // ==================== Linux 关键词 ====================
    
    /** Linux 默认失败关键词 */
    private List<String> linuxDefaultFailKeywords;
    
    /** Linux 自定义失败关键词 */
    private List<String> linuxCustomFailKeywords;
    
    // ==================== Windows 关键词 ====================
    
    /** Windows 默认失败关键词 */
    private List<String> windowsDefaultFailKeywords;
    
    /** Windows 自定义失败关键词 */
    private List<String> windowsCustomFailKeywords;
    
    // ==================== 通用关键词 ====================
    
    /** 通用失败关键词（所有系统适用） */
    private List<String> universalFailKeywords;
    
    public KeywordRules() {
        // Linux 默认失败关键词
        this.linuxDefaultFailKeywords = new ArrayList<>(List.of(
            "error", "failed", "fatal", "exception", "denied", "permission denied",
            "command not found", "no such file", "not found", "syntax error"
        ));
        
        // Windows 默认失败关键词
        this.windowsDefaultFailKeywords = new ArrayList<>(List.of(
            "error", "failed", "failure", "fatal", "exception", "access denied",
            "not recognized", "not found", "syntax error", "exit code: 1"
        ));
        
        // 自定义关键词（默认空）
        this.linuxCustomFailKeywords = new ArrayList<>();
        this.windowsCustomFailKeywords = new ArrayList<>();
        
        // 通用关键词
        this.universalFailKeywords = new ArrayList<>();
    }
    
    // ==================== Getter/Setter ====================
    
    public List<String> getLinuxDefaultFailKeywords() {
        return linuxDefaultFailKeywords;
    }
    
    public void setLinuxDefaultFailKeywords(List<String> linuxDefaultFailKeywords) {
        this.linuxDefaultFailKeywords = linuxDefaultFailKeywords;
    }
    
    public List<String> getLinuxCustomFailKeywords() {
        return linuxCustomFailKeywords;
    }
    
    public void setLinuxCustomFailKeywords(List<String> linuxCustomFailKeywords) {
        this.linuxCustomFailKeywords = linuxCustomFailKeywords;
    }
    
    public List<String> getWindowsDefaultFailKeywords() {
        return windowsDefaultFailKeywords;
    }
    
    public void setWindowsDefaultFailKeywords(List<String> windowsDefaultFailKeywords) {
        this.windowsDefaultFailKeywords = windowsDefaultFailKeywords;
    }
    
    public List<String> getWindowsCustomFailKeywords() {
        return windowsCustomFailKeywords;
    }
    
    public void setWindowsCustomFailKeywords(List<String> windowsCustomFailKeywords) {
        this.windowsCustomFailKeywords = windowsCustomFailKeywords;
    }
    
    public List<String> getUniversalFailKeywords() {
        return universalFailKeywords;
    }
    
    public void setUniversalFailKeywords(List<String> universalFailKeywords) {
        this.universalFailKeywords = universalFailKeywords;
    }
    
    // ==================== 便捷方法 ====================
    
    /**
     * 获取指定操作系统生效的失败关键词
     */
    public List<String> getEffectiveFailKeywords(OperatingSystem osType) {
        List<String> effective = new ArrayList<>();
        
        // 添加通用失败关键词
        effective.addAll(safeList(universalFailKeywords));
        
        // 添加系统特定失败关键词
        if (osType != null && osType.isUnixLike()) {
            effective.addAll(safeList(linuxDefaultFailKeywords));
            effective.addAll(linuxCustomFailKeywords);
        } else if (osType != null && osType.isWindows()) {
            effective.addAll(safeList(windowsDefaultFailKeywords));
            effective.addAll(windowsCustomFailKeywords);
        }
        
        return effective;
    }
    
    private List<String> safeList(List<String> list) {
        return list != null ? list : new ArrayList<>();
    }
    
    /**
     * 重置为默认规则
     * 从 JSON 反序列化后，列表可能为 null，需要重新创建
     */
    public void resetToDefault() {
        this.linuxCustomFailKeywords = new ArrayList<>();
        this.windowsCustomFailKeywords = new ArrayList<>();
        this.universalFailKeywords = new ArrayList<>();
    }
}
