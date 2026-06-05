package com.openxt.uploadsshfile.importexport;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置导入结果摘要。
 */
public class ImportResult {
    // 列表型配置统计
    private int serversAdded, serversUpdated, serversSkipped;
    private int pathsAdded, pathsUpdated, pathsSkipped;
    private int commandConfigsAdded, commandConfigsUpdated, commandConfigsSkipped;
    private int batchTasksAdded, batchTasksUpdated, batchTasksSkipped;

    // 单对象配置统计
    private List<String> aiConfigImportedModels = new ArrayList<>();
    private int blacklistMergedCount;
    private int keywordMergedCount;
    private int hasOutputCommandMergedCount;

    public int getServersAdded() { return serversAdded; }
    public void setServersAdded(int serversAdded) { this.serversAdded = serversAdded; }

    public int getServersUpdated() { return serversUpdated; }
    public void setServersUpdated(int serversUpdated) { this.serversUpdated = serversUpdated; }

    public int getServersSkipped() { return serversSkipped; }
    public void setServersSkipped(int serversSkipped) { this.serversSkipped = serversSkipped; }

    public int getPathsAdded() { return pathsAdded; }
    public void setPathsAdded(int pathsAdded) { this.pathsAdded = pathsAdded; }

    public int getPathsUpdated() { return pathsUpdated; }
    public void setPathsUpdated(int pathsUpdated) { this.pathsUpdated = pathsUpdated; }

    public int getPathsSkipped() { return pathsSkipped; }
    public void setPathsSkipped(int pathsSkipped) { this.pathsSkipped = pathsSkipped; }

    public int getCommandConfigsAdded() { return commandConfigsAdded; }
    public void setCommandConfigsAdded(int commandConfigsAdded) { this.commandConfigsAdded = commandConfigsAdded; }

    public int getCommandConfigsUpdated() { return commandConfigsUpdated; }
    public void setCommandConfigsUpdated(int commandConfigsUpdated) { this.commandConfigsUpdated = commandConfigsUpdated; }

    public int getCommandConfigsSkipped() { return commandConfigsSkipped; }
    public void setCommandConfigsSkipped(int commandConfigsSkipped) { this.commandConfigsSkipped = commandConfigsSkipped; }

    public int getBatchTasksAdded() { return batchTasksAdded; }
    public void setBatchTasksAdded(int batchTasksAdded) { this.batchTasksAdded = batchTasksAdded; }

    public int getBatchTasksUpdated() { return batchTasksUpdated; }
    public void setBatchTasksUpdated(int batchTasksUpdated) { this.batchTasksUpdated = batchTasksUpdated; }

    public int getBatchTasksSkipped() { return batchTasksSkipped; }
    public void setBatchTasksSkipped(int batchTasksSkipped) { this.batchTasksSkipped = batchTasksSkipped; }

    public List<String> getAiConfigImportedModels() { return aiConfigImportedModels; }
    public void setAiConfigImportedModels(List<String> aiConfigImportedModels) { this.aiConfigImportedModels = aiConfigImportedModels; }

    public int getBlacklistMergedCount() { return blacklistMergedCount; }
    public void setBlacklistMergedCount(int blacklistMergedCount) { this.blacklistMergedCount = blacklistMergedCount; }

    public int getKeywordMergedCount() { return keywordMergedCount; }
    public void setKeywordMergedCount(int keywordMergedCount) { this.keywordMergedCount = keywordMergedCount; }

    public int getHasOutputCommandMergedCount() { return hasOutputCommandMergedCount; }
    public void setHasOutputCommandMergedCount(int hasOutputCommandMergedCount) { this.hasOutputCommandMergedCount = hasOutputCommandMergedCount; }
}
