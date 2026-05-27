package com.openxt.uploadsshfile.model;

/**
 * 操作系统类型枚举
 */
public enum OsType {
    
    /**
     * Linux/Unix 系统
     */
    LINUX("Linux"),
    
    /**
     * Windows 系统
     */
    WINDOWS("Windows");
    
    private final String displayName;
    
    OsType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 根据显示名称获取枚举
     */
    public static OsType fromDisplayName(String displayName) {
        for (OsType type : values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        return OsType.LINUX;
    }
}
