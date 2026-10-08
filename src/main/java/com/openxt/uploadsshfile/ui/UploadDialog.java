package com.openxt.uploadsshfile.ui;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.ExecuteTiming;
import com.openxt.uploadsshfile.model.TaskIdGenerator;
import com.openxt.uploadsshfile.store.StoreManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.util.Logger;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 上传对话框 - 使用 IntelliJ DialogWrapper
 */
public class UploadDialog extends DialogWrapper {
    private JComboBox<ServerConfig> serverCombo;
    private JComboBox<PathConfig> pathCombo;
    private JComboBox<CommandConfig> commandCombo;
    private JPanel contentPanel;
    private JTextArea commandPreviewArea;
    private JRadioButton timingAutoRadio;
    private JRadioButton timingManualRadio;
    private JButton executeCommandsButton;
    private JButton cancelButton;
    private JButton okButton;
    /** 1.0.8/FR-06：任务 ID 行（单例任务，D-06/D-09；位置在文件清单与服务器下拉之间） */
    private TaskIdPanel taskIdPanel;

    private List<String> selectedPaths;
    private UnifiedConfigStore configStore;
    private ServerConfig selectedServer;
    private PathConfig selectedPath;
    private CommandConfig selectedCommandConfig;
    private ExecuteTiming selectedTiming;
    private LanguageManager lm;

    /**
     * 执行命令组回调接口
     */
    @FunctionalInterface
    public interface ExecuteCommandsCallback {
        void onExecuteCommands(CommandExecuteContext context);
    }

    private ExecuteCommandsCallback executeCommandsCallback;

    /**
     * 设置执行命令组回调
     */
    public void setExecuteCommandsCallback(ExecuteCommandsCallback callback) {
        this.executeCommandsCallback = callback;
    }

    public UploadDialog(Component parent, List<String> selectedPaths) {
        super(parent, true); // true = modal
        this.selectedPaths = selectedPaths;
        this.configStore = StoreManager.getInstance().getUnifiedConfigStore();
        this.lm = LanguageManager.getInstance();

        setTitle(lm.get("upload.title"));
        setOKButtonText(lm.get("upload.btn.upload"));
        setCancelButtonText(lm.get("dialog.close"));

        // 创建内容面板
        contentPanel = createMainPanel();

        init(); // 初始化 DialogWrapper
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return contentPanel;
    }

    private JPanel createMainPanel() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 文件信息
        JPanel filePanel = new JPanel(new BorderLayout());
        filePanel.add(new JLabel(lm.get("upload.file") + " " + selectedPaths.size()), BorderLayout.NORTH);

        JTextArea fileList = new JTextArea();
        fileList.setEditable(false);
        fileList.setFont(new Font("Monospaced", Font.PLAIN, 12));
        for (String path : selectedPaths) {
            fileList.append(path + "\n");
        }
        fileList.setRows(Math.min(4, selectedPaths.size()));
        filePanel.add(new JScrollPane(fileList), BorderLayout.CENTER);

        // 服务器选择
        JPanel serverPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        serverPanel.add(new JLabel(lm.get("upload.server")));
        serverCombo = new JComboBox<>();
        serverCombo.addActionListener(e -> onServerSelected());
        serverPanel.add(serverCombo);

        // 路径选择
        JPanel pathPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        pathPanel.add(new JLabel(lm.get("upload.path")));
        pathCombo = new JComboBox<>();
        pathCombo.addActionListener(e -> onPathSelected());
        pathPanel.add(pathCombo);

        // 命令组选择
        JPanel commandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        commandPanel.add(new JLabel(lm.get("upload.command.group")));
        commandCombo = new JComboBox<>();
        commandCombo.addActionListener(e -> onCommandSelected());
        commandPanel.add(commandCombo);

