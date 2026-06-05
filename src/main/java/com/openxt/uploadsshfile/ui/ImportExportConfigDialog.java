package com.openxt.uploadsshfile.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.openxt.uploadsshfile.importexport.ConfigExporter;
import com.openxt.uploadsshfile.importexport.ConfigImporter;
import com.openxt.uploadsshfile.importexport.ImportResult;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 导入导出配置对话框（Tab 分页）。
 * 导出 Tab：当前配置摘要 + 导出按钮
 * 导入 Tab：文件选择 + 差异预览 + 确认导入
 */
public class ImportExportConfigDialog extends JDialog {

    private final Project project;
    private final LanguageManager lang;
    private final UnifiedConfigStore configStore;
    private final ConfigExporter exporter;
    private final ConfigImporter importer;

    private JTabbedPane tabbedPane;
    private JTextField importFilePathField;
    private JButton browseBtn, importBtn;
    private DefaultTableModel previewTableModel;
    private JTable previewTable;
    private JLabel versionLabel, importVersionLabel;
    private JLabel exportHintLabel;
    private ImportResult previewResult;

    public ImportExportConfigDialog(Project project) {
        super((Frame) null, true);
        this.project = project;
        this.lang = LanguageManager.getInstance();
        this.configStore = UnifiedConfigStore.getInstance();
        this.exporter = new ConfigExporter();
        this.importer = new ConfigImporter();

        setTitle(lang.get("menu.import.export"));
        initUI();
        pack();
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(650, 500));
    }

    private void initUI() {
        tabbedPane = new JTabbedPane();

        // 导出 Tab
        tabbedPane.addTab(lang.get("config.import.export.tab.export"), createExportPanel());

        // 导入 Tab
        tabbedPane.addTab(lang.get("config.import.export.tab.import"), createImportPanel());

        setContentPane(tabbedPane);
    }

    // ==================== 导出 Tab ====================

    private JPanel createExportPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 配置摘要
        JPanel summaryPanel = new JPanel(new GridLayout(0, 1, 3, 3));
        summaryPanel.setBorder(BorderFactory.createTitledBorder(lang.get("config.import.export.summary")));

        UnifiedPluginConfig config = configStore.getConfig();
        int serverCount = config.getServers() != null ? config.getServers().size() : 0;
        int pathCount = config.getPaths() != null ? config.getPaths().size() : 0;
        int cmdCount = config.getCommandConfigs() != null ? config.getCommandConfigs().size() : 0;
        int batchCount = config.getBatchTasks() != null ? config.getBatchTasks().size() : 0;
        int blCount = config.getBlacklist() != null && config.getBlacklist().getOsSpecific() != null ?
                config.getBlacklist().getOsSpecific().values().stream().mapToInt(java.util.List::size).sum() : 0;
        int kwCount = config.getKeywordRules() != null && config.getKeywordRules().getFailKeywords() != null ?
                config.getKeywordRules().getFailKeywords().size() : 0;
        String aiModel = config.getAiConfig() != null ? config.getAiConfig().getModelType() : "N/A";

        summaryPanel.add(new JLabel(lang.get("config.tab.server") + ": " + serverCount));
        summaryPanel.add(new JLabel(lang.get("upload.path") + ": " + pathCount));
        summaryPanel.add(new JLabel(lang.get("cmd.tab.commandGroups") + ": " + cmdCount));
        summaryPanel.add(new JLabel(lang.get("batch.task.title") + ": " + batchCount));
        summaryPanel.add(new JLabel(lang.get("ai.config.title") + ": " + aiModel));
        summaryPanel.add(new JLabel(lang.get("blacklist.title") + ": " + blCount));
        summaryPanel.add(new JLabel(lang.get("keywords.title") + ": " + kwCount));

        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(summaryPanel, BorderLayout.CENTER);
        exportHintLabel = new JLabel("  " + lang.get("config.export.hint"));
        northPanel.add(exportHintLabel, BorderLayout.SOUTH);

        // 导出按钮
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        JButton exportBtn = new JButton(lang.get("config.export"));
        btnPanel.add(exportBtn);

        panel.add(northPanel, BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);

        exportBtn.addActionListener(e -> {
            String defaultName = "usf_config_" +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".json";
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle(lang.get("config.export"));
            chooser.setFileFilter(new FileNameExtensionFilter("JSON (*.json)", "json"));
            chooser.setSelectedFile(new File(defaultName));
            int result = chooser.showSaveDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (!file.getName().toLowerCase().endsWith(".json")) {
                    file = new File(file.getAbsolutePath() + ".json");
                }
                try {
                    exporter.export(file);
                    exportHintLabel.setText("  " + lang.get("config.export.success"));
                    Messages.showInfoMessage(this, lang.get("config.export.success"), lang.get("common.success"));
                } catch (IOException ex) {
                    Messages.showErrorDialog(this, ex.getMessage(), lang.get("error.title"));
                }
            }
        });

        return panel;
    }

    // ==================== 导入 Tab ====================

    private JPanel createImportPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 文件选择
        JPanel filePanel = new JPanel(new BorderLayout(5, 5));
        importFilePathField = new JTextField(35);
        importFilePathField.setEditable(false);
        browseBtn = new JButton(lang.get("config.import.export.browse"));
        filePanel.add(importFilePathField, BorderLayout.CENTER);
        filePanel.add(browseBtn, BorderLayout.EAST);

        importVersionLabel = new JLabel(" ");
        filePanel.add(importVersionLabel, BorderLayout.SOUTH);

        // 差异预览
        JPanel previewPanel = new JPanel(new BorderLayout(5, 5));
        previewPanel.setBorder(BorderFactory.createTitledBorder(lang.get("config.import.export.preview")));

        String[] columns = {lang.get("config.tab.server"), lang.get("config.import.added"),
                lang.get("config.import.updated"), lang.get("config.import.skipped")};
        previewTableModel = new DefaultTableModel(columns, 0);
        previewTable = new JTable(previewTableModel);
        JScrollPane previewScroll = new JScrollPane(previewTable);
        previewScroll.setPreferredSize(new Dimension(550, 150));
        previewPanel.add(previewScroll, BorderLayout.CENTER);

        // 按钮
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        importBtn = new JButton(lang.get("config.import.export.confirmImport"));
        importBtn.setEnabled(false);
        JButton cancelBtn = new JButton(lang.get("execution.btn.cancel"));
        btnPanel.add(importBtn);
        btnPanel.add(cancelBtn);

        panel.add(filePanel, BorderLayout.NORTH);
        panel.add(previewPanel, BorderLayout.CENTER);
        panel.add(btnPanel, BorderLayout.SOUTH);

        browseBtn.addActionListener(e -> onBrowseImportFile());
        importBtn.addActionListener(e -> onImport());
        cancelBtn.addActionListener(e -> dispose());

        return panel;
    }

    private void onBrowseImportFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(lang.get("config.import"));
        chooser.setFileFilter(new FileNameExtensionFilter("JSON (*.json)", "json"));
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            importFilePathField.setText(file.getAbsolutePath());
            try {
                String json = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                String validateResult = importer.validate(json);
                if (validateResult != null) {
                    importVersionLabel.setText("❌ " + validateResult);
                    importBtn.setEnabled(false);
                    return;
                }
                importVersionLabel.setText("✅ " + lang.get("config.import.success"));

                // 解析预览
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
                com.openxt.uploadsshfile.importexport.ExportPayload payload =
                        gson.fromJson(json, com.openxt.uploadsshfile.importexport.ExportPayload.class);
                updatePreview(payload);
                importBtn.setEnabled(true);
            } catch (IOException ex) {
                importVersionLabel.setText("❌ " + ex.getMessage());
                importBtn.setEnabled(false);
            }
        }
    }

    private void updatePreview(com.openxt.uploadsshfile.importexport.ExportPayload payload) {
        previewTableModel.setRowCount(0);
        if (payload == null) return;

        addPreviewRow(lang.get("config.tab.server"),
                payload.getServers() != null ? payload.getServers().size() : 0, 0, 0);
        addPreviewRow(lang.get("cmd.tab.commandGroups"),
                payload.getPaths() != null ? payload.getPaths().size() : 0, 0, 0);
        addPreviewRow(lang.get("cmd.tab.commandGroups"),
                payload.getCommandConfigs() != null ? payload.getCommandConfigs().size() : 0, 0, 0);
        addPreviewRow(lang.get("batch.task.title"),
                payload.getBatchTasks() != null ? payload.getBatchTasks().size() : 0, 0, 0);

        int aiCount = payload.getAiConfig() != null && payload.getAiConfig().getModelConfigs() != null ?
                payload.getAiConfig().getModelConfigs().size() : 0;
        addPreviewRow(lang.get("ai.config.title"), 0, 0, aiCount > 0 ? 1 : 0);
        addPreviewRow(lang.get("blacklist.title"), 0, 0,
                payload.getBlacklist() != null ? 1 : 0);
        addPreviewRow(lang.get("keywords.title"), 0, 0,
                payload.getKeywordRules() != null ? 1 : 0);
        addPreviewRow("HasOutput", 0, 0,
                payload.getHasOutputCommands() != null ? 1 : 0);
    }

    private void addPreviewRow(String name, int added, int updated, int skipped) {
        previewTableModel.addRow(new Object[]{name, added, updated, skipped});
    }

    private void onImport() {
        String filePath = importFilePathField.getText();
        if (filePath.isEmpty()) return;

        int confirm = Messages.showYesNoDialog(this,
                lang.get("msg.error.server.delete.confirm"), lang.get("confirm.title"), Messages.getQuestionIcon());
        if (confirm != Messages.YES) return;

        try {
            ImportResult result = importer.import_(new File(filePath));
            showImportSummary(result);
            Messages.showInfoMessage(this, lang.get("config.import.relaunchHint"), lang.get("common.success"));
        } catch (IOException ex) {
            Messages.showErrorDialog(this, ex.getMessage(), lang.get("error.title"));
        }
    }

    private void showImportSummary(ImportResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(lang.get("config.import.summary")).append(":\n\n");
        sb.append(lang.get("config.tab.server")).append(": ")
                .append(lang.get("config.import.added")).append(" ").append(result.getServersAdded()).append(" / ")
                .append(lang.get("config.import.updated")).append(" ").append(result.getServersUpdated()).append(" / ")
                .append(lang.get("config.import.skipped")).append(" ").append(result.getServersSkipped()).append("\n");
        sb.append(lang.get("upload.path")).append(": ")
                .append(lang.get("config.import.added")).append(" ").append(result.getPathsAdded()).append(" / ")
                .append(lang.get("config.import.updated")).append(" ").append(result.getPathsUpdated()).append(" / ")
                .append(lang.get("config.import.skipped")).append(" ").append(result.getPathsSkipped()).append("\n");
        sb.append(lang.get("cmd.tab.commandGroups")).append(": ")
                .append(lang.get("config.import.added")).append(" ").append(result.getCommandConfigsAdded()).append(" / ")
                .append(lang.get("config.import.updated")).append(" ").append(result.getCommandConfigsUpdated()).append(" / ")
                .append(lang.get("config.import.skipped")).append(" ").append(result.getCommandConfigsSkipped()).append("\n");
        sb.append(lang.get("batch.task.title")).append(": ")
                .append(lang.get("config.import.added")).append(" ").append(result.getBatchTasksAdded()).append(" / ")
                .append(lang.get("config.import.updated")).append(" ").append(result.getBatchTasksUpdated()).append(" / ")
                .append(lang.get("config.import.skipped")).append(" ").append(result.getBatchTasksSkipped()).append("\n");

        JOptionPane.showMessageDialog(this, sb.toString(), lang.get("config.import.summary"), JOptionPane.INFORMATION_MESSAGE);
    }

    public void showDialog() {
        setVisible(true);
    }
}
