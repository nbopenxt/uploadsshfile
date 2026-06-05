package com.openxt.uploadsshfile.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.openxt.uploadsshfile.config.ConfigManager;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.OperatingSystem;
import com.openxt.uploadsshfile.sftp.SftpValidator;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.util.Logger;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.List;

/**
 * \u914d\u7f6e\u7ba1\u7406\u5bf9\u8bdd\u6846
 */
public class ConfigDialog extends JDialog {
    private JTabbedPane tabbedPane;
    private ConfigManager configManager;
    private UnifiedConfigStore configStore;
    private JComboBox<LanguageManager.LanguageInfo> languageCombo;
    private JCheckBox logEnabledCheckbox;

    // \u670d\u52a1\u5668\u5217\u8868
    private JTable serverTable;
    private DefaultTableModel serverTableModel;

    // \u670d\u52a1\u5668\u8868\u5355
    private JTextField serverNameField;
    private JTextField serverHostField;
    private JTextField serverPortField;
    private JTextField serverUsernameField;
    private JPasswordField serverPasswordField;
    private JComboBox<OperatingSystem> serverOsCombo;

    // \u8def\u5f84\u5217\u8868
    private JTable pathTable;
    private DefaultTableModel pathTableModel;
    private JComboBox<ServerConfig> pathServerCombo;

// \u8def\u5f84\u8868\u5355
        private JTextField pathRemoteField;
        private PathConfig editingPathConfig; // \u5f53\u524d\u7f16\u8f91\u7684\u8def\u5f84\u914d\u7f6e

    public ConfigDialog(Project project) {
        super(WindowManager.getInstance().getFrame(project), 
              LanguageManager.getInstance().get("config.title"), true);
        this.configManager = ConfigManager.getInstance();
        this.configStore = UnifiedConfigStore.getInstance();

        initComponents();
        loadServers();
        loadPaths();
        pack();
        setLocationRelativeTo(getParent());
    }

    private void initComponents() {
        LanguageManager lm = LanguageManager.getInstance();
        
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 语言选择面板
        JPanel languagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        languagePanel.add(new JLabel(lm.get("language") + ":"));
        languageCombo = new JComboBox<>();
        for (LanguageManager.LanguageInfo lang : LanguageManager.getSupportedLanguages()) {
            languageCombo.addItem(lang);
        }
        // 设置当前语言
        String currentLang = lm.getCurrentLanguage();
        for (int i = 0; i < languageCombo.getItemCount(); i++) {
            LanguageManager.LanguageInfo item = languageCombo.getItemAt(i);
            if (item.code.equals(currentLang)) {
                languageCombo.setSelectedIndex(i);
                break;
            }
        }
        languageCombo.addActionListener(e -> onLanguageChanged());
        languagePanel.add(languageCombo);

        // 日志开关 Checkbox
        logEnabledCheckbox = new JCheckBox(lm.get("config.enable.log"));
        logEnabledCheckbox.setSelected(configStore.getConfig().isLogEnabled());
        logEnabledCheckbox.addActionListener(e -> {
            configStore.getConfig().setLogEnabled(logEnabledCheckbox.isSelected());
            Logger.setLogOutput(logEnabledCheckbox.isSelected());
            try {
                configStore.save();
            } catch (Exception ex) {
                Logger.error("ConfigDialog", "Failed to save log setting", ex);
            }
        });
        languagePanel.add(logEnabledCheckbox);

        tabbedPane = new JTabbedPane();

        // \u670d\u52a1\u5668\u7ba1\u7406\u9762\u677f
        tabbedPane.addTab(lm.get("config.tab.server"), createServerPanel());

        // \u8def\u5f84\u7ba1\u7406\u9762\u677f
        tabbedPane.addTab(lm.get("config.tab.path"), createPathPanel());

        // \u5e95\u90e8\u6309\u94ae
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeButton = new JButton(lm.get("config.btn.close"));
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);

        mainPanel.add(languagePanel, BorderLayout.NORTH);
        mainPanel.add(tabbedPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
        setMinimumSize(new Dimension(600, 400));
    }
    
