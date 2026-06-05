package com.openxt.uploadsshfile.model;

import com.openxt.uploadsshfile.batch.BatchTask;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统一插件配置
 * 将所有插件配置合并到一个文件中
 * 配置文件路径: {IDEA_CONFIG}/uploadsshfile/plugin-config.json
 */
public class UnifiedPluginConfig {
    
    /** 服务器配置列表 */
    private List<ServerConfig> servers;
    
    /** 路径配置列表 */
    private List<PathConfig> paths;
    
    /** AI 配置 */
    private AIConfig aiConfig;
    
    /** 全局黑名单配置 */
    private BlacklistConfig blacklist;
    
    /** 关键词规则配置 */
    private KeywordRules keywordRules;
    
    /** 命令配置列表 */
    private List<CommandConfig> commandConfigs;
    
    /** 语言设置 */
    private String language = "en";
    
    /** 上次成功上传/执行的服务器ID（记忆上次选择） */
    private String lastSuccessfulServerId;

    /** 上次成功上传/执行的路径ID（记忆上次选择） */
    private String lastSuccessfulPathId;

    /** 上次成功上传/执行的命令配置ID（记忆上次选择） */
    private String lastSuccessfulCommandConfigId;

    /** 上次成功上传/执行时选择的执行时机（记忆上次选择） */
    private String lastSuccessfulTiming;

    /** 有返回命令列表（按操作系统分类） */
    private CommandOutputConfig hasOutputCommands;

    /** 批处理任务列表（三期新增） */
    private List<BatchTask> batchTasks;

    /** 是否启用日志输出 */
    private boolean logEnabled = false;
    
    public UnifiedPluginConfig() {
        this.servers = new ArrayList<>();
        this.paths = new ArrayList<>();
        this.aiConfig = new AIConfig();
        this.blacklist = new BlacklistConfig();
        this.keywordRules = new KeywordRules();
        this.commandConfigs = new ArrayList<>();
        
        // 有返回命令列表需要单独初始化（CommandOutputConfig 默认是无返回命令）
        this.hasOutputCommands = new CommandOutputConfig();
        initDefaultHasOutputCommands();

        this.batchTasks = new ArrayList<>();
    }
    
    /**
     * 初始化默认的有返回命令列表
     */
    private void initDefaultHasOutputCommands() {
        // Linux 有返回命令
        hasOutputCommands.getLinux().addAll(List.of(
            "ls", "ll", "la", "dir",
            "cat", "head", "tail", "more", "less", "wc",
            "grep", "find", "which", "whereis", "type",
            "pwd", "whoami", "id", "who", "w",
            "date", "cal", "uptime", "hostname",
            "ps", "pstree", "top", "htop",
            "df", "du", "free", "mount",
            "ifconfig", "ip", "netstat", "ss", "ping", "traceroute", "nslookup", "dig",
            "uname", "arch", "cpuinfo", "meminfo", "lsblk",
            "tar", "gzip", "gunzip", "bzip2", "bunzip2", "xz", "unxz", "zip", "unzip",
            "cp", "mv", "rm", "mkdir", "touch", "ln", "file",
            "chmod", "chown", "chgrp",
            "echo", "printf", "tee",
            "awk", "sed", "cut", "sort", "uniq", "tr", "xargs",
            "ssh", "scp", "sftp", "rsync",
            "curl", "wget",
            "git", "svn",
            "java", "python", "python3", "node", "ruby", "perl",
            "make", "cmake", "gcc", "g++", "clang",
            "docker", "kubectl", "helm",
            "systemctl", "service", "journalctl",
            "apt", "apt-get", "yum", "dnf", "pacman", "zypper"
        ));
        
        // Windows 有返回命令
        hasOutputCommands.getWindows().addAll(List.of(
            "dir", "type", "more", "find", "findstr",
            "where", "whoami", "hostname", "date", "time",
            "tasklist", "taskkill", "schtasks",
            "netstat", "ipconfig", "ping", "tracert", "nslookup",
            "systeminfo", "ver", "vol", "chkdsk",
            "copy", "move", "del", "mkdir", "rmdir", "type",
            "echo", "set", "reg", "fc", "comp",
            "docker", "kubectl"
        ));
        
        // macOS 有返回命令（与 Linux 类似）
        hasOutputCommands.getMacos().addAll(hasOutputCommands.getLinux());
    }
    
