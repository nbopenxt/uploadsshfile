package com.openxt.uploadsshfile.ui;

import com.intellij.openapi.application.PathManager;
import com.openxt.uploadsshfile.batch.BatchExecutionOrchestrator;
import com.openxt.uploadsshfile.batch.BatchSubTask;
import com.openxt.uploadsshfile.batch.BatchTask;
import com.openxt.uploadsshfile.batch.BatchTaskManager;
import com.openxt.uploadsshfile.config.ConfigManager;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.TaskIdGenerator;
import com.openxt.uploadsshfile.store.StoreManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;

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
    /** 1.0.8/FR-07：任务 ID 行（批处理任务复用现有 BatchTask.id，可编辑；存量 UUID 不迁移，R16） */
    private TaskIdPanel taskIdPanel;
    /** 新建场景下"复制先定号"暂存 ID（D-15），onSave 统一随任务落库；编辑场景恒 null（直接写回 existingTask） */
    private String pendingTaskId;

    /** ID 文本框初值：编辑＝现有 id（可能是存量 UUID 或雪花）；新建＝pendingTaskId，无则
     *  D-25（FR-04）开窗预填雪花默认值（仅显示不落盘，保存/复制时才查重落库） */
    private String initialEditorId() {
        if (existingTask != null) {
            return existingTask.getId();
        }
        return pendingTaskId != null ? pendingTaskId : TaskIdGenerator.nextId();
    }

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

        // 任务 ID 行（1.0.8/FR-07：名称行之下；D-12 列表不加 ID 列、编辑器加一行）
        String cliBat = PathManager.getPluginsDir() + java.io.File.separator
                + "uploadsshfile" + java.io.File.separator + "uploadsshfile-cli.bat";
        taskIdPanel = new TaskIdPanel(new TaskIdPanel.TaskIdHost() {
            @Override
            public String persistedId() {
                return existingTask != null ? existingTask.getId() : null;
            }

            @Override
            public boolean persist(String newId) {
                // U-03 执行期间禁改号（批处理跑动中改 ID 会让运行中引用漂移）；
                // 单任务侧上传中为模态进度窗、无法触达面板，M4 服务器锁补齐后两路同口径
                if (BatchExecutionOrchestrator.isAnyBatchRunning()) {
                    JOptionPane.showMessageDialog(BatchTaskEditorDialog.this,
                            lang.get("task.id.running"), lang.get("common.warning"), JOptionPane.WARNING_MESSAGE);
                    return false;
                }
                String cur = persistedId();
                if (newId.equals(cur)) {
                    return true; // 存量原值回填＝视同未改号，按不透明放行（FR-05）
                }
                // D-25（FR-04 补注）：改号/新落盘值仅允许字母数字（拒 '-' 等符号）
                if (!TaskIdGenerator.isValidNewId(newId)) {
                    JOptionPane.showMessageDialog(BatchTaskEditorDialog.this,
                            lang.get("task.id.invalid"), lang.get("common.warning"),
                            JOptionPane.WARNING_MESSAGE);
                    return false;
                }
                String owner = StoreManager.getInstance().getUnifiedConfigStore()
                        .findTaskIdOwner(newId, cur);
                if (owner != null) {
                    String ownerName = UnifiedConfigStore.TASK_ID_OWNER_SINGLE.equals(owner)
                            ? lang.get("task.id.owner.single") : owner;
                    JOptionPane.showMessageDialog(BatchTaskEditorDialog.this,
                            lang.get("task.id.conflict", ownerName),
                            lang.get("common.warning"), JOptionPane.WARNING_MESSAGE);
                    return false;
                }
                if (existingTask != null) {
                    existingTask.setId(newId);
                    taskManager.saveBatchTask(existingTask);
                } else {
                    pendingTaskId = newId; // 新建场景：复制即定号，onSave 统一落库
                }
                return true;
            }

            @Override
            public boolean isBatch() {
                return true;
            }
        }, initialEditorId(), cliBat, lang);
        JPanel northPanel = new JPanel();
        northPanel.setLayout(new BoxLayout(northPanel, BoxLayout.Y_AXIS));
        namePanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        taskIdPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        northPanel.add(namePanel);
        northPanel.add(Box.createVerticalStrut(6));
        northPanel.add(taskIdPanel);

        // 组装
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.add(northPanel, BorderLayout.NORTH);
        centerPanel.add(subTaskPanel, BorderLayout.CENTER);
        centerPanel.add(subBtnPanel, BorderLayout.SOUTH);

        mainPanel.add(centerPanel, BorderLayout.CENTER);
        mainPanel.add(actionPanel, BorderLayout.SOUTH);

        // 事件
        addSubBtn.addActionListener(e -> onAddSubTask());
        editSubBtn.addActionListener(e -> onEditSubTask());
        deleteSubBtn.addActionListener(e -> onDeleteSubTask());
        saveBtn.addActionListener(e -> onSave());
        // D-30C（用户裁定）：关闭时存在"已改且非法"的任务 ID → 弹确认（值不会被落盘，
        // 静默丢弃曾致"没提示也没保存"困惑）；继续关闭＝放弃修改，返回编辑＝留在窗口
        cancelBtn.addActionListener(e -> {
            if (taskIdPanel != null && taskIdPanel.hasInvalidPendingEdit()) {
                int choice = JOptionPane.showConfirmDialog(this,
                        lang.get("task.id.invalid.discard"),
                        lang.get("common.warning"),
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (choice != JOptionPane.YES_OPTION) {
                    return;
                }
            }
            dispose();
        });

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

        // 1.0.8/FR-07（流程 E）：改号须过 U-03 与跨命名空间查重两道闸；未改号保持现值（存量 UUID 不迁移，R16）
        String taskId = taskIdPanel.getDisplayedId().trim();
        if (taskId.isEmpty()) {
            taskId = task.getId(); // 空框＝沿用构造默认（新建）或现值（编辑）
        }
        if (taskId != null && !taskId.equals(task.getId())) {
            if (BatchExecutionOrchestrator.isAnyBatchRunning()) {
                JOptionPane.showMessageDialog(this, lang.get("task.id.running"),
                        lang.get("common.warning"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            // D-25（FR-04 补注）：改号新值仅允许字母数字（拒 '-' 等符号）；未改号分支不校验（FR-05）。
            // 判定顺序＝流程 E 既有口径：执行中 → 字符集 → 查重
            if (!TaskIdGenerator.isValidNewId(taskId)) {
                JOptionPane.showMessageDialog(this, lang.get("task.id.invalid"),
                        lang.get("common.warning"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            String owner = StoreManager.getInstance().getUnifiedConfigStore()
                    .findTaskIdOwner(taskId, task.getId());
            if (owner != null) {
                String ownerName = UnifiedConfigStore.TASK_ID_OWNER_SINGLE.equals(owner)
                        ? lang.get("task.id.owner.single") : owner;
                JOptionPane.showMessageDialog(this, lang.get("task.id.conflict", ownerName),
                        lang.get("common.warning"), JOptionPane.WARNING_MESSAGE);
                return;
            }
            task.setId(taskId);
            pendingTaskId = null;
        }

        task.setSubTasks(subTasks);
        taskManager.saveBatchTask(task);
        saved = true;
        dispose();
    }

    public boolean isSaved() { return saved; }
}