        // 命令预览
        JPanel previewPanel = new JPanel(new BorderLayout());
        previewPanel.add(new JLabel(lm.get("upload.command.preview")), BorderLayout.NORTH);
        commandPreviewArea = new JTextArea();
        commandPreviewArea.setEditable(false);
        commandPreviewArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        commandPreviewArea.setLineWrap(true);
        commandPreviewArea.setWrapStyleWord(true);
        commandPreviewArea.setText(lm.get("upload.command.noSelection"));
        JScrollPane previewScrollPane = new JScrollPane(commandPreviewArea);
        previewScrollPane.setPreferredSize(new Dimension(400, 80));
        previewPanel.add(previewScrollPane, BorderLayout.CENTER);

        // 执行时机选择
        JPanel timingPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        timingPanel.add(new JLabel(lm.get("upload.execute.timing")));
        timingManualRadio = new JRadioButton(lm.get("upload.timing.manual"), true);
        timingAutoRadio = new JRadioButton(lm.get("upload.timing.auto"));
        ButtonGroup timingGroup = new ButtonGroup();
        timingGroup.add(timingManualRadio);
        timingGroup.add(timingAutoRadio);
        timingPanel.add(timingManualRadio);
        timingPanel.add(timingAutoRadio);

        // 提示信息
        JLabel hintLabel = new JLabel("<html><font color='gray'>" + lm.get("upload.hint") + "</font></html>");

        // 任务 ID 行（1.0.8/FR-06：文件清单与服务器下拉之间，设计 §6.2 页面 1；
        // bat 路径取 PathManager 实值（RISK-08，禁写死），目录名＝插件名 uploadsshfile）
        String cliBat = PathManager.getPluginsDir() + java.io.File.separator
                + "uploadsshfile" + java.io.File.separator + "uploadsshfile-cli.bat";
        // D-25（FR-04）：开窗即预填雪花默认值（从未落盘时；仅显示不落盘，落盘时机不变）。
        // 预填值极端情况下与存量撞号，由 persist 查重兜底拒落
        String initialTaskId = configStore.getSingleUploadTaskId();
        if (initialTaskId == null || initialTaskId.isEmpty()) {
            initialTaskId = TaskIdGenerator.nextId();
        }
        taskIdPanel = new TaskIdPanel(new TaskIdPanel.TaskIdHost() {
            @Override
            public String persistedId() {
                return configStore.getSingleUploadTaskId();
            }

            // D-38：snippetFiles() 契约作废（片段不再内嵌 --file，CLI 按任务 ID 读关窗快照）

            @Override
            public boolean persist(String newId) {
                String cur = configStore.getSingleUploadTaskId();
                if (newId.equals(cur)) {
                    return true; // 存量原值回填＝视同未改号，按不透明放行（FR-05）
                }
                // D-25（FR-04 补注）：改号/新落盘值仅允许字母数字（拒 '-' 等符号）
                if (!TaskIdGenerator.isValidNewId(newId)) {
                    JOptionPane.showMessageDialog(contentPanel, lm.get("task.id.invalid"),
                            lm.get("config.title"), JOptionPane.WARNING_MESSAGE);
                    return false;
                }
                // 跨命名空间查重（§4.2）；单任务为模态对话框且执行期不暴露本面板，
                // "执行中禁改"(U-03) 在批处理编辑器侧校验；M4 服务器锁接管后两路同口径
                String owner = configStore.findTaskIdOwner(newId, null);
                if (owner != null) {
                    String ownerName = UnifiedConfigStore.TASK_ID_OWNER_SINGLE.equals(owner)
                            ? lm.get("task.id.owner.single") : owner;
                    JOptionPane.showMessageDialog(contentPanel,
                            lm.get("task.id.conflict", ownerName),
                            lm.get("config.title"), JOptionPane.WARNING_MESSAGE);
                    return false;
                }
                configStore.setSingleUploadTaskId(newId);
                return true;
            }

            @Override
            public boolean isBatch() {
                return false;
            }
        }, initialTaskId, cliBat, lm);