    private void onLanguageChanged() {
        LanguageManager.LanguageInfo selected = (LanguageManager.LanguageInfo) languageCombo.getSelectedItem();
        if (selected != null) {
            LanguageManager.getInstance().setLanguage(selected.code);
            // \u91cd\u65b0\u521b\u5efa\u5bf9\u8bdd\u6846
            dispose();
            // \u663e\u793a\u63d0\u793a\u7528\u6237\u9700\u8981\u91cd\u542f IDEA
            JOptionPane.showMessageDialog(
                null,
                LanguageManager.getInstance().get("msg.language.changed", selected.displayName),
                LanguageManager.getInstance().get("msg.language.changed.title"),
                JOptionPane.INFORMATION_MESSAGE
            );
        }
    }

    private JPanel createServerPanel() {
        LanguageManager lm = LanguageManager.getInstance();
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // \u670d\u52a1\u5668\u5217\u8868 - \u4f7f\u7528\u56fd\u9645\u5316\u952e
        String[] columns = {
            lm.get("table.server.name"),
            lm.get("table.server.host"),
            lm.get("table.server.port"),
            lm.get("table.server.os"),
            lm.get("table.server.username")
        };
        serverTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        serverTable = new JTable(serverTableModel);
        serverTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = serverTable.getSelectedRow();
                Logger.debug("ConfigDialog", "Selection changed, row=" + row);
                if (row >= 0) {
                    List<ServerConfig> servers = configManager.getAllServers();
                    Logger.debug("ConfigDialog", "Total servers: " + servers.size() + ", requested row: " + row);
                    if (row < servers.size()) {
                        ServerConfig server = servers.get(row);
                        Logger.debug("ConfigDialog", "Selected server: id=" + server.getId() + ", name=" + server.getName());
                        serverNameField.setText(server.getName());
                        serverHostField.setText(server.getHost());
                        serverPortField.setText(String.valueOf(server.getPort()));
                        serverUsernameField.setText(server.getUsername());
                        // \u4ece\u5b89\u5168\u5b58\u50a8\u52a0\u8f7d\u5bc6\u7801\uff08\u63a9\u7801\u663e\u793a\uff09
                        String password = configManager.getPassword(server.getId());
                        Logger.debug("ConfigDialog", "Retrieved password: " + (password == null ? "null" : (password.isEmpty() ? "empty" : "has value (len=" + password.length() + ")")));
                        if (password != null && !password.isEmpty()) {
                            serverPasswordField.setText(password);
                        } else {
                            serverPasswordField.setText("");
                        }
                        // \u52a0\u8f7d\u64cd\u4f5c\u7cfb\u7edf\u7c7b\u578b
                        serverOsCombo.setSelectedItem(server.getOperatingSystem());
                    }
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(serverTable);

        // \u670d\u52a1\u5668\u8868\u5355
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder(lm.get("panel.server.info")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        serverNameField = new JTextField(15);
        serverHostField = new JTextField(15);
        serverPortField = new JTextField("22", 5);
        serverUsernameField = new JTextField(15);
        serverPasswordField = new JPasswordField(15);
        
        // 操作系统下拉框
        serverOsCombo = new JComboBox<>();
        for (OperatingSystem os : OperatingSystem.values()) {
            serverOsCombo.addItem(os);
        }
        serverOsCombo.setSelectedItem(OperatingSystem.LINUX); // 默认选中 Linux

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel(lm.get("server.name") + ":"), gbc);
        gbc.gridx = 1; formPanel.add(serverNameField, gbc);

        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(new JLabel(lm.get("server.host") + ":"), gbc);
        gbc.gridx = 1; formPanel.add(serverHostField, gbc);

        gbc.gridx = 0; gbc.gridy = 2; formPanel.add(new JLabel(lm.get("server.port") + ":"), gbc);
        gbc.gridx = 1; formPanel.add(serverPortField, gbc);

        gbc.gridx = 0; gbc.gridy = 3; formPanel.add(new JLabel(lm.get("server.username") + ":"), gbc);
        gbc.gridx = 1; formPanel.add(serverUsernameField, gbc);

        gbc.gridx = 0; gbc.gridy = 4; formPanel.add(new JLabel(lm.get("server.password") + ":"), gbc);
        gbc.gridx = 1; formPanel.add(serverPasswordField, gbc);

        gbc.gridx = 0; gbc.gridy = 5; formPanel.add(new JLabel(lm.get("server.os") + ":"), gbc);
        gbc.gridx = 1; formPanel.add(serverOsCombo, gbc);

        // \u6309\u94ae
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton(lm.get("config.btn.add"));
        JButton editButton = new JButton(lm.get("config.btn.save"));
        JButton deleteButton = new JButton(lm.get("config.btn.delete"));
        JButton testButton = new JButton(lm.get("config.btn.test"));

        addButton.addActionListener(e -> addServer());
        editButton.addActionListener(e -> editServer());
        deleteButton.addActionListener(e -> deleteServer());
        testButton.addActionListener(e -> testConnection());

        buttonPanel.add(addButton);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(testButton);

        // \u7ec4\u88c5
        panel.add(scrollPane, BorderLayout.NORTH);
        panel.add(formPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel createPathPanel() {
        LanguageManager lm = LanguageManager.getInstance();
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // \u8def\u5f84\u5217\u8868 - \u4f7f\u7528\u56fd\u9645\u5316\u952e
        String[] columns = {
            lm.get("table.path.server"),
            lm.get("table.path.remote")
        };
        pathTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        pathTable = new JTable(pathTableModel);
        // \u6dfb\u52a0\u8def\u5f84\u5217\u8868\u9009\u62e9\u76d1\u542c\u5668
        pathTable.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int row = pathTable.getSelectedRow();
                if (row >= 0 && row < pathTableModel.getRowCount()) {
                    Object serverNameObj = pathTableModel.getValueAt(row, 0);
                    Object remotePathObj = pathTableModel.getValueAt(row, 1);

                    // \u68c0\u67e5\u662f\u5426\u4e3a\u6709\u6548\u6570\u636e
                    if (serverNameObj == null || remotePathObj == null) {
                        return;
                    }

                    String serverName = serverNameObj.toString();
                    String remotePath = remotePathObj.toString();

                    // \u67e5\u627e\u5bf9\u5e94\u7684\u670d\u52a1\u5668\u5e76\u9009\u4e2d
                    for (int i = 0; i < pathServerCombo.getItemCount(); i++) {
                        ServerConfig server = pathServerCombo.getItemAt(i);
                        if (server != null && server.getName().equals(serverName)) {
                            pathServerCombo.setSelectedIndex(i);
                            break;
                        }
                    }
                    pathRemoteField.setText(remotePath);
                    editingPathConfig = findPathConfig(serverName, remotePath);
                }
            }
        });
        JScrollPane scrollPane = new JScrollPane(pathTable);

        // \u8def\u5f84\u8868\u5355
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createTitledBorder(lm.get("panel.path.info")));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        pathServerCombo = new JComboBox<>();
        pathRemoteField = new JTextField(15);

