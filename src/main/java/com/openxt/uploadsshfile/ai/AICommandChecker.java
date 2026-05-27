package com.openxt.uploadsshfile.ai;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.AIConfig;
import com.openxt.uploadsshfile.model.OperatingSystem;
import com.openxt.uploadsshfile.model.ValidationResult;
import com.openxt.uploadsshfile.store.StoreManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * AI 命令执行校验器
 * 
 * <b>功能</b>：
 * 1. 命令执行前 AI 风险分析（enablePreExecutionCheck）
 * 2. AI 黑名单检查 - 使用 AI 模型判断命令是否属于黑名单（enableAiBlacklistCheck）
 * 
 * <b>与黑名单的区别</b>：黑名单是强制拦截，AI校验是建议性质，由用户决定是否执行
 * <b>未接入AI时</b>：返回空字符串，不影响正常流程
 * 
 * 使用统一配置存储 plugin-config.json
 */
public class AICommandChecker {
    
    private final LanguageManager lang = LanguageManager.getInstance();
    private final UnifiedConfigStore configStore;
    private final AIService aiService;
    
    public AICommandChecker() {
        this(StoreManager.getInstance().getUnifiedConfigStore(), null);
    }
    
    public AICommandChecker(@NotNull UnifiedConfigStore configStore, AIService aiService) {
        this.configStore = configStore;
        this.aiService = aiService;
    }
    
    /**
     * 获取 AI 配置
     */
    private AIConfig getAIConfig() {
        return configStore.getConfig().getAiConfig();
    }
    
    /**
     * AI 校验命令执行
     * 
     * @param command 要执行的命令
     * @return 空字符串表示可以执行；非空字符串表示不建议执行，包含原因
     */
    public @NotNull String checkCommand(@NotNull String command) {
        // 检查是否启用 AI 校验
        AIConfig config = getAIConfig();
        if (!config.isEnabled() || !config.isEnablePreExecutionCheck()) {
            return "";
        }
        
        // 未接入 AI 或服务不可用时，返回空字符串（允许执行）
        if (aiService == null || !aiService.isAvailable()) {
            return "";
        }
        
        try {
            // 调用 AI 服务进行命令分析
            AIAnalysisResult result = aiService.analyzeCommand(command);
            
            if (result.isRisky()) {
                // 返回风险原因和建议
                return formatRiskMessage(command, result);
            }
            
            return ""; // 无风险
        } catch (Exception e) {
            // AI 服务异常时，返回空字符串（不阻断执行）
            return "";
        }
    }
    
    /**
     * AI 黑名单检查 - 使用 AI 模型判断命令是否属于危险命令
     * 
     * <b>启用条件</b>：AI 配置已启用且 enableAiBlacklistCheck 为 true
     * <b>返回结果</b>：
     * - allowed: 命令不在黑名单中
     * - blocked: 命令在黑名单中，强制拦截
     * - caution: 命令有风险但不是强制拦截，需用户确认
     * 
     * @param command 要检查的命令
     * @return 校验结果
     */
    public @NotNull ValidationResult checkBlacklist(@NotNull String command) {
        // 检查是否启用 AI 黑名单检查
        AIConfig config = getAIConfig();
        if (!config.isEnabled() || !config.isEnableAiBlacklistCheck()) {
            return ValidationResult.allowed();
        }
        
        // 未接入 AI 或服务不可用时，返回允许（由其他检查器处理）
        if (aiService == null || !aiService.isAvailable()) {
            return ValidationResult.allowed();
        }
        
        try {
            // 调用 AI 服务进行黑名单判断
            AIBlacklistResult result = aiService.analyzeBlacklist(command);
            
            if (result.isBlacklisted()) {
                // 命令在黑名单中，强制拦截
                return ValidationResult.blocked(formatBlacklistMessage(result));
            }
            
            if (result.isRisky()) {
                // 命令有风险但不是强制拦截，需用户确认
                return ValidationResult.caution(formatRiskMessage(command, result));
            }
            
            return ValidationResult.allowed();
        } catch (Exception e) {
            // AI 服务异常时，返回允许（不阻断执行）
            return ValidationResult.allowed();
        }
    }
    
    /**
     * 检查是否启用了 AI 黑名单检查
     */
    public boolean isAiBlacklistCheckEnabled() {
        AIConfig config = getAIConfig();
        return config.isEnabled() && config.isEnableAiBlacklistCheck();
    }
    
