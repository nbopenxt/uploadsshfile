package com.openxt.uploadsshfile.ui;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.vfs.VirtualFile;
import com.openxt.uploadsshfile.batch.BatchSubTask;
import com.openxt.uploadsshfile.config.ConfigManager;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 子任务编辑对话框。
 * 选择服务器 → 路径 → 命令组（可选） → 选择文件。
 */
public class BatchSubTaskEditorDialog extends JDialog {

    private final LanguageManager lang;
    private final UnifiedConfigStore configStore;
    private final ConfigManager configManager;
    private final BatchSubTask existingSubTask;

    private boolean saved = false;
    private BatchSubTask result;

    private JComboBox<ServerConfig> serverCombo;
    private JComboBox<PathConfig> pathCombo;
    private JComboBox<CommandConfig> cmdCombo;
    private JCheckBox noCmdCheckbox;
    private JTextArea cmdPreviewArea;
    private DefaultListModel<String> fileListModel;
    private JList<String> fileList;
    private List<String> filePaths = new ArrayList<>();

    public BatchSubTaskEditorDialog(JDialog parent, BatchSubTask existingSubTask, List<String> initialFilePaths) {
        super(parent, true);
        this.lang = LanguageManager.getInstance();
        this.configStore = UnifiedConfigStore.getInstance();
        this.configManager = ConfigManager.getInstance();
        this.existingSubTask = existingSubTask;

        if (existingSubTask != null && existingSubTask.getFilePaths() != null) {
            filePaths.addAll(existingSubTask.getFilePaths());
        }
        if (initialFilePaths != null && !initialFilePaths.isEmpty()) {
            for (String fp : initialFilePaths) {
                if (!filePaths.contains(fp)) {
                    filePaths.add(fp);
                }
            }
        }

        setTitle(lang.get("batch.task.subtask"));
        initUI();
        pack();
        setLocationRelativeTo(parent);
        setMinimumSize(new Dimension(550, 450));
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPanel formPanel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // 服务器
        gbc.gridx = 0; gbc.gridy = 0;
        formPanel.add(new JLabel(lang.get("upload.server")), gbc);
        gbc.gridx = 1;
        serverCombo = new JComboBox<>();
        List<ServerConfig> servers = configManager.getAllServers();
        for (ServerConfig s : servers) serverCombo.addItem(s);
        formPanel.add(serverCombo, gbc);

        // 路径
        gbc.gridx = 0; gbc.gridy = 1;
        formPanel.add(new JLabel(lang.get("upload.path")), gbc);
        gbc.gridx = 1;
        pathCombo = new JComboBox<>();
        formPanel.add(pathCombo, gbc);

        // 命令组
        gbc.gridx = 0; gbc.gridy = 2;
        formPanel.add(new JLabel(lang.get("upload.command.group")), gbc);
        gbc.gridx = 1;
        JPanel cmdPanel = new JPanel(new BorderLayout(5, 0));
        cmdCombo = new JComboBox<>();
        cmdPanel.add(cmdCombo, BorderLayout.CENTER);
        noCmdCheckbox = new JCheckBox(lang.get("batch.task.noCmdGroup"));
        cmdPanel.add(noCmdCheckbox, BorderLayout.EAST);
        formPanel.add(cmdPanel, gbc);

        // 命令组预览
        gbc.gridx = 0; gbc.gridy = 3;
        formPanel.add(new JLabel(lang.get("commandGroup.label.commands")), gbc);
        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weighty = 0.3;
        cmdPreviewArea = new JTextArea(3, 40);
        cmdPreviewArea.setEditable(false);
        cmdPreviewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        cmdPreviewArea.setLineWrap(true);
        JScrollPane previewScroll = new JScrollPane(cmdPreviewArea);
        previewScroll.setPreferredSize(new Dimension(400, 60));
        formPanel.add(previewScroll, gbc);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 0;

        // 文件列表
        gbc.gridx = 0; gbc.gridy = 4;
        formPanel.add(new JLabel(lang.get("upload.file")), gbc);
        gbc.gridx = 1;
        fileListModel = new DefaultListModel<>();
        for (String fp : filePaths) fileListModel.addElement(fp);
        fileList = new JList<>(fileListModel);
        JScrollPane fileScroll = new JScrollPane(fileList);
        fileScroll.setPreferredSize(new Dimension(400, 120));
        formPanel.add(fileScroll, gbc);

        // 文件按钮
        gbc.gridy = 5;
        JPanel fileBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addFileBtn = new JButton(lang.get("batch.task.addFile"));
        JButton addDirBtn = new JButton(lang.get("batch.task.addDir"));
        JButton removeBtn = new JButton(lang.get("config.btn.delete"));
        fileBtnPanel.add(addFileBtn);
        fileBtnPanel.add(addDirBtn);
        fileBtnPanel.add(removeBtn);
        formPanel.add(fileBtnPanel, gbc);

        // 按钮
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okBtn = new JButton(lang.get("common.btn.ok"));
        JButton cancelBtn = new JButton(lang.get("execution.btn.cancel"));
        actionPanel.add(okBtn);
        actionPanel.add(cancelBtn);

        mainPanel.add(formPanel, BorderLayout.CENTER);
        mainPanel.add(actionPanel, BorderLayout.SOUTH);

        // 联动
        serverCombo.addActionListener(e -> onServerChanged());
        cmdCombo.addActionListener(e -> updateCmdPreview());
        noCmdCheckbox.addActionListener(e -> {
            cmdCombo.setEnabled(!noCmdCheckbox.isSelected());
            updateCmdPreview();
        });

        // 事件
        addFileBtn.addActionListener(e -> onBrowseFiles());
        addDirBtn.addActionListener(e -> onBrowseDirectory());
        removeBtn.addActionListener(e -> onRemoveFile());
        okBtn.addActionListener(e -> onOk());
        cancelBtn.addActionListener(e -> dispose());

        // 初始化选中
        if (serverCombo.getItemCount() > 0) {
            onServerChanged();
            if (existingSubTask != null) {
                for (int i = 0; i < serverCombo.getItemCount(); i++) {
                    ServerConfig s = serverCombo.getItemAt(i);
                    if (s.getId().equals(existingSubTask.getServerId())) {
                        serverCombo.setSelectedIndex(i);
                        break;
                    }
                }
            }
        }

        setContentPane(mainPanel);
    }

