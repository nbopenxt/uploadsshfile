package com.openxt.uploadsshfile.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.i18n.LanguageManager.LanguageInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * 语言选择子菜单
 * 动态生成每种支持语言的 ToggleAction，当前语言项前显示勾选标记
 */
public class LanguageMenuGroup extends DefaultActionGroup {

    @Override
    public AnAction @NotNull [] getChildren(AnActionEvent e) {
        List<LanguageInfo> languages = LanguageManager.getSupportedLanguages();

        AnAction[] actions = new AnAction[languages.size()];
        for (int i = 0; i < languages.size(); i++) {
            LanguageInfo info = languages.get(i);
            actions[i] = new ChangeLanguageAction(info.code, info.displayName);
        }
        return actions;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        super.update(e);
        // 一级菜单文本使用"Language"对应的本地化文本
        e.getPresentation().setText(LanguageManager.getInstance().get("menu.language"));
    }
}
