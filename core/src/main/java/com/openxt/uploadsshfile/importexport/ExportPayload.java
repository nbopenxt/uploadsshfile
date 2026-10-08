package com.openxt.uploadsshfile.importexport;

import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.batch.BatchTask;
import com.openxt.uploadsshfile.model.AIConfig;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.KeywordRules;
import com.openxt.uploadsshfile.model.SingleUploadTask;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;

import java.util.List;

/**
 * 配置导出数据传输对象。
 * 独立于 UnifiedPluginConfig，包含导出元数据和加密密码。
 */
public class ExportPayload {
    private String version;             // 1.0.8 起 "3.1"（新增 5 字段；旧插件读 3.1 一律拒绝导入，补定②）；D-37 起 "3.2"（新增关窗快照）
    private String exportTime;          // ISO 8601 格式
    private String exportSource;        // "UploadSSHFile Plugin"

    /**
     * 1.0.8 / D-11（清单⑥）新增 5 字段：单例任务 ID 与 lastSuccessful* 四项。
     * 用途：新机器导入后 CLI 单任务上下文不再为空（否则首跑必退码 2）。
     * 导入策略＝**本机为空才采纳、非空保留本机值**（D-10，ConfigImporter 实现）。
     */
    private String singleUploadTaskId;
    private String lastSuccessfulServerId;
    private String lastSuccessfulPathId;
    private String lastSuccessfulCommandConfigId;
    private String lastSuccessfulTiming;

    /** 1.0.8 / D-37：单任务关窗快照（文件清单+上下文引用）。旧导出无此字段→Gson 补 null 兼容 */
    private SingleUploadTask singleUploadTask;

    private List<ServerConfig> servers;
    private List<PathConfig> paths;
    private List<CommandConfig> commandConfigs;
    private List<BatchTask> batchTasks;
    private AIConfig aiConfig;
    private UnifiedPluginConfig.BlacklistConfig blacklist;
    private KeywordRules keywordRules;
    private UnifiedPluginConfig.CommandOutputConfig hasOutputCommands;

    public ExportPayload() {
        this.version = "3.2";
        this.exportSource = "UploadSSHFile Plugin";
    }

    public String getSingleUploadTaskId() { return singleUploadTaskId; }
    public void setSingleUploadTaskId(String v) { this.singleUploadTaskId = v; }

    public SingleUploadTask getSingleUploadTask() { return singleUploadTask; }
    public void setSingleUploadTask(SingleUploadTask v) { this.singleUploadTask = v; }
    public String getLastSuccessfulServerId() { return lastSuccessfulServerId; }
    public void setLastSuccessfulServerId(String v) { this.lastSuccessfulServerId = v; }
    public String getLastSuccessfulPathId() { return lastSuccessfulPathId; }
    public void setLastSuccessfulPathId(String v) { this.lastSuccessfulPathId = v; }
    public String getLastSuccessfulCommandConfigId() { return lastSuccessfulCommandConfigId; }
    public void setLastSuccessfulCommandConfigId(String v) { this.lastSuccessfulCommandConfigId = v; }
    public String getLastSuccessfulTiming() { return lastSuccessfulTiming; }
    public void setLastSuccessfulTiming(String v) { this.lastSuccessfulTiming = v; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getExportTime() { return exportTime; }
    public void setExportTime(String exportTime) { this.exportTime = exportTime; }

    public String getExportSource() { return exportSource; }
    public void setExportSource(String exportSource) { this.exportSource = exportSource; }

    public List<ServerConfig> getServers() { return servers; }
    public void setServers(List<ServerConfig> servers) { this.servers = servers; }

    public List<PathConfig> getPaths() { return paths; }
    public void setPaths(List<PathConfig> paths) { this.paths = paths; }

    public List<CommandConfig> getCommandConfigs() { return commandConfigs; }
    public void setCommandConfigs(List<CommandConfig> commandConfigs) { this.commandConfigs = commandConfigs; }

    public List<BatchTask> getBatchTasks() { return batchTasks; }
    public void setBatchTasks(List<BatchTask> batchTasks) { this.batchTasks = batchTasks; }

    public AIConfig getAiConfig() { return aiConfig; }
    public void setAiConfig(AIConfig aiConfig) { this.aiConfig = aiConfig; }

    public UnifiedPluginConfig.BlacklistConfig getBlacklist() { return blacklist; }
    public void setBlacklist(UnifiedPluginConfig.BlacklistConfig blacklist) { this.blacklist = blacklist; }

    public KeywordRules getKeywordRules() { return keywordRules; }
    public void setKeywordRules(KeywordRules keywordRules) { this.keywordRules = keywordRules; }

    public UnifiedPluginConfig.CommandOutputConfig getHasOutputCommands() { return hasOutputCommands; }
    public void setHasOutputCommands(UnifiedPluginConfig.CommandOutputConfig hasOutputCommands) { this.hasOutputCommands = hasOutputCommands; }
}
