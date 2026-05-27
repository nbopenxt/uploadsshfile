package com.openxt.uploadsshfile.ui;

import com.openxt.uploadsshfile.ai.AiModelFactory;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.AIConfig;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import com.openxt.uploadsshfile.store.StoreManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AI 配置面板
 * 支持 8 个主流大模型配置（通过下拉框选择）
 * 
 * 使用统一配置存储 plugin-config.json
 * 
 * 布局说明：
 * - 框线外：全局配置（启用AI校验、超时时间、全局校验选项）
 * - 框线内：具体模型配置（模型选择、API Key、Base URL、模型名称）
 */
public class AIConfigPanel extends JPanel {

    private final UnifiedConfigStore configStore;
    private final AtomicReference<Object> modelRef;

    // UI 组件
    private JComboBox<String> modelTypeCombo;
    private JPasswordField apiKeyField;
    private JTextField baseUrlField;
    private JTextField modelNameField;
    private JSpinner timeoutSpinner;
    private JCheckBox enabledCheckBox;
    private JCheckBox preCheckCheckBox;
    private JCheckBox postCheckCheckBox;
    private JCheckBox aiBlacklistCheckBox;
    private JLabel statusLabel;

    public AIConfigPanel(UnifiedConfigStore configStore) {
        this.configStore = configStore;
        this.modelRef = new AtomicReference<>();
        initComponents();
        loadConfig();
    }
    
    /**
     * 默认构造函数，使用全局配置存储
     */
    public AIConfigPanel() {
        this(StoreManager.getInstance().getUnifiedConfigStore());
    }

