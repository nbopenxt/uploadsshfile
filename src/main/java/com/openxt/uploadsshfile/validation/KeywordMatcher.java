package com.openxt.uploadsshfile.validation;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.model.EvaluationResult;
import com.openxt.uploadsshfile.model.KeywordRules;
import com.openxt.uploadsshfile.model.OperatingSystem;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 关键词匹配器
 * 只判断失败（成功关键词已移除，exitcode=0 即为成功）
 * 
 * 判断优先级：
 * 1. 命令返回值（exit code != 0 → 失败）
 * 2. stderr 非空 → 失败
 * 3. 输出中是否包含失败关键词
 */
public class KeywordMatcher {
    
    private final LanguageManager lang = LanguageManager.getInstance();
    private final UnifiedConfigStore configStore;
    
    /** 编译后的模式缓存 */
    private final Map<String, Pattern> failPatternCache;
    
    public KeywordMatcher() {
        this(UnifiedConfigStore.getInstance());
    }
    
    public KeywordMatcher(@NotNull UnifiedConfigStore configStore) {
        this.configStore = configStore;
        this.failPatternCache = new HashMap<>();
    }
    
    /**
     * 获取关键词规则
     */
    private KeywordRules getKeywordRules() {
        return configStore.getKeywordRules();
    }
    
    /**
     * 评估命令执行结果
     * 
     * 判断优先级：
     * 1. 命令返回值（exit code != 0 → 失败）
     * 2. 输出中是否包含失败关键词
     */
    public @NotNull EvaluationResult evaluate(@NotNull CommandResult result, @Nullable OperatingSystem osType) {
        // 优先级1: 返回码检查
        if (result.getExitCode() != 0) {
            return EvaluationResult.failed(
                lang.get("validation.exitcode", result.getExitCode()),
                result.getExitCode()
            );
        }

        if(!result.getStderr().isEmpty()){
            return EvaluationResult.failed(
                result.getStderr(),
                result.getExitCode()
            );
        }
        
        // 针对 stdout 进行分析
        String output = result.getStdout() + "\n" + result.getStderr();
        
        // 获取生效的关键词规则
        KeywordRules rules = getKeywordRules();
        List<String> failKeywords = rules.getEffectiveFailKeywords(osType);
        
        // 优先级2: 失败关键词检查
        for (String keyword : failKeywords) {
            if (containsKeyword(output, keyword, failPatternCache)) {
                return EvaluationResult.failed(
                    lang.get("validation.failKeyword", keyword),
                    result.getExitCode()
                );
            }
        }
        
        return EvaluationResult.success(result.getExitCode());
    }
    
    /**
     * 评估命令执行结果（使用默认操作系统类型）
     */
    public @NotNull EvaluationResult evaluate(@NotNull CommandResult result) {
        return evaluate(result, null);
    }
    
    /**
     * 检查输出是否包含关键词（忽略大小写）
     * 使用子串匹配：关键词是输出的子串即算匹配
     */
    private boolean containsKeyword(@NotNull String output, @NotNull String keyword, 
                                     @NotNull Map<String, Pattern> cache) {
        Pattern pattern = cache.computeIfAbsent(
            keyword,
            k -> Pattern.compile(Pattern.quote(k), Pattern.CASE_INSENSITIVE)
        );
        return pattern.matcher(output.toLowerCase()).find();
    }
    
    /**
     * 刷新缓存
     */
    public void invalidateCache() {
        failPatternCache.clear();
    }
}
