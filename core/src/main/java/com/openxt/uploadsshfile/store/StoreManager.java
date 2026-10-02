package com.openxt.uploadsshfile.store;

import com.openxt.uploadsshfile.model.AIConfig;

/**
 * Store 管理器
 * 统一管理所有配置存储实例
 * 使用单例模式，确保所有配置使用同一个存储实例
 * 
 * 配置统一保存在 plugin-config.json 中
 * 路径: {IDEA_CONFIG}/uploadsshfile/plugin-config.json
 */
public class StoreManager {
    
    private static volatile StoreManager instance;
    
    private final UnifiedConfigStore unifiedConfigStore;
    
    private StoreManager() {
        this.unifiedConfigStore = UnifiedConfigStore.getInstance();
    }
    
    /**
     * 获取 StoreManager 单例实例
     */
    public static StoreManager getInstance() {
        if (instance == null) {
            synchronized (StoreManager.class) {
                if (instance == null) {
                    instance = new StoreManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取统一配置存储（包含所有配置）
     */
    public UnifiedConfigStore getUnifiedConfigStore() {
        return unifiedConfigStore;
    }
    
    /**
     * 获取 AI 配置（从统一配置中获取）
     */
    public AIConfig getAIConfig() {
        return unifiedConfigStore.getConfig().getAiConfig();
    }
    
    /**
     * 重新加载所有配置（从文件）
     * 通常在配置被外部修改后调用
     */
    public void reloadAll() {
        // 重新创建实例以强制从文件加载
        synchronized (StoreManager.class) {
            instance = new StoreManager();
        }
    }
}
