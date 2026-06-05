package com.openxt.uploadsshfile.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.ui.ImportExportConfigDialog;
import org.jetbrains.annotations.NotNull;

/**
 * 导入导出配置 Action
 * 右键菜单入口：导入导出配置（Tab 分页：导出 Tab + 导入 Tab）
 *
 * L1 信息边界层 - 入口定义
 */
public class ImportExportConfigAction extends AnAction {

    public ImportExportConfigAction() {
        super();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        LanguageManager lm = LanguageManager.getInstance();
        e.getPresentation().setText(lm.get("menu.import.export"));
        e.getPresentation().setDescription(lm.get("menu.import.export.desc"));
        e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        ImportExportConfigDialog dialog = new ImportExportConfigDialog(project);
        dialog.showDialog();
    }
}