    private void initComponents() {
        LanguageManager lm = LanguageManager.getInstance();
        setLayout(new BorderLayout(10, 10));
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 主面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));

        // ========== 第一部分：全局配置（框线外）==========
        JPanel globalPanel = new JPanel();
        globalPanel.setLayout(new BoxLayout(globalPanel, BoxLayout.Y_AXIS));
        // globalPanel无边框，只保留左边距5px与modelPanel内容对齐
        globalPanel.setBorder(BorderFactory.createEmptyBorder(0, 5, 5, 5));

        // 第一行：启用AI校验
        JPanel row1 = new JPanel();
        row1.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));
        enabledCheckBox = new JCheckBox(lm.get("ai.config.enabled"));
        row1.add(enabledCheckBox);
        row1.setAlignmentX(Component.LEFT_ALIGNMENT);
        globalPanel.add(row1);
        globalPanel.add(Box.createVerticalStrut(10));

        // 第二行：超时时间
        JPanel row2 = new JPanel();
        row2.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JLabel timeoutLabel = new JLabel(lm.get("ai.config.timeout") + " " + lm.get("ai.config.timeout.unit") + ":");
        timeoutLabel.setPreferredSize(new Dimension(100, 25)); // 与createFormRow标签同宽
        timeoutSpinner = new JSpinner(new SpinnerNumberModel(30, 10, 300, 5));
        row2.add(timeoutLabel);
        row2.add(timeoutSpinner);
        row2.setAlignmentX(Component.LEFT_ALIGNMENT);
        globalPanel.add(row2);
        globalPanel.add(Box.createVerticalStrut(10));

        // 第三行：全局校验选项
        JPanel row3 = new JPanel();
        row3.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));
        preCheckCheckBox = new JCheckBox(lm.get("ai.config.preCheck"));
        postCheckCheckBox = new JCheckBox(lm.get("ai.config.postCheck"));
        aiBlacklistCheckBox = new JCheckBox(lm.get("ai.config.aiBlacklistCheck"));
        row3.add(preCheckCheckBox);
        row3.add(postCheckCheckBox);
        row3.add(Box.createHorizontalStrut(10));
        row3.add(aiBlacklistCheckBox);
        row3.setAlignmentX(Component.LEFT_ALIGNMENT);
        globalPanel.add(row3);

        // BoxLayout Y_AXIS 中需要设置 alignmentX 让组件左对齐
        globalPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        globalPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        mainPanel.add(globalPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // ========== 第二部分：具体模型配置（框线内）==========
        JPanel modelPanel = new JPanel();
        modelPanel.setLayout(new BoxLayout(modelPanel, BoxLayout.Y_AXIS));
        // 使用TitledBorder让"模型配置"显示在边框左上角
        Border lineBorder = BorderFactory.createLineBorder(Color.GRAY);
        Border emptyBorder = BorderFactory.createEmptyBorder(0, 5, 5, 5);
        Border titledBorder = BorderFactory.createTitledBorder(
            BorderFactory.createCompoundBorder(lineBorder, emptyBorder),
            lm.get("ai.config.modelConfig")
        );
        modelPanel.setBorder(titledBorder);

        modelPanel.add(Box.createVerticalStrut(10)); // 标题与内容间距

        // 模型类型下拉框
        modelTypeCombo = new JComboBox<>();
        modelTypeCombo.addItem("OpenAI");
        modelTypeCombo.addItem("Alibaba Qwen (Qwen)");
        modelTypeCombo.addItem("DeepSeek (DeepSeek)");
        modelTypeCombo.addItem("ByteDance Doubao (Doubao)");
        modelTypeCombo.addItem("Moonshot AI (Moonshot)");
        modelTypeCombo.addItem("Google Gemini");
        modelTypeCombo.addItem("Anthropic Claude");
        modelTypeCombo.addItem("Local Ollama");
        modelTypeCombo.addActionListener(e -> onModelTypeChanged());

        JPanel modelTypePanel = createFormRow(lm.get("ai.config.modelType") + ":", modelTypeCombo);
        modelPanel.add(modelTypePanel);

        // API Key
        apiKeyField = new JPasswordField(30);
        JPanel apiKeyPanel = createFormRow(lm.get("ai.config.apiKey") + ":", apiKeyField);
        modelPanel.add(apiKeyPanel);

        // Base URL
        baseUrlField = new JTextField(30);
        JPanel baseUrlPanel = createFormRow(lm.get("ai.config.baseUrl") + ":", baseUrlField);
        modelPanel.add(baseUrlPanel);

        // 模型名称
        modelNameField = new JTextField(30);
        JPanel modelNamePanel = createFormRow(lm.get("ai.config.modelName") + ":", modelNameField);
        modelPanel.add(modelNamePanel);

        // 模型配置按钮
        JPanel modelButtonPanel = new JPanel();
        modelButtonPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 5));
        
        JButton saveModelButton = new JButton(lm.get("ai.config.saveModel"));
        JButton testButton = new JButton(lm.get("ai.config.test"));

        saveModelButton.addActionListener(e -> saveCurrentModelConfig());
        testButton.addActionListener(e -> testConnection());

        modelButtonPanel.add(saveModelButton);
        modelButtonPanel.add(testButton);
        modelPanel.add(modelButtonPanel);

        // BoxLayout Y_AXIS 中需要设置 alignmentX 让组件左对齐
        modelPanel.setAlignmentX(Component.LEFT_ALIGNMENT);
        modelPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        mainPanel.add(modelPanel);

        // ========== 第三部分：底部状态（框线外）==========
        JPanel bottomPanel = new JPanel();
        bottomPanel.setLayout(new BorderLayout(5, 5));

        // 状态标签
        statusLabel = new JLabel();
        statusLabel.setForeground(Color.GRAY);
        bottomPanel.add(statusLabel, BorderLayout.WEST);

        add(mainPanel, BorderLayout.NORTH);
        add(bottomPanel, BorderLayout.SOUTH);
    }

    /**
     * 创建表单行（标签 + 输入组件）
     */
    private JPanel createFormRow(String label, JComponent field) {
        JPanel panel = new JPanel();
        panel.setLayout(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JLabel jLabel = new JLabel(label);
        jLabel.setPreferredSize(new Dimension(100, 25));
        panel.add(jLabel);
        panel.add(field);
        return panel;
    }

    /**
     * 加载配置
     */
    public void loadConfig() {
        AIConfig config = configStore.getConfig().getAiConfig();

        enabledCheckBox.setSelected(config.isEnabled());
        preCheckCheckBox.setSelected(config.isEnablePreExecutionCheck());
        postCheckCheckBox.setSelected(config.isEnablePostResultCheck());
        aiBlacklistCheckBox.setSelected(config.isEnableAiBlacklistCheck());
        timeoutSpinner.setValue((int) (config.getTimeoutMs() / 1000));

        // 设置模型类型
        String modelType = config.getModelType();
        selectModelType(modelType);
        
        // 加载当前模型的已保存配置
        loadCurrentModelConfig(config);

        updateStatus();
    }

    /**
     * 保存当前模型配置（仅保存模型相关的配置）
     */
    public void saveCurrentModelConfig() {
        UnifiedPluginConfig fullConfig = configStore.getConfig();
        AIConfig config = fullConfig.getAiConfig();

        // 保存模型相关配置
        config.setModelType(getSelectedModelType());
        config.setApiKey(new String(apiKeyField.getPassword()));
        config.setBaseUrl(baseUrlField.getText().trim());
        config.setModelName(modelNameField.getText().trim());

        configStore.save(fullConfig);

        // 清除模型缓存，强制重新创建
        modelRef.set(null);

        LanguageManager lm = LanguageManager.getInstance();
        JOptionPane.showMessageDialog(this, lm.get("ai.config.saveModel.success"), 
                lm.get("msg.success.title"), JOptionPane.INFORMATION_MESSAGE);

        updateStatus();
    }

    /**
     * 保存所有配置（包括全局配置和当前模型配置）
     */
    public void saveAllConfig() {
        UnifiedPluginConfig fullConfig = configStore.getConfig();
        AIConfig config = fullConfig.getAiConfig();

        // 保存全局配置
        config.setEnabled(enabledCheckBox.isSelected());
        config.setEnablePreExecutionCheck(preCheckCheckBox.isSelected());
        config.setEnablePostResultCheck(postCheckCheckBox.isSelected());
        config.setEnableAiBlacklistCheck(aiBlacklistCheckBox.isSelected());
        config.setTimeoutMs(((Integer) timeoutSpinner.getValue()) * 1000L);

        // 保存当前模型配置
        config.setModelType(getSelectedModelType());
        config.setApiKey(new String(apiKeyField.getPassword()));
        config.setBaseUrl(baseUrlField.getText().trim());
        config.setModelName(modelNameField.getText().trim());

        configStore.save(fullConfig);

        // 清除模型缓存
        modelRef.set(null);

        LanguageManager lm = LanguageManager.getInstance();
        JOptionPane.showMessageDialog(this, lm.get("ai.config.save.success"), 
                lm.get("msg.success.title"), JOptionPane.INFORMATION_MESSAGE);

        updateStatus();
    }

    /**
     * 测试连接
     */
    public void testConnection() {
        // 自动保存当前模型配置（但不保存全局配置）
        UnifiedPluginConfig fullConfig = configStore.getConfig();
        AIConfig config = fullConfig.getAiConfig();

        config.setModelType(getSelectedModelType());
        config.setApiKey(new String(apiKeyField.getPassword()));
        config.setBaseUrl(baseUrlField.getText().trim());
        config.setModelName(modelNameField.getText().trim());

        configStore.save(fullConfig);

        if (!config.isConfigured()) {
            LanguageManager lm = LanguageManager.getInstance();
            JOptionPane.showMessageDialog(this, lm.get("ai.config.test.notConfigured"),
                    lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 在后台线程测试
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() {
                try {
                    Object model = AiModelFactory.createModel(config);
                    java.lang.reflect.Method method = model.getClass().getMethod("chat", String.class);
                    Object result = method.invoke(model, "Say 'OK' if you can understand me.");
                    if (result instanceof String) {
                        return (String) result;
                    } else {
                        java.lang.reflect.Method textMethod = result.getClass().getMethod("text");
                        return (String) textMethod.invoke(result);
                    }
                } catch (Exception e) {
                    String errorMsg = e.getMessage();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        Throwable cause = e.getCause();
                        if (cause != null) {
                            errorMsg = cause.getMessage();
                        }
                        if (errorMsg == null || errorMsg.isEmpty()) {
                            errorMsg = e.getClass().getSimpleName();
                        }
                    }
                    return "Error: " + errorMsg;
                }
            }

            @Override
            protected void done() {
                try {
                    String result = get();
                    LanguageManager lm = LanguageManager.getInstance();
                    if (result.startsWith("Error:")) {
                        JOptionPane.showMessageDialog(AIConfigPanel.this, 
                                lm.get("ai.config.test.failed") + "\n" + result,
                                lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(AIConfigPanel.this, 
                                lm.get("ai.config.test.success"),
                                lm.get("msg.success.title"), JOptionPane.INFORMATION_MESSAGE);
                    }
                } catch (Exception e) {
                    LanguageManager lm = LanguageManager.getInstance();
                    String errorMsg = e.getMessage();
                    if (errorMsg == null || errorMsg.isEmpty()) {
                        errorMsg = e.getClass().getSimpleName();
                    }
                    JOptionPane.showMessageDialog(AIConfigPanel.this, 
                            lm.get("ai.config.test.failed") + "\n" + errorMsg,
                            lm.get("error.title"), JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * 加载当前模型的默认值
     */
    public void loadDefaults() {
        String modelType = getSelectedModelType();
        AIConfig.ModelDefaults defaults = AIConfig.getModelDefaults(modelType);

        baseUrlField.setText(defaults.baseUrl() != null ? defaults.baseUrl() : "");
        modelNameField.setText(defaults.modelName());
    }

    /**
     * 模型类型改变事件
     */
    private void onModelTypeChanged() {
        AIConfig config = configStore.getConfig().getAiConfig();
        
        // 切换到新模型
        config.setModelType(getSelectedModelType());
        
        // 加载新模型的已保存配置
        loadCurrentModelConfig(config);
        
        updateStatus();
    }
    
    /**
     * 加载当前模型的配置到 UI 字段
     */
    private void loadCurrentModelConfig(AIConfig config) {
        String savedApiKey = config.getApiKey();
        String savedBaseUrl = config.getBaseUrl();
        String savedModelName = config.getModelName();
        
        AIConfig.ModelDefaults defaults = AIConfig.getModelDefaults(config.getModelType());
        
        apiKeyField.setText(savedApiKey != null ? savedApiKey : "");
        
        baseUrlField.setText(savedBaseUrl != null && !savedBaseUrl.isEmpty() ? savedBaseUrl : 
            (defaults.baseUrl() != null ? defaults.baseUrl() : ""));
        modelNameField.setText(savedModelName != null && !savedModelName.isEmpty() ? savedModelName : defaults.modelName());
    }

    /**
     * 更新状态标签
     */
    private void updateStatus() {
        LanguageManager lm = LanguageManager.getInstance();
        String modelType = getSelectedModelType();
        AIConfig.ModelDefaults defaults = AIConfig.getModelDefaults(modelType);

        StringBuilder status = new StringBuilder();
        status.append(lm.get("ai.config.model")).append(defaults.displayName());

        if ("OLLAMA".equals(modelType)) {
            status.append(" - ").append(lm.get("ai.config.ollama.hint"));
        } else if ("GEMINI".equals(modelType)) {
            status.append(" - ").append(lm.get("ai.config.gemini.hint"));
        }

        statusLabel.setText(status.toString());
    }

    private LanguageManager lm() {
        return LanguageManager.getInstance();
    }

    /**
     * 根据模型类型代码选择下拉框项（不触发事件）
     */
    private void selectModelType(String modelType) {
        String displayName = switch (modelType) {
            case "QWEN" -> "Alibaba Qwen (Qwen)";
            case "DEEPSEEK" -> "DeepSeek (DeepSeek)";
            case "DOUBAO" -> "ByteDance Doubao (Doubao)";
            case "MOONSHOT" -> "Moonshot AI (Moonshot)";
            case "GEMINI" -> "Google Gemini";
            case "CLAUDE" -> "Anthropic Claude";
            case "OLLAMA" -> "Local Ollama";
            default -> "OpenAI";
        };

        ActionListener[] listeners = modelTypeCombo.getActionListeners();
        for (ActionListener l : listeners) {
            modelTypeCombo.removeActionListener(l);
        }

        for (int i = 0; i < modelTypeCombo.getItemCount(); i++) {
            if (modelTypeCombo.getItemAt(i).equals(displayName)) {
                modelTypeCombo.setSelectedIndex(i);
                break;
            }
        }

        for (ActionListener l : listeners) {
            modelTypeCombo.addActionListener(l);
        }
    }

    /**
     * 获取选中的模型类型代码
     */
    private String getSelectedModelType() {
        String selected = (String) modelTypeCombo.getSelectedItem();
        return switch (selected) {
            case "Alibaba Qwen (Qwen)" -> "QWEN";
            case "DeepSeek (DeepSeek)" -> "DEEPSEEK";
            case "ByteDance Doubao (Doubao)" -> "DOUBAO";
            case "Moonshot AI (Moonshot)" -> "MOONSHOT";
            case "Google Gemini" -> "GEMINI";
            case "Anthropic Claude" -> "CLAUDE";
            case "Local Ollama" -> "OLLAMA";
            default -> "OPENAI";
        };
    }
}
