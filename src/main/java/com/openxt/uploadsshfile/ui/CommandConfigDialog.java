package com.openxt.uploadsshfile.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.*;
import com.openxt.uploadsshfile.store.StoreManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令配置对话框
 * 包含四个 Tab：黑名单管理、关键词管理、无返回命令管理、命令组管理
 */
public class CommandConfigDialog extends DialogWrapper {
    
    /**
     * 下拉框项封装类
     * 用于存储选项的 id 和显示名称
     */
    private static class ComboBoxItem {
        private final String id;
        private final String displayName;
        
        public ComboBoxItem(String id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }
        
        public String getId() {
            return id;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
        
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || getClass() != obj.getClass()) return false;
            ComboBoxItem other = (ComboBoxItem) obj;
            return id != null ? id.equals(other.id) : other.id == null;
        }
        
        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }
    
    private JTabbedPane tabbedPane;
    private BlacklistPanel blacklistPanel;
    private DangerousPathsPanel dangerousPathsPanel;
    private KeywordPanel keywordPanel;
    private CommandGroupPanel commandGroupPanel;
    private final LanguageManager lang = LanguageManager.getInstance();
    
    public CommandConfigDialog() {
        super(true); // modal
        setTitle(lang.get("cmd.title"));
        setOKButtonText(lang.get("cmd.btn.save"));
        init();
    }
    
    @Override
    protected void init() {
        // 先创建所有组件
        tabbedPane = new JTabbedPane();
        
        // Tab 1: 黑名单管理
        blacklistPanel = new BlacklistPanel();
        tabbedPane.addTab(lang.get("cmd.tab.blacklist"), blacklistPanel.getPanel());
        
        // Tab 2: 敏感目录管理
        dangerousPathsPanel = new DangerousPathsPanel();
        tabbedPane.addTab(lang.get("cmd.tab.dangerousPaths"), dangerousPathsPanel.getPanel());
        
        // Tab 3: 关键词管理
        keywordPanel = new KeywordPanel();
        tabbedPane.addTab(lang.get("cmd.tab.keywords"), keywordPanel.getPanel());
        
        // Tab 3: 命令组管理
        commandGroupPanel = new CommandGroupPanel();
        tabbedPane.addTab(lang.get("cmd.tab.commandGroups"), commandGroupPanel.getPanel());
        
        // 设置首选大小
        tabbedPane.setPreferredSize(new Dimension(800, 550));
        
        // 再调用父类初始化
        super.init();
    }
    
    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        return null; // 命令组配置不强制要求非空
    }
    
    @Override
    protected void doOKAction() {
        // 保存所有配置
        blacklistPanel.save();
        dangerousPathsPanel.save();
        keywordPanel.save();
        commandGroupPanel.save();
        super.doOKAction();
    }
    
    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        return tabbedPane;
    }
    
    // ==================== 黑名单管理面板 ====================
    
    private static class BlacklistPanel {
        private final JPanel panel;
        private final JComboBox<String> osTypeCombo;
        private final JList<String> blacklistList;
        private final DefaultListModel<String> listModel;
        private JTextField patternField;
        private final LanguageManager lang = LanguageManager.getInstance();
        private final UnifiedConfigStore store;
        
        public BlacklistPanel() {
            store = StoreManager.getInstance().getUnifiedConfigStore();
            panel = new JPanel(new BorderLayout(5, 5));
            
            // 顶部：操作系统选择
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            topPanel.add(new JLabel(lang.get("blacklist.label.osType") + ":"));
            osTypeCombo = new JComboBox<>(new String[]{lang.get("os.type.linux"), lang.get("os.type.windows")});
            topPanel.add(osTypeCombo);
            panel.add(topPanel, BorderLayout.NORTH);
            
            // 中间：黑名单列表
            listModel = new DefaultListModel<>();
            blacklistList = new JList<>(listModel);
            blacklistList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            blacklistList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int index = blacklistList.getSelectedIndex();
                    if (index >= 0) {
                        patternField.setText(listModel.get(index));
                    }
                }
            });
            
            // 加载初始数据
            loadBlacklist();
            
            // 操作系统切换时重新加载列表
            osTypeCombo.addActionListener(e -> loadBlacklist());
            
            panel.add(new JScrollPane(blacklistList), BorderLayout.CENTER);
            
            // 底部：输入和按钮
            JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
            patternField = new JTextField();
            bottomPanel.add(patternField, BorderLayout.CENTER);
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton addButton = new JButton(lang.get("cmd.btn.add"));
            JButton updateButton = new JButton(lang.get("cmd.btn.update"));
            JButton removeButton = new JButton(lang.get("cmd.btn.remove"));
            JButton resetButton = new JButton(lang.get("cmd.btn.reset"));
            
            addButton.addActionListener(e -> addPattern());
            updateButton.addActionListener(e -> updatePattern());
            removeButton.addActionListener(e -> removePattern());
            resetButton.addActionListener(e -> resetToDefault());
            
            buttonPanel.add(addButton);
            buttonPanel.add(updateButton);
            buttonPanel.add(removeButton);
            buttonPanel.add(resetButton);
            bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
            
            panel.add(bottomPanel, BorderLayout.SOUTH);
        }
        
        public JPanel getPanel() {
            return panel;
        }
        
        private void loadBlacklist() {
            listModel.clear();
            List<String> blacklist = getCurrentBlacklist();
            for (String pattern : blacklist) {
                listModel.addElement(pattern);
            }
        }
        
        private List<String> getCurrentBlacklist() {
            int selected = osTypeCombo.getSelectedIndex();
            UnifiedPluginConfig.BlacklistConfig blacklist = store.getBlacklist();
            if (selected == 0) {
                return new ArrayList<>(blacklist.getLinuxBlacklist());
            } else {
                return new ArrayList<>(blacklist.getWindowsBlacklist());
            }
        }
        
        private void saveCurrentBlacklist(List<String> blacklist) {
            int selected = osTypeCombo.getSelectedIndex();
            UnifiedPluginConfig.BlacklistConfig config = store.getBlacklist();
            if (selected == 0) {
                config.setLinuxBlacklist(blacklist);
            } else {
                config.setWindowsBlacklist(blacklist);
            }
            store.saveBlacklist(config);
        }
        
        private void addPattern() {
            String pattern = patternField.getText().trim();
            if (!pattern.isEmpty() && !listModel.contains(pattern)) {
                List<String> current = new ArrayList<>();
                for (int i = 0; i < listModel.size(); i++) {
                    current.add(listModel.get(i));
                }
                current.add(pattern);
                saveCurrentBlacklist(current);
                listModel.addElement(pattern);
                patternField.setText("");
            }
        }
        
        private void updatePattern() {
            int index = blacklistList.getSelectedIndex();
            String pattern = patternField.getText().trim();
            if (index >= 0 && !pattern.isEmpty()) {
                List<String> current = new ArrayList<>();
                for (int i = 0; i < listModel.size(); i++) {
                    current.add(listModel.get(i));
                }
                current.set(index, pattern);
                saveCurrentBlacklist(current);
                listModel.set(index, pattern);
            }
        }
        
        private void removePattern() {
            int index = blacklistList.getSelectedIndex();
            if (index >= 0) {
                List<String> current = new ArrayList<>();
                for (int i = 0; i < listModel.size(); i++) {
                    current.add(listModel.get(i));
                }
                current.remove(index);
                saveCurrentBlacklist(current);
                listModel.remove(index);
                patternField.setText("");
            }
        }
        
        private void resetToDefault() {
            // 只恢复当前选中的操作系统的黑名单
            int selected = osTypeCombo.getSelectedIndex();
            String osKey = (selected == 0) ? "linux" : "windows";
            UnifiedPluginConfig.BlacklistConfig config = store.getBlacklist();
            config.resetOsBlacklist(osKey);
            store.saveBlacklist(config);
            loadBlacklist();
            patternField.setText("");
        }
        
        public void save() {
            // 数据已在操作时保存
        }
    }
    
    // ==================== 敏感目录管理面板 ====================
    
    private static class DangerousPathsPanel {
        private final JPanel panel;
        private final JComboBox<String> osTypeCombo;
        private final JList<String> pathsList;
        private final DefaultListModel<String> listModel;
        private JTextField pathField;
        private final LanguageManager lang = LanguageManager.getInstance();
        private final UnifiedConfigStore store;
        
        public DangerousPathsPanel() {
            store = StoreManager.getInstance().getUnifiedConfigStore();
            panel = new JPanel(new BorderLayout(5, 5));
            
            // 顶部：操作系统选择
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            topPanel.add(new JLabel(lang.get("dangerousPaths.label.osType") + ":"));
            osTypeCombo = new JComboBox<>(new String[]{lang.get("os.type.linux"), lang.get("os.type.windows")});
            topPanel.add(osTypeCombo);
            panel.add(topPanel, BorderLayout.NORTH);
            
            // 中间：目录列表
            listModel = new DefaultListModel<>();
            pathsList = new JList<>(listModel);
            pathsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            pathsList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    int index = pathsList.getSelectedIndex();
                    if (index >= 0) {
                        pathField.setText(listModel.get(index));
                    }
                }
            });
            
            // 加载初始数据
            loadPaths();
            
            // 操作系统切换时重新加载列表
            osTypeCombo.addActionListener(e -> loadPaths());
            
            panel.add(new JScrollPane(pathsList), BorderLayout.CENTER);
            
            // 底部：输入和按钮
            JPanel bottomPanel = new JPanel(new BorderLayout(5, 5));
            pathField = new JTextField();
            bottomPanel.add(pathField, BorderLayout.CENTER);
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton addButton = new JButton(lang.get("cmd.btn.add"));
            JButton updateButton = new JButton(lang.get("cmd.btn.update"));
            JButton removeButton = new JButton(lang.get("cmd.btn.remove"));
            JButton resetButton = new JButton(lang.get("cmd.btn.reset"));
            
            addButton.addActionListener(e -> addPath());
            updateButton.addActionListener(e -> updatePath());
            removeButton.addActionListener(e -> removePath());
            resetButton.addActionListener(e -> resetToDefault());
            
            buttonPanel.add(addButton);
            buttonPanel.add(updateButton);
            buttonPanel.add(removeButton);
            buttonPanel.add(resetButton);
            bottomPanel.add(buttonPanel, BorderLayout.SOUTH);
            
            panel.add(bottomPanel, BorderLayout.SOUTH);
        }
        
        public JPanel getPanel() {
            return panel;
        }
        
        private void loadPaths() {
            listModel.clear();
            List<String> paths = getCurrentPaths();
            for (String path : paths) {
                listModel.addElement(path);
            }
        }
        
        private List<String> getCurrentPaths() {
            int selected = osTypeCombo.getSelectedIndex();
            UnifiedPluginConfig.BlacklistConfig blacklist = store.getBlacklist();
            if (selected == 0) {
                return new ArrayList<>(blacklist.getLinuxDangerousPaths());
            } else {
                return new ArrayList<>(blacklist.getWindowsDangerousPaths());
            }
        }
        
        private void saveCurrentPaths(List<String> paths) {
            int selected = osTypeCombo.getSelectedIndex();
            UnifiedPluginConfig.BlacklistConfig config = store.getBlacklist();
            if (selected == 0) {
                config.setLinuxDangerousPaths(paths);
            } else {
                config.setWindowsDangerousPaths(paths);
            }
            store.saveBlacklist(config);
        }
        
        private void addPath() {
            String path = pathField.getText().trim();
            if (!path.isEmpty() && !listModel.contains(path)) {
                List<String> current = new ArrayList<>();
                for (int i = 0; i < listModel.size(); i++) {
                    current.add(listModel.get(i));
                }
                current.add(path);
                saveCurrentPaths(current);
                listModel.addElement(path);
                pathField.setText("");
            }
        }
        
        private void updatePath() {
            int index = pathsList.getSelectedIndex();
            String path = pathField.getText().trim();
            if (index >= 0 && !path.isEmpty()) {
                List<String> current = new ArrayList<>();
                for (int i = 0; i < listModel.size(); i++) {
                    current.add(listModel.get(i));
                }
                current.set(index, path);
                saveCurrentPaths(current);
                listModel.set(index, path);
            }
        }
        
        private void removePath() {
            int index = pathsList.getSelectedIndex();
            if (index >= 0) {
                List<String> current = new ArrayList<>();
                for (int i = 0; i < listModel.size(); i++) {
                    current.add(listModel.get(i));
                }
                current.remove(index);
                saveCurrentPaths(current);
                listModel.remove(index);
                pathField.setText("");
            }
        }
        
        private void resetToDefault() {
            // 只恢复当前选中的操作系统的敏感目录
            int selected = osTypeCombo.getSelectedIndex();
            String osKey = (selected == 0) ? "linux" : "windows";
            UnifiedPluginConfig.BlacklistConfig config = store.getBlacklist();
            config.resetOsDangerousPaths(osKey);
            store.saveBlacklist(config);
            loadPaths();
            pathField.setText("");
        }
        
        public void save() {
            // 数据已在操作时保存
        }
    }
    
    // ==================== 关键词管理面板 ====================
    
    private static class KeywordPanel {
        private final JPanel panel;
        private final JComboBox<String> osTypeCombo;
        private final JTextArea failArea;
        private final LanguageManager lang = LanguageManager.getInstance();
        private final UnifiedConfigStore store;
        
        public KeywordPanel() {
            store = StoreManager.getInstance().getUnifiedConfigStore();
            panel = new JPanel(new BorderLayout(5, 5));
            
            // 顶部：操作系统选择
            JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            topPanel.add(new JLabel(lang.get("keywords.label.osType") + ":"));
            osTypeCombo = new JComboBox<>(new String[]{lang.get("os.type.linux"), lang.get("os.type.windows")});
            topPanel.add(osTypeCombo);
            
            JButton resetButton = new JButton(lang.get("cmd.btn.reset"));
            resetButton.addActionListener(e -> {
                // 只恢复当前选中的操作系统的关键词
                int selected = osTypeCombo.getSelectedIndex();
                KeywordRules rules = store.getKeywordRules();
                if (selected == 0) {
                    rules.setLinuxCustomFailKeywords(new ArrayList<>());
                } else {
                    rules.setWindowsCustomFailKeywords(new ArrayList<>());
                }
                store.saveKeywordRules(rules);
                loadKeywords();
            });
            topPanel.add(resetButton);
            
            panel.add(topPanel, BorderLayout.NORTH);
            
            // 中间：失败关键词编辑区域（只保留失败关键词，成功关键词已移除）
            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.add(new JLabel(lang.get("keywords.label.fail")), BorderLayout.NORTH);
            failArea = new JTextArea(10, 30);
            contentPanel.add(new JScrollPane(failArea), BorderLayout.CENTER);
            
            panel.add(contentPanel, BorderLayout.CENTER);
            
            // 底部提示
            JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            bottomPanel.add(new JLabel(lang.get("keywords.hint.fail")));
            panel.add(bottomPanel, BorderLayout.SOUTH);
            
            // 加载初始数据
            loadKeywords();
            
            // 操作系统切换时重新加载
            osTypeCombo.addActionListener(e -> loadKeywords());
        }
        
        public JPanel getPanel() {
            return panel;
        }
        
        private void loadKeywords() {
            KeywordRules rules = store.getKeywordRules();
            int selected = osTypeCombo.getSelectedIndex();
            
            List<String> fail;
            if (selected == 0) {
                fail = rules.getEffectiveFailKeywords(OperatingSystem.LINUX);
            } else {
                fail = rules.getEffectiveFailKeywords(OperatingSystem.WINDOWS);
            }
            
            failArea.setText(String.join("\n", fail));
        }
        
        private void saveKeywords() {
            KeywordRules rules = store.getKeywordRules();
            int selected = osTypeCombo.getSelectedIndex();
            List<String> fail = parseKeywords(failArea.getText());
            
            if (selected == 0) {
                rules.setLinuxCustomFailKeywords(fail);
            } else {
                rules.setWindowsCustomFailKeywords(fail);
            }
            store.saveKeywordRules(rules);
        }
        
        private List<String> parseKeywords(String text) {
            List<String> keywords = new ArrayList<>();
            for (String line : text.split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    keywords.add(trimmed);
                }
            }
            return keywords;
        }
        
        public void save() {
            saveKeywords();
        }
    }
    
    // ==================== 命令组管理面板 ====================
    
    private static class CommandGroupPanel {
        private final JPanel panel;
        private final JList<CommandConfig> groupList;
        private final DefaultListModel<CommandConfig> listModel;
        private final JTextField nameField;
        private final JComboBox<ComboBoxItem> serverCombo;
        private final JComboBox<ComboBoxItem> pathCombo;
        private final JComboBox<String> timingCombo;
        private final JTextArea commandsArea;
        private final LanguageManager lang = LanguageManager.getInstance();
        private final UnifiedConfigStore store;
        
        public CommandGroupPanel() {
            store = StoreManager.getInstance().getUnifiedConfigStore();
            panel = new JPanel(new BorderLayout(5, 5));
            
            // 左侧：命令组列表
            JPanel leftPanel = new JPanel(new BorderLayout());
            listModel = new DefaultListModel<>();
            groupList = new JList<>(listModel);
            groupList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            groupList.addListSelectionListener(e -> {
                if (!e.getValueIsAdjusting()) {
                    loadGroupDetails(groupList.getSelectedValue());
                }
            });
            
            JPanel listButtonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton addButton = new JButton(lang.get("cmd.btn.add"));
            JButton removeButton = new JButton(lang.get("cmd.btn.remove"));
            addButton.addActionListener(e -> addGroup());
            removeButton.addActionListener(e -> removeGroup());
            listButtonPanel.add(addButton);
            listButtonPanel.add(removeButton);
            
            leftPanel.add(new JScrollPane(groupList), BorderLayout.CENTER);
            leftPanel.add(listButtonPanel, BorderLayout.SOUTH);
            
            // 右侧：编辑区域
            JPanel rightPanel = new JPanel(new BorderLayout(5, 5));
            
            // 基本信息
            JPanel infoPanel = new JPanel(new GridLayout(4, 2, 5, 5));
            infoPanel.add(new JLabel(lang.get("commandGroup.label.name") + ":"));
            nameField = new JTextField();
            infoPanel.add(nameField);
            
            infoPanel.add(new JLabel(lang.get("commandGroup.label.server") + ":"));
            serverCombo = new JComboBox<>();
            loadServerOptions();
            infoPanel.add(serverCombo);
            
            infoPanel.add(new JLabel(lang.get("commandGroup.label.path") + ":"));
            pathCombo = new JComboBox<>();
            infoPanel.add(pathCombo);
            
            infoPanel.add(new JLabel(lang.get("commandGroup.label.timing") + ":"));
            timingCombo = new JComboBox<>(new String[]{
                lang.get("timing.none"),
                lang.get("timing.auto"),
                lang.get("timing.manual")
            });
            infoPanel.add(timingCombo);
            
            rightPanel.add(infoPanel, BorderLayout.NORTH);
            
            // 命令列表
            JPanel commandsPanel = new JPanel(new BorderLayout());
            commandsPanel.add(new JLabel(lang.get("commandGroup.label.commands") + ":"), BorderLayout.NORTH);
            commandsArea = new JTextArea(8, 40);
            commandsArea.setLineWrap(true);
            commandsPanel.add(new JScrollPane(commandsArea), BorderLayout.CENTER);
            rightPanel.add(commandsPanel, BorderLayout.CENTER);
            
            // 操作按钮
            JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton saveButton = new JButton(lang.get("cmd.btn.save"));
            JButton viewButton = new JButton(lang.get("cmd.btn.viewDetails"));
            saveButton.addActionListener(e -> saveGroup());
            viewButton.addActionListener(e -> viewDetails());
            actionPanel.add(saveButton);
            actionPanel.add(viewButton);
            rightPanel.add(actionPanel, BorderLayout.SOUTH);
            
            // 左右布局
            JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPanel, rightPanel);
            splitPane.setDividerLocation(200);
            panel.add(splitPane, BorderLayout.CENTER);
            
            // 加载初始数据
            loadGroups();
        }
        
        public JPanel getPanel() {
            return panel;
        }
        
        private void loadGroups() {
            listModel.clear();
            List<CommandConfig> groups = store.getAllCommandConfigs();
            for (CommandConfig config : groups) {
                listModel.addElement(config);
            }
        }
        
        private void loadGroupDetails(CommandConfig config) {
            if (config == null) {
                nameField.setText("");
                commandsArea.setText("");
                return;
            }
            
            nameField.setText(config.getName());
            
            // 设置服务器
            String serverId = config.getServerId();
            if (serverId != null && !serverId.isEmpty()) {
                boolean serverFound = false;
                for (int i = 0; i < serverCombo.getItemCount(); i++) {
                    ComboBoxItem item = serverCombo.getItemAt(i);
                    if (item != null && serverId.equals(item.getId())) {
                        serverCombo.setSelectedIndex(i);
                        serverFound = true;
                        break;
                    }
                }
                // 设置路径（只有在服务器匹配成功后才加载路径选项）
                if (serverFound) {
                    loadPathOptions(serverId);
                }
            }
            
            // 设置路径（如果服务器未匹配，仍然尝试加载路径）
            String pathId = config.getPathId();
            if (pathId != null && !pathId.isEmpty()) {
                for (int i = 0; i < pathCombo.getItemCount(); i++) {
                    ComboBoxItem item = pathCombo.getItemAt(i);
                    if (item != null && pathId.equals(item.getId())) {
                        pathCombo.setSelectedIndex(i);
                        break;
                    }
                }
            }
            
            // 设置执行时机
            ExecuteTiming timing = config.getExecuteTiming();
            if (timing == ExecuteTiming.AUTO) {
                timingCombo.setSelectedIndex(1);
            } else if (timing == ExecuteTiming.MANUAL) {
                timingCombo.setSelectedIndex(2);
            } else {
                timingCombo.setSelectedIndex(0);
            }
            
            // 设置命令
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < config.getCommands().size(); i++) {
                if (i > 0) sb.append("\n");
                sb.append(config.getCommands().get(i).getCommand());
            }
            commandsArea.setText(sb.toString());
        }
        
        private void loadServerOptions() {
            serverCombo.removeAllItems();
            serverCombo.addItem(new ComboBoxItem("", "-- " + lang.get("common.select") + " --"));
            List<ServerConfig> servers = store.getAllServers();
            for (var server : servers) {
                // 服务器下拉框显示服务器名称，如果 name 为空则显示 id
                String displayName = (server.getName() != null && !server.getName().isEmpty()) 
                    ? server.getName() 
                    : server.getId();
                serverCombo.addItem(new ComboBoxItem(server.getId(), displayName));
            }
            serverCombo.addActionListener(e -> {
                if (serverCombo.getSelectedIndex() > 0) {
                    ComboBoxItem selected = (ComboBoxItem) serverCombo.getSelectedItem();
                    if (selected != null) {
                        loadPathOptions(selected.getId());
                    }
                }
            });
        }
        
        private void loadPathOptions(String serverId) {
            pathCombo.removeAllItems();
            pathCombo.addItem(new ComboBoxItem("", "-- " + lang.get("common.select") + " --"));
            List<PathConfig> paths = store.getPathsByServer(serverId);
            for (var path : paths) {
                // 路径下拉框显示实际路径（如 /home/temp），如果 remotePath 为空则显示 id
                String displayPath = (path.getRemotePath() != null && !path.getRemotePath().isEmpty()) 
                    ? path.getRemotePath() 
                    : path.getId();
                pathCombo.addItem(new ComboBoxItem(path.getId(), displayPath));
            }
        }
        
        private void addGroup() {
            CommandConfig newConfig = new CommandConfig();
            newConfig.setName(lang.get("commandGroup.defaultName") + " " + (listModel.size() + 1));
            store.saveCommandConfig(newConfig);
            loadGroups();
            
            // 选中新添加的项
            for (int i = 0; i < listModel.size(); i++) {
                if (listModel.get(i).getId().equals(newConfig.getId())) {
                    groupList.setSelectedIndex(i);
                    break;
                }
            }
        }
        
        private void removeGroup() {
            CommandConfig selected = groupList.getSelectedValue();
            if (selected != null) {
                store.deleteCommandConfig(selected.getId());
                loadGroups();
                nameField.setText("");
                commandsArea.setText("");
            }
        }
        
        private void saveGroup() {
            CommandConfig selected = groupList.getSelectedValue();
            if (selected == null) {
                JOptionPane.showMessageDialog(panel, 
                    lang.get("commandGroup.message.selectFirst"),
                    lang.get("common.warning"),
                    JOptionPane.WARNING_MESSAGE);
                return;
            }
            
            selected.setName(nameField.getText());
            
            // 解析服务器和路径 ID
            if (serverCombo.getSelectedIndex() > 0) {
                ComboBoxItem selectedItem = (ComboBoxItem) serverCombo.getSelectedItem();
                if (selectedItem != null) {
                    selected.setServerId(selectedItem.getId());
                }
            } else {
                selected.setServerId(null);
            }
            
            if (pathCombo.getSelectedIndex() > 0) {
                ComboBoxItem selectedItem = (ComboBoxItem) pathCombo.getSelectedItem();
                if (selectedItem != null) {
                    selected.setPathId(selectedItem.getId());
                }
            } else {
                selected.setPathId(null);
            }
            
            // 执行时机
            int timingIndex = timingCombo.getSelectedIndex();
            if (timingIndex == 1) {
                selected.setExecuteTiming(ExecuteTiming.AUTO);
            } else if (timingIndex == 2) {
                selected.setExecuteTiming(ExecuteTiming.MANUAL);
            } else {
                selected.setExecuteTiming(ExecuteTiming.NONE);
            }
            
            // 解析命令
            List<String> lines = new ArrayList<>();
            for (String line : commandsArea.getText().split("\n")) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) {
                    lines.add(trimmed);
                }
            }
            selected.setCommandsFromStrings(lines);
            
            store.saveCommandConfig(selected);
            loadGroups();
            
            JOptionPane.showMessageDialog(panel, 
                lang.get("commandGroup.message.saved"),
                lang.get("common.success"),
                JOptionPane.INFORMATION_MESSAGE);
        }
        
        private void viewDetails() {
            CommandConfig selected = groupList.getSelectedValue();
            if (selected != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("=== ").append(selected.getName()).append(" ===\n\n");
                sb.append(lang.get("commandGroup.details.server")).append(selected.getServerId()).append("\n");
                sb.append(lang.get("commandGroup.details.path")).append(selected.getPathId()).append("\n");
                sb.append(lang.get("commandGroup.details.timing")).append(selected.getExecuteTiming()).append("\n\n");
                sb.append(lang.get("commandGroup.details.commands")).append("\n");
                for (CommandItem item : selected.getCommands()) {
                    sb.append("- ").append(item.getCommand());
                    if (!item.isEnabled()) {
                        sb.append(" (").append(lang.get("commandGroup.details.disabled")).append(")");
                    }
                    sb.append("\n");
                }
                JTextArea textArea = new JTextArea(sb.toString());
                textArea.setEditable(false);
                JScrollPane scrollPane = new JScrollPane(textArea);
                scrollPane.setPreferredSize(new Dimension(400, 300));
                JOptionPane.showMessageDialog(panel, scrollPane,
                    lang.get("commandGroup.details.title"),
                    JOptionPane.INFORMATION_MESSAGE);
            }
        }
        
        public void save() {
            // 数据已在操作时保存
        }
    }
}
