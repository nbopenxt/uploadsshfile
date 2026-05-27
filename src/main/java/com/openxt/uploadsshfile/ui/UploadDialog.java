package com.openxt.uploadsshfile.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.ExecuteTiming;
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

        // 组装
        JPanel centerPanel = new JPanel();
        centerPanel.setLayout(new BoxLayout(centerPanel, BoxLayout.Y_AXIS));
        centerPanel.add(filePanel);
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
     * 尝试恢复上次成功选择的服务器
     */
    private void restoreLastSuccessfulServer() {
        String lastServerId = configStore.getLastSuccessfulServerId();
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
        String lastPathId = configStore.getLastSuccessfulPathId();
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
        String lastCommandConfigId = configStore.getLastSuccessfulCommandConfigId();
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
        String lastTiming = configStore.getLastSuccessfulTiming();
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
     */
    private void onCancel() {
        super.doCancelAction();
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
