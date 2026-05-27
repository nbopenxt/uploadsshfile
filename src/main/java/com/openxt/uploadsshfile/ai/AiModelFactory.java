package com.openxt.uploadsshfile.ai;

import com.openxt.uploadsshfile.model.AIConfig;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

/**
 * Unified factory class - auto-adapt all models
 *
 * Switch model = just change config, one codebase for all models
 * Supports: OpenAI, Gemini, Claude, Ollama, Qwen, DeepSeek, Doubao, Moonshot
 */
public class AiModelFactory {

    /**
     * Create model based on config (returns generic interface type)
     *
     * @param config AI config
     * @return chat model instance
     */
    public static Object createModel(@NotNull AIConfig config) {
        String type = config.getModelType();
        String apiKey = config.getApiKey();
        String baseUrl = config.getBaseUrl();
        String modelName = config.getModelName();
        int timeoutSeconds = (int) (config.getTimeoutMs() / 1000);

        Duration timeoutDuration = Duration.ofSeconds(Math.max(timeoutSeconds, 10));

        return switch (type) {
            // ========== Chinese Models (OpenAI compatible protocol) ==========
            case "QWEN" -> OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(timeoutDuration)
                    .build();

            case "DEEPSEEK" -> OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(timeoutDuration)
                    .build();

            case "DOUBAO" -> OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(timeoutDuration)
                    .build();

            case "MOONSHOT" -> OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(timeoutDuration)
                    .build();

            // ========== Foreign Models ==========
            case "OPENAI" -> OpenAiChatModel.builder()
                    .apiKey(apiKey)
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(timeoutDuration)
                    .build();

            case "GEMINI" -> GoogleAiGeminiChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .timeout(timeoutDuration)
                    .build();

            case "CLAUDE" -> AnthropicChatModel.builder()
                    .apiKey(apiKey)
                    .modelName(modelName)
                    .timeout(timeoutDuration)
                    .build();

            // ========== Local Models ==========
            case "OLLAMA" -> OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .timeout(timeoutDuration)
                    .build();

            default -> throw new IllegalArgumentException("Unsupported model type: " + type);
        };
    }
}
