package com.openxt.uploadsshfile.importexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.importexport.merger.*;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import com.openxt.uploadsshfile.persistence.SecureStorage;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;

/**
 * 配置导入器。
 * 读取 JSON 文件 → 校验 → 逐项合并 → 保存。
 * 支持回滚：导入前做备份，异常时恢复。
 */
public class ConfigImporter {

    private static final String SUPPORTED_VERSION = "3.0";

    private final UnifiedConfigStore configStore;
    private final SecureStorage secureStorage;
    private final Gson gson;

    public ConfigImporter() {
        this(UnifiedConfigStore.getInstance(), SecureStorage.getInstance());
    }

    /** 用于测试 - 允许注入测试专用 store 和 secureStorage */
    public ConfigImporter(UnifiedConfigStore configStore, SecureStorage secureStorage) {
        this.configStore = configStore;
        this.secureStorage = secureStorage;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * 从文件导入配置并合并。
     * @param sourceFile 源 JSON 文件
     * @return 导入结果摘要
     * @throws IOException 导入失败
     */
    public ImportResult import_(File sourceFile) throws IOException {
        Logger.debug("ConfigImporter", "import() from " + sourceFile.getAbsolutePath());

        // 1. 读取 JSON
        String json = Files.readString(sourceFile.toPath(), StandardCharsets.UTF_8);

        // 2. 校验
        String validationError = validate(json);
        if (validationError != null) {
            throw new IOException(validationError);
        }

        // 3. 反序列化
        ExportPayload exported = gson.fromJson(json, ExportPayload.class);

        // 4. 备份当前配置（回滚用）
        String backupJson = gson.toJson(configStore.getConfig());
        Map<String, String> backupSecure = secureStorage.loadAll();
        UnifiedPluginConfig current = configStore.getConfig();

        try {
            RemapContext ctx = new RemapContext();
            ImportResult result = new ImportResult();

            // 步骤1: 服务器
            ServerMerger serverMerger = new ServerMerger();
            current.setServers(serverMerger.merge(
                    exported.getServers() != null ? exported.getServers() : new ArrayList<>(),
                    current.getServers(), ctx));
            result.setServersAdded(serverMerger.getMergeCounts().getAdded());
            result.setServersUpdated(serverMerger.getMergeCounts().getUpdated());
            result.setServersSkipped(serverMerger.getMergeCounts().getSkipped());

            // 步骤2: 路径
            PathMerger pathMerger = new PathMerger();
            current.setPaths(pathMerger.merge(
                    exported.getPaths() != null ? exported.getPaths() : new ArrayList<>(),
                    current.getPaths(), ctx));
            result.setPathsAdded(pathMerger.getMergeCounts().getAdded());
            result.setPathsUpdated(pathMerger.getMergeCounts().getUpdated());
            result.setPathsSkipped(pathMerger.getMergeCounts().getSkipped());

            // 步骤3: 命令配置
            CommandConfigMerger cmdMerger = new CommandConfigMerger();
            current.setCommandConfigs(cmdMerger.merge(
                    exported.getCommandConfigs() != null ? exported.getCommandConfigs() : new ArrayList<>(),
                    current.getCommandConfigs(), ctx));
            result.setCommandConfigsAdded(cmdMerger.getMergeCounts().getAdded());
            result.setCommandConfigsUpdated(cmdMerger.getMergeCounts().getUpdated());
            result.setCommandConfigsSkipped(cmdMerger.getMergeCounts().getSkipped());

            // 步骤4: 批处理任务
            BatchTaskMerger batchMerger = new BatchTaskMerger();
            current.setBatchTasks(batchMerger.merge(
                    exported.getBatchTasks() != null ? exported.getBatchTasks() : new ArrayList<>(),
                    current.getBatchTasks(), ctx));
            result.setBatchTasksAdded(batchMerger.getMergeCounts().getAdded());
            result.setBatchTasksUpdated(batchMerger.getMergeCounts().getUpdated());
            result.setBatchTasksSkipped(batchMerger.getMergeCounts().getSkipped());

            // 步骤5: AI 配置
            AiConfigMerger aiMerger = new AiConfigMerger();
            current.setAiConfig(aiMerger.merge(exported.getAiConfig(), current.getAiConfig(), ctx));
            result.setAiConfigImportedModels(aiMerger.getImportedModels());

            // 步骤6: 黑名单
            BlacklistMerger blMerger = new BlacklistMerger();
            current.setBlacklist(blMerger.merge(exported.getBlacklist(), current.getBlacklist(), ctx));
            result.setBlacklistMergedCount(blMerger.getMergeCounts().getAdded());

            // 步骤7: 关键词
            KeywordRulesMerger kwMerger = new KeywordRulesMerger();
            current.setKeywordRules(kwMerger.merge(exported.getKeywordRules(), current.getKeywordRules(), ctx));
            result.setKeywordMergedCount(kwMerger.getMergeCounts().getAdded());

            // 步骤8: 有返回命令
            HasOutputCommandMerger hocMerger = new HasOutputCommandMerger();
            current.setHasOutputCommands(hocMerger.merge(exported.getHasOutputCommands(), current.getHasOutputCommands(), ctx));
            result.setHasOutputCommandMergedCount(hocMerger.getMergeCounts().getAdded());

            // 保存合并后的配置
            configStore.save(current);

            Logger.debug("ConfigImporter", "import() completed successfully");
            return result;

        } catch (Exception e) {
            // 回滚: 恢复 plugin-config.json 和 secure.dat
            Logger.error("ConfigImporter", "Import failed, rolling back: " + e.getMessage(), e);
            try {
                UnifiedPluginConfig backup = gson.fromJson(backupJson, UnifiedPluginConfig.class);
                if (backup != null) {
                    configStore.save(backup);
                }
                // 恢复密码文件
                if (backupSecure != null) {
                    secureStorage.restore(backupSecure);
                }
            } catch (Exception rollbackEx) {
                Logger.error("ConfigImporter", "Rollback also failed: " + rollbackEx.getMessage());
            }
            throw new IOException(LanguageManager.getInstance().get("config.import.rollback") + ": " + e.getMessage(), e);
        }
    }

    /**
     * 校验 JSON 格式和版本号。
     * @return 校验结果消息（成功返回 null）
     */
    public String validate(String jsonContent) {
        LanguageManager lm = LanguageManager.getInstance();
        try {
            ExportPayload payload = gson.fromJson(jsonContent, ExportPayload.class);
            if (payload == null) {
                return lm.get("config.import.invalid");
            }
            if (payload.getVersion() == null || !payload.getVersion().equals(SUPPORTED_VERSION)) {
                return lm.get("config.import.versionError");
            }
            return null;
        } catch (Exception e) {
            return lm.get("config.import.invalid") + ": " + e.getMessage();
        }
    }
}