    private void onServerChanged() {
        ServerConfig server = (ServerConfig) serverCombo.getSelectedItem();
        pathCombo.removeAllItems();
        cmdCombo.removeAllItems();

        if (server == null) return;

        // 加载路径
        List<PathConfig> paths = configManager.getPathsByServer(server.getId());
        for (PathConfig p : paths) pathCombo.addItem(p);

        if (existingSubTask != null && paths.stream().anyMatch(p -> p.getId().equals(existingSubTask.getPathId()))) {
            for (int i = 0; i < pathCombo.getItemCount(); i++) {
                if (pathCombo.getItemAt(i).getId().equals(existingSubTask.getPathId())) {
                    pathCombo.setSelectedIndex(i);
                    break;
                }
            }
        }

        // 加载命令组
        List<CommandConfig> cmds = configStore.getAllCommandConfigs().stream()
                .filter(c -> server.getId().equals(c.getServerId()))
                .collect(Collectors.toList());
        for (CommandConfig c : cmds) cmdCombo.addItem(c);

        if (existingSubTask != null && existingSubTask.getCommandConfigId() != null) {
            for (int i = 0; i < cmdCombo.getItemCount(); i++) {
                if (cmdCombo.getItemAt(i).getId().equals(existingSubTask.getCommandConfigId())) {
                    cmdCombo.setSelectedIndex(i);
                    break;
                }
            }
        } else if (existingSubTask != null && existingSubTask.getCommandConfigId() == null) {
            noCmdCheckbox.setSelected(true);
            cmdCombo.setEnabled(false);
        }
        updateCmdPreview();
    }

    private void updateCmdPreview() {
        if (noCmdCheckbox.isSelected() || cmdCombo.getSelectedItem() == null) {
            cmdPreviewArea.setText("");
            return;
        }
        CommandConfig config = (CommandConfig) cmdCombo.getSelectedItem();
        if (config.getCommands() == null || config.getCommands().isEmpty()) {
            cmdPreviewArea.setText("");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < config.getCommands().size(); i++) {
            sb.append(i + 1).append(". ").append(config.getCommands().get(i).getCommand()).append("\n");
        }
        cmdPreviewArea.setText(sb.toString());
        cmdPreviewArea.setCaretPosition(0);
    }

    private void onBrowseFiles() {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createMultipleFilesNoJarsDescriptor();
        descriptor.setTitle(lang.get("upload.file"));
        VirtualFile[] chosen = FileChooser.chooseFiles(descriptor, null, null);
        if (chosen.length > 0) {
            for (VirtualFile vf : chosen) {
                String path = vf.getPath();
                if (!filePaths.contains(path)) {
                    filePaths.add(path);
                    fileListModel.addElement(path);
                }
            }
        }
    }

    private void onBrowseDirectory() {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor();
        descriptor.setTitle(lang.get("upload.dir"));
        VirtualFile[] chosen = FileChooser.chooseFiles(descriptor, null, null);
        if (chosen.length > 0) {
            for (VirtualFile vf : chosen) {
                String path = vf.getPath();
                if (!filePaths.contains(path)) {
                    filePaths.add(path);
                    fileListModel.addElement(path);
                }
            }
        }
    }

    private void onRemoveFile() {
        int idx = fileList.getSelectedIndex();
        if (idx >= 0) {
            filePaths.remove(idx);
            fileListModel.remove(idx);
        }
    }

    private void onOk() {
        ServerConfig server = (ServerConfig) serverCombo.getSelectedItem();
        PathConfig path = (PathConfig) pathCombo.getSelectedItem();
        if (server == null || path == null) {
            JOptionPane.showMessageDialog(this, lang.get("upload.error.noServer"), lang.get("common.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (filePaths.isEmpty()) {
            JOptionPane.showMessageDialog(this, lang.get("batch.task.noFile"), lang.get("common.warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        result = new BatchSubTask();
        if (existingSubTask != null) {
            result.setId(existingSubTask.getId());
        }
        result.setServerId(server.getId());
        result.setPathId(path.getId());
        if (!noCmdCheckbox.isSelected() && cmdCombo.getSelectedItem() != null) {
            result.setCommandConfigId(((CommandConfig) cmdCombo.getSelectedItem()).getId());
        }
        result.setFilePaths(new ArrayList<>(filePaths));
        result.setOrder(0);
        saved = true;
        dispose();
    }

    public boolean isSaved() { return saved; }
    public BatchSubTask getResult() { return result; }
}