    /**
     * 确保默认命令列表被初始化
     * 在反序列化后调用，确保配置完整
     */
    public void ensureDefaultCommands() {
        // 初始化有返回命令配置
        if (hasOutputCommands == null) {
            hasOutputCommands = new CommandOutputConfig();
        }
        hasOutputCommands.initDefaults();
        // 有返回命令需要额外添加默认列表
        // 检查是否需要初始化有返回命令（列表为空或缺少常见有返回命令）
        if (hasOutputCommands.getLinux().isEmpty() || !hasOutputCommands.getLinux().contains("ls")) {
            initDefaultHasOutputCommands();
        }
    }
    
    // ========== Getter/Setter ==========
    
    public List<ServerConfig> getServers() {
        return servers;
    }
    
    public void setServers(List<ServerConfig> servers) {
        this.servers = servers;
    }
    
    public List<PathConfig> getPaths() {
        return paths;
    }
    
    public void setPaths(List<PathConfig> paths) {
        this.paths = paths;
    }
    
    public AIConfig getAiConfig() {
        return aiConfig;
    }
    
    public void setAiConfig(AIConfig aiConfig) {
        this.aiConfig = aiConfig;
    }
    
    public BlacklistConfig getBlacklist() {
        return blacklist;
    }
    
    public void setBlacklist(BlacklistConfig blacklist) {
        this.blacklist = blacklist;
    }
    
    public KeywordRules getKeywordRules() {
        return keywordRules;
    }
    
    public void setKeywordRules(KeywordRules keywordRules) {
        this.keywordRules = keywordRules;
    }
    
    public List<CommandConfig> getCommandConfigs() {
        return commandConfigs;
    }
    
    public void setCommandConfigs(List<CommandConfig> commandConfigs) {
        this.commandConfigs = commandConfigs;
    }
    
    public String getLanguage() {
        return language;
    }
    
    public void setLanguage(String language) {
        this.language = language;
    }
    
    public String getLastSuccessfulServerId() {
        return lastSuccessfulServerId;
    }
    
    public void setLastSuccessfulServerId(String lastSuccessfulServerId) {
        this.lastSuccessfulServerId = lastSuccessfulServerId;
    }
    
    public String getLastSuccessfulCommandConfigId() {
        return lastSuccessfulCommandConfigId;
    }

    public void setLastSuccessfulCommandConfigId(String lastSuccessfulCommandConfigId) {
        this.lastSuccessfulCommandConfigId = lastSuccessfulCommandConfigId;
    }

    public String getLastSuccessfulPathId() {
        return lastSuccessfulPathId;
    }

    public void setLastSuccessfulPathId(String lastSuccessfulPathId) {
        this.lastSuccessfulPathId = lastSuccessfulPathId;
    }

    public String getLastSuccessfulTiming() {
        return lastSuccessfulTiming;
    }

    public void setLastSuccessfulTiming(String lastSuccessfulTiming) {
        this.lastSuccessfulTiming = lastSuccessfulTiming;
    }

    public CommandOutputConfig getHasOutputCommands() {
        return hasOutputCommands;
    }
    
    public void setHasOutputCommands(CommandOutputConfig hasOutputCommands) {
        this.hasOutputCommands = hasOutputCommands;
    }

    public List<BatchTask> getBatchTasks() {
        if (batchTasks == null) batchTasks = new ArrayList<>();
        return batchTasks;
    }

    public void setBatchTasks(List<BatchTask> batchTasks) {
        this.batchTasks = batchTasks;
    }

    public boolean isLogEnabled() {
        return logEnabled;
    }

    public void setLogEnabled(boolean logEnabled) {
        this.logEnabled = logEnabled;
    }
    
    // ========== 内部类 ==========
    
    /**
     * 黑名单配置
     * 支持按操作系统区分的黑名单命令
     */
    public static class BlacklistConfig {
        // 危险命令黑名单
        private List<String> universal = new ArrayList<>();
        private Map<String, List<String>> osSpecific = new HashMap<>();
        
        // 危险目录黑名单
        private List<String> universalPaths = new ArrayList<>();
        private Map<String, List<String>> osSpecificPaths = new HashMap<>();
        