    /**
     * 格式化风险提示信息
     */
    private @NotNull String formatRiskMessage(@NotNull String command, @NotNull AIAnalysisResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(lang.get("ai.risk.title")).append("\n");
        
        for (String risk : result.getRisks()) {
            sb.append("- ").append(risk).append("\n");
        }
        
        sb.append("\n").append(lang.get("ai.suggestion.title")).append("\n");
        for (String suggestion : result.getSuggestions()) {
            sb.append("- ").append(suggestion).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化风险提示信息（使用 AIBlacklistResult）
     */
    private @NotNull String formatRiskMessage(@NotNull String command, @NotNull AIBlacklistResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(lang.get("ai.blacklist.risk.title")).append("\n");
        
        for (String risk : result.getRisks()) {
            sb.append("- ").append(risk).append("\n");
        }
        
        if (result.getSeverity() != null && !result.getSeverity().isEmpty()) {
            sb.append("\n").append(lang.get("ai.blacklist.severity")).append(result.getSeverity()).append("\n");
        }
        
        if (!result.getSuggestions().isEmpty()) {
            sb.append("\n").append(lang.get("ai.suggestion.title")).append("\n");
            for (String suggestion : result.getSuggestions()) {
                sb.append("- ").append(suggestion).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * 格式化黑名单消息
     */
    private @NotNull String formatBlacklistMessage(@NotNull AIBlacklistResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(lang.get("ai.blacklist.blocked.title")).append("\n");
        
        for (String reason : result.getReasons()) {
            sb.append("- ").append(reason).append("\n");
        }
        
        if (!result.getSuggestions().isEmpty()) {
            sb.append("\n").append(lang.get("ai.suggestion.title")).append("\n");
            for (String suggestion : result.getSuggestions()) {
                sb.append("- ").append(suggestion).append("\n");
            }
        }
        
        return sb.toString();
    }
    
    /**
     * AI 服务接口（待实现）
     */
    public interface AIService {
        boolean isAvailable();
        AIAnalysisResult analyzeCommand(String command) throws Exception;
        AIBlacklistResult analyzeBlacklist(String command) throws Exception;
    }
    
    /**
     * AI 分析结果
     */
    public static class AIAnalysisResult {
        private final boolean risky;
        private final List<String> risks;
        private final List<String> suggestions;
        
        public AIAnalysisResult(boolean risky, List<String> risks, List<String> suggestions) {
            this.risky = risky;
            this.risks = risks != null ? risks : Collections.emptyList();
            this.suggestions = suggestions != null ? suggestions : Collections.emptyList();
        }
        
        public boolean isRisky() {
            return risky;
        }
        
        public List<String> getRisks() {
            return risks;
        }
        
        public List<String> getSuggestions() {
            return suggestions;
        }
        
        public static AIAnalysisResult safe() {
            return new AIAnalysisResult(false, Collections.emptyList(), Collections.emptyList());
        }
        
        public static AIAnalysisResult risky(List<String> risks, List<String> suggestions) {
            return new AIAnalysisResult(true, risks, suggestions);
        }
    }
    
    /**
     * AI 黑名单检查结果
     */
    public static class AIBlacklistResult {
        private final boolean blacklisted;  // 是否在黑名单中（强制拦截）
        private final boolean risky;        // 是否有风险（需确认）
        private final String severity;     // 严重程度（low, medium, high, critical）
        private final List<String> reasons; // 原因列表
        private final List<String> risks;   // 风险列表
        private final List<String> suggestions; // 建议列表
        
        public AIBlacklistResult(boolean blacklisted, boolean risky, String severity,
                                 List<String> reasons, List<String> risks, List<String> suggestions) {
            this.blacklisted = blacklisted;
            this.risky = risky;
            this.severity = severity != null ? severity : "";
            this.reasons = reasons != null ? reasons : Collections.emptyList();
            this.risks = risks != null ? risks : Collections.emptyList();
            this.suggestions = suggestions != null ? suggestions : Collections.emptyList();
        }
        
        public boolean isBlacklisted() {
            return blacklisted;
        }
        
        public boolean isRisky() {
            return risky;
        }
        
        public String getSeverity() {
            return severity;
        }
        
        public List<String> getReasons() {
            return reasons;
        }
        
        public List<String> getRisks() {
            return risks;
        }
        
        public List<String> getSuggestions() {
            return suggestions;
        }
        
        public static AIBlacklistResult safe() {
            return new AIBlacklistResult(false, false, "", 
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
        
        public static AIBlacklistResult blacklisted(List<String> reasons, List<String> suggestions) {
            return new AIBlacklistResult(true, false, "critical", 
                reasons, Collections.emptyList(), suggestions);
        }
        
        public static AIBlacklistResult risky(String severity, List<String> risks, List<String> suggestions) {
            return new AIBlacklistResult(false, true, severity, 
                Collections.emptyList(), risks, suggestions);
        }
    }
}
