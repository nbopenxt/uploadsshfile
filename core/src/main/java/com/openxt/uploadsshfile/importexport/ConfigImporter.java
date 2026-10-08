package com.openxt.uploadsshfile.importexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.importexport.merger.*;
import com.openxt.uploadsshfile.model.SingleUploadTask;
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

    private static final String SUPPORTED_VERSION = "3.2";

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

            // 步骤9（1.0.8/D-10+D-11，清单⑥）：单任务上下文 5 项——本机为空才采纳、非空保留本机；
            // lastSuccessful* 的 id 须按 merger 重映射（跨机 serverId/pathId/cmdId 本机不同值），
            // 解析不出映射即不采纳；singleUploadTaskId 不透明串直采（与本机命名空间相撞则跳过）
            boolean adoptedTaskId = false;
            if (isBlank(current.getSingleUploadTaskId()) && !isBlank(exported.getSingleUploadTaskId())
                    && configStore.findTaskIdOwner(exported.getSingleUploadTaskId(), null) == null) {
                current.setSingleUploadTaskId(exported.getSingleUploadTaskId());
                adoptedTaskId = true;
            }
            if (isBlank(current.getLastSuccessfulServerId()) && !isBlank(exported.getLastSuccessfulServerId())) {
                String mapped = ctx.resolveServerId(exported.getLastSuccessfulServerId());
                if (mapped != null) {
                    current.setLastSuccessfulServerId(mapped);
                }
            }
            if (isBlank(current.getLastSuccessfulPathId()) && !isBlank(exported.getLastSuccessfulPathId())) {
                String mapped = ctx.resolvePathId(exported.getLastSuccessfulPathId());
                if (mapped != null) {
                    current.setLastSuccessfulPathId(mapped);
                }
            }
            if (isBlank(current.getLastSuccessfulCommandConfigId())
                    && !isBlank(exported.getLastSuccessfulCommandConfigId())) {
                String mapped = ctx.resolveCommandConfigId(exported.getLastSuccessfulCommandConfigId());
                if (mapped != null) {
                    current.setLastSuccessfulCommandConfigId(mapped);
                }
            }
            if (isBlank(current.getLastSuccessfulTiming()) && !isBlank(exported.getLastSuccessfulTiming())) {
                current.setLastSuccessfulTiming(exported.getLastSuccessfulTiming());
            }
            // D-37 关窗快照：本机为空才采纳；上下文 id 按 merger 重映射，解析不出→该引用置 null
            //（CLI 将以"配置不完整"早退点名，与 lastSuccessful* 的"不采纳"不同层：快照是执行依据，宁可残缺可见也不静默丢弃）
            if (current.getSingleUploadTask() == null && exported.getSingleUploadTask() != null) {
                SingleUploadTask src = exported.getSingleUploadTask();
                SingleUploadTask snap = new SingleUploadTask();
                snap.setFilePaths(src.getFilePaths() != null
                        ? new ArrayList<>(src.getFilePaths()) : new ArrayList<>());
                snap.setServerId(isBlank(src.getServerId()) ? null : ctx.resolveServerId(src.getServerId()));
                snap.setPathId(isBlank(src.getPathId()) ? null : ctx.resolvePathId(src.getPathId()));
                snap.setCommandConfigId(isBlank(src.getCommandConfigId())
                        ? null : ctx.resolveCommandConfigId(src.getCommandConfigId()));
                snap.setTiming(src.getTiming());
                current.setSingleUploadTask(snap);
            }
            result.setSingleTaskIdAdopted(adoptedTaskId);

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
            if (payload.getVersion() == null) {
                return lm.get("config.import.versionError");
            }
            // 1.0.8/补定②（清单⑥）：支持 3.0 与 3.1 与 3.2（旧版无新字段→Gson 补 null，天然兼容）；
            // 高于本机支持 → 拒绝并提示升级（防旧插件吞读新文件造成字段静默丢失）
            int cmp = compareVersion(payload.getVersion(), SUPPORTED_VERSION);
            if (cmp > 0) {
                return lm.get("config.import.versionTooNew", payload.getVersion(), SUPPORTED_VERSION);
            }
            if (cmp < 0 && compareVersion(payload.getVersion(), "3.0") < 0) {
                return lm.get("config.import.versionError");
            }
            return null;
        } catch (Exception e) {
            return lm.get("config.import.invalid") + ": " + e.getMessage();
        }
    }

    /** "3.10" vs "3.9" 之类按段数值比较（非字典序）；非法段按 0 处理 */
    static int compareVersion(String a, String b) {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int va = i < pa.length ? parseInt0(pa[i]) : 0;
            int vb = i < pb.length ? parseInt0(pb[i]) : 0;
            if (va != vb) {
                return Integer.compare(va, vb);
            }
        }
        return 0;
    }

    private static int parseInt0(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