        public BlacklistConfig() {
            // 设置默认的黑名单
            initDefaults();
        }
        
        /**
         * 初始化默认黑名单（从 OsBlacklistPresets 加载，确保单一数据源）
         */
        public void initDefaults() {
            // 危险命令黑名单
            this.osSpecific = new HashMap<>();
            this.osSpecific.put("linux", new ArrayList<>(OsBlacklistPresets.getPresetBlacklist(OperatingSystem.LINUX)));
            this.osSpecific.put("windows", new ArrayList<>(OsBlacklistPresets.getPresetBlacklist(OperatingSystem.WINDOWS)));
            this.osSpecific.put("macos", new ArrayList<>(OsBlacklistPresets.getPresetBlacklist(OperatingSystem.MACOS)));
            this.universal = new ArrayList<>(); // 通用命令黑名单已废弃，保持为空
            
            // 危险目录黑名单
            this.osSpecificPaths = new HashMap<>();
            this.osSpecificPaths.put("linux", new ArrayList<>(OsBlacklistPresets.getPresetDangerousPaths(OperatingSystem.LINUX)));
            this.osSpecificPaths.put("windows", new ArrayList<>(OsBlacklistPresets.getPresetDangerousPaths(OperatingSystem.WINDOWS)));
            this.osSpecificPaths.put("macos", new ArrayList<>(OsBlacklistPresets.getPresetDangerousPaths(OperatingSystem.MACOS)));
            this.universalPaths = new ArrayList<>(); // 通用危险目录已废弃，保持为空
        }
        
        /**
         * 从 OsBlacklistPresets 恢复完整的默认黑名单
         * 这会覆盖用户自定义的黑名单
         */
        public void restoreFromPresets() {
            // 恢复危险命令黑名单（按操作系统独立管理，通用黑名单已废弃）
            this.osSpecific = new HashMap<>();
            this.osSpecific.put("linux", new ArrayList<>(OsBlacklistPresets.getPresetBlacklist(OperatingSystem.LINUX)));
            this.osSpecific.put("windows", new ArrayList<>(OsBlacklistPresets.getPresetBlacklist(OperatingSystem.WINDOWS)));
            this.osSpecific.put("macos", new ArrayList<>(OsBlacklistPresets.getPresetBlacklist(OperatingSystem.MACOS)));
            this.universal = new ArrayList<>();
            
            // 恢复危险目录黑名单（按操作系统独立管理，通用目录已废弃）
            this.osSpecificPaths = new HashMap<>();
            this.osSpecificPaths.put("linux", new ArrayList<>(OsBlacklistPresets.getPresetDangerousPaths(OperatingSystem.LINUX)));
            this.osSpecificPaths.put("windows", new ArrayList<>(OsBlacklistPresets.getPresetDangerousPaths(OperatingSystem.WINDOWS)));
            this.osSpecificPaths.put("macos", new ArrayList<>(OsBlacklistPresets.getPresetDangerousPaths(OperatingSystem.MACOS)));
            this.universalPaths = new ArrayList<>();
        }
        
        /**
         * 只恢复指定操作系统的危险命令黑名单（不修改其他配置）
         * @param osKey 操作系统标识符（"linux" / "windows" / "macos"）
         */
        public void resetOsBlacklist(String osKey) {
            if (osKey == null) return;
            String key = osKey.toLowerCase();
            switch (key) {
                case "linux":
                    this.osSpecific.put("linux", new ArrayList<>(OsBlacklistPresets.getPresetBlacklist(OperatingSystem.LINUX)));
                    break;
                case "windows":
                    this.osSpecific.put("windows", new ArrayList<>(OsBlacklistPresets.getPresetBlacklist(OperatingSystem.WINDOWS)));
                    break;
                case "macos":
                    this.osSpecific.put("macos", new ArrayList<>(OsBlacklistPresets.getPresetBlacklist(OperatingSystem.MACOS)));
                    break;
            }
        }
        
