package com.openxt.uploadsshfile.model;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Map;

/**
 * AI Validation Configuration
 * Used to configure AI command validation service
 *
 * Supported models: OpenAI, Gemini, Claude, Ollama, Qwen, DeepSeek, Doubao, Moonshot
 */
public class AIConfig {
    
    /** 当前选中的模型类型 */
    private String modelType = "OPENAI";
    
    /** 是否启用 AI 校验 */
    @SerializedName("enabled")
    private boolean enabled;
    
    /** 各模型的独立配置 */
    private Map<String, ModelConfig> modelConfigs = new HashMap<>();
    
    /** 执行前校验 - 是否启用 AI 命令执行前校验 */
    @SerializedName("enablePreExecutionCheck")
    private boolean enablePreExecutionCheck;
    
    /** 执行后校验 - 是否启用 AI 结果校验 */
    @SerializedName("enablePostResultCheck")
    private boolean enablePostResultCheck;
    
    /** AI 黑名单检查 - 是否启用 AI 模型判断命令是否属于黑名单 */
    @SerializedName("enableAiBlacklistCheck")
    private boolean enableAiBlacklistCheck;
    
    /** 超时时间（毫秒） */
    private long timeoutMs;
    
    public AIConfig() {
        this.enabled = false;
        this.enablePreExecutionCheck = true;
        this.enablePostResultCheck = true;
        this.enableAiBlacklistCheck = false;
        this.timeoutMs = 30000;
    }
    
    /**
     * 获取当前模型的配置
     */
    public ModelConfig getCurrentModelConfig() {
        return modelConfigs.computeIfAbsent(modelType, k -> new ModelConfig());
    }
    
    /**
     * 获取指定模型的配置
     */
    public ModelConfig getModelConfig(String type) {
        return modelConfigs.computeIfAbsent(type, k -> new ModelConfig());
    }
    
    /**
     * 检查当前模型是否配置完整（用于测试连接）
     */
    public boolean isConfigured() {
        ModelConfig config = getCurrentModelConfig();
        String type = modelType;
        
        // 测试连接时不检查 enabled 标志
        if (config.apiKey == null || config.apiKey.trim().isEmpty()) return false;
        
        // 本地 Ollama 不需要 API Key
        if ("OLLAMA".equals(type)) {
            return config.baseUrl != null && !config.baseUrl.trim().isEmpty();
        }
        
        // Gemini 不需要 baseUrl
        if ("GEMINI".equals(type)) {
            return true;
        }
        
        // Doubao requires modelName
        if ("DOUBAO".equals(type)) {
            return config.modelName != null && !config.modelName.trim().isEmpty();
        }
        
        // 其他模型需要 baseUrl
        return config.baseUrl != null && !config.baseUrl.trim().isEmpty();
    }
    
    // ========== Getter/Setter ==========
    
    public String getModelType() {
        return modelType;
    }
    
    public void setModelType(String modelType) {
        this.modelType = modelType;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public String getBaseUrl() {
        return getCurrentModelConfig().baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        getCurrentModelConfig().baseUrl = baseUrl;
    }
    
    public String getApiKey() {
        return getCurrentModelConfig().apiKey;
    }
    
    public void setApiKey(String apiKey) {
        getCurrentModelConfig().apiKey = apiKey;
    }
    
    public String getModelName() {
        return getCurrentModelConfig().modelName;
    }
    
    public void setModelName(String modelName) {
        getCurrentModelConfig().modelName = modelName;
    }
    
    public boolean isEnablePreExecutionCheck() {
        return enablePreExecutionCheck;
    }
    
    public void setEnablePreExecutionCheck(boolean enablePreExecutionCheck) {
        this.enablePreExecutionCheck = enablePreExecutionCheck;
    }
    
    public boolean isEnablePostResultCheck() {
        return enablePostResultCheck;
    }
    
    public void setEnablePostResultCheck(boolean enablePostResultCheck) {
        this.enablePostResultCheck = enablePostResultCheck;
    }
    
    public boolean isEnableAiBlacklistCheck() {
        return enableAiBlacklistCheck;
    }
    
    public void setEnableAiBlacklistCheck(boolean enableAiBlacklistCheck) {
        this.enableAiBlacklistCheck = enableAiBlacklistCheck;
    }
    
    public long getTimeoutMs() {
        return timeoutMs;
    }
    
    public void setTimeoutMs(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }
    
    public Map<String, ModelConfig> getModelConfigs() {
        return modelConfigs;
    }
    
    public void setModelConfigs(Map<String, ModelConfig> modelConfigs) {
        this.modelConfigs = modelConfigs;
    }
    
    /**
     * 模型配置（每个模型独立存储）
     */
    public static class ModelConfig {
        private String apiKey = "";
        private String baseUrl = "";
        private String modelName = "";
        
        public ModelConfig() {}
        
        public ModelConfig(String apiKey, String baseUrl, String modelName) {
            this.apiKey = apiKey;
            this.baseUrl = baseUrl;
            this.modelName = modelName;
        }
        
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        
        public String getModelName() { return modelName; }
        public void setModelName(String modelName) { this.modelName = modelName; }
    }
    
    /**
     * 获取模型的默认配置
     */
    public static ModelDefaults getModelDefaults(String modelType) {
        return switch (modelType) {
            case "QWEN" -> new ModelDefaults("Alibaba Qwen",
                "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.6-flash");
            case "DEEPSEEK" -> new ModelDefaults("DeepSeek",
                "https://api.deepseek.com/v1", "deepseek-chat");
            case "DOUBAO" -> new ModelDefaults("ByteDance Doubao",
                "https://ark.cn-beijing.volces.com/api/v3", "Doubao-Seed-2.0-lite");
            case "MOONSHOT" -> new ModelDefaults("Moonshot AI",
                "https://api.moonshot.cn/v1", "moonshot-v1-8k");
            case "GEMINI" -> new ModelDefaults("Google Gemini", null, "gemini-2.0-flash");
            case "CLAUDE" -> new ModelDefaults("Anthropic Claude", null, "claude-3-5-sonnet-latest");
            case "OLLAMA" -> new ModelDefaults("Local Ollama", 
                "http://localhost:11434", "llama3.2:3b");
            default -> new ModelDefaults("OpenAI", 
                "https://api.openai.com/v1", "gpt-4o-mini");
        };
    }
    
    /**
     * 模型默认配置
     */
    public record ModelDefaults(String displayName, String baseUrl, String modelName) {}
}
