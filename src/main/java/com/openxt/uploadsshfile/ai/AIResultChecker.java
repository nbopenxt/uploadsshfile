package com.openxt.uploadsshfile.ai;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.AIConfig;
import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.store.StoreManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

/**
 * AI 命令结果校验器
 * 
 * <b>功能</b>：在命令执行后，AI 分析输出结果，识别潜在错误并给出建议
 * <b>触发时机</b>：返回码检查 + 关键词匹配处理正常后执行
 * <b>未接入AI时</b>：返回空字符串，不影响正常流程
 * 
 * 使用统一配置存储 plugin-config.json
 */
public class AIResultChecker {
    
    private final LanguageManager lang = LanguageManager.getInstance();
    private final UnifiedConfigStore configStore;
    private final AIAnalysisService aiService;
    
    public AIResultChecker() {
        this(StoreManager.getInstance().getUnifiedConfigStore(), null);
    }
    
    public AIResultChecker(@NotNull UnifiedConfigStore configStore, AIAnalysisService aiService) {
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
     * AI 校验命令执行结果
     * 
     * @param command 执行的命令
     * @param result  命令执行结果
     * @return 空字符串表示正常；非空字符串表示发现错误，包含错误解释和建议措施
     */
    public @NotNull String checkResult(@NotNull String command, @NotNull CommandResult result) {
        // 检查是否启用 AI 校验
        AIConfig config = getAIConfig();
        if (!config.isEnabled() || !config.isEnablePostResultCheck()) {
            return "";
        }
        
        // 未接入 AI 或服务不可用时，返回空字符串（判定为正常）
        if (aiService == null || !aiService.isAvailable()) {
            return "";
        }
        
        try {
            // 调用 AI 服务进行结果分析
            AIResult analysisResult = aiService.analyzeOutput(command, result);
            
            if (analysisResult.hasIssues()) {
                // 返回错误解释和建议措施
                return formatIssueMessage(command, analysisResult);
            }
            
            return ""; // 无问题
        } catch (Exception e) {
            // AI 服务异常时，返回空字符串（不阻断执行）
            return "";
        }
    }
    
    /**
     * 格式化问题提示信息
     */
    private @NotNull String formatIssueMessage(@NotNull String command, @NotNull AIResult result) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(lang.get("ai.issue.title")).append("\n");
        for (String issue : result.getIssues()) {
            sb.append("- ").append(issue).append("\n");
        }
        
        sb.append("\n").append(lang.get("ai.action.title")).append("\n");
        for (String action : result.getRecommendedActions()) {
            sb.append("- ").append(action).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * AI 分析服务接口（待实现）
     */
    public interface AIAnalysisService {
        boolean isAvailable();
        AIResult analyzeOutput(String command, CommandResult result) throws Exception;
    }
    
    /**
     * AI 分析结果
     */
    public static class AIResult {
        private final boolean hasIssues;
        private final List<String> issues;
        private final List<String> recommendedActions;
        
        public AIResult(boolean hasIssues, List<String> issues, List<String> recommendedActions) {
            this.hasIssues = hasIssues;
            this.issues = issues != null ? issues : Collections.emptyList();
            this.recommendedActions = recommendedActions != null ? recommendedActions : Collections.emptyList();
        }
        
        public boolean hasIssues() {
            return hasIssues;
        }
        
        public List<String> getIssues() {
            return issues;
        }
        
        public List<String> getRecommendedActions() {
            return recommendedActions;
        }
        
        public static AIResult normal() {
            return new AIResult(false, Collections.emptyList(), Collections.emptyList());
        }
        
        public static AIResult withIssues(List<String> issues, List<String> recommendedActions) {
            return new AIResult(true, issues, recommendedActions);
        }
    }
}
