package com.openxt.uploadsshfile.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationGroupManager;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.TaskIdGenerator;
import com.openxt.uploadsshfile.snippet.BuildSnippetGenerator;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;

/**
 * 任务 ID 行（1.0.8 / FR-06/FR-07/FR-08；设计文档 §6.1/§6.2/§6.3，D-15/D-18/D-19/D-20）。
 * UploadDialog（单任务）与 BatchTaskEditorDialog（批任务）复用的"新增一行"：
 *   [Task ID 文本框（可改）] [ Copy build-snippet ▾ ]
 * 交互契约（全部经用户逐项确认）：
 *  - 单个复制按钮点击弹四工具菜单；Maven/Ant 项自带"未实测"标注（复制前即告知，D-19）；
 *  - 选定工具时若 ID 尚未落盘：先经宿主 persist()（含查重/执行中禁改 U-03）再写剪贴板（D-15）；
 *  - 复制反馈分级：Gradle 两份＝信息级气泡；Maven/Ant＝警告级气泡（D-19，非模态不阻断）；
 *  - 本次先落盘则追加一行 id.unsaved；文案全走 i18n（8 语言，§6.3 译表）；不记忆所选工具（D-18）。
 */
public class TaskIdPanel extends JPanel {

    /** 宿主对话框能力回调 */
    public interface TaskIdHost {
        /** 当前已落盘 ID（可能 null＝单任务从未落盘） */
        String persistedId();

        /**
         * 查重＋落盘（宿主完成，返回 false＝被拒绝且已自行提示用户，本面板中止复制动作）。
         * 拒绝场景：跨命名空间冲突（提示占用人）、执行中禁改（U-03）。
         */
        boolean persist(String newId);

        /** true＝批处理任务片段（不含 --file，R26）；false＝单任务 */
        boolean isBatch();
    }

    private final JTextField idField;
    private final TaskIdHost host;
    private final String cliBatPath;
    private final LanguageManager lm;

    /**
     * @param initialId  文本框初值（persistedId；null→空框，首次复制时自动预生成）
     * @param cliBatPath 插件 bat 绝对路径实值（plugin 层以 PathManager 取得，RISK-08——禁止写死）
     */
    public TaskIdPanel(TaskIdHost host, String initialId, String cliBatPath, LanguageManager lm) {
        this.host = host;
        this.cliBatPath = cliBatPath;
        this.lm = lm;

        setLayout(new FlowLayout(FlowLayout.LEFT, 6, 0));
        add(new JLabel(lm.get("task.id.label")));
        idField = new JTextField(initialId == null ? "" : initialId, 26);
        idField.setToolTipText(lm.get("task.id.tooltip"));
        add(idField);

        JButton copyBtn = new JButton(lm.get("task.id.copy"));
        copyBtn.setComponentPopupMenu(buildToolMenu());
        copyBtn.addActionListener(e -> copyBtn.getComponentPopupMenu().show(copyBtn, 0, copyBtn.getHeight()));
        add(copyBtn);
    }

    public String getDisplayedId() {
        return idField.getText().trim();
    }

    private JPopupMenu buildToolMenu() {
        JPopupMenu menu = new JPopupMenu();
        String hint = " " + lm.get("copy.snippet.menu.hint");
        menu.add(toolItem(BuildSnippetGenerator.Tool.GRADLE_GROOVY, "Gradle (Groovy DSL)", null));
        menu.add(toolItem(BuildSnippetGenerator.Tool.GRADLE_KOTLIN, "Gradle (Kotlin DSL)", null));
        menu.add(toolItem(BuildSnippetGenerator.Tool.MAVEN, "Maven" + hint, true));
        menu.add(toolItem(BuildSnippetGenerator.Tool.ANT, "Ant" + hint, true));
        return menu;
    }

    private JMenuItem toolItem(BuildSnippetGenerator.Tool tool, String text, Boolean warnLevel) {
        JMenuItem item = new JMenuItem(text);
        item.addActionListener(e -> doCopy(tool, Boolean.TRUE.equals(warnLevel)));
        return item;
    }

    private void doCopy(BuildSnippetGenerator.Tool tool, boolean isWarnFeed) {
        String id = idField.getText().trim();
        boolean savedNow = false;

        if (id.isEmpty()) {
            // 首落盘：预生成雪花并查重（#13——撞则重新生成；查重循环兜底理论碰撞）
            for (int i = 0; i < 5; i++) {
                id = TaskIdGenerator.nextId();
                if (!host.persist(id)) {
                    return; // 宿主拒绝（含冲突/执行中），中止且不写剪贴板
                }
                savedNow = true;
                break;
            }
            if (!savedNow) {
                return;
            }
            idField.setText(id);
        } else if (host.persistedId() == null || !id.equals(host.persistedId())) {
            // 改号或未落盘值：复制即先落盘（D-15），宿主经查重/禁改校验；
            // 落盘成功即属"本次复制触发了写盘"，气泡追加 id.unsaved 行
            if (!host.persist(id)) {
                return;
            }
            savedNow = true;
        }

        String snippet = BuildSnippetGenerator.generate(tool, cliBatPath, id, host.isBatch());
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(snippet), null);

        // D-19 分级反馈：非模态气泡，不新增对话框
        String content;
        NotificationType type;
        switch (tool) {
            case MAVEN:
                content = lm.get("copy.snippet.maven.warn");
                type = NotificationType.WARNING;
                break;
            case ANT:
                content = lm.get("copy.snippet.ant.warn");
                type = NotificationType.WARNING;
                break;
            default:
                content = lm.get("copy.snippet.gradle.ok");
                type = NotificationType.INFORMATION;
        }
        if (savedNow) {
            content = content + "\n" + lm.get("copy.snippet.id.unsaved");
        }
        Notifications.Bus.notify(
                NotificationGroupManager.getInstance()
                        .getNotificationGroup("UploadSSHFile.Snippet")
                        .createNotification(lm.get("task.id.copy"), content, type),
                null);
    }
}
