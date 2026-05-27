package com.openxt.uploadsshfile.validation;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.OsBlacklistPresets;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig.BlacklistConfig;
import com.openxt.uploadsshfile.model.ValidationResult;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 语义分析黑名单检查器
 * 
 * 根据用户配置的黑名单检测危险命令和危险目录。
 * 黑名单从配置文件加载，支持按操作系统区分。
 * 
 * <b>检测流程</b>：
 * 1. 首先匹配用户黑名单（命令 + 系统目录）→ BLOCKED
 * 2. 未匹配则由 AI 或直接放行（在 CommandOrchestrator 中处理）
 * 
 * <b>用户可配置</b>：
 * <ul>
 *   <li>危险命令黑名单（按操作系统区分）</li>
 *   <li>危险目录黑名单（按操作系统区分）</li>
 *   <li>恢复系统默认黑名单设置</li>
 * </ul>
 */
public class SemanticBlacklistChecker {
    
    private final LanguageManager lang = LanguageManager.getInstance();
    private BlacklistConfig blacklistConfig;
    
    /** 用户黑名单缓存（按操作系统） */
    private volatile Map<String, Set<String>> userBlacklistCache;
    /** 最后加载配置版本 */
    private volatile long lastConfigVersion = -1;
    
    /** 危险命令关键词 */
    private static final Set<String> DANGEROUS_COMMANDS = new HashSet<>(Arrays.asList(
        "rm", "del", "rd", "format", "mkfs", "fdisk", "dd",
        "shutdown", "reboot", "halt", "init",
        "iptables", "firewall-cmd", "ufw",
        "useradd", "userdel", "usermod", "passwd",
        "chmod", "chown", "chgrp",
        "pkill", "killall", "kill",
        "docker", "kubectl",
        "crontab", "at"
    ));
    
    /**
     * 默认构造函数
     * 从配置文件加载用户黑名单
     */
    public SemanticBlacklistChecker() {
        this(null);
    }
    
    /**
     * 构造函数（支持注入配置）
     * @param config 黑名单配置，为 null 时从配置文件加载
     */
    public SemanticBlacklistChecker(@Nullable BlacklistConfig config) {
        if (config != null) {
            this.blacklistConfig = config;
        } else {
            this.blacklistConfig = UnifiedConfigStore.getInstance().getBlacklist();
        }
        this.userBlacklistCache = new HashMap<>();
    }
    
    /**
     * 从配置文件重新加载黑名单
     */
    public void reloadBlacklist() {
        BlacklistConfig fresh = UnifiedConfigStore.getInstance().getBlacklist();
        synchronized (this) {
            this.blacklistConfig = fresh;
            this.userBlacklistCache.clear();
            this.lastConfigVersion = -1;
        }
    }
    
    /**
     * 获取指定操作系统的用户黑名单
     */
    private Set<String> getUserBlacklist(@Nullable String osType) {
        if (blacklistConfig == null) {
            return Collections.emptySet();
        }
        
        // 检查是否需要刷新缓存
        long currentVersion = System.identityHashCode(blacklistConfig);
        if (currentVersion != lastConfigVersion) {
            synchronized (this) {
                if (currentVersion != lastConfigVersion) {
                    userBlacklistCache.clear();
                    lastConfigVersion = currentVersion;
                }
            }
        }
        
        String osKey = normalizeOsType(osType);
        
        // 先检查缓存
        if (userBlacklistCache.containsKey(osKey)) {
            return userBlacklistCache.get(osKey);
        }
        
        // 构建黑名单集合
        Set<String> blacklist = new HashSet<>();
        
        // 添加通用黑名单
        if (blacklistConfig.getUniversal() != null) {
            blacklist.addAll(blacklistConfig.getUniversal());
        }
        
        // 添加指定操作系统的黑名单
        Map<String, List<String>> osSpecific = blacklistConfig.getOsSpecific();
        if (osSpecific != null) {
            // 添加操作系统特定黑名单
            List<String> osBlacklist = osSpecific.get(osKey);
            if (osBlacklist != null) {
                blacklist.addAll(osBlacklist);
            }
            
            // Linux/Unix 系列共享黑名单
            if ("linux".equals(osKey) || "unix".equals(osKey)) {
                // 尝试添加其他 Linux 发行版特有的黑名单
                addOsSpecificBlacklist(blacklist, osSpecific, "ubuntu");
                addOsSpecificBlacklist(blacklist, osSpecific, "debian");
                addOsSpecificBlacklist(blacklist, osSpecific, "centos");
                addOsSpecificBlacklist(blacklist, osSpecific, "rocky");
                addOsSpecificBlacklist(blacklist, osSpecific, "alpine");
                addOsSpecificBlacklist(blacklist, osSpecific, "rhel");
            }
        }
        
        // 缓存结果
        userBlacklistCache.put(osKey, Collections.unmodifiableSet(blacklist));
        return blacklist;
    }
    
