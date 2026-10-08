package com.openxt.uploadsshfile.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.openxt.uploadsshfile.batch.BatchSubTask;
import com.openxt.uploadsshfile.batch.BatchTask;
import com.openxt.uploadsshfile.batch.BatchTaskManager;
import com.openxt.uploadsshfile.i18n.LanguageManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 批处理任务管理主界面。
 * 显示已保存任务列表，支持 新建/复制/编辑/删除/执行。
 */
public class BatchTaskDialog extends JDialog {

    private final Project project;
    private final BatchTaskManager taskManager;
    private final LanguageManager lang;
    private final List<String> initialFilePaths;

    private DefaultListModel<BatchTask> taskListModel;
    private JList<BatchTask> taskList;
    private JButton newBtn, copyBtn, editBtn, deleteBtn, executeBtn, closeBtn;

    public BatchTaskDialog(Project project, List<String> initialFilePaths) {
        super((Frame) null, true);
        this.project = project;
        this.taskManager = BatchTaskManager.getInstance();
        this.lang = LanguageManager.getInstance();
        this.initialFilePaths = initialFilePaths != null ? new ArrayList<>(initialFilePaths) : new ArrayList<>();

        setTitle(lang.get("batch.task.title"));
        initUI();
        refreshTaskList();
        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(500, 400));
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 任务列表
        taskListModel = new DefaultListModel<>();
        taskList = new JList<>(taskListModel);
        taskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(taskList);
        scrollPane.setPreferredSize(new Dimension(450, 250));

        JPanel listPanel = new JPanel(new BorderLayout());
        listPanel.setBorder(BorderFactory.createTitledBorder(lang.get("batch.task.title")));
        listPanel.add(scrollPane, BorderLayout.CENTER);

        // 按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        newBtn = new JButton(lang.get("config.btn.add"));
        copyBtn = new JButton(lang.get("batch.task.copy"));
        editBtn = new JButton(lang.get("config.btn.edit"));
        deleteBtn = new JButton(lang.get("config.btn.delete"));
        executeBtn = new JButton(lang.get("batch.task.execute"));
        closeBtn = new JButton(lang.get("dialog.close"));

        buttonPanel.add(newBtn);
        buttonPanel.add(copyBtn);
        buttonPanel.add(editBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(executeBtn);
        buttonPanel.add(closeBtn);

        mainPanel.add(listPanel, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        // 事件绑定
        newBtn.addActionListener(e -> onNew());
        copyBtn.addActionListener(e -> onCopy());
        editBtn.addActionListener(e -> onEdit());
        deleteBtn.addActionListener(e -> onDelete());
        executeBtn.addActionListener(e -> onExecute());
        closeBtn.addActionListener(e -> dispose());

        setContentPane(mainPanel);
    }

    private void refreshTaskList() {
        taskListModel.clear();
        List<BatchTask> tasks = taskManager.listBatchTasks();
        for (BatchTask t : tasks) {
            taskListModel.addElement(t);
        }
    }

    private BatchTask getSelectedTask() {
        return taskList.getSelectedValue();
    }

    private void onNew() {
        BatchTaskEditorDialog editor = new BatchTaskEditorDialog(this, null, initialFilePaths);
        editor.setVisible(true);
        if (editor.isSaved()) {
            refreshTaskList();
        }
    }

    private void onCopy() {
        BatchTask selected = getSelectedTask();
        if (selected == null) {
            Messages.showWarningDialog(this, lang.get("batch.task.noSubtask"), lang.get("common.warning"));
            return;
        }

        String newName = Messages.showInputDialog(this, lang.get("batch.task.copy.prompt"),
                lang.get("batch.task.copy.title"), Messages.getQuestionIcon());
        if (newName != null && !newName.trim().isEmpty()) {
            taskManager.copyBatchTask(selected.getId(), newName.trim());
            refreshTaskList();
        }
    }

    private void onEdit() {
        BatchTask selected = getSelectedTask();
        if (selected == null) {
            Messages.showWarningDialog(this, lang.get("batch.task.noSubtask"), lang.get("common.warning"));
            return;
        }
        BatchTaskEditorDialog editor = new BatchTaskEditorDialog(this, selected, java.util.Collections.emptyList());
        editor.setVisible(true);
        if (editor.isSaved()) {
            refreshTaskList();
        }
    }

    private void onDelete() {
        BatchTask selected = getSelectedTask();
        if (selected == null) {
            Messages.showWarningDialog(this, lang.get("batch.task.noSubtask"), lang.get("common.warning"));
            return;
        }
        int result = Messages.showYesNoDialog(this,
                lang.get("msg.error.server.delete.confirm"),
                lang.get("confirm.title"), Messages.getQuestionIcon());
        if (result == Messages.YES) {
            taskManager.deleteBatchTask(selected.getId());
            refreshTaskList();
        }
    }

    private void onExecute() {
        BatchTask selected = getSelectedTask();
        if (selected == null) {
            Messages.showWarningDialog(this, lang.get("batch.task.noSubtask"), lang.get("common.warning"));
            return;
        }

        // D-33（2026-10-04 生产机误传事故）：执行前列出全部"服务器→路径"目标并确认——
        // 批次目标混着生产/测试机时，跑前一眼可见（CLI 侧同口径见 CliRunner.confirmTargets）
        StringBuilder targets = new StringBuilder(lang.get("batch.confirm.targets")).append("\n");
        com.openxt.uploadsshfile.config.ConfigManager cm = com.openxt.uploadsshfile.config.ConfigManager.getInstance();
        for (BatchSubTask st : selected.getSubTasks()) {
            com.openxt.uploadsshfile.config.ServerConfig s = cm.getServer(st.getServerId());
            String remote = st.getPathId();
            java.util.List<com.openxt.uploadsshfile.config.PathConfig> ps = cm.getPathsByServer(st.getServerId());
            if (ps != null) {
                for (com.openxt.uploadsshfile.config.PathConfig pc : ps) {
                    if (pc.getId() != null && pc.getId().equals(st.getPathId())) {
                        remote = pc.getRemotePath();
                        break;
                    }
                }
            }
            targets.append("[").append(st.getOrder()).append("] ")
                    .append(s != null ? s.getHost() + ":" + s.getPort() : st.getServerId())
                    .append(" -> ").append(remote).append(" (")
                    .append(st.getFilePaths() == null ? 0 : st.getFilePaths().size()).append(" file[s])\n");
        }
        int proceed = Messages.showYesNoDialog(this, targets.toString(),
                lang.get("batch.confirm.proceed"), Messages.getQuestionIcon());
        if (proceed != Messages.YES) {
            return;
        }

        BatchProgressDialog progressDialog = new BatchProgressDialog(this, selected);
        progressDialog.setVisible(true);
        refreshTaskList();
    }

    public void showDialog() {
        setVisible(true);
    }
}
