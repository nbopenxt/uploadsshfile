package com.openxt.uploadsshfile.ui;

import com.openxt.uploadsshfile.batch.BatchSubTask;
import com.openxt.uploadsshfile.batch.BatchTask;
import com.openxt.uploadsshfile.batch.BatchTaskManager;
import com.openxt.uploadsshfile.config.ConfigManager;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 创建/编辑批处理任务对话框。
 * 任务名称 + 子任务列表（添加/编辑/删除）。
 */
public class BatchTaskEditorDialog extends JDialog {

    private final BatchTaskManager taskManager;
    private final LanguageManager lang;
    private final BatchTask existingTask;
    private final List<String> initialFilePaths;

    private boolean saved = false;
    private JTextField nameField;
    private DefaultListModel<BatchSubTask> subTaskListModel;
    private JList<BatchSubTask> subTaskList;
    private List<BatchSubTask> subTasks;

    public BatchTaskEditorDialog(JDialog parent, BatchTask existingTask, List<String> initialFilePaths) {
        super(parent, true);
        this.taskManager = BatchTaskManager.getInstance();
        this.lang = LanguageManager.getInstance();
        this.existingTask = existingTask;
        this.initialFilePaths = initialFilePaths != null ? new ArrayList<>(initialFilePaths) : Collections.emptyList();
        this.subTasks = new ArrayList<>();

        if (existingTask != null) {
            setTitle(lang.get("batch.task.edit"));
            if (existingTask.getSubTasks() != null) {
                subTasks.addAll(existingTask.getSubTasks());
            }
        } else {
            setTitle(lang.get("batch.task.create"));
        }

        initUI();
        pack();
        setLocationRelativeTo(parent);
        setMinimumSize(new Dimension(550, 450));
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 名称区域
        JPanel namePanel = new JPanel(new BorderLayout(5, 5));
        namePanel.add(new JLabel(lang.get("server.name") + ":"), BorderLayout.WEST);
        nameField = new JTextField(30);
        if (existingTask != null && existingTask.getName() != null) {
            nameField.setText(existingTask.getName());
        }
        namePanel.add(nameField, BorderLayout.CENTER);

        // 子任务列表
        subTaskListModel = new DefaultListModel<>();
        refreshSubTaskDisplay();
        subTaskList = new JList<>(subTaskListModel);
        subTaskList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        subTaskList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof BatchSubTask) {
                    BatchSubTask st = (BatchSubTask) value;
                    ConfigManager cm = ConfigManager.getInstance();
                    ServerConfig sc = cm.getServer(st.getServerId());
                    String serverName = sc != null ? sc.getName() : st.getServerId();
                    String pathName = st.getPathId();
                    List<PathConfig> paths = cm.getPathsByServer(st.getServerId());
                    if (paths != null) {
                        for (PathConfig p : paths) {
                            if (p.getId().equals(st.getPathId())) {
                                pathName = p.getRemotePath();
                                break;
                            }
                        }
                    }
                    int fileCount = st.getFilePaths() != null ? st.getFilePaths().size() : 0;
                    setText(serverName + " -> " + pathName + " (" + fileCount + " " + lang.get("batch.task.items") + ")");
                }
                return this;
            }
        });
        JScrollPane scrollPane = new JScrollPane(subTaskList);
        scrollPane.setPreferredSize(new Dimension(500, 250));

        JPanel subTaskPanel = new JPanel(new BorderLayout());
        subTaskPanel.setBorder(BorderFactory.createTitledBorder(lang.get("batch.task.subtask")));
        subTaskPanel.add(scrollPane, BorderLayout.CENTER);

        // 子任务按钮
        JPanel subBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addSubBtn = new JButton(lang.get("batch.task.addSubtask"));
        JButton editSubBtn = new JButton(lang.get("config.btn.edit"));
        JButton deleteSubBtn = new JButton(lang.get("config.btn.delete"));
        subBtnPanel.add(addSubBtn);
        subBtnPanel.add(editSubBtn);
        subBtnPanel.add(deleteSubBtn);

        // 主按钮
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton saveBtn = new JButton(lang.get("config.btn.save"));
        JButton cancelBtn = new JButton(lang.get("execution.btn.cancel"));
        actionPanel.add(saveBtn);
        actionPanel.add(cancelBtn);

        // 组装
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(namePanel, BorderLayout.NORTH);
        centerPanel.add(subTaskPanel, BorderLayout.CENTER);
        centerPanel.add(subBtnPanel, BorderLayout.SOUTH);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(actionPanel, BorderLayout.SOUTH);

        // 事件
        addSubBtn.addActionListener(e -> onAddSubTask());
        editSubBtn.addActionListener(e -> onEditSubTask());
        deleteSubBtn.addActionListener(e -> onDeleteSubTask());
        saveBtn.addActionListener(e -> onSave());
        cancelBtn.addActionListener(e -> dispose());

        setContentPane(mainPanel);
    }

    private void refreshSubTaskDisplay() {
        subTaskListModel.clear();
        for (int i = 0; i < subTasks.size(); i++) {
            BatchSubTask st = subTasks.get(i);
            st.setOrder(i);
            subTaskListModel.addElement(st);
        }
    }

    private void onAddSubTask() {
        BatchSubTaskEditorDialog editor = new BatchSubTaskEditorDialog(this, null, initialFilePaths);
        editor.setVisible(true);
        if (editor.isSaved()) {
            subTasks.add(editor.getResult());
            refreshSubTaskDisplay();
        }
    }

    private void onEditSubTask() {
        BatchSubTask selected = subTaskList.getSelectedValue();
        int idx = subTaskList.getSelectedIndex();
        if (selected == null || idx < 0) {
            return;
        }
        BatchSubTaskEditorDialog editor = new BatchSubTaskEditorDialog(this, selected, java.util.Collections.emptyList());
        editor.setVisible(true);
        if (editor.isSaved()) {
            subTasks.set(idx, editor.getResult());
            refreshSubTaskDisplay();
        }
    }

    private void onDeleteSubTask() {
        int idx = subTaskList.getSelectedIndex();
        if (idx < 0) return;
        subTasks.remove(idx);
        refreshSubTaskDisplay();
    }

    private void onSave() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, lang.get("msg.error.validation.name"), lang.get("common.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (subTasks.isEmpty()) {
            JOptionPane.showMessageDialog(this, lang.get("batch.task.noSubtask"), lang.get("common.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        BatchTask task;
        if (existingTask != null) {
            task = existingTask;
            task.setName(name);
            task.setUpdateTime(System.currentTimeMillis());
        } else {
            task = new BatchTask();
            task.setName(name);
        }
        task.setSubTasks(subTasks);
        taskManager.saveBatchTask(task);
        saved = true;
        dispose();
    }

    public boolean isSaved() { return saved; }
}
