package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 路径合并器。
 * 比对依据：服务器(host+port) + remotePath
 * 依赖 ServerMerger 建立的 serverIdMap。
 */
public class PathMerger implements ConfigMerger<List<PathConfig>> {

    private final MergeCounts counts = new MergeCounts();

    @Override
    public List<PathConfig> merge(List<PathConfig> exported, List<PathConfig> current, RemapContext ctx) {
        List<PathConfig> result = new ArrayList<>(current);

        for (PathConfig exportedPath : exported) {
            // 1. 解析旧 serverId → 新 serverId
            String newServerId = ctx.resolveServerId(exportedPath.getServerId());
            if (newServerId == null) {
                counts.incrementSkipped();
                Logger.debug("PathMerger", "Skipped path (server not matched): " + exportedPath.getRemotePath());
                continue;
            }

            // 2. 在新 serverId 下找匹配的 remotePath
            PathConfig match = findMatch(result, newServerId, exportedPath.getRemotePath());
            if (match != null) {
                String oldId = exportedPath.getId();
                match.setRemotePath(exportedPath.getRemotePath());
                match.setUpdateTime(System.currentTimeMillis());
                ctx.putPath(oldId, match.getId());
                counts.incrementUpdated();
                Logger.debug("PathMerger", "Updated path: " + exportedPath.getRemotePath());
            } else {
                PathConfig newPath = new PathConfig();
                newPath.setId(UUID.randomUUID().toString());
                newPath.setServerId(newServerId);
                newPath.setRemotePath(exportedPath.getRemotePath());
                newPath.setCreateTime(System.currentTimeMillis());
                newPath.setUpdateTime(System.currentTimeMillis());
                result.add(newPath);
                ctx.putPath(exportedPath.getId(), newPath.getId());
                counts.incrementAdded();
                Logger.debug("PathMerger", "Added path: " + newPath.getRemotePath());
            }
        }

        return result;
    }

    private PathConfig findMatch(List<PathConfig> current, String serverId, String remotePath) {
        for (PathConfig p : current) {
            if (p.getServerId() != null && p.getServerId().equals(serverId)
                    && p.getRemotePath() != null && p.getRemotePath().equals(remotePath)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public MergeCounts getMergeCounts() { return counts; }
}
