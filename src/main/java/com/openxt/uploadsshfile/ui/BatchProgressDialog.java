package com.openxt.uploadsshfile.ui;

import com.openxt.uploadsshfile.batch.*;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.util.Logger;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.text.DecimalFormat;
import java.util.List;

/**
 * 批处理执行进度对话框。
 * 布局参考单次上传进度对话框，包含：
 * - 当前子任务文件总体进度条
 * - 当前文件进度条
 * - 总子任务完成进度条
 * - 执行日志区域（可复制）
 * - 复制/关闭按钮（关闭时中断执行）
 */
public class BatchProgressDialog extends JDialog {

    private final LanguageManager lang;
    private final BatchTask task;
    private final BatchExecutionOrchestrator orchestrator;

    // 进度条与标签
    private JLabel subTaskOverallLabel;
    private JProgressBar subTaskOverallBar;
    private JLabel fileLabel;
    private JProgressBar fileBar;
    private JLabel speedLabel;
    private JLabel etaLabel;
    private JLabel batchOverallLabel;
    private JLabel batchOverallEtaLabel;
    private JProgressBar batchOverallBar;

    // 日志
    private JTextArea logArea;

    // 按钮
    private JButton copyBtn;
    private JButton closeBtn;

    // 状态
    private volatile boolean completed = false;
    private volatile boolean cancelled = false;
    private List<BatchSubTaskResult> allResults;

    // 当前执行状态
    private int totalSubTasks = 0;
    private int currentSubTaskIndex = 0;
    private int totalFilesInSubTask = 0;
    private int completedFilesInSubTask = 0;
    private long currentFileStartTime = 0;
    private long batchStartTime = 0;

    // 基于字节数的进度跟踪（所有进度相关状态集中管理）
    private long batchTotalBytes = 0;                 // 批处理总字节数（所有文件）
    private long batchUploadedBytes = 0;              // 已上传字节数（已完成文件）
    private long currentFileUploaded = 0;             // 当前文件已上传字节数
    private long currentFileTotal = 0;                // 当前文件总字节数
    
    // 子任务级别的字节数跟踪
    private long subTaskTotalBytes = 0;               // 当前子任务总字节数
    private long subTaskUploadedBytes = 0;            // 当前子任务已上传字节数
    
    /**
     * 原子性地更新进度状态并刷新UI。
     * 确保状态更新和UI更新在同一个invokeLater块中，避免时序问题。
     */
    private void updateProgressState(Runnable stateUpdater) {
        SwingUtilities.invokeLater(() -> {
            stateUpdater.run();
            updateBatchOverallProgress();
        });
    }

