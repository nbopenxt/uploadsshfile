package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 命令配置合并器。
 * 比对依据：服务器(host+port) + 路径(remotePath) + name
 * 依赖 ServerMerger 和 PathMerger 建立的映射。
 */
public class CommandConfigMerger implements ConfigMerger<List<CommandConfig>> {

    private final MergeCounts counts = new MergeCounts();

    @Override
    public List<CommandConfig> merge(List<CommandConfig> exported, List<CommandConfig> current, RemapContext ctx) {
        List<CommandConfig> result = new ArrayList<>(current);

        for (CommandConfig exportedCmd : exported) {
            String newServerId = ctx.resolveServerId(exportedCmd.getServerId());
            String newPathId = ctx.resolvePathId(exportedCmd.getPathId());
            if (newServerId == null || newPathId == null) {
                counts.incrementSkipped();
                continue;
            }

            CommandConfig match = findMatch(result, newServerId, newPathId, exportedCmd.getName());
            if (match != null) {
                match.setName(exportedCmd.getName());
                match.setExecuteTiming(exportedCmd.getExecuteTiming());
                match.setCommands(exportedCmd.getCommands());
                match.setMaxRetries(exportedCmd.getMaxRetries());
                match.setCommandTimeoutMs(exportedCmd.getCommandTimeoutMs());
                match.setUpdateTime(System.currentTimeMillis());
                ctx.putCommandConfig(exportedCmd.getId(), match.getId());
                counts.incrementUpdated();
                Logger.debug("CommandConfigMerger", "Updated command config: " + exportedCmd.getName());
            } else {
                CommandConfig newCmd = new CommandConfig();
                newCmd.setId(UUID.randomUUID().toString());
                newCmd.setName(exportedCmd.getName());
                newCmd.setServerId(newServerId);
                newCmd.setPathId(newPathId);
                newCmd.setExecuteTiming(exportedCmd.getExecuteTiming());
                newCmd.setCommands(exportedCmd.getCommands());
                newCmd.setMaxRetries(exportedCmd.getMaxRetries());
                newCmd.setCommandTimeoutMs(exportedCmd.getCommandTimeoutMs());
                newCmd.setCreateTime(System.currentTimeMillis());
                newCmd.setUpdateTime(System.currentTimeMillis());
                result.add(newCmd);
                ctx.putCommandConfig(exportedCmd.getId(), newCmd.getId());
                counts.incrementAdded();
                Logger.debug("CommandConfigMerger", "Added command config: " + newCmd.getName());
            }
        }

        return result;
    }

    private CommandConfig findMatch(List<CommandConfig> current, String serverId, String pathId, String name) {
        for (CommandConfig c : current) {
            if (c.getServerId() != null && c.getServerId().equals(serverId)
                    && c.getPathId() != null && c.getPathId().equals(pathId)
                    && c.getName() != null && c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    @Override
    public MergeCounts getMergeCounts() { return counts; }
}
