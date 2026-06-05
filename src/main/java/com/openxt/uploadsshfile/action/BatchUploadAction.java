package com.openxt.uploadsshfile.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.ui.BatchTaskDialog;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 批处理上传 Action
 * 右键菜单入口：批处理上传
 *
 * L1 信息边界层 - 入口定义
 */
public class BatchUploadAction extends AnAction {

    public BatchUploadAction() {
        super();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        LanguageManager lm = LanguageManager.getInstance();
        e.getPresentation().setText(lm.get("menu.upload.batch"));
        e.getPresentation().setDescription(lm.get("menu.upload.batch.desc"));
        e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        List<String> selectedPaths = new ArrayList<>();
        DataContext dataContext = e.getDataContext();
        VirtualFile[] virtualFiles = LangDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
        if (virtualFiles != null) {
            for (VirtualFile vf : virtualFiles) {
                if (vf.exists()) {
                    selectedPaths.add(vf.getPath());
                }
            }
        }

        BatchTaskDialog dialog = new BatchTaskDialog(project, selectedPaths);
        dialog.showDialog();
    }
}
