package com.openxt.uploadsshfile.util;

import com.intellij.openapi.application.PathManager;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 路径管理工具类
 * 统一管理插件配置目录和日志目录
 * 
 * 注意：配置目录放在 {IDEA_CONFIG}/uploadsshfile/ 而非 {IDEA_CONFIG}/plugins/uploadsshfile/
 * 这样插件卸载时配置不会被删除，重装后可自动恢复
 */
public class PluginPathManager {
    
    private static final String PLUGIN_CONFIG_DIR = "uploadsshfile";
    
    private static PluginPathManager instance;
    
    private PluginPathManager() {
    }
    
    public static synchronized PluginPathManager getInstance() {
        if (instance == null) {
            instance = new PluginPathManager();
        }
        return instance;
    }
    
    /**
     * 获取插件配置目录
     */
    public Path getConfigPath() {
        String configPath = PathManager.getConfigPath();
        String path = configPath + File.separator + PLUGIN_CONFIG_DIR;
        return Paths.get(path);
    }
    
    /**
     * 获取插件日志目录
     */
    public Path getLogPath() {
        String logPath = PathManager.getLogPath();
        String path = logPath + File.separator + PLUGIN_CONFIG_DIR;
        return Paths.get(path);
    }
    
    /**
     * 确保目录存在
     */
    public Path ensureDirectory(Path path) {
        File dir = path.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return path;
    }
}
