package com.openxt.uploadsshfile.model;

/**
 * 上传后命令执行时机枚举
 */
public enum ExecuteTiming {
    
    /**
     * 不执行命令
     */
    NONE(0),
    
    /**
     * 上传后自动执行
     */
    AUTO(1),
    
    /**
     * 上传后手动执行
     */
    MANUAL(2);
    
    private final int value;
    
    ExecuteTiming(int value) {
        this.value = value;
    }
    
    public int getValue() {
        return value;
    }
    
    /**
     * 根据值获取枚举
     */
    public static ExecuteTiming fromValue(int value) {
        for (ExecuteTiming timing : values()) {
            if (timing.value == value) {
                return timing;
            }
        }
        return NONE;
    }
}
