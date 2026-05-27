package com.openxt.uploadsshfile.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.ui.CommandConfigDialog;
import org.jetbrains.annotations.NotNull;

/**
 * 打开命令配置对话框 Action
 */
public class OpenConfigDialogAction extends AnAction {
    
    public OpenConfigDialogAction() {
        super();
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        // 设置菜单文本（支持国际化）
        LanguageManager lm = LanguageManager.getInstance();
        e.getPresentation().setText(lm.get("menu.command.config"));
        e.getPresentation().setDescription(lm.get("menu.command.config.desc"));
        // 始终可用
        e.getPresentation().setEnabled(true);
    }
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        
        CommandConfigDialog dialog = new CommandConfigDialog();
        dialog.show();
        
        if (dialog.isOK()) {
            // 配置已保存
        }
    }
}