    /**
     * 规范化操作系统类型
     */
    private String normalizeOsType(@Nullable String osType) {
        if (osType == null || osType.isEmpty()) {
            return "linux"; // 默认 Linux
        }
        String lower = osType.toLowerCase();
        if (lower.contains("windows")) {
            return "windows";
        } else if (lower.contains("macos") || lower.contains("darwin")) {
            return "macos";
        } else if (lower.contains("linux")) {
            return "linux";
        } else if (lower.contains("unix")) {
            return "unix";
        }
        return "linux";
    }
    
    /**
     * 添加特定操作系统的黑名单
     */
    private void addOsSpecificBlacklist(Set<String> blacklist, Map<String, List<String>> osSpecific, String osKey) {
        List<String> specific = osSpecific.get(osKey);
        if (specific != null) {
            blacklist.addAll(specific);
        }
    }
    
    /** 危险选项关键词 */
    private static final Set<String> DANGEROUS_OPTIONS = new HashSet<>(Arrays.asList(
        "-rf", "-r", "-f", "/s", "/q", "/f",
        "--force", "--recursive", "-y", "-yes"
    ));
    
    /** 系统关键目录模式 */
    private static final List<Pattern> SYSTEM_PATH_PATTERNS = Arrays.asList(
        Pattern.compile("^\\s*/\\s*$"),                           // 根目录 /
        Pattern.compile("^\\s*/etc\\b"),                          // /etc
        Pattern.compile("^\\s*/var\\b"),                          // /var
        Pattern.compile("^\\s*/usr\\b"),                          // /usr
        Pattern.compile("^\\s*/boot\\b"),                         // /boot
        Pattern.compile("^\\s*/sys\\b"),                          // /sys
        Pattern.compile("^\\s*/proc\\b"),                         // /proc
        Pattern.compile("^\\s*/dev\\b"),                          // /dev
        Pattern.compile("^\\s*C:\\\\Windows\\\\?", Pattern.CASE_INSENSITIVE),  // C:\Windows
        Pattern.compile("^\\s*C:\\\\Program\\s+Files", Pattern.CASE_INSENSITIVE), // C:\Program Files
        Pattern.compile("^\\s*/root\\b")                           // /root
    );
    
