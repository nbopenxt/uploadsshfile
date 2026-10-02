package com.openxt.uploadsshfile.model;

import com.openxt.uploadsshfile.i18n.LanguageManager;

import java.util.*;

/**
 * 操作系统预设黑名单
 * 定义各操作系统常见的危险命令黑名单
 */
public class OsBlacklistPresets {
    
    /**
     * Linux 通用危险命令（所有 Linux 发行版）
     */
    private static final List<String> LINUX_DANGEROUS = Arrays.asList(
        "rm -rf /",
        "rm -rf /*",
        "rm -rf *",
        "shutdown",
        "reboot",
        "halt",
        "poweroff",
        "init 0",
        "init 6",
        "telinit 0",
        "telinit 6",
        "mkfs",
        "mkfs.ext4",
        "mkfs.xfs",
        "mkfs.btrfs",
        "dd if=",
        "fdisk",
        "parted",
        ":(){ :|: & };:",  // fork bomb (带空格)
        ":(){:|:&};:",      // fork bomb (无空格)
        "fork bomb",
        "chmod -R 777 /",
        "chown -R",
        "wget http://",
        "curl http://",
        "> /dev/sda",
        "| dd of=",
        "cat /dev/urandom >",
        "yum remove --all",
        "apt-get remove --purge --yes",
        "dpkg -r --force",
        "systemctl stop firewalld",
        "iptables -F",
        "ufw disable",
        "service iptables stop"
    );
    
    /**
     * Ubuntu 特定危险命令
     */
    private static final List<String> UBUNTU_DANGEROUS = Arrays.asList(
        "apt purge",
        "apt remove --purge",
        "dpkg -P",
        "snap remove --all",
        "systemctl disable --now",
        "snap install",
        "flatpak remove"
    );
    
    /**
     * CentOS/RHEL/Rocky 特定危险命令
     */
    private static final List<String> REDHAT_DANGEROUS = Arrays.asList(
        "yum history undo",
        "rpm -e --nodeps",
        "rpm --force --nodeps",
        "subscription-manager unregister",
        "dnf remove --all",
        "dnf history undo",
        "yum-complete-transaction"
    );
    
    /**
     * Alpine 特定危险命令
     */
    private static final List<String> ALPINE_DANGEROUS = Arrays.asList(
        "apk del --purge",
        "apk fix"
    );
    
    /**
     * Debian 特定危险命令
     */
    private static final List<String> DEBIAN_DANGEROUS = Arrays.asList(
        "apt-get purge",
        "apt-get autoremove --purge",
        "dpkg --remove --force",
        "apt-cache clean",
        "apt-get clean"
    );
    
    /**
     * Windows 危险命令
     */
    private static final List<String> WINDOWS_DANGEROUS = Arrays.asList(
        "format",
        "format c:",
        "del /f /s /q c:",
        "rd /s /q c:\\",
        "cipher /w:",
        "bcdedit /delete",
        "net user administrator",
        "reg delete",
        " Disable-WindowsOptionalFeature",
        "Uninstall-WindowsFeature",
        "rmdir /s /q",
        "attrib -s -h",
        "takeown /f",
        "icacls /grant",
        "powershell -Command \"Remove-Item\"",
        "rmdir"
    );
    
    /**
     * macOS 危险命令
     */
    private static final List<String> MACOS_DANGEROUS = Arrays.asList(
        "rm -rf /Library/",
        "diskutil eraseDisk",
        "csrutil disable",
        "spctl --master-disable",
        "sudo rm -rf",
        "brew uninstall --force",
        "launchctl unload",
        "killall -9",
        "dscl . delete",
        "csrutil clear"
    );
    
    /**
     * 获取指定操作系统的预设黑名单
     * 
     * @param os 操作系统类型
     * @return 该操作系统的所有危险命令列表（已去重，按危险程度排序）
     */
    public static List<String> getPresetBlacklist(OperatingSystem os) {
        Set<String> combined = new LinkedHashSet<>();
        
        // 1. 添加 Linux 通用命令（如果适用）
        if (os.isUnixLike()) {
            combined.addAll(LINUX_DANGEROUS);
        }
        
        // 2. 添加操作系统特定的危险命令
        switch (os) {
            case UBUNTU:
                combined.addAll(UBUNTU_DANGEROUS);
                break;
            case CENTOS:
            case RHEL:
            case ROCKY:
                combined.addAll(REDHAT_DANGEROUS);
                break;
            case ALPINE:
                combined.addAll(ALPINE_DANGEROUS);
                break;
            case DEBIAN:
                combined.addAll(DEBIAN_DANGEROUS);
                break;
            case WINDOWS:
                combined.addAll(WINDOWS_DANGEROUS);
                break;
            case MACOS:
                combined.addAll(MACOS_DANGEROUS);
                break;
            case LINUX:
            default:
                // Linux 通用版不加额外命令
                break;
        }
        
        return new ArrayList<>(combined);
    }
    