        // 组装
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(filePanel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(taskIdPanel);
        centerPanel.add(Box.createVerticalStrut(10));
        centerPanel.add(serverPanel);
        centerPanel.add(pathPanel);
        centerPanel.add(commandPanel);
        centerPanel.add(previewPanel);
        centerPanel.add(Box.createVerticalStrut(5));
        centerPanel.add(timingPanel);
        centerPanel.add(Box.createVerticalStrut(5));
        centerPanel.add(hintLabel);

        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // 加载服务器数据
        loadServers();

        return mainPanel;
    }

    /**
     * 1.0.8/FR-06：文本框当前任务 ID（可能已改未落盘；
     * UploadAction 成功路径经查重后落盘——U-01"全部上传且校验通过即落盘"）。
     */
    public String getTaskIdValue() {
        return taskIdPanel == null ? null : taskIdPanel.getDisplayedId();
    }

    /**
     * 1.0.8/D-37：本对话框当前上传的文件/目录清单（右键新选、或空选时回放的已存快照清单）。
     * UploadAction 执行与关窗快照落盘共用此单点数据源。
     */
    public List<String> getUploadPaths() {
        return new java.util.ArrayList<>(selectedPaths);
    }

    /**
     * D-37 关窗快照单点：DialogWrapper（253 无 windowClosed 钩子，实测以 javap 为准）
     * OK / Cancel / ✕（经 doCancelAction）三路收口后必过 dispose()——"每次修改，
     * 关闭界面时持久化"即落在此唯一钩子；doCancelAction 的"留在窗口"分支不到 dispose，
     * doOKAction 校验失败早退同理——只有真关窗才落盘。
     * 数据源＝当前界面实态（下拉与单选钮即时值＋本次清单），整体覆盖单槽。
     */
    @Override
    protected void dispose() {
        persistSingleTaskSnapshotOnClose();
        super.dispose();
    }

    private void persistSingleTaskSnapshotOnClose() {
        if (configStore == null || serverCombo == null) {
            return; // 面板未装配（异常构造路径），不落残缺快照
        }
        ServerConfig s = (ServerConfig) serverCombo.getSelectedItem();
        PathConfig p = (PathConfig) pathCombo.getSelectedItem();
        CommandConfig c = (CommandConfig) commandCombo.getSelectedItem();
        String timing = null;
        if (timingAutoRadio != null && timingAutoRadio.isSelected()) {
            timing = "AUTO";
        } else if (timingManualRadio != null && timingManualRadio.isSelected()) {
            timing = "MANUAL";
        }
        try {
            configStore.saveSingleUploadTask(getUploadPaths(),
                    s != null ? s.getId() : null,
                    p != null ? p.getId() : null,
                    c != null ? c.getId() : null,
                    timing);
            Logger.debug("UploadDialog", "D-37 snapshot saved on close: " + selectedPaths.size() + " file(s)");
        } catch (Exception ex) {
            Logger.error("UploadDialog", "D-37 snapshot save failed: " + ex.getMessage());
        }
    }

    /** D-37：回显取 ID——关窗快照优先（"上次保存"），回落 lastSuccessful*（"上次成功"，旧语义保留） */
    private String effectiveServerId() {
        com.openxt.uploadsshfile.model.SingleUploadTask t = configStore.getSingleUploadTask();
        if (t != null && t.getServerId() != null) return t.getServerId();
        return configStore.getLastSuccessfulServerId();
    }

    private String effectivePathId() {
        com.openxt.uploadsshfile.model.SingleUploadTask t = configStore.getSingleUploadTask();
        if (t != null && t.getPathId() != null) return t.getPathId();
        return configStore.getLastSuccessfulPathId();
    }

    private String effectiveCommandConfigId() {
        com.openxt.uploadsshfile.model.SingleUploadTask t = configStore.getSingleUploadTask();
        if (t != null && t.getCommandConfigId() != null) return t.getCommandConfigId();
        return configStore.getLastSuccessfulCommandConfigId();
    }