        gbc.gridx = 0; gbc.gridy = 0; formPanel.add(new JLabel(lm.get("path.belong.server") + ":"), gbc);
        gbc.gridx = 1; formPanel.add(pathServerCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 1; formPanel.add(new JLabel(lm.get("path.remote") + ":"), gbc);
        gbc.gridx = 1; formPanel.add(pathRemoteField, gbc);

        // \u6309\u94ae
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton addButton = new JButton(lm.get("config.btn.add"));
        JButton saveButton = new JButton(lm.get("config.btn.save"));
        JButton deleteButton = new JButton(lm.get("config.btn.delete"));

        addButton.addActionListener(e -> addPath());
        saveButton.addActionListener(e -> savePath());
        deleteButton.addActionListener(e -> deletePath());

        buttonPanel.add(addButton);
        buttonPanel.add(saveButton);
        buttonPanel.add(deleteButton);

        // \u7ec4\u88c5
        panel.add(scrollPane, BorderLayout.NORTH);
        panel.add(formPanel, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void loadServers() {
        Logger.debug("ConfigDialog", "loadServers() started");
        serverTableModel.setRowCount(0);
        List<ServerConfig> servers = configManager.getAllServers();
        Logger.debug("ConfigDialog", "loadServers() got " + servers.size() + " servers from ConfigManager");

        // \u5148\u6e05\u7a7a\u5e76\u91cd\u65b0\u52a0\u8f7d\u8def\u5f84\u9762\u677f\u7684\u670d\u52a1\u5668\u4e0b\u62c9\u6846
        pathServerCombo.removeAllItems();

        for (ServerConfig server : servers) {
            // \u8fc7\u6ee4\u65e0\u6548\u6570\u636e
            if (server == null || server.getName() == null || server.getName().trim().isEmpty()) {
                Logger.debug("ConfigDialog", "  Skipping invalid server record");
                continue;
            }
            Logger.debug("ConfigDialog", "  Adding row for server: " + server.getName());
            serverTableModel.addRow(new Object[]{
                server.getName(), 
                server.getHost(), 
                server.getPort(), 
                server.getOperatingSystem().getDisplayName(),
                server.getUsername()
            });
            pathServerCombo.addItem(server);
        }

        Logger.debug("ConfigDialog", "loadServers() completed, table rows: " + serverTableModel.getRowCount());
    }

    private void loadPaths() {
        pathTableModel.setRowCount(0);
        List<ServerConfig> servers = configManager.getAllServers();
        for (ServerConfig server : servers) {
            // \u8df3\u8fc7\u65e0\u6548\u7684\u670d\u52a1\u5668
            if (server == null || server.getName() == null || server.getName().trim().isEmpty()) {
                continue;
            }
            List<PathConfig> paths = configManager.getPathsByServer(server.getId());
            for (PathConfig path : paths) {
                // \u8fc7\u6ee4\u65e0\u6548\u7684\u8def\u5f84\u8bb0\u5f55
                if (path == null || path.getRemotePath() == null || path.getRemotePath().trim().isEmpty()) {
                    Logger.debug("ConfigDialog", "  Skipping invalid path record");
                    continue;
                }
                pathTableModel.addRow(new Object[]{server.getName(), path.getRemotePath()});
            }
        }
    }

    private void addServer() {
        if (!validateServerForm()) return;

        LanguageManager lm = LanguageManager.getInstance();
        // \u68c0\u67e5\u670d\u52a1\u5668\u540d\u79f0\u662f\u5426\u91cd\u590d
        String name = serverNameField.getText().trim();
        for (ServerConfig existing : configManager.getAllServers()) {
            if (existing.getName().equalsIgnoreCase(name)) {
                JOptionPane.showMessageDialog(this, lm.get("msg.error.server.name.exists"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        try {
            ServerConfig server = new ServerConfig();
            server.setName(name);
            server.setHost(serverHostField.getText().trim());
            server.setPort(Integer.parseInt(serverPortField.getText().trim()));
            server.setUsername(serverUsernameField.getText().trim());
            server.setPassword(new String(serverPasswordField.getPassword()));
            // \u4fdd\u5b58\u64cd\u4f5c\u7cfb\u7edf\u7c7b\u578b
            OperatingSystem selectedOs = (OperatingSystem) serverOsCombo.getSelectedItem();
            server.setOsType(selectedOs != null ? selectedOs.getKey() : OperatingSystem.LINUX.getKey());

            Logger.debug("ConfigDialog", "Calling configManager.addServer()");
            configManager.addServer(server);
            Logger.debug("ConfigDialog", "addServer completed, clearing form and reloading");
            clearServerForm();
            loadServers();
            // \u53d6\u6d88\u8868\u683c\u9009\u62e9
            serverTable.clearSelection();
        } catch (Exception e) {
            Logger.error("ConfigDialog", "addServer failed", e);
            JOptionPane.showMessageDialog(this, lm.get("msg.error.add") + e.getMessage(), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void editServer() {
        LanguageManager lm = LanguageManager.getInstance();
        int row = serverTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, lm.get("msg.error.server.select"), lm.get("warning.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (!validateServerForm()) return;

        // \u68c0\u67e5\u670d\u52a1\u5668\u540d\u79f0\u662f\u5426\u91cd\u590d\uff08\u6392\u9664\u5f53\u524d\u7f16\u8f91\u7684\u670d\u52a1\u5668\uff09
        String name = serverNameField.getText().trim();
        List<ServerConfig> servers = configManager.getAllServers();
        ServerConfig currentServer = servers.get(row);
        for (ServerConfig existing : servers) {
            if (existing.getId().equals(currentServer.getId())) continue;
            if (existing.getName().equalsIgnoreCase(name)) {
                JOptionPane.showMessageDialog(this, lm.get("msg.error.server.name.exists"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        try {
            currentServer.setName(name);
            currentServer.setHost(serverHostField.getText().trim());
            currentServer.setPort(Integer.parseInt(serverPortField.getText().trim()));
            currentServer.setUsername(serverUsernameField.getText().trim());
            String password = new String(serverPasswordField.getPassword());
            if (!password.isEmpty()) {
                currentServer.setPassword(password);
            }
            // \u66f4\u65b0\u64cd\u4f5c\u7cfb\u7edf\u7c7b\u578b
            OperatingSystem selectedOs = (OperatingSystem) serverOsCombo.getSelectedItem();
            currentServer.setOsType(selectedOs != null ? selectedOs.getKey() : OperatingSystem.LINUX.getKey());

            configManager.updateServer(currentServer);
            clearServerForm();
            loadServers();
            // \u53d6\u6d88\u8868\u683c\u9009\u62e9
            serverTable.clearSelection();
        } catch (Exception e) {
            Logger.error("ConfigDialog", "editServer failed", e);
            JOptionPane.showMessageDialog(this, lm.get("msg.error.save") + e.getMessage(), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteServer() {
        LanguageManager lm = LanguageManager.getInstance();
        int row = serverTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, lm.get("msg.error.server.delete.select"), lm.get("warning.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, lm.get("msg.error.server.delete.confirm"), lm.get("confirm.title"), JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            List<ServerConfig> servers = configManager.getAllServers();
            ServerConfig server = servers.get(row);
            configManager.deleteServer(server.getId());
            clearServerForm();
            loadServers();
            loadPaths();
            // \u53d6\u6d88\u8868\u683c\u9009\u62e9
            serverTable.clearSelection();
        }
    }

    private void testConnection() {
        if (!validateServerForm()) return;

        String host = serverHostField.getText().trim();
        int port = Integer.parseInt(serverPortField.getText().trim());
        String username = serverUsernameField.getText().trim();
        String password = new String(serverPasswordField.getPassword());

        boolean success = SftpValidator.testConnection(host, port, username, password);

        LanguageManager lm = LanguageManager.getInstance();
        if (success) {
            JOptionPane.showMessageDialog(this, lm.get("msg.test.success"), lm.get("warning.title"), JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, lm.get("msg.test.connection.failed"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * \u6839\u636e\u670d\u52a1\u5668\u540d\u79f0\u548c\u8fdc\u7a0b\u8def\u5f84\u67e5\u627e\u8def\u5f84\u914d\u7f6e
     */
    private PathConfig findPathConfig(String serverName, String remotePath) {
        List<ServerConfig> servers = configManager.getAllServers();
        for (ServerConfig server : servers) {
            if (server.getName().equals(serverName)) {
                List<PathConfig> paths = configManager.getPathsByServer(server.getId());
                for (PathConfig path : paths) {
                    if (path.getRemotePath().equals(remotePath)) {
                        return path;
                    }
                }
            }
        }
        return null;
    }

    private boolean validateServerForm() {
        LanguageManager lm = LanguageManager.getInstance();
        if (serverNameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, lm.get("msg.error.validation.name"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (serverHostField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, lm.get("msg.error.validation.host"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (serverUsernameField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, lm.get("msg.error.validation.username"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
            return false;
        }
        return true;
    }

    private void clearServerForm() {
        serverNameField.setText("");
        serverHostField.setText("");
        serverPortField.setText("22");
        serverUsernameField.setText("");
        serverPasswordField.setText("");
        serverOsCombo.setSelectedItem(OperatingSystem.LINUX);
    }

    private void addPath() {
        LanguageManager lm = LanguageManager.getInstance();
        ServerConfig server = (ServerConfig) pathServerCombo.getSelectedItem();
        if (server == null) {
            JOptionPane.showMessageDialog(this, lm.get("msg.error.path.server.required"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (pathRemoteField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, lm.get("msg.error.path.remote.required"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        String remotePath = pathRemoteField.getText().trim();

        // \u67e5\u91cd\uff1a\u68c0\u67e5\u540c\u4e00\u670d\u52a1\u5668\u4e0b\u662f\u5426\u6709\u76f8\u540c\u8def\u5f84
        List<PathConfig> existingPaths = configManager.getPathsByServer(server.getId());
        for (PathConfig existing : existingPaths) {
            if (existing.getRemotePath().equalsIgnoreCase(remotePath)) {
                JOptionPane.showMessageDialog(this, lm.get("msg.error.path.remote.exists"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        PathConfig path = new PathConfig();
        path.setServerId(server.getId());
        path.setRemotePath(remotePath);

        configManager.addPath(path);
        pathRemoteField.setText("");
        loadPaths();
    }

    private void savePath() {
        LanguageManager lm = LanguageManager.getInstance();
        ServerConfig server = (ServerConfig) pathServerCombo.getSelectedItem();
        if (server == null) {
            JOptionPane.showMessageDialog(this, lm.get("msg.error.path.select"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (pathRemoteField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, lm.get("msg.error.path.remote.required"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (editingPathConfig == null) {
            JOptionPane.showMessageDialog(this, lm.get("msg.error.path.select.item"), lm.get("warning.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        String remotePath = pathRemoteField.getText().trim();

        // \u67e5\u91cd\uff1a\u68c0\u67e5\u540c\u4e00\u670d\u52a1\u5668\u4e0b\u662f\u5426\u6709\u76f8\u540c\u8def\u5f84\uff08\u6392\u9664\u81ea\u8eab\uff09
        List<PathConfig> existingPaths = configManager.getPathsByServer(server.getId());
        for (PathConfig existing : existingPaths) {
            if (!existing.getId().equals(editingPathConfig.getId())
                    && existing.getRemotePath().equalsIgnoreCase(remotePath)) {
                JOptionPane.showMessageDialog(this, lm.get("msg.error.path.remote.exists"), lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        editingPathConfig.setServerId(server.getId());
        editingPathConfig.setRemotePath(remotePath);
        configManager.updatePath(editingPathConfig);
        editingPathConfig = null;
        pathRemoteField.setText("");
        loadPaths();
    }

    private void deletePath() {
        LanguageManager lm = LanguageManager.getInstance();
        int row = pathTable.getSelectedRow();
        if (row < 0 || row >= pathTableModel.getRowCount()) {
            JOptionPane.showMessageDialog(this, lm.get("msg.error.path.delete.select"), lm.get("warning.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this, lm.get("msg.error.path.delete.confirm"), lm.get("confirm.title"), JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            // \u83b7\u53d6\u9009\u4e2d\u7684\u8def\u5f84\u4fe1\u606f
            Object serverNameObj = pathTableModel.getValueAt(row, 0);
            Object remotePathObj = pathTableModel.getValueAt(row, 1);

            // \u68c0\u67e5\u662f\u5426\u4e3a\u6709\u6548\u6570\u636e
            if (serverNameObj == null || remotePathObj == null) {
                return;
            }

            String serverName = serverNameObj.toString();
            String remotePath = remotePathObj.toString();

            // \u67e5\u627e\u5bf9\u5e94\u7684\u914d\u7f6e\u5e76\u5220\u9664
            List<ServerConfig> servers = configManager.getAllServers();
            for (ServerConfig server : servers) {
                if (server != null && server.getName().equals(serverName)) {
                    List<PathConfig> pathList = configManager.getPathsByServer(server.getId());
                    for (PathConfig path : pathList) {
                        if (path != null && path.getRemotePath().equals(remotePath)) {
                            configManager.deletePath(path.getId());
                            break;
                        }
                    }
                }
            }

            // \u91cd\u7f6e\u7f16\u8f91\u72b6\u6001
            editingPathConfig = null;
            pathRemoteField.setText("");

            // \u91cd\u65b0\u52a0\u8f7d
            loadPaths();
            loadServers();
        }
    }
}