        /**
         * 只恢复指定操作系统的危险目录黑名单（不修改其他配置）
         * @param osKey 操作系统标识符（"linux" / "windows" / "macos"）
         */
        public void resetOsDangerousPaths(String osKey) {
            if (osKey == null) return;
            String key = osKey.toLowerCase();
            switch (key) {
                case "linux":
                    this.osSpecificPaths.put("linux", new ArrayList<>(OsBlacklistPresets.getPresetDangerousPaths(OperatingSystem.LINUX)));
                    break;
                case "windows":
                    this.osSpecificPaths.put("windows", new ArrayList<>(OsBlacklistPresets.getPresetDangerousPaths(OperatingSystem.WINDOWS)));
                    break;
                case "macos":
                    this.osSpecificPaths.put("macos", new ArrayList<>(OsBlacklistPresets.getPresetDangerousPaths(OperatingSystem.MACOS)));
                    break;
            }
        }
        
        public List<String> getUniversal() {
            return universal;
        }
        
        public void setUniversal(List<String> universal) {
            this.universal = universal;
        }
        
        public Map<String, List<String>> getOsSpecific() {
            return osSpecific;
        }
        
        public void setOsSpecific(Map<String, List<String>> osSpecific) {
            this.osSpecific = osSpecific;
        }
        
        /**
         * 获取 Linux 黑名单
         */
        public List<String> getLinuxBlacklist() {
            return osSpecific.getOrDefault("linux", new ArrayList<>());
        }
        
        /**
         * 设置 Linux 黑名单
         */
        public void setLinuxBlacklist(List<String> linuxBlacklist) {
            this.osSpecific.put("linux", new ArrayList<>(linuxBlacklist));
        }
        
        /**
         * 获取 Windows 黑名单
         */
        public List<String> getWindowsBlacklist() {
            return osSpecific.getOrDefault("windows", new ArrayList<>());
        }
        
        /**
         * 设置 Windows 黑名单
         */
        public void setWindowsBlacklist(List<String> windowsBlacklist) {
            this.osSpecific.put("windows", new ArrayList<>(windowsBlacklist));
        }
        
        /**
         * 添加命令到指定操作系统的黑名单
         */
        public void addCommand(String osKey, String command) {
            if (command == null || command.trim().isEmpty()) return;
            osSpecific.computeIfAbsent(osKey, k -> new ArrayList<>()).add(command);
        }
        
        /**
         * 从指定操作系统的黑名单移除命令
         */
        public void removeCommand(String osKey, String command) {
            if (command == null || command.isEmpty()) return;
            List<String> list = osSpecific.get(osKey);
            if (list != null) {
                list.remove(command);
            }
        }
        
        // ========== 危险目录黑名单相关方法 ==========
        
        public List<String> getUniversalPaths() {
            return universalPaths;
        }
        
        public void setUniversalPaths(List<String> universalPaths) {
            this.universalPaths = universalPaths;
        }
        
        public Map<String, List<String>> getOsSpecificPaths() {
            return osSpecificPaths;
        }
        
        public void setOsSpecificPaths(Map<String, List<String>> osSpecificPaths) {
            this.osSpecificPaths = osSpecificPaths;
        }
        
        /**
         * 获取 Linux 危险目录黑名单
         */
        public List<String> getLinuxDangerousPaths() {
            return osSpecificPaths.getOrDefault("linux", new ArrayList<>());
        }
        
        /**
         * 设置 Linux 危险目录黑名单
         */
        public void setLinuxDangerousPaths(List<String> paths) {
            this.osSpecificPaths.put("linux", new ArrayList<>(paths));
        }
        
        /**
         * 获取 Windows 危险目录黑名单
         */
        public List<String> getWindowsDangerousPaths() {
            return osSpecificPaths.getOrDefault("windows", new ArrayList<>());
        }
        
        /**
         * 设置 Windows 危险目录黑名单
         */
        public void setWindowsDangerousPaths(List<String> paths) {
            this.osSpecificPaths.put("windows", new ArrayList<>(paths));
        }
        
        /**
         * 添加危险目录到指定操作系统的黑名单
         */
        public void addDangerousPath(String osKey, String path) {
            if (path == null || path.trim().isEmpty()) return;
            osSpecificPaths.computeIfAbsent(osKey, k -> new ArrayList<>()).add(path);
        }
        