    /** 危险操作模式 */
    private static final List<Pattern> DANGEROUS_OPERATION_PATTERNS = Arrays.asList(
        // 递归删除
        Pattern.compile("\\brm\\s+(-[rfv]+|\\s+.*-rf)"),
        Pattern.compile("\\bdel\\s+(/s|/q|/e|/f)"),
        Pattern.compile("\\brmdir\\s+/s"),
        
        // 管道注入
        Pattern.compile("\\|\\s*(sh|bash|cmd|powershell|perl|python|ruby)\\s+-"),
        Pattern.compile("`[^`]+`"),
        
        // 危险下载执行
        Pattern.compile("\\bwget\\s+[^|]+\\|\\s*(sh|bash)"),
        Pattern.compile("\\bcurl\\s+[^|]+\\|\\s*(sh|bash)"),
        Pattern.compile("\\bcurl\\s+.*\\s+-o\\s+/tmp/"),
        
        // fork炸弹
        Pattern.compile(":\\s*\\(\\s*\\)\\s*\\{.*:.*\\|.*:.*\\&.*\\}"),
        Pattern.compile("fork\\s*;\\s*fork"),
        
        // 后台执行
        Pattern.compile("\\bnohup\\s+.*&\\s*$"),
        Pattern.compile("screen\\s+-dm"),
        Pattern.compile("tmux\\s+new-session\\s+-d"),
        
        // 系统修改
        Pattern.compile("sysctl\\s+-w\\s+.*="),
        Pattern.compile("sysctl\\s+-p"),
        Pattern.compile("iptables\\s+-[AI]"),
        Pattern.compile("firewall-cmd\\s+--add"),
        
        // 权限操作
        Pattern.compile("chmod\\s+[47]\\d{3}"),
        Pattern.compile("chmod\\s+777"),
        Pattern.compile("chown\\s+root:root"),
        Pattern.compile("chmod\\s+u\\+s"),
        Pattern.compile("chmod\\s+s"),
        
        // 用户操作
        Pattern.compile("useradd\\s+"),
        Pattern.compile("userdel\\s+"),
        Pattern.compile("usermod\\s+"),
        Pattern.compile("passwd\\s+root"),
        Pattern.compile("echo\\s+.*password.*\\|\\s*passwd"),
        
        // 容器操作
        Pattern.compile("docker\\s+run\\s+.*--privileged"),
        Pattern.compile("docker\\s+exec\\s+.*/bin/(sh|bash)"),
        Pattern.compile("kubectl\\s+exec\\s+.*-it\\s+.*--\\s*/bin/(sh|bash)"),
        
        // crontab操作
        Pattern.compile("crontab\\s+-[lr]"),
        Pattern.compile("crontab\\s+<<\\s*<")
    );
    
    /** 低风险但需确认的操作模式 */
    private static final List<Pattern> CAUTION_OPERATION_PATTERNS = Arrays.asList(
        // 文件操作
        Pattern.compile("\\bcp\\s+.*-r\\b"),
        Pattern.compile("\\bmv\\s+.*/"),
        Pattern.compile("\\btar\\s+.*cvf"),
        
        // 网络操作
        Pattern.compile("\\bcurl\\s+"),
        Pattern.compile("\\bwget\\s+"),
        Pattern.compile("\\bscp\\s+"),
        Pattern.compile("\\brsync\\s+"),
        
        // 服务操作
        Pattern.compile("\\bsystemctl\\s+(start|stop|restart|enable|disable)\\s+"),
        Pattern.compile("\\bservice\\s+"),
        
        // 编辑操作
        Pattern.compile("\\bsed\\s+-[ei]"),
        Pattern.compile("\\bawk\\s+"),
        Pattern.compile("\\bsudo\\s+")
    );
    
    /**
     * 校验命令（不带操作系统信息，默认使用 Linux）
     * @param command 待校验的命令
     * @return 校验结果：匹配用户黑名单返回 BLOCKED，否则返回 ALLOWED
     */
    public @NotNull ValidationResult validate(@NotNull String command) {
        return validate(command, "linux");
    }
    
    /**
     * 校验命令（带操作系统信息）
     * <p>
     * 只检查用户自定义黑名单（危险命令 + 危险目录）。
     * 如果启用 AI，增强判断在 CommandOrchestrator 中处理。
     * </p>
     * @param command 待校验的命令
     * @param osType 操作系统类型（linux/windows/macos）
     * @return 校验结果：匹配用户黑名单返回 BLOCKED，否则返回 ALLOWED
     */
    public @NotNull ValidationResult validate(@NotNull String command, @Nullable String osType) {
        String trimmedCommand = command.trim();
        
        // 1. 检查用户自定义危险命令黑名单
        ValidationResult commandResult = checkUserBlacklist(trimmedCommand, osType);
        if (commandResult != null) {
            return commandResult;
        }
        
        // 2. 检查用户自定义危险目录黑名单
        ValidationResult pathResult = checkUserPathBlacklist(trimmedCommand, osType);
        if (pathResult != null) {
            return pathResult;
        }
        
        // 未匹配用户黑名单，返回 ALLOWED（AI 判断或放行在 CommandOrchestrator 中处理）
        return ValidationResult.allowed();
    }
    
