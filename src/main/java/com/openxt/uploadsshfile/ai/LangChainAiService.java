package com.openxt.uploadsshfile.ai;

import com.openxt.uploadsshfile.model.AIConfig;
import com.openxt.uploadsshfile.model.OperatingSystem;
import com.openxt.uploadsshfile.store.StoreManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

/**
 * LangChain4j AI 服务
 * 
 * 功能：
 * 1. 命令执行前 AI 风险分析
 * 2. AI 黑名单检查 - 判断命令是否属于危险命令
 * 3. 命令执行后 AI 结果分析
 * 
 * 特性：
 * - 异步执行（适配 IDEA 插件，不卡 UI）
 * - 自动重连
 * - 异常处理完善
 * 
 * 使用统一配置存储 plugin-config.json
 */
public class LangChainAiService implements AICommandChecker.AIService, AIResultChecker.AIAnalysisService {

    private final UnifiedConfigStore configStore;
    private final AtomicReference<Object> modelRef;

    public LangChainAiService() {
        this(StoreManager.getInstance().getUnifiedConfigStore());
    }

    public LangChainAiService(@NotNull UnifiedConfigStore configStore) {
        this.configStore = configStore;
        this.modelRef = new AtomicReference<>();
    }

    /**
     * 检查服务是否可用
     */
    @Override
    public boolean isAvailable() {
        AIConfig config = configStore.getConfig().getAiConfig();
        return config.isConfigured();
    }

    /**
     * 获取或创建模型实例
     */
    private Object getOrCreateModel() {
        // 先尝试返回缓存的模型
        Object cached = modelRef.get();
        if (cached != null) {
            return cached;
        }

        // 检查配置
        AIConfig config = configStore.getConfig().getAiConfig();
        if (!config.isConfigured()) {
            return null;
        }

        // 创建新模型
        Object model = AiModelFactory.createModel(config);
        modelRef.set(model);
        return model;
    }

    /**
     * 刷新模型（配置变更后调用）
     */
    public void refreshModel() {
        modelRef.set(null);
    }

    /**
     * AI 分析命令风险
     */
    @Override
    public AICommandChecker.AIAnalysisResult analyzeCommand(String command) throws Exception {
        Object model = getOrCreateModel();
        if (model == null) {
            return AICommandChecker.AIAnalysisResult.safe();
        }

        String prompt = buildCommandAnalysisPrompt(command);
        String response = callModelChat(model, prompt);

        return parseCommandAnalysisResult(response);
    }
    
    /**
     * AI 黑名单检查 - 判断命令是否属于危险命令
     */
    @Override
    public AICommandChecker.AIBlacklistResult analyzeBlacklist(String command) throws Exception {
        Object model = getOrCreateModel();
        if (model == null) {
            return AICommandChecker.AIBlacklistResult.safe();
        }

        String prompt = buildBlacklistCheckPrompt(command);
        String response = callModelChat(model, prompt);

        return parseBlacklistCheckResult(response);
    }

    /**
     * AI 分析命令输出
     */
    @Override
    public AIResultChecker.AIResult analyzeOutput(String command, 
                                                   com.openxt.uploadsshfile.model.CommandResult result) throws Exception {
        Object model = getOrCreateModel();
        if (model == null) {
            return AIResultChecker.AIResult.normal();
        }

        String prompt = buildOutputAnalysisPrompt(command, result);
        String response = callModelChat(model, prompt);

        return parseOutputAnalysisResult(response);
    }

