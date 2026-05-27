package com.openxt.uploadsshfile.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.KeywordRules;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig.BlacklistConfig;
import com.openxt.uploadsshfile.util.PluginPathManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 统一配置存储
 * 将所有插件配置保存到单个 JSON 文件中
 * 
 * 配置路径: {IDEA_CONFIG}/uploadsshfile/plugin-config.json
 * 
 * 优势:
 * - 所有配置保存在一个文件中，便于管理
 * - 配置放在 IDEA 配置根目录，插件卸载时配置不会被删除
 * - 重装插件后可自动恢复所有配置
 */
public class UnifiedConfigStore {
    
    private static final String CONFIG_FILE = "plugin-config.json";
    private static volatile UnifiedConfigStore instance;
    
    private final Path configPath;
    private final Gson gson;
    private UnifiedPluginConfig cache;
    
    private UnifiedConfigStore() {
        this(PluginPathManager.getInstance().getConfigPath());
    }
    
    private UnifiedConfigStore(@NotNull Path configDir) {
        this.configPath = configDir.resolve(CONFIG_FILE);
        this.gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();
        this.cache = loadFromFile();
        // 确保配置文件存在（首次创建时）
        ensureConfigExists();
    }
    
    /**
     * 确保配置文件存在，如果不存在则创建默认配置
     */
    private void ensureConfigExists() {
        if (!Files.exists(configPath)) {
            saveToFile();
        }
    }
    
    /**
     * 获取单例实例
     */
    public static UnifiedConfigStore getInstance() {
        if (instance == null) {
            synchronized (UnifiedConfigStore.class) {
                if (instance == null) {
                    instance = new UnifiedConfigStore();
                }
            }
        }
        return instance;
    }
    
    /**
     * 重置实例（用于测试）
     */
    public static void resetInstance() {
        synchronized (UnifiedConfigStore.class) {
            instance = null;
        }
    }
    
    /**
     * 使用指定目录创建实例（用于测试）
     * @param configDir 测试配置目录
     * @return 配置存储实例
     */
    public static UnifiedConfigStore createForTest(@NotNull java.nio.file.Path configDir) {
        synchronized (UnifiedConfigStore.class) {
            instance = new UnifiedConfigStore(configDir);
            // 确保使用默认黑名单配置
            instance.cache = new UnifiedPluginConfig();
            instance.cache.getBlacklist().restoreFromPresets();
            return instance;
        }
    }
    
    /**
     * 获取配置（单例缓存）
     */
    public synchronized @NotNull UnifiedPluginConfig getConfig() {
        return cache;
    }
    
    /**
     * 保存配置（使用缓存）
     */
    public synchronized void save() {
        saveToFile();
    }
    
    /**
     * 保存配置
     */
    public synchronized void save(@NotNull UnifiedPluginConfig config) {
        this.cache = config;
        saveToFile();
    }
    
    /**
     * 重置为默认配置
     */
    public synchronized void resetToDefault() {
        this.cache = new UnifiedPluginConfig();
        saveToFile();
    }
    