    public BatchProgressDialog(JDialog parent, BatchTask task) {
        super(parent, true);
        this.lang = LanguageManager.getInstance();
        this.task = task;
        this.orchestrator = new BatchExecutionOrchestrator();

        setTitle(lang.get("batch.task.progress"));
        initUI();
        setSize(800, 600);
        setLocationRelativeTo(parent);
        setMinimumSize(new Dimension(600, 400));

        startExecution();
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // ===== 标题 =====
        JLabel titleLabel = new JLabel(lang.get("batch.task.title") + ": " + task.getName());

        // ===== 进度条面板 =====
        JPanel progressPanel = new JPanel();
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));

        // 1. 当前子任务文件总体进度
        subTaskOverallLabel = new JLabel("0/0 (0%)");
        subTaskOverallBar = new JProgressBar(0, 100);
        subTaskOverallBar.setStringPainted(true);
        subTaskOverallBar.setPreferredSize(new Dimension(400, 25));
        JPanel subTaskOverallPanel = new JPanel(new BorderLayout(5, 5));
        subTaskOverallPanel.add(subTaskOverallLabel, BorderLayout.NORTH);
        subTaskOverallPanel.add(subTaskOverallBar, BorderLayout.CENTER);

        // 2. 当前文件进度
        fileLabel = new JLabel("...");
        speedLabel = new JLabel("0 KB/s");
        etaLabel = new JLabel("--");
        fileBar = new JProgressBar(0, 100);
        fileBar.setStringPainted(true);
        fileBar.setPreferredSize(new Dimension(400, 20));
        JPanel fileInfoPanel = new JPanel(new BorderLayout());
        fileInfoPanel.add(fileLabel, BorderLayout.WEST);
        JPanel filePanel = new JPanel(new BorderLayout(5, 5));
        filePanel.add(fileInfoPanel, BorderLayout.NORTH);
        filePanel.add(fileBar, BorderLayout.CENTER);
        // 速率和剩余时间面板（与 ProgressDialog 一致）
        JPanel infoPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        infoPanel.add(speedLabel);
        infoPanel.add(etaLabel);
        filePanel.add(infoPanel, BorderLayout.SOUTH);

        // 3. 总子任务完成进度
        batchOverallLabel = new JLabel("0/0 (0%)");
        batchOverallEtaLabel = new JLabel("--");
        batchOverallBar = new JProgressBar(0, 100);
        batchOverallBar.setStringPainted(true);
        batchOverallBar.setPreferredSize(new Dimension(400, 25));
        JPanel batchOverallPanel = new JPanel(new BorderLayout(5, 5));
        batchOverallPanel.add(batchOverallLabel, BorderLayout.NORTH);
        batchOverallPanel.add(batchOverallBar, BorderLayout.CENTER);
        JPanel batchOverallEtaPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        batchOverallEtaPanel.add(batchOverallEtaLabel);
        batchOverallPanel.add(batchOverallEtaPanel, BorderLayout.SOUTH);

        progressPanel.add(filePanel);
        progressPanel.add(Box.createVerticalStrut(10));
        progressPanel.add(subTaskOverallPanel);
        progressPanel.add(Box.createVerticalStrut(10));
        progressPanel.add(batchOverallPanel);

        // ===== 日志区域 =====
        logArea = new JTextArea(20, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);

        // ===== 底部按钮 =====
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.add(Box.createHorizontalGlue());

        copyBtn = new JButton(lang.get("progress.btn.copy"));
        copyBtn.setPreferredSize(new Dimension(80, 28));
        copyBtn.addActionListener(e -> {
            String text = logArea.getText();
            if (text != null && !text.isEmpty()) {
                java.awt.datatransfer.Clipboard clipboard = getToolkit().getSystemClipboard();
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
                clipboard.setContents(selection, selection);
                JOptionPane.showMessageDialog(this, lang.get("progress.copy.success"), lang.get("progress.copy.title"), JOptionPane.INFORMATION_MESSAGE);
            }
        });

        closeBtn = new JButton(lang.get("config.btn.close"));
        closeBtn.addActionListener(e -> {
            if (!completed && !cancelled) {
                orchestrator.cancel();
                cancelled = true;
                appendLog(lang.get("batch.task.cancelRequested"));
            }
            dispose();
        });

        buttonPanel.add(copyBtn);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(closeBtn);

        // ===== 组装北侧面板（标题 + 进度条） =====
        JPanel northPanel = new JPanel(new BorderLayout());
        northPanel.add(titleLabel, BorderLayout.NORTH);
        northPanel.add(progressPanel, BorderLayout.CENTER);

        mainPanel.add(northPanel, BorderLayout.NORTH);
        mainPanel.add(logScrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    private void startExecution() {
        Logger.debug("BatchProgressDialog", "Starting batch execution for task: " + task.getName());
        orchestrator.setListener(new BatchExecutionListener() {
            @Override
            public void onBatchStart(BatchTask task, int totalSubTasks) {
                Logger.debug("BatchProgressDialog", "onBatchStart: totalSubTasks=" + totalSubTasks);
                BatchProgressDialog.this.totalSubTasks = totalSubTasks;
                batchStartTime = System.currentTimeMillis();
                batchUploadedBytes = 0;
                batchTotalBytes = 0;
                for (BatchSubTask st : task.getSubTasks()) {
                    if (st.getFilePaths() != null) {
                        for (String fp : st.getFilePaths()) {
                            long fileSize = calculateFileSize(new File(fp));
                            batchTotalBytes += fileSize;
                            Logger.debug("BatchProgressDialog", "Calculating size for " + fp + ": " + fileSize);
                        }
                    }
                }
                Logger.debug("BatchProgressDialog", "Total batch size calculated: " + batchTotalBytes);
                SwingUtilities.invokeLater(() -> {
                    batchOverallBar.setMaximum(100);
                    batchOverallBar.setValue(0);
                    batchOverallLabel.setText("0/" + totalSubTasks + " (0%)");
                    batchOverallEtaLabel.setText("--");
                    appendLog(lang.get("batch.task.start") + ": " + task.getName());
                    appendLog(lang.get("batch.task.totalSubTasks") + ": " + totalSubTasks);
                });
            }

            @Override
            public void onSubTaskStart(BatchSubTask subTask, int index, int total) {
                Logger.debug("BatchProgressDialog", "onSubTaskStart: index=" + index + "/" + total + ", subTaskId=" + subTask.getId());
                currentSubTaskIndex = index;
                totalFilesInSubTask = subTask.getFilePaths() != null ? subTask.getFilePaths().size() : 0;
                
                // Calculate total bytes for current subtask
                subTaskTotalBytes = 0;
                subTaskUploadedBytes = 0;
                if (subTask.getFilePaths() != null) {
                    for (String fp : subTask.getFilePaths()) {
                        long fileSize = calculateFileSize(new File(fp));
                        subTaskTotalBytes += fileSize;
                        Logger.debug("BatchProgressDialog", "SubTask file size: " + fp + " -> " + fileSize);
                    }
                }
                Logger.debug("BatchProgressDialog", "SubTask total bytes: " + subTaskTotalBytes);
                
                SwingUtilities.invokeLater(() -> {
                    completedFilesInSubTask = 0;
                    appendLog("[" + index + "/" + total + "] " + getSubTaskDesc(subTask));
                    subTaskOverallBar.setValue(0);
                    subTaskOverallLabel.setText("0/" + totalFilesInSubTask + " (0%)");
                    fileLabel.setText("...");
                    fileBar.setValue(0);
                    updateBatchOverallProgress();
                });
            }

            @Override
            public void onFileStart(String fileName, int fileIndex, int totalFiles) {
                Logger.debug("BatchProgressDialog", "onFileStart: " + fileName + " (" + fileIndex + "/" + totalFiles + ")");
                currentFileStartTime = System.currentTimeMillis();
                updateProgressState(() -> {
                    currentFileUploaded = 0;
                    currentFileTotal = 0;
                    fileLabel.setText(fileName + " (0%)");
                    fileBar.setValue(0);
                    speedLabel.setText("0 KB/s");
                    etaLabel.setText("--");
                });
            }

            @Override
            public void onUploadProgress(String fileName, int percent, long uploaded, long total) {
                currentFileUploaded = uploaded;
                currentFileTotal = total;
                long elapsed = System.currentTimeMillis() - currentFileStartTime;
                long fileSpeed = elapsed > 0 ? uploaded * 1000 / elapsed : 0;
                String speedStr = formatSpeed(fileSpeed);
                long fileRemaining = total - uploaded;
                long fileEtaSeconds = (fileSpeed > 0 && fileRemaining > 0) ? fileRemaining / fileSpeed : -1;
                String fileEtaStr = formatEta(fileEtaSeconds);
                updateProgressState(() -> {
                    fileLabel.setText(fileName + " (" + percent + "%)");
                    fileBar.setValue(percent);
                    speedLabel.setText(speedStr);
                    etaLabel.setText(fileEtaStr);
                    updateSubTaskProgress(uploaded);
                });
            }

            @Override
            public void onFileCompleted(String fileName, long size, boolean success, String errorMessage) {
                Logger.debug("BatchProgressDialog", "onFileCompleted: " + fileName + ", size=" + size + ", success=" + success);
                updateProgressState(() -> {
                    completedFilesInSubTask++;
                    batchUploadedBytes += size;
                    subTaskUploadedBytes += size;
                    currentFileUploaded = 0;
                    currentFileTotal = 0;
                    
                    if (subTaskTotalBytes > 0) {
                        int pct = (int) (subTaskUploadedBytes * 100.0 / subTaskTotalBytes);
                        subTaskOverallBar.setValue(pct);
                        subTaskOverallLabel.setText(completedFilesInSubTask + "/" + totalFilesInSubTask + " (" + pct + "%)");
                    }
                    
                    if (success) {
                        appendLog("  [OK] " + fileName + " (" + formatSize(size) + ")");
                    } else {
                        appendLog("  [FAIL] " + fileName + " - " + errorMessage);
                    }
                });
            }

            @Override
            public void onSubTaskCompleted(BatchSubTaskResult result) {
                Logger.debug("BatchProgressDialog", "onSubTaskCompleted: " + result.getSubTaskId() + ", status=" + result.getStatus());
                updateProgressState(() -> {
                    String icon = result.isSuccess() ? "[OK]" : "[FAIL]";
                    appendLog(icon + " " + result.getTaskDescription());
                    if (!result.isSuccess() && result.getErrorMessage() != null) {
                        appendLog("  " + result.getErrorMessage());
                    }
                });
            }

            @Override
            public void onBatchCompleted(List<BatchSubTaskResult> results) {
                Logger.debug("BatchProgressDialog", "onBatchCompleted: results count=" + results.size());
                allResults = results;
                SwingUtilities.invokeLater(() -> {
                    completed = true;
                    batchTotalBytes = batchUploadedBytes;
                    
                    batchOverallBar.setValue(100);
                    batchOverallLabel.setText(totalSubTasks + "/" + totalSubTasks + " (100%)");
                    batchOverallEtaLabel.setText("0s");

                    int success = 0;
                    long totalDuration = 0;
                    for (BatchSubTaskResult r : results) {
                        if (r.isSuccess()) success++;
                        totalDuration += r.getDurationMs();
                    }
                    appendLog("\n" + lang.get("batch.task.completed"));
                    appendLog(lang.get("batch.task.log.files") + ": " + results.size());
                    appendLog(lang.get("batch.task.success") + ": " + success);
                    appendLog(lang.get("batch.task.failed") + ": " + (results.size() - success));
                    appendLog(lang.get("batch.task.log.time") + ": " + formatTime(totalDuration / 1000));
                });
            }

            @Override
            public void onBatchCancelled() {
                Logger.debug("BatchProgressDialog", "onBatchCancelled");
                SwingUtilities.invokeLater(() -> {
                    completed = true;
                    cancelled = true;
                    appendLog(lang.get("batch.task.cancelled"));
                });
            }

            @Override
            public void onLog(String message) {
                SwingUtilities.invokeLater(() -> appendLog(message));
            }
        });

        orchestrator.execute(task);
    }

    private void appendLog(String message) {
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }

    /**
     * 更新子任务进度（基于字节数计算）。
     * @param uploadedBytes 当前文件已上传字节数
     */
    private void updateSubTaskProgress(long uploadedBytes) {
        if (subTaskTotalBytes <= 0) return;
        
        // 子任务进度 = (已完成文件字节数 + 当前文件已上传字节数) / 子任务总字节数
        long totalUploadedInSubTask = subTaskUploadedBytes + uploadedBytes;
        int pct = (int) (totalUploadedInSubTask * 100.0 / subTaskTotalBytes);
        
        subTaskOverallBar.setValue(Math.min(pct, 100));
        subTaskOverallLabel.setText(completedFilesInSubTask + "/" + totalFilesInSubTask + " (" + pct + "%)");
    }

    /**
     * 更新全量任务进度（基于已上传字节数）。
     * 剩余时间 = 剩余字节数 / 当前平均上传速率。
     */
    private void updateBatchOverallProgress() {
        if (totalSubTasks <= 0) return;
        int completedSubTasks = currentSubTaskIndex - 1;
        long totalUploaded = batchUploadedBytes + currentFileUploaded;
        int overallPct = 0;
        if (batchTotalBytes > 0) {
            overallPct = (int) (totalUploaded * 100.0 / batchTotalBytes);
        }
        batchOverallBar.setValue(Math.min(overallPct, 100));
        batchOverallBar.setString(overallPct + "%");
        batchOverallLabel.setText(completedSubTasks + "/" + totalSubTasks + " (" + overallPct + "%)");

        if (batchStartTime > 0 && overallPct > 0 && overallPct < 100) {
            long elapsed = System.currentTimeMillis() - batchStartTime;
            long speed = elapsed > 0 ? totalUploaded * 1000 / elapsed : 0;
            long remainingBytes = batchTotalBytes - totalUploaded;
            long etaSeconds = speed > 0 ? remainingBytes / speed : -1;
            batchOverallEtaLabel.setText(formatEta(etaSeconds));
        } else if (overallPct >= 100) {
            batchOverallEtaLabel.setText("0s");
        } else {
            batchOverallEtaLabel.setText("--");
        }
    }

    private String formatSpeed(long bytesPerSecond) {
        DecimalFormat df = new DecimalFormat("#.##");
        if (bytesPerSecond < 1024) {
            return bytesPerSecond + " B/s";
        } else if (bytesPerSecond < 1024 * 1024) {
            return df.format(bytesPerSecond / 1024.0) + " KB/s";
        } else {
            return df.format(bytesPerSecond / 1024.0 / 1024.0) + " MB/s";
        }
    }

    private String formatEta(long seconds) {
        if (seconds < 0) {
            return "--";
        } else if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            long m = seconds / 60;
            long s = seconds % 60;
            return m + "m " + s + "s";
        } else {
            long h = seconds / 3600;
            long m = (seconds % 3600) / 60;
            return h + "h " + m + "m";
        }
    }

    private String formatSize(long bytes) {
        DecimalFormat df = new DecimalFormat("#.##");
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return df.format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return df.format(bytes / 1024.0 / 1024.0) + " MB";
        } else {
            return df.format(bytes / 1024.0 / 1024.0 / 1024.0) + " GB";
        }
    }

    private String formatTime(long seconds) {
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return (seconds / 60) + "m " + (seconds % 60) + "s";
        } else {
            return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
        }
    }

    /**
     * 递归计算文件或目录的总大小。
     */
    private long calculateFileSize(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long size = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                size += calculateFileSize(child);
            }
        }
        return size;
    }

    private String getSubTaskDesc(BatchSubTask subTask) {
        return task.getName() + " (" + lang.get("batch.task.log.files") + ":" + (subTask.getFilePaths() != null ? subTask.getFilePaths().size() : 0) + ")";
    }
}
