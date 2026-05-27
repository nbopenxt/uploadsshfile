package com.openxt.uploadsshfile.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.openxt.uploadsshfile.i18n.LanguageManager;
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
        dialog.show();
        
        if (dialog.isOK()) {
            // 配置已保存
        }
    }
}