    /**
     * 检查用户自定义危险命令黑名单
     */
    private ValidationResult checkUserBlacklist(@NotNull String command, @Nullable String osType) {
        Set<String> blacklist = getUserCommandBlacklist(osType);
        String lowerCommand = command.toLowerCase();
        
        for (String blocked : blacklist) {
            if (blocked != null && !blocked.isEmpty()) {
                // 检查命令是否包含黑名单项（不区分大小写）
                if (lowerCommand.contains(blocked.toLowerCase())) {
                    return ValidationResult.blocked(
                        lang.get("validation.semantic.userBlacklist", blocked)
                    );
                }
            }
        }
        return null;
    }
    
    /**
     * 检查用户自定义危险目录黑名单
     */
    private ValidationResult checkUserPathBlacklist(@NotNull String command, @Nullable String osType) {
        Set<String> pathBlacklist = getUserPathBlacklist(osType);
        
        // 提取命令中的所有路径（支持空格分隔的多个路径）
        String[] parts = command.split("\\s+");
        
        for (String part : parts) {
            // 跳过选项参数（如 -rf, /S 等）
            if (part.startsWith("-") || part.startsWith("/")) {
                // 检查路径是否以危险目录开头（前缀匹配）
                for (String blockedPath : pathBlacklist) {
                    if (blockedPath != null && !blockedPath.isEmpty()) {
                        String lowerBlockedPath = blockedPath.toLowerCase();
                        String lowerPart = part.toLowerCase();
                        // 匹配条件：路径以危险目录开头，且后面是 / 或 字符串结束
                        if (lowerPart.startsWith(lowerBlockedPath) 
                            && (lowerPart.length() == lowerBlockedPath.length() 
                                || lowerPart.charAt(lowerBlockedPath.length()) == '/')) {
                            return ValidationResult.blocked(
                                lang.get("validation.semantic.userBlacklistPath", blockedPath)
                            );
                        }
                    }
                }
            }
        }
        return null;
    }
    
    /**
     * 获取指定操作系统的用户危险命令黑名单
     * 
     * 用户黑名单是最高判断标准：
     * - 如果用户配置为空，不做任何拦截
     * - 只有用户明确配置的黑名单才生效
     */
    private Set<String> getUserCommandBlacklist(@Nullable String osType) {
        BlacklistConfig config = blacklistConfig;
        if (config == null) {
            return Collections.emptySet();
        }
        
        // 检查用户是否配置了任何黑名单
        // 如果 universal 和 osSpecific 都为空/null，表示用户未配置任何黑名单
        List<String> universalList = config.getUniversal();
        Map<String, List<String>> osSpecificMap = config.getOsSpecific();
        
        boolean hasUserConfig = (universalList != null && !universalList.isEmpty())
            || (osSpecificMap != null && !osSpecificMap.isEmpty());
        
        // 用户未配置任何黑名单，不做拦截
        if (!hasUserConfig) {
            return Collections.emptySet();
        }
        
        String osKey = normalizeOsType(osType);
        Set<String> commands = new HashSet<>();
        
        // 添加用户配置的通用危险命令
        if (universalList != null) {
            commands.addAll(universalList);
        }
        
        // 添加用户配置的操作系统特定危险命令
        if (osSpecificMap != null) {
            List<String> osCommands = osSpecificMap.get(osKey);
            if (osCommands != null) {
                commands.addAll(osCommands);
            }
        }
        
        return commands;
    }
    
