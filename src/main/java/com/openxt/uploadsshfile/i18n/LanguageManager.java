package com.openxt.uploadsshfile.i18n;

import com.intellij.openapi.application.PathManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.util.Logger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.*;

/**
 * 语言管理器
 * 负责加载语言资源和切换语言
 */
public class LanguageManager {
    private static LanguageManager instance;
    
    /**
     * 支持的语言列表
     */
    public static final List<LanguageInfo> SUPPORTED_LANGUAGES = Arrays.asList(
        new LanguageInfo("en", "English"),
        new LanguageInfo("zh", "中文"),
        new LanguageInfo("de", "Deutsch"),
        new LanguageInfo("fr", "Français"),
        new LanguageInfo("es", "Español"),
        new LanguageInfo("ja", "日本語"),
        new LanguageInfo("ko", "한국어"),
        new LanguageInfo("ar", "العربية")
    );
    
    private String currentLanguage;
    private Properties messages;
    
    /**
     * 语言信息
     */
    public static class LanguageInfo {
        public final String code;
        public final String displayName;
        
        public LanguageInfo(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    private LanguageManager() {
        messages = new Properties();
        loadLanguagePreference();
        loadMessages();
    }
    
    public static LanguageManager getInstance() {
        if (instance == null) {
            synchronized (LanguageManager.class) {
                if (instance == null) {
                    instance = new LanguageManager();
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取当前语言代码
     */
    public String getCurrentLanguage() {
        return currentLanguage;
    }
    
    /**
     * 设置语言
     */
    public void setLanguage(String languageCode) {
        if (languageCode == null || languageCode.isEmpty()) {
            languageCode = "en";
        }
        
        this.currentLanguage = languageCode;
        saveLanguagePreference();
        loadMessages();
        Logger.debug("LanguageManager", "Language changed to: " + languageCode);
    }
    
    /**
     * 获取本地化文本
     */
    public String get(String key) {
        return messages.getProperty(key, key);
    }
    
    /**
     * 获取本地化文本，支持参数替换
     */
    public String get(String key, Object... args) {
        String text = messages.getProperty(key, key);
        try {
            // 使用 {0}, {1}, {2}... 格式占位符，替换为对应参数
            MessageFormat mf = new MessageFormat(text);
            return mf.format(args);
        } catch (Exception e) {
            return text;
        }
    }
    
    /**
     * 加载语言偏好设置（从统一配置中读取）
     */
    private void loadLanguagePreference() {
        try {
            // 从统一配置中获取语言设置
            UnifiedConfigStore configStore = UnifiedConfigStore.getInstance();
            currentLanguage = configStore.getConfig().getLanguage();
            if (currentLanguage == null || currentLanguage.isEmpty()) {
                currentLanguage = "en";
            }
            Logger.debug("LanguageManager", "Loaded language preference: " + currentLanguage);
        } catch (Exception e) {
            Logger.error("LanguageManager", "Failed to load language preference", e);
            currentLanguage = "en";
        }
    }
    
    /**
     * 保存语言偏好设置（保存到统一配置中）
     */
    private void saveLanguagePreference() {
        try {
            UnifiedConfigStore configStore = UnifiedConfigStore.getInstance();
            configStore.getConfig().setLanguage(currentLanguage);
            configStore.save();
            Logger.debug("LanguageManager", "Saved language preference: " + currentLanguage);
        } catch (Exception e) {
            Logger.error("LanguageManager", "Failed to save language preference", e);
        }
    }
    
    /**
     * 加载语言资源文件
     */
    private void loadMessages() {
        messages.clear();
        
        // 先加载默认的英文资源
        Properties defaultMessages = new Properties();
        try (InputStream is = getClass().getResourceAsStream("/messages/messages.properties")) {
            if (is != null) {
                // 使用 UTF-8 编码读取 properties 文件
                defaultMessages.load(new InputStreamReader(is, StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Logger.error("LanguageManager", "Failed to load default messages", e);
        }
        messages.putAll(defaultMessages);
        
        // 如果不是英文，再加载对应语言的资源（会覆盖默认的英文）
        if (!"en".equals(currentLanguage)) {
            try (InputStream is = getClass().getResourceAsStream("/messages/messages_" + currentLanguage + ".properties")) {
                if (is != null) {
                    Properties localizedMessages = new Properties();
                    // 使用 UTF-8 编码读取 properties 文件
                    localizedMessages.load(new InputStreamReader(is, StandardCharsets.UTF_8));
                    messages.putAll(localizedMessages);
                    Logger.debug("LanguageManager", "Loaded localized messages for: " + currentLanguage);
                } else {
                    Logger.debug("LanguageManager", "No localized messages found for: " + currentLanguage);
                }
            } catch (Exception e) {
                Logger.error("LanguageManager", "Failed to load localized messages", e);
            }
        }
    }
    
    /**
     * 获取支持的语言列表
     */
    public static List<LanguageInfo> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }
    
    /**
     * 根据代码获取语言显示名称
     */
    public static String getLanguageDisplayName(String code) {
        for (LanguageInfo lang : SUPPORTED_LANGUAGES) {
            if (lang.code.equals(code)) {
                return lang.displayName;
            }
        }
        return code;
    }
}
