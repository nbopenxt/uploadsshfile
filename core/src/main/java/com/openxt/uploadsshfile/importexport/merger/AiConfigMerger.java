package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.config.ConfigManager;
import com.openxt.uploadsshfile.model.AIConfig;
import com.openxt.uploadsshfile.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 配置合并器。
 * 按 modelType 粒度合并：B 已配置则跳过空字段，B 未配置则导入。
 * 顶层字段 "B 有值则跳过"。
 */
public class AiConfigMerger implements ConfigMerger<AIConfig> {

    private final MergeCounts counts = new MergeCounts();
    private final List<String> importedModels = new ArrayList<>();

    @Override
    public AIConfig merge(AIConfig exported, AIConfig current, RemapContext ctx) {
        if (exported == null) return current;
        if (current == null) current = new AIConfig();

        // modelConfigs: 按 modelType 粒度
        if (exported.getModelConfigs() != null) {
            for (String modelType : exported.getModelConfigs().keySet()) {
                AIConfig.ModelConfig exportedMc = exported.getModelConfigs().get(modelType);
                AIConfig.ModelConfig existing = current.getModelConfigs().get(modelType);

                if (existing != null) {
                    // B 已配置 → 仅填充空字段
                    boolean changed = false;
                    if (ConfigManager.isEmpty(existing.getApiKey()) && !ConfigManager.isEmpty(exportedMc.getApiKey())) {
                        existing.setApiKey(exportedMc.getApiKey());
                        changed = true;
                    }
                    if (ConfigManager.isEmpty(existing.getBaseUrl()) && !ConfigManager.isEmpty(exportedMc.getBaseUrl())) {
                        existing.setBaseUrl(exportedMc.getBaseUrl());
                        changed = true;
                    }
                    if (ConfigManager.isEmpty(existing.getModelName()) && !ConfigManager.isEmpty(exportedMc.getModelName())) {
                        existing.setModelName(exportedMc.getModelName());
                        changed = true;
                    }
                    if (changed) importedModels.add(modelType);
                } else {
                    // B 未配置 → 导入整个 modelConfig
                    AIConfig.ModelConfig newMc = new AIConfig.ModelConfig(
                            exportedMc.getApiKey(), exportedMc.getBaseUrl(), exportedMc.getModelName());
                    current.getModelConfigs().put(modelType, newMc);
                    importedModels.add(modelType);
                }
            }
        }

        // 顶层字段："B 有值则跳过"
        if (current.getModelType() == null || current.getModelType().equals("OPENAI")) {
            current.setModelType(exported.getModelType());
        }
        // boolean 字段以 B 当前配置为准

        return current;
    }

    public List<String> getImportedModels() { return importedModels; }

    @Override
    public MergeCounts getMergeCounts() {
        counts.setAdded(importedModels.size());
        return counts;
    }
}
