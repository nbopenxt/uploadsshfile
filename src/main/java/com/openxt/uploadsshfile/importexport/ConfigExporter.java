package com.openxt.uploadsshfile.importexport;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.openxt.uploadsshfile.config.ConfigManager;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import com.openxt.uploadsshfile.persistence.SecureStorage;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.util.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 配置导出器。
 * 从 UnifiedConfigStore + SecureStorage 读取全部配置，
 * 构建 ExportPayload DTO，序列化为 JSON 文件。
 */
public class ConfigExporter {

    private final UnifiedConfigStore configStore;
    private final SecureStorage secureStorage;
    private final Gson gson;

    public ConfigExporter() {
        this(UnifiedConfigStore.getInstance(), SecureStorage.getInstance());
    }

    /** 用于测试 - 允许注入测试专用 store 和 secureStorage */
    public ConfigExporter(UnifiedConfigStore configStore, SecureStorage secureStorage) {
        this.configStore = configStore;
        this.secureStorage = secureStorage;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * 导出全部配置到指定文件。
     * @param targetFile 目标 JSON 文件路径
     * @throws IOException 导出失败
     */
    public void export(File targetFile) throws IOException {
        Logger.debug("ConfigExporter", "export() to " + targetFile.getAbsolutePath());

        UnifiedPluginConfig config = configStore.getConfig();
        ExportPayload payload = new ExportPayload();
        payload.setExportTime(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        // 服务器配置：注入加密密码
        List<ServerConfig> servers = new ArrayList<>();
        for (ServerConfig s : config.getServers()) {
            ServerConfig copy = copyServerConfig(s);
            servers.add(copy);
        }
        payload.setServers(servers);

        // 路径配置
        payload.setPaths(copyList(config.getPaths()));

        // 命令配置
        payload.setCommandConfigs(copyList(config.getCommandConfigs()));

        // 批处理任务
        payload.setBatchTasks(copyList(config.getBatchTasks()));

        // AI 配置
        payload.setAiConfig(config.getAiConfig());

        // 黑名单
        payload.setBlacklist(config.getBlacklist());

        // 关键词规则
        payload.setKeywordRules(config.getKeywordRules());

        // 有返回命令
        payload.setHasOutputCommands(config.getHasOutputCommands());

        // 写入文件
        String json = gson.toJson(payload);
        Files.writeString(targetFile.toPath(), json, StandardCharsets.UTF_8);

        Logger.debug("ConfigExporter", "export() completed successfully");
    }

    private ServerConfig copyServerConfig(ServerConfig src) {
        ServerConfig copy = new ServerConfig();
        copy.setId(src.getId());
        copy.setName(src.getName());
        copy.setHost(src.getHost());
        copy.setPort(src.getPort());
        copy.setUsername(src.getUsername());
        copy.setOsType(src.getOsType());
        copy.setCreateTime(src.getCreateTime());
        copy.setUpdateTime(src.getUpdateTime());

        // 从 SecureStorage 读取加密密码值
        String encryptedPwd = secureStorage.retrieve(src.getId());
        copy.setPassword(encryptedPwd);
        return copy;
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> copyList(List<T> src) {
        return src != null ? new ArrayList<>(src) : new ArrayList<>();
    }
}
