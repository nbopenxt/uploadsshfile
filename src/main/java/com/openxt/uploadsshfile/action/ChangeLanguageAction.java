package com.openxt.uploadsshfile.action;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.ui.Messages;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.startup.IdeBootstrap;
import org.jetbrains.annotations.NotNull;

/**
 * 切换语言 ToggleAction
 * isSelected 返回 true 时菜单项前显示勾选标记
 */
public class ChangeLanguageAction extends ToggleAction {

    private final String languageCode;
    private final String displayName;

    public ChangeLanguageAction(String languageCode, String displayName) {
        super(displayName, null, null);
        this.languageCode = languageCode;
        this.displayName = displayName;
    }

    @Override
    public boolean isSelected(@NotNull AnActionEvent e) {
        // D-26：勾选态渲染发生在菜单展开期，统一入口幂等保证路径注入
        // D-31：ensureReady＝注入＋bat 自愈（热载半生态点菜单即补齐）
        IdeBootstrap.ensureReady();
        return LanguageManager.getInstance().getCurrentLanguage().equals(languageCode);
    }

    @Override
    public void setSelected(@NotNull AnActionEvent e, boolean state) {
        if (!state) {
            // ToggleAction 取消勾选时不处理（不支持取消语言设为空）
            return;
        }
        String current = LanguageManager.getInstance().getCurrentLanguage();
        if (current.equals(languageCode)) {
            return; // 已经是该语言，不重复设置
        }

        LanguageManager.getInstance().setLanguage(languageCode);

        // 提示用户重启 IDE 后生效
        String title = LanguageManager.getInstance().get("language.changed.title");
        String message = LanguageManager.getInstance().get("language.changed.restart");
        Messages.showInfoMessage(
                e.getProject(),
                message,
                title
        );
    }
}
