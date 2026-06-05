package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.persistence.SecureStorage;
import com.openxt.uploadsshfile.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 服务器合并器。
 * 比对依据：host + port
 */
public class ServerMerger implements ConfigMerger<List<ServerConfig>> {

    private final MergeCounts counts = new MergeCounts();
    private final SecureStorage secureStorage = SecureStorage.getInstance();

    @Override
    public List<ServerConfig> merge(List<ServerConfig> exported, List<ServerConfig> current, RemapContext ctx) {
        List<ServerConfig> result = new ArrayList<>(current);

        for (ServerConfig exportedServer : exported) {
            ServerConfig match = findMatch(result, exportedServer);
            if (match != null) {
                // B 已有 → 覆盖（保留 B 的 ID）
                String oldId = exportedServer.getId();
                String newId = match.getId();

                match.setName(exportedServer.getName());
                match.setHost(exportedServer.getHost());
                match.setPort(exportedServer.getPort());
                match.setUsername(exportedServer.getUsername());
                match.setOsType(exportedServer.getOsType());
                match.setUpdateTime(System.currentTimeMillis());

                // 密码加密值写入 SecureStorage
                if (exportedServer.getPassword() != null && !exportedServer.getPassword().isEmpty()) {
                    secureStorage.store(newId, exportedServer.getPassword());
                }

                ctx.putServer(oldId, newId);
                counts.incrementUpdated();
                Logger.debug("ServerMerger", "Updated server: " + exportedServer.getHost() + ":" + exportedServer.getPort());
            } else {
                // B 没有 → 新增
                ServerConfig newServer = new ServerConfig();
                newServer.setId(UUID.randomUUID().toString());
                newServer.setName(exportedServer.getName());
                newServer.setHost(exportedServer.getHost());
                newServer.setPort(exportedServer.getPort());
                newServer.setUsername(exportedServer.getUsername());
                newServer.setOsType(exportedServer.getOsType());
                newServer.setCreateTime(System.currentTimeMillis());
                newServer.setUpdateTime(System.currentTimeMillis());

                // 密码加密值写入 SecureStorage
                if (exportedServer.getPassword() != null && !exportedServer.getPassword().isEmpty()) {
                    secureStorage.store(newServer.getId(), exportedServer.getPassword());
                }

                result.add(newServer);
                ctx.putServer(exportedServer.getId(), newServer.getId());
                counts.incrementAdded();
                Logger.debug("ServerMerger", "Added server: " + newServer.getHost() + ":" + newServer.getPort());
            }
        }

        return result;
    }

    private ServerConfig findMatch(List<ServerConfig> current, ServerConfig exported) {
        for (ServerConfig s : current) {
            if (s.getHost() != null && s.getHost().equals(exported.getHost())
                    && s.getPort() == exported.getPort()) {
                return s;
            }
        }
        return null;
    }

    @Override
    public MergeCounts getMergeCounts() { return counts; }
}