    private String effectiveTiming() {
        com.openxt.uploadsshfile.model.SingleUploadTask t = configStore.getSingleUploadTask();
        if (t != null && t.getTiming() != null) return t.getTiming();
        return configStore.getLastSuccessfulTiming();
    }

    private void loadServers() {
        Logger.debug("UploadDialog", "loadServers() started");
        serverCombo.removeAllItems();
        List<ServerConfig> servers = configStore.getAllServers();
        Logger.debug("UploadDialog", "Found " + servers.size() + " servers");

        for (ServerConfig server : servers) {
            if (server != null && server.getName() != null && !server.getName().isEmpty()) {
                serverCombo.addItem(server);
                Logger.debug("UploadDialog", "Added server: " + server.getName());
            }
        }

        if (servers.isEmpty()) {
            Logger.debug("UploadDialog", "No servers configured, showing warning");
            // 先关闭对话框，然后显示提示
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(
                    contentPanel,
                    lm.get("upload.error.noServer"),
                    lm.get("config.title"),
                    JOptionPane.WARNING_MESSAGE
                );
            });
        } else {
            // 尝试恢复上次成功选择的服务器
            restoreLastSuccessfulServer();
            // 如果没有可选服务器，则手动触发一次选择事件
            if (serverCombo.getSelectedIndex() < 0) {
                onServerSelected();
            }
            // 所有选择恢复完成后，再恢复用户上次手动选择的执行时机
            restoreLastSuccessfulTiming();
        }
    }

    /**
     * 尝试恢复上次成功选择的服务器（D-37 起：关窗快照优先，回落成功记忆——方法名与旧语义并存保留）
     */
    private void restoreLastSuccessfulServer() {
        String lastServerId = effectiveServerId();
        if (lastServerId == null) return;

        for (int i = 0; i < serverCombo.getItemCount(); i++) {
            ServerConfig server = serverCombo.getItemAt(i);
            if (server != null && lastServerId.equals(server.getId())) {
                serverCombo.setSelectedIndex(i);
                return;
            }
        }
        // 上次的服务器已不存在，自动选择第一个
        serverCombo.setSelectedIndex(0);
    }

    /**
     * 尝试恢复上次成功选择的路径
     * @return true 如果找到并选中了上次的路径
     */
    private boolean restoreLastSuccessfulPath() {
        String lastPathId = effectivePathId(); // D-37 快照优先
        if (lastPathId == null) return false;

        for (int i = 0; i < pathCombo.getItemCount(); i++) {
            PathConfig path = pathCombo.getItemAt(i);
            if (path != null && lastPathId.equals(path.getId())) {
                pathCombo.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    /**
     * 尝试恢复上次成功选择的命令组
     * @return true 如果找到并选中了上次的命令组
     */
    private boolean restoreLastSuccessfulCommandConfig() {
        String lastCommandConfigId = effectiveCommandConfigId(); // D-37 快照优先
        if (lastCommandConfigId == null) return false;

        for (int i = 0; i < commandCombo.getItemCount(); i++) {
            CommandConfig cfg = commandCombo.getItemAt(i);
            if (cfg != null && lastCommandConfigId.equals(cfg.getId())) {
                commandCombo.setSelectedIndex(i);
                return true;
            }
        }
        return false;
    }

    /**
     * 尝试恢复上次成功选择的执行时机
     */
    private void restoreLastSuccessfulTiming() {
        String lastTiming = effectiveTiming(); // D-37 快照优先
        if (lastTiming == null) return;
        if ("AUTO".equals(lastTiming)) {
            timingAutoRadio.setSelected(true);
        } else if ("MANUAL".equals(lastTiming)) {
            timingManualRadio.setSelected(true);
        }
    }

    private void onServerSelected() {
        pathCombo.removeAllItems();
        commandCombo.removeAllItems();
        commandPreviewArea.setText(lm.get("upload.command.noSelection"));
        ServerConfig server = (ServerConfig) serverCombo.getSelectedItem();
        if (server != null) {
            Logger.debug("UploadDialog", "Loading paths for server: " + server.getName());
            List<PathConfig> paths = configStore.getPathsByServer(server.getId());
            for (PathConfig path : paths) {
                if (path != null && path.getRemotePath() != null && !path.getRemotePath().isEmpty()) {
                    pathCombo.addItem(path);
                }
            }
            // 尝试恢复上次路径；设置 selectedIndex 会触发 onPathSelected()
            if (pathCombo.getItemCount() > 0) {
                if (!restoreLastSuccessfulPath()) {
                    pathCombo.setSelectedIndex(0);
                }
            }
        }
    }

    private void onPathSelected() {
        commandCombo.removeAllItems();
        commandPreviewArea.setText(lm.get("upload.command.noSelection"));
        
        ServerConfig server = (ServerConfig) serverCombo.getSelectedItem();
        PathConfig path = (PathConfig) pathCombo.getSelectedItem();
        
        if (server != null && path != null) {
            Logger.debug("UploadDialog", "Loading command configs for server: " + server.getName() + ", path: " + path.getRemotePath());
            
            // 获取匹配的命令配置
            configStore.getAllCommandConfigs()
                .stream()
                .filter(cfg -> server.getId().equals(cfg.getServerId()))
                .filter(cfg -> cfg.getPathId() == null || cfg.getPathId().isEmpty() || cfg.getPathId().equals(path.getId()))
                .forEach(commandCombo::addItem);
            
            // 尝试恢复上次成功选择的命令组
            if (commandCombo.getItemCount() > 0) {
                if (!restoreLastSuccessfulCommandConfig()) {
                    onCommandSelected();
                }
            }
        }
    }

    private void onCommandSelected() {
        CommandConfig config = (CommandConfig) commandCombo.getSelectedItem();
        if (config != null && config.getCommands() != null && !config.getCommands().isEmpty()) {
            String preview = config.getCommands().stream()
                .filter(cmd -> cmd.isEnabled())
                .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
                .map(cmd -> cmd.getCommand())
                .collect(Collectors.joining("\n"));
            commandPreviewArea.setText(preview);
            
            // 如果命令组设置了自动执行，默认选中自动执行，否则默认手动执行
            if (config.getExecuteTiming() == ExecuteTiming.AUTO) {
                timingAutoRadio.setSelected(true);
            } else {
                timingManualRadio.setSelected(true);
            }
        } else {
            commandPreviewArea.setText(lm.get("upload.command.noSelection"));
        }
    }

    /**
     * D-30C（用户裁定）：关窗（标题栏 ✕ 走本方法；Cancel 按钮经既有私有 onCancel 重定向至此）
     * 若存在"已改且非法"的任务 ID——该值不会被落盘（四入口校验在先），静默丢弃曾致
     * 用户困惑"没提示也没保存"，现改为弹一次确认：继续关闭＝放弃修改；返回编辑＝留在窗口。
     */
    @Override
    public void doCancelAction() {
        if (taskIdPanel != null && taskIdPanel.hasInvalidPendingEdit()) {
            int choice = JOptionPane.showConfirmDialog(this.getContentPanel(),
                    lm.get("task.id.invalid.discard"),
                    lm.get("common.warning"),
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (choice != JOptionPane.YES_OPTION) {
                return; // 留在窗口内修改
            }
        }
        super.doCancelAction();
    }

    @Override
    protected void doOKAction() {
        selectedServer = (ServerConfig) serverCombo.getSelectedItem();
        selectedPath = (PathConfig) pathCombo.getSelectedItem();
        selectedCommandConfig = (CommandConfig) commandCombo.getSelectedItem();

        if (selectedServer == null) {
            JOptionPane.showMessageDialog(contentPanel, lm.get("upload.error.noServer"), lm.get("config.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (selectedPath == null) {
            JOptionPane.showMessageDialog(contentPanel, lm.get("upload.error.noPath"), lm.get("config.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        // D-25（FR-04 补注）："已改未落盘"的任务 ID 才做格式校验（字母数字、拒 '-'）；
        // 存量值（含带 '-' 的旧 UUID）未改动时按不透明原样放行（FR-05），执行成功落盘沿用
        String idInput = taskIdPanel.getDisplayedId();
        String persisted = configStore.getSingleUploadTaskId();
        if (!idInput.isEmpty() && !idInput.equals(persisted)
                && !TaskIdGenerator.isValidNewId(idInput)) {
            JOptionPane.showMessageDialog(contentPanel, lm.get("task.id.invalid"),
                    lm.get("config.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 确定执行时机（默认 MANUAL）
        if (timingAutoRadio.isSelected()) {
            selectedTiming = ExecuteTiming.AUTO;
        } else {
            selectedTiming = ExecuteTiming.MANUAL;
        }

        super.doOKAction();
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return serverCombo;
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel panel = new JPanel(new BorderLayout());

        // 左侧：上传按钮 + 执行命令组
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        okButton = new JButton(lm.get("upload.btn.upload"));
        okButton.addActionListener(e -> doOKAction());
        executeCommandsButton = new JButton(lm.get("upload.btn.executeCommands"));
        executeCommandsButton.addActionListener(e -> onExecuteCommands());
        leftButtons.add(okButton);
        leftButtons.add(executeCommandsButton);
        panel.add(leftButtons, BorderLayout.WEST);

        // 右侧：关闭按钮（单独）
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        cancelButton = new JButton(lm.get("dialog.close"));
        cancelButton.addActionListener(e -> onCancel());
        rightButtons.add(cancelButton);
        panel.add(rightButtons, BorderLayout.EAST);

        return panel;
    }

    /**
     * 执行取消操作
     * D-30C：改走 this.doCancelAction()（原直调 super 会绕过关窗确认守卫）
     */
    private void onCancel() {
        doCancelAction();
    }

    /**
     * 执行命令组按钮点击事件
     * 不关闭窗口，直接执行命令
     */
    private void onExecuteCommands() {
        selectedServer = (ServerConfig) serverCombo.getSelectedItem();
        selectedPath = (PathConfig) pathCombo.getSelectedItem();
        selectedCommandConfig = (CommandConfig) commandCombo.getSelectedItem();

        if (selectedServer == null) {
            JOptionPane.showMessageDialog(contentPanel, lm.get("upload.error.noServer"), lm.get("config.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (selectedCommandConfig == null || selectedCommandConfig.getEnabledCommands() == null || selectedCommandConfig.getEnabledCommands().isEmpty()) {
            JOptionPane.showMessageDialog(contentPanel, lm.get("upload.error.noCommand"), lm.get("config.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 通过回调通知 UploadAction 处理命令执行
        if (executeCommandsCallback != null) {
            executeCommandsCallback.onExecuteCommands(new CommandExecuteContext(selectedServer, selectedPath, selectedCommandConfig));
        }
    }

    /**
     * 命令执行上下文
     */
    public static class CommandExecuteContext {
        public final ServerConfig server;
        public final PathConfig path;
        public final CommandConfig commandConfig;

        public CommandExecuteContext(ServerConfig server, PathConfig path, CommandConfig commandConfig) {
            this.server = server;
            this.path = path;
            this.commandConfig = commandConfig;
        }
    }

    public boolean isConfirmed() {
        return isOK();
    }

    public ServerConfig getSelectedServer() {
        return selectedServer;
    }

    public PathConfig getSelectedPath() {
        return selectedPath;
    }

    public CommandConfig getSelectedCommandConfig() {
        return selectedCommandConfig;
    }

    public ExecuteTiming getSelectedTiming() {
        return selectedTiming;
    }

    public boolean shouldExecuteCommands() {
        return selectedCommandConfig != null 
            && selectedCommandConfig.getEnabledCommands() != null
            && !selectedCommandConfig.getEnabledCommands().isEmpty();
    }
}
