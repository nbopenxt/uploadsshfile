package com.openxt.uploadsshfile.ai;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * AI Configuration Class
 * Stores user-configured API Key, Base URL, model name, etc.
 */
public class AiConfig {

    private AiModelType modelType;
    private String apiKey;
    private String baseUrl;
    private String modelName;
    private int timeoutSeconds;
    private boolean enabled;

    public AiConfig() {
        // Default config
        this.modelType = AiModelType.OPENAI;
        this.apiKey = "";
        this.baseUrl = "";
        this.modelName = "";
        this.timeoutSeconds = 30;
        this.enabled = false;
    }

    public AiConfig(@NotNull AiModelType modelType, @NotNull String apiKey) {
        this.modelType = modelType;
        this.apiKey = apiKey;
        this.baseUrl = modelType.getDefaultBaseUrl() != null ? modelType.getDefaultBaseUrl() : "";
        this.modelName = modelType.getDefaultModel();
        this.timeoutSeconds = modelType == AiModelType.OLLAMA ? 60 : 30;
        this.enabled = !apiKey.isEmpty();
    }

    /**
     * Check if config is complete and usable
     */
    public boolean isConfigured() {
        if (modelType == null || !enabled) {
            return false;
        }
        
        if (apiKey == null || apiKey.trim().isEmpty()) {
            return false;
        }

        // Local Ollama does not require API Key
        if (modelType == AiModelType.OLLAMA) {
            return baseUrl != null && !baseUrl.trim().isEmpty();
        }

        // Gemini does not require baseUrl
        if (modelType == AiModelType.GEMINI) {
            return true;
        }

        // Other models require baseUrl
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return false;
        }

        // Doubao requires custom endpoint
        if (modelType == AiModelType.DOUBAO) {
            return modelName != null && !modelName.trim().isEmpty() && !modelName.contains("endpoint-required");
        }

        return true;
    }

    /**
     * Get full model identifier
     */
    public String getFullModelId() {
        if (modelType == AiModelType.OLLAMA) {
            return modelType.getDisplayName() + "/" + modelName;
        }
        return modelType.getDisplayName();
    }

    // ========== Getter/Setter ==========

    public AiModelType getModelType() {
        return modelType;
    }

    public void setModelType(AiModelType modelType) {
        this.modelType = modelType;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AiConfig aiConfig = (AiConfig) o;
        return timeoutSeconds == aiConfig.timeoutSeconds &&
                enabled == aiConfig.enabled &&
                modelType == aiConfig.modelType &&
                Objects.equals(apiKey, aiConfig.apiKey) &&
                Objects.equals(baseUrl, aiConfig.baseUrl) &&
                Objects.equals(modelName, aiConfig.modelName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(modelType, apiKey, baseUrl, modelName, timeoutSeconds, enabled);
    }

    @Override
    public String toString() {
        return "AiConfig{" +
                "modelType=" + modelType +
                ", apiKey='" + (apiKey != null && !apiKey.isEmpty() ? "****" : "empty") + '\'' +
                ", baseUrl='" + baseUrl + '\'' +
                ", modelName='" + modelName + '\'' +
                ", timeoutSeconds=" + timeoutSeconds +
                ", enabled=" + enabled +
                '}';
    }
}
