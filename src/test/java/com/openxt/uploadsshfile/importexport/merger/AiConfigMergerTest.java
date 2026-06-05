package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.model.AIConfig;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * AiConfigMerger 单元测试
 * UT-15: 同 modelType B 已配置则跳过
 */
public class AiConfigMergerTest {

    // ========== UT-15: 同 modelType B 已配置则跳过 ==========

    @Test
    public void testExistingModelSkipped() {
        AiConfigMerger merger = new AiConfigMerger();
        RemapContext ctx = new RemapContext();

        AIConfig exported = new AIConfig();
        AIConfig.ModelConfig expMc = new AIConfig.ModelConfig("exported-key", "https://exported.com", "exported-model");
        exported.getModelConfigs().put("QWEN", expMc);

        AIConfig current = new AIConfig();
        AIConfig.ModelConfig curMc = new AIConfig.ModelConfig("my-key", "https://my.com", "my-model");
        current.getModelConfigs().put("QWEN", curMc);

        AIConfig result = merger.merge(exported, current, ctx);

        // B 已配置 → 跳过（不覆盖已填字段）
        assertEquals("my-key", result.getModelConfigs().get("QWEN").getApiKey());
        assertEquals("https://my.com", result.getModelConfigs().get("QWEN").getBaseUrl());
        assertEquals("my-model", result.getModelConfigs().get("QWEN").getModelName());

        // 未导入任何模型
        assertTrue(merger.getImportedModels().isEmpty());
    }

    @Test
    public void testExistingModelWithEmptyFieldsFilled() {
        AiConfigMerger merger = new AiConfigMerger();
        RemapContext ctx = new RemapContext();

        AIConfig exported = new AIConfig();
        AIConfig.ModelConfig expMc = new AIConfig.ModelConfig("exp-key", "https://exp.com", "exp-model");
        exported.getModelConfigs().put("DEEPSEEK", expMc);

        AIConfig current = new AIConfig();
        // B 已配置但 baseUrl 为空
        AIConfig.ModelConfig curMc = new AIConfig.ModelConfig("", "", "cur-model");
        current.getModelConfigs().put("DEEPSEEK", curMc);

        AIConfig result = merger.merge(exported, current, ctx);

        // apiKey 和 baseUrl 为空 → 从 A 填充
        assertEquals("exp-key", result.getModelConfigs().get("DEEPSEEK").getApiKey());
        assertEquals("https://exp.com", result.getModelConfigs().get("DEEPSEEK").getBaseUrl());
        // modelName 不为空 → 保留 B
        assertEquals("cur-model", result.getModelConfigs().get("DEEPSEEK").getModelName());

        assertFalse(merger.getImportedModels().isEmpty());
    }

    @Test
    public void testNewModelImported() {
        AiConfigMerger merger = new AiConfigMerger();
        RemapContext ctx = new RemapContext();

        AIConfig exported = new AIConfig();
        exported.getModelConfigs().put("QWEN",
            new AIConfig.ModelConfig("qwen-key", "https://qwen.api", "qwen-model"));

        AIConfig current = new AIConfig();
        // B 没有 QWEN 配置

        AIConfig result = merger.merge(exported, current, ctx);

        // QWEN 被导入
        assertNotNull(result.getModelConfigs().get("QWEN"));
        assertEquals("qwen-key", result.getModelConfigs().get("QWEN").getApiKey());
        assertEquals("https://qwen.api", result.getModelConfigs().get("QWEN").getBaseUrl());
        assertEquals("qwen-model", result.getModelConfigs().get("QWEN").getModelName());

        assertEquals(1, merger.getImportedModels().size());
        assertEquals("QWEN", merger.getImportedModels().get(0));
    }

    @Test
    public void testMultipleModelsMerge() {
        AiConfigMerger merger = new AiConfigMerger();
        RemapContext ctx = new RemapContext();

        AIConfig exported = new AIConfig();
        exported.getModelConfigs().put("QWEN", new AIConfig.ModelConfig("k1", "u1", "m1"));
        exported.getModelConfigs().put("DEEPSEEK", new AIConfig.ModelConfig("k2", "u2", "m2"));
        exported.getModelConfigs().put("OPENAI", new AIConfig.ModelConfig("k3", "u3", "m3"));

        AIConfig current = new AIConfig();
        // OPENAI 已配置, 其余未配置
        current.getModelConfigs().put("OPENAI", new AIConfig.ModelConfig("cur-k", "cur-u", "cur-m"));

        AIConfig result = merger.merge(exported, current, ctx);

        // OPENAI: B 已配置 → 跳过
        assertEquals("cur-k", result.getModelConfigs().get("OPENAI").getApiKey());
        // QWEN, DEEPSEEK: B 未配置 → 导入
        assertEquals("k1", result.getModelConfigs().get("QWEN").getApiKey());
        assertEquals("k2", result.getModelConfigs().get("DEEPSEEK").getApiKey());

        assertEquals(2, merger.getImportedModels().size());
    }

    @Test
    public void testNullExportedReturnsCurrent() {
        AiConfigMerger merger = new AiConfigMerger();
        RemapContext ctx = new RemapContext();

        AIConfig current = new AIConfig();
        current.setEnabled(true);

        AIConfig result = merger.merge(null, current, ctx);
        assertTrue(result.isEnabled());
    }
}
