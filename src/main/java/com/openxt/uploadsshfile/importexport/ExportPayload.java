package com.openxt.uploadsshfile.importexport;

import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.batch.BatchTask;
import com.openxt.uploadsshfile.model.AIConfig;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.KeywordRules;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;

import java.util.List;

/**
 * 配置导出数据传输对象。
 * 独立于 UnifiedPluginConfig，包含导出元数据和加密密码。
 */
public class ExportPayload {
    private String version;             // "3.0"
    private String exportTime;          // ISO 8601 格式
    private String exportSource;        // "UploadSSHFile Plugin"

    private List<ServerConfig> servers;
    private List<PathConfig> paths;
    private List<CommandConfig> commandConfigs;
    private List<BatchTask> batchTasks;
    private AIConfig aiConfig;
    private UnifiedPluginConfig.BlacklistConfig blacklist;
    private KeywordRules keywordRules;
    private UnifiedPluginConfig.CommandOutputConfig hasOutputCommands;

    public ExportPayload() {
        this.version = "3.0";
        this.exportSource = "UploadSSHFile Plugin";
    }

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