    /**
     * 调用模型的 chat 方法
     * LangChain4j 所有模型都有 chat(String) 方法
     */
    private String callModelChat(Object model, String prompt) throws Exception {
        try {
            // 使用反射调用 chat 方法
            java.lang.reflect.Method method = model.getClass().getMethod("chat", String.class);
            Object result = method.invoke(model, prompt);
            // chat 方法可能返回 String 或 AiMessage，根据实际返回类型处理
            if (result instanceof String) {
                return (String) result;
            } else {
                // 如果返回的是 AiMessage，获取其 content
                java.lang.reflect.Method contentMethod = result.getClass().getMethod("text");
                return (String) contentMethod.invoke(result);
            }
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    /**
     * Build command analysis prompt (English for AI compatibility)
     */
    private String buildCommandAnalysisPrompt(String command) {
        return String.format("""
            Please analyze the following Linux/Shell command for potential risks.

            Command: %s

            Respond in JSON format with the following fields:
            - risky: true/false, whether the command has risks
            - risks: array of risk descriptions
            - suggestions: array of suggestions

            Example response:
            {"risky": true, "risks": ["Recursive file deletion", "May delete critical system directories"], "suggestions": ["Use -i parameter for confirmation", "Run ls first to verify directory"]}

            Note: If the command is safe, risks and suggestions should be empty arrays.
            """, command);
    }
    
    /**
     * Build blacklist check prompt (English for AI compatibility)
     * Determines if the command is a dangerous blacklist command
     */
    private String buildBlacklistCheckPrompt(String command) {
        return String.format("""
            Please analyze whether the following command belongs to the dangerous command blacklist.

            Command: %s

            Respond in JSON format with the following fields:
            - blacklisted: true/false, whether it is a mandatory blocklist command (e.g., recursive root deletion, disk format)
            - risky: true/false, whether it has risks (but not mandatory blocked)
            - severity: severity level, "critical"/"high"/"medium"/"low", only returned when there are risks
            - reasons: array of reasons for being blacklisted (only when blacklisted is true)
            - risks: array of risk descriptions (only when risky is true)
            - suggestions: array of suggestions

            Blacklist command examples:
            - rm -rf / or rm -rf /* (recursive root directory deletion)
            - format C: /fs ntfs (system disk format)
            - dd if=/dev/zero of=/dev/sda (disk zeroing)
            - Fork bomb: :(){:|:&};:
            - wget ... | sh (download and execute)

            Example response (blacklist command):
            {"blacklisted": true, "risky": false, "severity": "critical", "reasons": ["Recursive deletion operation", "May cause permanent data loss"], "suggestions": ["Check command path", "Use ls to verify target directory"]}

            Example response (risky but not blocked):
            {"blacklisted": false, "risky": true, "severity": "medium", "reasons": [], "risks": ["Modifies system configuration", "May affect service operation"], "suggestions": ["Backup configuration", "Confirm operation necessity"]}

            Example response (safe command):
            {"blacklisted": false, "risky": false, "severity": "", "reasons": [], "risks": [], "suggestions": []}
            """, command);
    }
    
    /**
     * 解析黑名单检查结果
     */
    private AICommandChecker.AIBlacklistResult parseBlacklistCheckResult(String response) {
        try {
            boolean blacklisted = extractBoolean(response, "blacklisted");
            boolean risky = extractBoolean(response, "risky");
            String severity = extractString(response, "severity");
            var reasons = extractStringArray(response, "reasons");
            var risks = extractStringArray(response, "risks");
            var suggestions = extractStringArray(response, "suggestions");

            if (blacklisted) {
                return AICommandChecker.AIBlacklistResult.blacklisted(reasons, suggestions);
            } else if (risky) {
                return AICommandChecker.AIBlacklistResult.risky(severity, risks, suggestions);
            } else {
                return AICommandChecker.AIBlacklistResult.safe();
            }
        } catch (Exception e) {
            // 解析失败，返回安全结果
            return AICommandChecker.AIBlacklistResult.safe();
        }
    }
    
    /**
     * 提取字符串值
     */
    private String extractString(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]*)\"";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    /**
     * Build output analysis prompt (English for AI compatibility)
     */
    private String buildOutputAnalysisPrompt(String command,
                                            com.openxt.uploadsshfile.model.CommandResult result) {
        String stdout = result.getStdout() != null ? result.getStdout() : "";
        String stderr = result.getStderr() != null ? result.getStderr() : "";
        return String.format("""
            Please analyze whether the following command execution result has any issues.

            Command: %s
            Exit Code: %d
            Stdout:
            %s
            Stderr:
            %s

            Respond in JSON format with the following fields:
            - hasIssues: true/false, whether there are issues
            - issues: array of issue descriptions
            - recommendedActions: array of recommended actions

            Example response:
            {"hasIssues": true, "issues": ["Insufficient file permissions", "Some files failed to copy"], "recommendedActions": ["Check directory permissions", "Re-execute the command"]}

            Note: If the execution result is normal, issues and recommendedActions should be empty arrays.
            """, command, result.getExitCode(), stdout, stderr);
    }

    /**
     * 解析命令分析结果
     */
    private AICommandChecker.AIAnalysisResult parseCommandAnalysisResult(String response) {
        try {
            // 简单的 JSON 解析（避免引入 JSON 库）
            boolean risky = extractBoolean(response, "risky");
            var risks = extractStringArray(response, "risks");
            var suggestions = extractStringArray(response, "suggestions");

            return new AICommandChecker.AIAnalysisResult(risky, risks, suggestions);
        } catch (Exception e) {
            // 解析失败，返回安全结果
            return AICommandChecker.AIAnalysisResult.safe();
        }
    }

    /**
     * 解析输出分析结果
     */
    private AIResultChecker.AIResult parseOutputAnalysisResult(String response) {
        try {
            boolean hasIssues = extractBoolean(response, "hasIssues");
            var issues = extractStringArray(response, "issues");
            var actions = extractStringArray(response, "recommendedActions");

            return new AIResultChecker.AIResult(hasIssues, issues, actions);
        } catch (Exception e) {
            // 解析失败，返回正常结果
            return AIResultChecker.AIResult.normal();
        }
    }

    // ========== 简单 JSON 解析工具 ==========

    private boolean extractBoolean(String json, String key) {
        String pattern = "\"" + key + "\"\\s*:\\s*(\\w+)";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        if (matcher.find()) {
            return "true".equalsIgnoreCase(matcher.group(1));
        }
        return false;
    }

    private java.util.List<String> extractStringArray(String json, String key) {
        java.util.List<String> result = new java.util.ArrayList<>();
        
        // 找到键值对
        String pattern = "\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]";
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(pattern).matcher(json);
        
        if (matcher.find()) {
            String arrayContent = matcher.group(1);
            // 提取所有引号内的字符串
            java.util.regex.Matcher itemsMatcher = 
                java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(arrayContent);
            while (itemsMatcher.find()) {
                result.add(itemsMatcher.group(1));
            }
        }
        
        return result;
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...(truncated)";
    }
}