    /**
     * 获取指定操作系统的用户危险目录黑名单
     * 
     * 用户黑名单是最高判断标准：
     * - 如果用户配置为空，不做任何拦截
     * - 只有用户明确配置的黑名单才生效
     */
    private Set<String> getUserPathBlacklist(@Nullable String osType) {
        BlacklistConfig config = blacklistConfig;
        if (config == null) {
            return Collections.emptySet();
        }
        
        String osKey = normalizeOsType(osType);
        
        // 检查用户是否配置了目录黑名单（只检查当前操作系统的配置）
        List<String> universalPathsList = config.getUniversalPaths();
        Map<String, List<String>> osSpecificPathsMap = config.getOsSpecificPaths();
        List<String> osPaths = (osSpecificPathsMap != null) ? osSpecificPathsMap.get(osKey) : null;
        
        boolean hasUserConfig = (universalPathsList != null && !universalPathsList.isEmpty())
            || (osPaths != null && !osPaths.isEmpty());
        
        // 用户未配置任何目录黑名单，不做拦截
        if (!hasUserConfig) {
            return Collections.emptySet();
        }
        
        Set<String> paths = new HashSet<>();
        
        // 添加用户配置的通用危险目录
        if (universalPathsList != null) {
            paths.addAll(universalPathsList);
        }
        
        // 添加用户配置的操作系统特定危险目录
        if (osPaths != null) {
            paths.addAll(osPaths);
        }
        
        return paths;
    }
    
    /**
     * 简单分词
     */
    private String[] tokenize(String command) {
        return command.split("\\s+");
    }
    
    /**
     * 检查是否包含危险目标
     */
    private boolean containsDangerousTarget(String command) {
        String lower = command.toLowerCase();
        return lower.contains("/home") || 
               lower.contains("/tmp") ||
               lower.contains("/var/log") ||
               lower.contains("/etc/passwd") ||
               lower.contains("/etc/shadow") ||
               lower.contains("c:\\users") ||
               lower.contains("c:\\program files");
    }
    
    /**
     * 提取路径
     */
    private String extractPath(String command, Pattern pattern) {
        java.util.regex.Matcher matcher = pattern.matcher(command);
        if (matcher.find()) {
            return matcher.group();
        }
        return command;
    }
    
    /**
     * 获取操作描述
     */
    private String getOperationDescription(Pattern pattern, String command) {
        if (command.contains("rm -rf") || command.contains("rm -r")) {
            return lang.get("semantic.op.recursive.delete");
        } else if (command.contains("| sh") || command.contains("| bash")) {
            return lang.get("semantic.op.pipe.shell");
        } else if (command.contains("wget") || command.contains("curl")) {
            return lang.get("semantic.op.download.execute");
        } else if (command.contains("fork") || command.contains(":()")) {
            return lang.get("semantic.op.fork.bomb");
        } else if (command.contains("nohup")) {
            return lang.get("semantic.op.background");
        } else if (command.contains("iptables") || command.contains("firewall")) {
            return lang.get("semantic.op.firewall");
        } else if (command.contains("chmod 777") || command.contains("chmod +s")) {
            return lang.get("semantic.op.dangerous.permission");
        } else if (command.contains("useradd") || command.contains("userdel")) {
            return lang.get("semantic.op.user.management");
        } else if (command.contains("docker") && command.contains("--privileged")) {
            return lang.get("semantic.op.privileged.container");
        } else {
            return lang.get("semantic.op.dangerous.system");
        }
    }
    
    /**
     * 获取需确认操作描述
     */
    private String getCautionDescription(Pattern pattern, String command) {
        if (command.contains("cp -r") || command.contains("cp -a")) {
            return lang.get("semantic.caution.recursive.copy");
        } else if (command.contains("mv")) {
            return lang.get("semantic.caution.move");
        } else if (command.contains("curl") || command.contains("wget")) {
            return lang.get("semantic.caution.download");
        } else if (command.contains("systemctl") || command.contains("service")) {
            return lang.get("semantic.caution.service");
        } else if (command.contains("sed") || command.contains("awk")) {
            return lang.get("semantic.caution.text.processing");
        } else if (command.contains("sudo")) {
            return lang.get("semantic.caution.sudo");
        } else {
            return lang.get("semantic.caution.general");
        }
    }
}
