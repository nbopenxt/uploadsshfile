package com.openxt.uploadsshfile.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.ui.ConfigDialog;
import org.jetbrains.annotations.NotNull;

/**
 * 配置管理动作
 * 打开配置管理对话框
 */
public class ConfigAction extends AnAction {

    public ConfigAction() {
        super();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 设置菜单文本（支持国际化）
        LanguageManager lm = LanguageManager.getInstance();
        e.getPresentation().setText(lm.get("menu.config"));
        e.getPresentation().setDescription(lm.get("menu.config.desc"));
        // 始终可用
        e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        // 显示配置对话框
        ConfigDialog dialog = new ConfigDialog(project);
        dialog.show();
    }
}