    private UnifiedPluginConfig loadFromFile() {
        try {
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                UnifiedPluginConfig config = gson.fromJson(json, UnifiedPluginConfig.class);
                if (config != null) {
                    // 确保反序列化后默认命令列表被初始化
                    config.ensureDefaultCommands();
                    return config;
                }
            }
        } catch (IOException e) {
            com.openxt.uploadsshfile.util.Logger.error("Config", "Failed to load config: " + e.getMessage());
        }
        return new UnifiedPluginConfig();
    }
    
    private void saveToFile() {
        try {
            Files.createDirectories(configPath.getParent());
            String json = gson.toJson(cache);
            Files.writeString(configPath, json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save plugin config", e);
        }
    }
    
    /**
     * 获取配置文件路径（用于调试）
     */
    public Path getConfigPath() {
        return configPath;
    }
    
    // ========== 服务器配置操作 ==========
    
    /**
     * 获取所有服务器配置
     */
    public synchronized List<ServerConfig> getAllServers() {
        return cache.getServers();
    }
    
    /**
     * 根据 ID 获取服务器配置
     */
    public synchronized ServerConfig getServer(String serverId) {
        return cache.getServers().stream()
                .filter(s -> s.getId().equals(serverId))
                .findFirst()
                .orElse(null);
    }
    
    /**
     * 添加服务器配置
     */
    public synchronized void addServer(ServerConfig server) {
        cache.getServers().add(server);
        save();
    }
    
    /**
     * 更新服务器配置
     */
    public synchronized void updateServer(ServerConfig server) {
        List<ServerConfig> servers = cache.getServers();
        for (int i = 0; i < servers.size(); i++) {
            if (servers.get(i).getId().equals(server.getId())) {
                servers.set(i, server);
                break;
            }
        }
        save();
    }
    
    /**
     * 删除服务器配置
     */
    public synchronized void deleteServer(String serverId) {
        cache.getServers().removeIf(s -> s.getId().equals(serverId));
        // 同时删除关联的路径配置
        cache.getPaths().removeIf(p -> p.getServerId().equals(serverId));
        // 如果删除的恰好是记忆的服务器，则清除记忆
        if (serverId != null && serverId.equals(cache.getLastSuccessfulServerId())) {
            cache.setLastSuccessfulServerId(null);
        }
        save();
    }

    // ========== 记忆上次成功选择 ==========

    /**
     * 保存上次成功上传/执行的选择（用于下次打开对话框时自动选中）
     */
    public synchronized void saveLastSuccessfulSelection(String serverId, String pathId, String commandConfigId, String timing) {
        cache.setLastSuccessfulServerId(serverId);
        cache.setLastSuccessfulPathId(pathId);
        cache.setLastSuccessfulCommandConfigId(commandConfigId);
        cache.setLastSuccessfulTiming(timing);
        save();
    }

    public synchronized String getLastSuccessfulServerId() {
        return cache.getLastSuccessfulServerId();
    }

    public synchronized String getLastSuccessfulPathId() {
        return cache.getLastSuccessfulPathId();
    }

    public synchronized String getLastSuccessfulCommandConfigId() {
        return cache.getLastSuccessfulCommandConfigId();
    }

    public synchronized String getLastSuccessfulTiming() {
        return cache.getLastSuccessfulTiming();
    }
    
    // ========== 路径配置操作 ==========
    
    /**
     * 获取服务器的所有路径配置
     */
    public synchronized List<PathConfig> getPathsByServer(String serverId) {
        return cache.getPaths().stream()
                .filter(p -> p.getServerId().equals(serverId))
                .collect(Collectors.toList());
    }
    
    /**
     * 添加路径配置
     */
    public synchronized void addPath(PathConfig path) {
        cache.getPaths().add(path);
        save();
    }
    
    /**
     * 更新路径配置
     */
    public synchronized void updatePath(PathConfig path) {
        List<PathConfig> paths = cache.getPaths();
        for (int i = 0; i < paths.size(); i++) {
            if (paths.get(i).getId().equals(path.getId())) {
                paths.set(i, path);
                break;
            }
        }
        save();
    }
    
    /**
     * 删除路径配置
     */
    public synchronized void deletePath(String pathId) {
        cache.getPaths().removeIf(p -> p.getId().equals(pathId));
        // 如果删除的恰好是记忆的路径，则清除记忆
        if (pathId != null && pathId.equals(cache.getLastSuccessfulPathId())) {
            cache.setLastSuccessfulPathId(null);
        }
        save();
    }
    
    // ========== 黑名单配置操作 ==========
    
    /**
     * 获取黑名单配置
     */
    public synchronized BlacklistConfig getBlacklist() {
        return cache.getBlacklist();
    }
    
    /**
     * 设置黑名单配置（不保存到文件，用于测试）
     */
    public synchronized void setBlacklist(@NotNull BlacklistConfig blacklist) {
        cache.setBlacklist(blacklist);
    }
    
    /**
     * 保存黑名单配置
     */
    public synchronized void saveBlacklist(@NotNull BlacklistConfig blacklist) {
        cache.setBlacklist(blacklist);
        save();
    }
    
    /**
     * 重置黑名单为默认值（从预设恢复）
     */
    public synchronized void resetBlacklist() {
        UnifiedPluginConfig.BlacklistConfig newBlacklist = new UnifiedPluginConfig.BlacklistConfig();
        newBlacklist.restoreFromPresets();
        cache.setBlacklist(newBlacklist);
        save();
    }
    
    // ========== 关键词规则配置操作 ==========
    
    /**
     * 获取关键词规则配置
     */
    public synchronized KeywordRules getKeywordRules() {
        return cache.getKeywordRules();
    }
    
    /**
     * 保存关键词规则配置
     */
    public synchronized void saveKeywordRules(@NotNull KeywordRules rules) {
        cache.setKeywordRules(rules);
        save();
    }
    
    // ========== 命令配置操作 ==========
    
    /**
     * 获取所有命令配置
     */
    public synchronized List<CommandConfig> getAllCommandConfigs() {
        return new ArrayList<>(cache.getCommandConfigs());
    }
    
    /**
     * 获取默认命令配置
     */
    public synchronized CommandConfig getDefaultCommandConfig() {
        List<CommandConfig> configs = cache.getCommandConfigs();
        if (configs.isEmpty()) {
            return null;
        }
        return configs.get(0);
    }
    
    /**
     * 根据ID获取命令配置
     */
    public synchronized Optional<CommandConfig> getCommandConfig(String configId) {
        return cache.getCommandConfigs().stream()
                .filter(c -> c.getId().equals(configId))
                .findFirst();
    }
    
    /**
     * 根据服务器ID和路径ID获取命令配置
     */
    public synchronized Optional<CommandConfig> getCommandConfig(String serverId, String pathId) {
        return cache.getCommandConfigs().stream()
                .filter(c -> serverId.equals(c.getServerId()))
                .filter(c -> pathId.equals(c.getPathId()))
                .findFirst();
    }
    
    /**
     * 保存命令配置（新增或更新）
     */
    public synchronized void saveCommandConfig(@NotNull CommandConfig config) {
        // 生成ID
        if (config.getId() == null || config.getId().isEmpty()) {
            config.setId(UUID.randomUUID().toString());
        }
        config.setUpdateTime(System.currentTimeMillis());
        
        List<CommandConfig> configs = cache.getCommandConfigs();
        int index = -1;
        for (int i = 0; i < configs.size(); i++) {
            if (configs.get(i).getId().equals(config.getId())) {
                index = i;
                break;
            }
        }
        
        if (index >= 0) {
            configs.set(index, config);
        } else {
            config.setCreateTime(System.currentTimeMillis());
            configs.add(config);
        }
        save();
    }
    
    /**
     * 删除命令配置
     */
    public synchronized void deleteCommandConfig(String configId) {
        cache.getCommandConfigs().removeIf(c -> c.getId().equals(configId));
        // 如果删除的恰好是记忆的命令组，则清除记忆
        if (configId != null && configId.equals(cache.getLastSuccessfulCommandConfigId())) {
            cache.setLastSuccessfulCommandConfigId(null);
        }
        save();
    }
    
    /**
     * 根据服务器ID和路径ID删除命令配置
     */
    public synchronized void deleteCommandConfigByServerAndPath(String serverId, String pathId) {
        cache.getCommandConfigs().removeIf(c -> 
            serverId.equals(c.getServerId()) && pathId.equals(c.getPathId()));
        save();
    }
}
