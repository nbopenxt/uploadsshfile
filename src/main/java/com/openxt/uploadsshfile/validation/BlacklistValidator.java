package com.openxt.uploadsshfile.validation;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.OperatingSystem;
import com.openxt.uploadsshfile.model.OsBlacklistPresets;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import com.openxt.uploadsshfile.model.ValidationResult;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 黑名单校验器
 * 支持精确匹配和模糊匹配（通配符）
 */
public class BlacklistValidator {
    
    private final LanguageManager lang = LanguageManager.getInstance();
    private final UnifiedConfigStore configStore;
    
    /** 编译后的正则缓存 */
    private final java.util.Map<String, Pattern> patternCache;
    
    public BlacklistValidator() {
        this(UnifiedConfigStore.getInstance());
    }
    
    public BlacklistValidator(@NotNull UnifiedConfigStore configStore) {
        this.configStore = configStore;
        this.patternCache = new java.util.HashMap<>();
    }
    
    /**
     * 获取生效的黑名单配置
     */
    private UnifiedPluginConfig.BlacklistConfig getBlacklistConfig() {
        return configStore.getBlacklist();
    }
    
    /**
     * 校验命令是否在黑名单中（仅全局黑名单）
     */
    public @NotNull ValidationResult validate(@NotNull String command) {
        return validate(command, null, (String) null);
    }
    
    /**
     * 校验命令是否在黑名单中（包含操作系统黑名单）
     * 
     * @param command 要校验的命令
     * @param serverId 服务器 ID
     * @param osType 操作系统类型（可选，用于 OS 特定黑名单）
     */
    public @NotNull ValidationResult validate(@NotNull String command, @Nullable String serverId, @Nullable String osType) {
        // 获取生效的黑名单列表
        List<String> effectiveBlacklist = getEffectiveBlacklist(osType);
        
        for (String blockedPattern : effectiveBlacklist) {
            if (matches(command, blockedPattern)) {
                return ValidationResult.blocked(
                    lang.get("validation.blacklist.blocked", blockedPattern)
                );
            }
        }
        
        return ValidationResult.allowed();
    }
    
    /**
     * 校验命令是否在黑名单中（使用操作系统对象）
     */
    public @NotNull ValidationResult validate(@NotNull String command, @Nullable String serverId, @Nullable OperatingSystem os) {
        return validate(command, serverId, os != null ? os.getKey() : null);
    }
    
    /**
     * 获取生效的黑名单列表（OS 预设 + 用户配置）
     */
    private List<String> getEffectiveBlacklist(@Nullable String osType) {
        List<String> result = new ArrayList<>();
        
        // 将字符串转换为 OperatingSystem 枚举
        OperatingSystem os = osType != null ? OperatingSystem.fromKey(osType) : null;
        
        // 获取黑名单配置
        UnifiedPluginConfig.BlacklistConfig config = getBlacklistConfig();
        
        // 1. 添加用户配置的通用黑名单
        result.addAll(config.getUniversal());
        
        // 2. 根据 OS 类型添加系统特定黑名单
        if (os != null && os.isUnixLike()) {
            result.addAll(config.getLinuxBlacklist());
            result.addAll(OsBlacklistPresets.getPresetBlacklist(os));
        } else if (os != null && os.isWindows()) {
            result.addAll(config.getWindowsBlacklist());
            result.addAll(OsBlacklistPresets.getPresetBlacklist(os));
        } else {
            // 未指定 OS 或无法识别，添加 Linux 和 Windows 黑名单
            result.addAll(config.getLinuxBlacklist());
            result.addAll(config.getWindowsBlacklist());
        }
        
        return result;
    }
    
    /**
     * 通配符匹配
     * 支持: * (任意字符), ? (单个字符)
     */
    private boolean matches(@NotNull String command, @NotNull String pattern) {
        // 精确匹配
        if (command.equals(pattern)) {
            return true;
        }
        
        // 通配符匹配
        if (pattern.contains("*") || pattern.contains("?")) {
            String regex = wildcardToRegex(pattern);
            Pattern compiled = patternCache.computeIfAbsent(
                pattern, 
                p -> Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
            );
            return compiled.matcher(command).matches();
        }
        
        // 忽略大小写的包含匹配
        return command.toLowerCase().contains(pattern.toLowerCase());
    }
    
    /**
     * 将通配符转换为正则表达式
     */
    private @NotNull String wildcardToRegex(@NotNull String wildcard) {
        StringBuilder sb = new StringBuilder();
        for (char c : wildcard.toCharArray()) {
            switch (c) {
                case '*':
                    sb.append(".*");
                    break;
                case '?':
                    sb.append(".");
                    break;
                case '(':
                case ')':
                case '[':
                case ']':
                case '{':
                case '}':
                case '\\':
                case '^':
                case '$':
                case '|':
                case '+':
                case '.':
                    sb.append("\\").append(c);
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }
    
    /**
     * 刷新缓存（当黑名单更新时调用）
     */
    public void invalidateCache() {
        patternCache.clear();
    }
}
