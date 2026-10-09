package com.openxt.uploadsshfile.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.startup.IdeBootstrap;
import com.openxt.uploadsshfile.ui.AIConfigDialog;
import org.jetbrains.annotations.NotNull;

/**
 * 打开 AI 配置对话框 Action
 */
public class OpenAIConfigDialogAction extends AnAction {
    
    public OpenAIConfigDialogAction() {
        super();
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        // D-26：菜单展开可早于启动预热回调（D-45），统一入口幂等保证路径注入
        // D-31：ensureReady＝注入＋bat 自愈（热载半生态点菜单即补齐）
        IdeBootstrap.ensureReady();
        LanguageManager lm = LanguageManager.getInstance();
        e.getPresentation().setText(lm.get("ai.config.menu"));
        e.getPresentation().setDescription(lm.get("ai.config.menu.desc"));
        e.getPresentation().setEnabled(true);
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        
        AIConfigDialog dialog = new AIConfigDialog();
        if (dialog.showAndGet()) {
            // 配置已保存
        }
    }
}
