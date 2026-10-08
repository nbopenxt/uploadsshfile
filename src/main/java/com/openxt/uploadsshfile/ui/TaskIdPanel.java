package com.openxt.uploadsshfile.ui;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationGroup;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.TaskIdGenerator;
import com.openxt.uploadsshfile.snippet.BuildSnippetGenerator;
import com.openxt.uploadsshfile.startup.IdeBootstrap;

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

        /** true＝批处理任务片段；false＝单任务。D-38 起两形态命令行同构（run <任务ID> --yes，
         *  均不含 --file——文件清单由 CLI 按任务 ID 读配置：批＝子任务 filePaths，单＝关窗快照） */
        boolean isBatch();
        // D-36 的 snippetFiles() 契约已被 D-38 作废删除（片段不再内嵌选中文件）
    }

    private final JTextField idField;
    private final TaskIdHost host;
    private final String cliBatPath;
    private final LanguageManager lm;

    /**
     * @param initialId  文本框初值。D-25（FR-04）起宿主开窗即预填（已落盘值或雪花默认值），
     *                   正常不为空；空框分支保留＝兜底，首次复制时仍自动预生成雪花
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
        installLiveValidation();
        add(idField);

        JButton copyBtn = new JButton(lm.get("task.id.copy"));
        copyBtn.setComponentPopupMenu(buildToolMenu());
        copyBtn.addActionListener(e -> copyBtn.getComponentPopupMenu().show(copyBtn, 0, copyBtn.getHeight()));
        add(copyBtn);
    }

    public String getDisplayedId() {
        return idField.getText().trim();
    }

    /**
     * D-30C（用户裁定 2026-10-04："输入不符合规则的内容……应当提示错误信息"）：
     * 输入期实时标记——非法**新值**（非空、不同于已落盘值、字符集不过）即时红框＋
     * 错误文案 tooltip（非模态不打断输入）；合法/回填存量值恢复正常外观。
     * 落盘四入口的模态弹窗（D-25）保留＝双保险，语义不冲突。
     */
    private void installLiveValidation() {
        javax.swing.event.DocumentListener watcher = new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshValidityUi(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshValidityUi(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshValidityUi(); }
        };
        idField.getDocument().addDocumentListener(watcher);
        refreshValidityUi();
    }

    private void refreshValidityUi() {
        boolean invalid = isInvalidPending();
        idField.setBorder(invalid
                ? javax.swing.BorderFactory.createLineBorder(java.awt.Color.RED)
                : UIManager.getBorder("TextField.border"));
        idField.setToolTipText(invalid ? lm.get("task.id.invalid") : lm.get("task.id.tooltip"));
    }

    /** 当前显示值＝"已改且非法"的新值（供对话框关窗守卫查询，D-30C） */
    public boolean hasInvalidPendingEdit() {
        return isInvalidPending();
    }

    private boolean isInvalidPending() {
        String shown = getDisplayedId();
        if (shown.isEmpty()) {
            return false; // 空值＝D-25 兜底预生成分支，不算非法
        }
        String persisted = host.persistedId();
        if (shown.equals(persisted)) {
            return false; // 回填存量原值＝视同未改号（FR-05 不透明放行）
        }
        return !TaskIdGenerator.isValidNewId(shown);
    }

    private JPopupMenu buildToolMenu() {
        JPopupMenu menu = new JPopupMenu();
        // D-34（2026-10-04 用户裁定）：菜单顶部灰字说明——片段面向构建钩子（上传构建产物），
        // 与"右键选中文件即传"的 GUI 交互用法区分，消除"为什么片段里是 war 不是我选的 a.txt"困惑
        JMenuItem note = new JMenuItem(lm.get("copy.snippet.menu.note"));
        note.setEnabled(false);
        menu.add(note);
        menu.addSeparator();
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
        // D-29（2026-10-04 热载半生态实证）：片段内嵌 bat 绝对路径——插件装/升级若经
        // 动态热载（loaded without restart），appStarted/projectOpened 均不再触发、
        // bat 已被安装过程清掉＝复制出死链。复制动作前先幂等补生成（文件 I/O 毫秒级，
        // MD5 一致时零写盘，EDT 可接受）。
        IdeBootstrap.ensureCliBat();
        // D-38：--file 旗标整体作废——单任务片段仅带任务 ID，CLI 执行时读关窗快照清单
        //（多选拒绝逻辑 copy.snippet.files.needone 随之取消；文件＝最后一次关窗保存的清单）
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

        String snippet = BuildSnippetGenerator.generate(tool, cliBatPath, id, host.isBatch()); // D-38：形态以布尔区分，不含文件
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
        // D-29（2026-10-04 动态热载半生态 NPE 实证）：热载时 plugin.xml 的
        // <notificationGroup> EP 可能未注册，getNotificationGroup 返回 null——
        // 剪贴板已写入、复制事实成功，反馈降级为静默＋日志，不得再炸 EDT 异常
        NotificationGroup group = NotificationGroupManager.getInstance()
                .getNotificationGroup("UploadSSHFile.Snippet");
        if (group != null) {
            Notifications.Bus.notify(
                    group.createNotification(lm.get("task.id.copy"), content, type),
                    null);
        } else {
            com.openxt.uploadsshfile.util.Logger.debug("TaskIdPanel",
                    "snippet copied; notification group unavailable (dynamic-reload state?), balloon skipped");
        }
    }
}
