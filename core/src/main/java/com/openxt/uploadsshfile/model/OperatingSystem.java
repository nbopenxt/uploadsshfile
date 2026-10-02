package com.openxt.uploadsshfile.model;

/**
 * 操作系统类型枚举
 * 用于标识服务器操作系统，以便加载对应的预设黑名单和安全配置
 */
public enum OperatingSystem {
    
    /**
     * 通用 Linux（默认值，用于未明确识别的 Linux 发行版）
     */
    LINUX("Linux", "linux"),
    
    /**
     * Ubuntu Server / Ubuntu Desktop
     */
    UBUNTU("Ubuntu", "ubuntu"),
    
    /**
     * Debian
     */
    DEBIAN("Debian", "debian"),
    
    /**
     * CentOS
     */
    CENTOS("CentOS", "centos"),
    
    /**
     * Rocky Linux
     */
    ROCKY("Rocky Linux", "rocky"),
    
    /**
     * Alpine Linux
     */
    ALPINE("Alpine Linux", "alpine"),
    
    /**
     * Red Hat Enterprise Linux
     */
    RHEL("RHEL", "rhel"),
    
    /**
     * Windows Server / Windows Desktop
     */
    WINDOWS("Windows", "windows"),
    
    /**
     * macOS
     */
    MACOS("macOS", "macos");
    
    private final String displayName;
    private final String key;
    
    OperatingSystem(String displayName, String key) {
        this.displayName = displayName;
        this.key = key;
    }
    
    /**
     * 获取显示名称
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * 获取配置键名
     */
    public String getKey() {
        return key;
    }
    
    /**
     * 根据 key 查找枚举值
     */
    public static OperatingSystem fromKey(String key) {
        if (key == null || key.isEmpty()) {
            return LINUX; // 默认值
        }
        for (OperatingSystem os : values()) {
            if (os.key.equalsIgnoreCase(key)) {
                return os;
            }
        }
        return LINUX; // 未找到时返回默认值
    }
    
    /**
     * 判断是否为 Windows 系统
     */
    public boolean isWindows() {
        return this == WINDOWS;
    }
    
    /**
     * 判断是否为 Linux/Unix 系统
     */
    public boolean isUnixLike() {
        return this != WINDOWS;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}