        /**
         * 从指定操作系统的危险目录黑名单移除目录
         */
        public void removeDangerousPath(String osKey, String path) {
            if (path == null || path.isEmpty()) return;
            List<String> list = osSpecificPaths.get(osKey);
            if (list != null) {
                list.remove(path);
            }
        }
    }
    
    /**
     * 命令输出配置
     * 按操作系统分类的命令列表（无返回/有返回）
     */
    public static class CommandOutputConfig {
        /** Linux 无返回命令列表 */
        private List<String> linux;
        
        /** Windows 无返回命令列表 */
        private List<String> windows;
        
        /** macOS 无返回命令列表 */
        private List<String> macos;
        
        /** Ubuntu 无返回命令列表 */
        private List<String> ubuntu;
        
        /** Debian 无返回命令列表 */
        private List<String> debian;
        
        /** CentOS 无返回命令列表 */
        private List<String> centos;
        
        /** Rocky Linux 无返回命令列表 */
        private List<String> rocky;
        
        /** Alpine 无返回命令列表 */
        private List<String> alpine;
        
        /** RHEL 无返回命令列表 */
        private List<String> rhel;
        
        public CommandOutputConfig() {
            initDefaults();
        }
        
        /**
         * 初始化默认命令列表
         * 用于构造函数和反序列化后初始化
         */
        public void initDefaults() {
            // 默认的 Linux 无返回命令
            if (this.linux == null) {
                this.linux = new ArrayList<>(List.of(
                    "cd", "pushd", "popd", "export", "unset", "alias", "unalias",
                    "true", "exit", "read", "wait", "bg", "fg", "jobs", "kill",
                    "nohup", "sync", "umask", "ulimit", "source", "."
                ));
            }
            // 默认的 Windows 无返回命令
            if (this.windows == null) {
                this.windows = new ArrayList<>(List.of(
                    "cd", "chdir", "md", "mkdir", "rd", "rmdir", "del", "erase",
                    "copy", "move", "ren", "rename", "set", "setx", "exit", "call",
                    "prompt", "doskey"
                ));
            }
            // 默认的 macOS 无返回命令
            if (this.macos == null) {
                this.macos = new ArrayList<>(List.of(
                    "cd", "pushd", "popd", "export", "unset", "alias", "unalias",
                    "true", "exit", "read", "wait", "bg", "fg", "jobs", "kill",
                    "nohup", "sync", "umask", "ulimit", "source", "."
                ));
            }
            // 其他操作系统初始化为空列表（如果为空则使用 Linux 默认）
            if (this.ubuntu == null) this.ubuntu = new ArrayList<>();
            if (this.debian == null) this.debian = new ArrayList<>();
            if (this.centos == null) this.centos = new ArrayList<>();
            if (this.rocky == null) this.rocky = new ArrayList<>();
            if (this.alpine == null) this.alpine = new ArrayList<>();
            if (this.rhel == null) this.rhel = new ArrayList<>();
        }
        
        /**
         * 根据操作系统获取对应的命令列表
         */
        public List<String> getCommandsForOs(OperatingSystem os) {
            if (os == null) {
                return new ArrayList<>();
            }
            switch (os) {
                case UBUNTU:
                    return ubuntu.isEmpty() ? linux : ubuntu;
                case DEBIAN:
                    return debian.isEmpty() ? linux : debian;
                case CENTOS:
                    return centos.isEmpty() ? linux : centos;
                case ROCKY:
                    return rocky.isEmpty() ? linux : rocky;
                case ALPINE:
                    return alpine.isEmpty() ? linux : alpine;
                case RHEL:
                    return rhel.isEmpty() ? linux : rhel;
                case WINDOWS:
                    return windows;
                case MACOS:
                    return macos.isEmpty() ? linux : macos;
                default:
                    return linux;
            }
        }
        
        /**
         * 根据操作系统 key 获取对应的命令列表
         */
        public List<String> getCommandsForOsKey(String osKey) {
            if (osKey == null) {
                return new ArrayList<>();
            }
            String key = osKey.toLowerCase();
            switch (key) {
                case "ubuntu":
                    return ubuntu.isEmpty() ? linux : ubuntu;
                case "debian":
                    return debian.isEmpty() ? linux : debian;
                case "centos":
                    return centos.isEmpty() ? linux : centos;
                case "rocky":
                    return rocky.isEmpty() ? linux : rocky;
                case "alpine":
                    return alpine.isEmpty() ? linux : alpine;
                case "rhel":
                    return rhel.isEmpty() ? linux : rhel;
                case "windows":
                    return windows;
                case "macos":
                    return macos.isEmpty() ? linux : macos;
                default:
                    return linux;
            }
        }
        
