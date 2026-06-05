package com.openxt.uploadsshfile.ui;

import com.openxt.uploadsshfile.batch.BatchSubTaskResult;
import com.openxt.uploadsshfile.batch.BatchTaskManager;
import com.openxt.uploadsshfile.i18n.LanguageManager;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 批处理执行结果汇总对话框。
 * 显示成功/失败统计、失败详情、复制失败列表、从失败创建新任务。
 */
public class BatchResultDialog extends JDialog {

    private final LanguageManager lang;
    private final BatchTaskManager taskManager;
    private final List<BatchSubTaskResult> results;

    public BatchResultDialog(JDialog parent, List<BatchSubTaskResult> results) {
        super(parent, true);
        this.lang = LanguageManager.getInstance();
        this.taskManager = BatchTaskManager.getInstance();
        this.results = results;

        setTitle(lang.get("batch.task.title"));
        initUI();
        pack();
        setLocationRelativeTo(parent);
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        long successCount = results.stream().filter(BatchSubTaskResult::isSuccess).count();
        long failCount = results.size() - successCount;

        // 统计面板
        JPanel statsPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        statsPanel.setBorder(BorderFactory.createTitledBorder(lang.get("execution.title")));
        statsPanel.add(new JLabel(lang.get("batch.task.total") + ": " + results.size()));
        statsPanel.add(new JLabel(lang.get("batch.task.success") + ": " + successCount));
        statsPanel.add(new JLabel(lang.get("batch.task.failed") + ": " + failCount));

        mainPanel.add(statsPanel, BorderLayout.NORTH);

        // 失败列表
        if (failCount > 0) {
            JPanel failPanel = new JPanel(new BorderLayout(5, 5));
            failPanel.setBorder(BorderFactory.createTitledBorder(lang.get("batch.task.failed")));

            DefaultListModel<String> failModel = new DefaultListModel<>();
            List<BatchSubTaskResult> failures = results.stream()
                    .filter(r -> !r.isSuccess())
                    .collect(Collectors.toList());

            for (int i = 0; i < failures.size(); i++) {
                BatchSubTaskResult r = failures.get(i);
                failModel.addElement((i + 1) + ". " + r.getTaskDescription());
                if (r.getErrorMessage() != null) {
                    failModel.addElement("   " + lang.get("progress.failed") + " " + r.getErrorMessage());
                }
            }

            JList<String> failList = new JList<>(failModel);
            JScrollPane failScroll = new JScrollPane(failList);
            failScroll.setPreferredSize(new Dimension(450, 150));
            failPanel.add(failScroll, BorderLayout.CENTER);

            // 失败操作按钮
            JPanel failBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));

            JButton copyBtn = new JButton(lang.get("batch.task.copyFailed"));
            copyBtn.addActionListener(e -> {
                StringBuilder sb = new StringBuilder();
                sb.append("=== ").append(lang.get("batch.task.failed")).append(" ===\n");
                sb.append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
                for (int i = 0; i < failures.size(); i++) {
                    BatchSubTaskResult r = failures.get(i);
                    sb.append(i + 1).append(". ").append(r.getTaskDescription()).append("\n");
                    if (r.getErrorMessage() != null) {
                        sb.append("   ").append(r.getErrorMessage()).append("\n");
                    }
                    sb.append("\n");
                }
                Toolkit.getDefaultToolkit().getSystemClipboard()
                        .setContents(new StringSelection(sb.toString()), null);
                JOptionPane.showMessageDialog(this, lang.get("progress.copy.success"), lang.get("progress.copy.title"), JOptionPane.INFORMATION_MESSAGE);
            });

            JButton createFromFailuresBtn = new JButton(lang.get("batch.task.createFromFailures.yes"));
            createFromFailuresBtn.addActionListener(e -> {
                String newName = JOptionPane.showInputDialog(this, lang.get("batch.task.copy.prompt"),
                        lang.get("batch.task.createFromFailures"), JOptionPane.QUESTION_MESSAGE);
                if (newName != null && !newName.trim().isEmpty()) {
                    taskManager.createFromFailures(failures, newName.trim());
                    JOptionPane.showMessageDialog(this, lang.get("msg.save.success"), lang.get("common.success"), JOptionPane.INFORMATION_MESSAGE);
                }
            });

            failBtnPanel.add(copyBtn);
            failBtnPanel.add(createFromFailuresBtn);
            failPanel.add(failBtnPanel, BorderLayout.SOUTH);

            mainPanel.add(failPanel, BorderLayout.CENTER);
        }

        // 关闭按钮
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton closeBtn = new JButton(lang.get("dialog.close"));
        closeBtn.addActionListener(e -> dispose());
        btnPanel.add(closeBtn);
        mainPanel.add(btnPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }
}
