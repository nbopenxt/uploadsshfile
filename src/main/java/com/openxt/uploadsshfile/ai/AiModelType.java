package com.openxt.uploadsshfile.ai;

/**
 * Unified model enum (change config to switch)
 * Supports: OpenAI, Gemini, Claude, Ollama, Qwen, DeepSeek, Doubao, Moonshot
 */
public enum AiModelType {
    // ========== Chinese Models ==========
    QWEN("Alibaba Qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen3.6-flash"),
    DEEPSEEK("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    DOUBAO("ByteDance Doubao", "https://ark.cn-beijing.volces.com/api/v3", "Doubao-Seed-2.0-lite"),
    MOONSHOT("Moonshot AI", "https://api.moonshot.cn/v1", "moonshot-v1-8k"),

    // ========== Foreign Models ==========
    OPENAI("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    GEMINI("Google Gemini", null, "gemini-2.0-flash"),
    CLAUDE("Anthropic Claude", null, "claude-3-5-sonnet-latest"),
    OLLAMA("Local Ollama", "http://localhost:11434", "llama3.2:3b");

    private final String displayName;
    private final String defaultBaseUrl;
    private final String defaultModel;

    AiModelType(String displayName, String defaultBaseUrl, String defaultModel) {
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModel = defaultModel;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    /**
     * Whether to use OpenAI compatible protocol
     */
    public boolean isOpenAiCompatible() {
        return this == QWEN || this == DEEPSEEK || this == DOUBAO || 
               this == MOONSHOT || this == OPENAI || this == OLLAMA;
    }

    /**
     * Whether to use Google native API
     */
    public boolean isGoogleGemini() {
        return this == GEMINI;
    }

    /**
     * Whether to use Anthropic native API
     */
    public boolean isAnthropicClaude() {
        return this == CLAUDE;
    }

    @Override
    public String toString() {
        return displayName;
    }

    /**
     * Find enum by display name
     */
    public static AiModelType fromDisplayName(String displayName) {
        for (AiModelType type : values()) {
            if (type.displayName.equals(displayName)) {
                return type;
            }
        }
        return OPENAI; // 默认
    }
}