        /**
         * 添加命令到指定操作系统列表
         */
        public void addCommand(OperatingSystem os, String command) {
            if (command == null || command.trim().isEmpty()) return;
            List<String> commands = getListForOs(os);
            if (commands != null && !commands.contains(command)) {
                commands.add(command);
            }
        }
        
        /**
         * 添加命令到指定操作系统列表（使用 osKey）
         */
        public void addCommand(String osKey, String command) {
            if (command == null || command.trim().isEmpty()) return;
            List<String> commands = getListForOsKey(osKey);
            if (commands != null && !commands.contains(command)) {
                commands.add(command);
            }
        }
        
        /**
         * 从指定操作系统列表移除命令
         */
        public void removeCommand(OperatingSystem os, String command) {
            if (command == null || command.isEmpty()) return;
            List<String> commands = getListForOs(os);
            if (commands != null) {
                commands.remove(command);
            }
        }
        
        /**
         * 从指定操作系统列表移除命令（使用 osKey）
         */
        public void removeCommand(String osKey, String command) {
            if (command == null || command.isEmpty()) return;
            List<String> commands = getListForOsKey(osKey);
            if (commands != null) {
                commands.remove(command);
            }
        }
        
        /**
         * 清空指定操作系统的命令列表
         */
        public void clearCommands(String osKey) {
            if (osKey == null) return;
            String key = osKey.toLowerCase();
            switch (key) {
                case "ubuntu":
                    ubuntu.clear();
                    break;
                case "debian":
                    debian.clear();
                    break;
                case "centos":
                    centos.clear();
                    break;
                case "rocky":
                    rocky.clear();
                    break;
                case "alpine":
                    alpine.clear();
                    break;
                case "rhel":
                    rhel.clear();
                    break;
                case "windows":
                    windows.clear();
                    break;
                case "macos":
                    macos.clear();
                    break;
                default:
                    linux.clear();
                    break;
            }
        }
        
        private List<String> getListForOs(OperatingSystem os) {
            if (os == null) return null;
            switch (os) {
                case UBUNTU:
                    return ubuntu;
                case DEBIAN:
                    return debian;
                case CENTOS:
                    return centos;
                case ROCKY:
                    return rocky;
                case ALPINE:
                    return alpine;
                case RHEL:
                    return rhel;
                case WINDOWS:
                    return windows;
                case MACOS:
                    return macos;
                default:
                    return linux;
            }
        }
        
        /**
         * 根据 osKey 获取对应的命令列表
         * @param osKey 操作系统标识符（不区分大小写）
         * @return 对应的命令列表，如果未找到则返回 linux 列表
         */
        private List<String> getListForOsKey(String osKey) {
            return getListForOs(OperatingSystem.fromKey(osKey));
        }
        
        // Getter/Setter
        public List<String> getLinux() { return linux; }
        public void setLinux(List<String> linux) { this.linux = linux; }
        
        public List<String> getWindows() { return windows; }
        public void setWindows(List<String> windows) { this.windows = windows; }
        
        public List<String> getMacos() { return macos; }
        public void setMacos(List<String> macos) { this.macos = macos; }
        
        public List<String> getUbuntu() { return ubuntu; }
        public void setUbuntu(List<String> ubuntu) { this.ubuntu = ubuntu; }
        
        public List<String> getDebian() { return debian; }
        public void setDebian(List<String> debian) { this.debian = debian; }
        
        public List<String> getCentos() { return centos; }
        public void setCentos(List<String> centos) { this.centos = centos; }
        
        public List<String> getRocky() { return rocky; }
        public void setRocky(List<String> rocky) { this.rocky = rocky; }
        
        public List<String> getAlpine() { return alpine; }
        public void setAlpine(List<String> alpine) { this.alpine = alpine; }
        
        public List<String> getRhel() { return rhel; }
        public void setRhel(List<String> rhel) { this.rhel = rhel; }
    }
}