    /**
     * 获取通用预设黑名单（已废弃，返回空列表）
     * 黑名单已改为按操作系统独立管理，不再使用通用列表
     */
    public static List<String> getUniversalBlacklist() {
        return new ArrayList<>();
    }
    
    // ==================== 危险目录预设 ====================
    
    /**
     * Linux 通用危险目录
     */
    private static final List<String> LINUX_DANGEROUS_PATHS = Arrays.asList(
        "/bin",
        "/sbin",
        "/usr/bin",
        "/usr/sbin",
        "/lib",
        "/lib64",
        "/etc",
        "/sys",
        "/proc",
        "/boot",
        "/dev",
        "/run",
        "/root",
        "/var",
        "/var/log",
        "/var/etc",
        "/srv",
        "/mnt",
        "/media"
    );
    
    /**
     * Windows 危险目录
     */
    private static final List<String> WINDOWS_DANGEROUS_PATHS = Arrays.asList(
        "C:\\"
    );
    
    /**
     * macOS 危险目录
     */
    private static final List<String> MACOS_DANGEROUS_PATHS = Arrays.asList(
        "/",
        "/bin",
        "/sbin",
        "/usr/bin",
        "/usr/sbin",
        "/System",
        "/Library",
        "/Applications",
        "/etc",
        "/var",
        "/tmp",
        "/root",
        "/home",
        "/Users"
    );
    
    /**
     * 获取指定操作系统的预设危险目录
     */
    public static List<String> getPresetDangerousPaths(OperatingSystem os) {
        switch (os) {
            case WINDOWS:
                return WINDOWS_DANGEROUS_PATHS;
            case MACOS:
                return MACOS_DANGEROUS_PATHS;
            case LINUX:
            default:
                return LINUX_DANGEROUS_PATHS;
        }
    }
    
    /**
     * 获取通用危险目录（已废弃，返回空列表）
     * 危险目录已改为按操作系统独立管理，不再使用通用列表
     */
    public static List<String> getUniversalDangerousPaths() {
        return new ArrayList<>();
    }
    
    /**
     * 获取操作系统的描述信息
     */
    public static String getOsDescription(OperatingSystem os) {
        switch (os) {
            case LINUX:
                return LanguageManager.getInstance().get("os.desc.linux");
            case UBUNTU:
                return LanguageManager.getInstance().get("os.desc.ubuntu");
            case DEBIAN:
                return LanguageManager.getInstance().get("os.desc.debian");
            case CENTOS:
                return LanguageManager.getInstance().get("os.desc.centos");
            case ROCKY:
                return LanguageManager.getInstance().get("os.desc.rocky");
            case ALPINE:
                return LanguageManager.getInstance().get("os.desc.alpine");
            case RHEL:
                return LanguageManager.getInstance().get("os.desc.rhel");
            case WINDOWS:
                return LanguageManager.getInstance().get("os.desc.windows");
            case MACOS:
                return LanguageManager.getInstance().get("os.desc.macos");
            default:
                return os.getDisplayName();
        }
    }
    
    /**
     * 获取操作系统图标/emoji标识
     */
    public static String getOsIcon(OperatingSystem os) {
        switch (os) {
            case LINUX:
                return "🐧";
            case UBUNTU:
                return "🐧 Ubuntu";
            case DEBIAN:
                return "🐧 Debian";
            case CENTOS:
                return "🐧 CentOS";
            case ROCKY:
                return "🐧 Rocky";
            case ALPINE:
                return "🐧 Alpine";
            case RHEL:
                return "🐧 RHEL";
            case WINDOWS:
                return "🪟";
            case MACOS:
                return "🍎";
            default:
                return "";
        }
    }
}
