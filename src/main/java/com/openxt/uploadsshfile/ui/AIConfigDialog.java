package com.openxt.uploadsshfile.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * AI 配置对话框
 * 
 * 使用统一配置存储 plugin-config.json
 */
public class AIConfigDialog extends DialogWrapper {
    
    private final AIConfigPanel configPanel;
    private final LanguageManager lang = LanguageManager.getInstance();
    
    public AIConfigDialog() {
        super(true); // modal
        setTitle(lang.get("ai.config.title"));
        setOKButtonText(lang.get("ai.config.save"));
        
        // 使用默认构造函数，自动使用 UnifiedConfigStore
        configPanel = new AIConfigPanel();
        
        init();
    }
    
    @Override
    protected void init() {
        super.init();
    }
    
    @Override
    protected void doOKAction() {
        // 点击确定时保存所有配置
        configPanel.saveAllConfig();
        super.doOKAction();
    }
    
    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return configPanel;
    }
}
