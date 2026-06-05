package com.openxt.uploadsshfile.config;

import com.openxt.uploadsshfile.persistence.SecureStorage;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.util.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置管理器
 * 负责配置的加载、保存和管理
 * 现在使用 UnifiedConfigStore 统一管理所有配置
 */
public class ConfigManager {
    
    private static ConfigManager instance;
    
    private final UnifiedConfigStore configStore;
    private final SecureStorage secureStorage;

    /**
     * 判断字符串是否为空（null 或空字符串）
     * 替换 StringUtils.isEmpty
     */
    public static boolean isEmpty(String str) {
        return str == null || str.isEmpty();
    }

    /**
     * 判断字符串是否不为空
     */
    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    private ConfigManager() {
        this.configStore = UnifiedConfigStore.getInstance();
        this.secureStorage = SecureStorage.getInstance();
    }

    public static ConfigManager getInstance() {
        if (instance == null) {
            synchronized (ConfigManager.class) {
                if (instance == null) {
                    instance = new ConfigManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取统一配置存储
     */
    public UnifiedConfigStore getConfigStore() {
        return configStore;
    }

    /**
     * 获取所有服务器配置（过滤无效记录）
     */
    public List<ServerConfig> getAllServers() {
        Logger.debug("ConfigManager", "getAllServers() called");
        List<ServerConfig> servers = configStore.getAllServers();
        // 过滤无效记录
        servers.removeIf(s -> s == null || isEmpty(s.getId()) || isEmpty(s.getName()));
        Logger.debug("ConfigManager", "getAllServers() returning " + servers.size() + " servers");
        return new ArrayList<>(servers);
    }

    /**
     * 获取服务器配置
     */
    public ServerConfig getServer(String serverId) {
        return configStore.getServer(serverId);
    }

    /**
     * 添加服务器配置
     */
    public void addServer(ServerConfig server) {
        Logger.debug("ConfigManager", "addServer called, id=" + server.getId() + ", name=" + server.getName());
        
        // 如果有密码，先存储密码（不保存到JSON）
        if (!isEmpty(server.getPassword())) {
            Logger.debug("ConfigManager", "Storing password for id=" + server.getId());
            try {
                secureStorage.store(server.getId(), server.getPassword());
                Logger.debug("ConfigManager", "Password stored successfully");
            } catch (Exception e) {
                Logger.error("ConfigManager", "Password storage failed", e);
            }
            server.setPassword(null); // 不保存密码到文件
        }
        
        configStore.addServer(server);
        Logger.debug("ConfigManager", "Server added successfully");
    }

    /**
     * 更新服务器配置
     */
    public void updateServer(ServerConfig server) {
        // 如果有密码，先存储密码
        if (!isEmpty(server.getPassword())) {
            secureStorage.store(server.getId(), server.getPassword());
            server.setPassword(null);
        }
        server.setUpdateTime(System.currentTimeMillis());
        configStore.updateServer(server);
    }

    /**
     * 删除服务器配置
     */
    public void deleteServer(String serverId) {
        configStore.deleteServer(serverId);
        secureStorage.delete(serverId);
    }

    /**
     * 获取服务器的所有路径配置（过滤无效记录）
     */
    public List<PathConfig> getPathsByServer(String serverId) {
        List<PathConfig> result = new ArrayList<>();
        for (PathConfig p : configStore.getPathsByServer(serverId)) {
            if (p != null && !isEmpty(p.getServerId()) && !isEmpty(p.getRemotePath())) {
                result.add(p);
            }
        }
        return result;
    }

    /**
     * 添加路径配置
     */
    public void addPath(PathConfig path) {
        configStore.addPath(path);
    }

    /**
     * 更新路径配置
     */
    public void updatePath(PathConfig path) {
        path.setUpdateTime(System.currentTimeMillis());
        configStore.updatePath(path);
    }

    /**
     * 删除路径配置
     */
    public void deletePath(String pathId) {
        configStore.deletePath(pathId);
    }

    /**
     * 获取密码
     */
    public String getPassword(String serverId) {
        return secureStorage.retrieve(serverId);
    }

    /**
     * 保存密码到安全存储
     */
    public void savePassword(String serverId, String password) {
        if (!isEmpty(password)) {
            Logger.debug("ConfigManager", "Storing password for id=" + serverId);
            try {
                secureStorage.store(serverId, password);
                Logger.debug("ConfigManager", "Password stored successfully");
            } catch (Exception e) {
                Logger.error("ConfigManager", "Password storage failed", e);
            }
        }
    }
}
