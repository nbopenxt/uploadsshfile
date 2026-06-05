package com.openxt.uploadsshfile.ui;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.logging.DailyLogService;

import javax.swing.*;
import java.awt.*;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 上传进度对话框
 */
public class ProgressDialog extends JDialog {
    private JProgressBar overallProgressBar;
    private JProgressBar fileProgressBar;
    private JTextArea logArea;
    private JLabel overallLabel;
    private JLabel currentFileLabel;
    private JLabel speedLabel;
    private JLabel timeLabel;
    private LanguageManager lm;
    private DailyLogService logService;

    private long totalBytes = 0;
    private long uploadedBytes = 0;
    private int totalFiles = 0;
    private int completedFiles = 0;
    private long startTime = 0;

    /** 上传失败时用户是否选择继续（true=继续，false=停止） */
    private volatile boolean continueOnUploadFail = true;
    /** 命令失败时用户是否选择继续（true=继续，false=停止） */
    private volatile boolean continueOnCommandFail = true;

    /** 上传失败选项标志 */
    public static final int USER_CHOICE_CONTINUE = 0;
    public static final int USER_CHOICE_STOP = 1;
    public static final int USER_CHOICE_CANCEL_ALL = 2;

    public ProgressDialog(Frame parent) {
        super(parent, LanguageManager.getInstance().get("progress.title"), false);
        this.lm = LanguageManager.getInstance();
        this.logService = new DailyLogService();
        initComponents();
        // 设置窗口大小为 800x600
        setSize(800, 600);
        setLocationRelativeTo(parent);
    }

    private void initComponents() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        // 进度信息面板
        JPanel progressPanel = new JPanel();
        progressPanel.setLayout(new BoxLayout(progressPanel, BoxLayout.Y_AXIS));

        // 总体进度
        overallLabel = new JLabel("0/0 (0%)");
        overallProgressBar = new JProgressBar(0, 100);
        overallProgressBar.setStringPainted(true);
        overallProgressBar.setPreferredSize(new Dimension(400, 25));

        // 当前文件进度
        currentFileLabel = new JLabel("...");
        fileProgressBar = new JProgressBar(0, 100);
        fileProgressBar.setStringPainted(true);
        fileProgressBar.setPreferredSize(new Dimension(400, 20));

        speedLabel = new JLabel("0 KB/s");
        timeLabel = new JLabel("--");

        // 总体进度面板
        JPanel overallPanel = new JPanel(new BorderLayout(5, 5));
        overallPanel.add(overallLabel, BorderLayout.NORTH);
        overallPanel.add(overallProgressBar, BorderLayout.CENTER);

        // 当前文件面板
        JPanel currentPanel = new JPanel(new BorderLayout(5, 5));
        currentPanel.add(currentFileLabel, BorderLayout.NORTH);
        currentPanel.add(fileProgressBar, BorderLayout.CENTER);

        // 速度和剩余时间面板
        JPanel infoPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        infoPanel.add(speedLabel);
        infoPanel.add(timeLabel);

        progressPanel.add(overallPanel);
        progressPanel.add(Box.createVerticalStrut(10));
        progressPanel.add(currentPanel);
        progressPanel.add(Box.createVerticalStrut(10));
        progressPanel.add(infoPanel);

        // 日志区域 - 增大行数使窗口更大
        logArea = new JTextArea(20, 50);
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);

        // 底部按钮面板 - 使用BoxLayout实现左右分布
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
        buttonPanel.add(Box.createHorizontalGlue()); // 左侧撑开

        // 复制按钮
        JButton copyButton = new JButton(lm.get("progress.btn.copy"));
        copyButton.setPreferredSize(new Dimension(80, 28));
        copyButton.addActionListener(e -> {
            String text = logArea.getText();
            if (text != null && !text.isEmpty()) {
                // 复制到系统剪贴板
                java.awt.datatransfer.Clipboard clipboard = getToolkit().getSystemClipboard();
                java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
                clipboard.setContents(selection, selection);
                // 显示提示
                JOptionPane.showMessageDialog(this, lm.get("progress.copy.success"), lm.get("progress.copy.title"), JOptionPane.INFORMATION_MESSAGE);
            }
        });

        // 关闭按钮
        JButton closeButton = new JButton(lm.get("config.btn.close"));
        closeButton.addActionListener(e -> dispose());

        // 添加按钮到面板（复制按钮在左，关闭按钮在右）
        buttonPanel.add(copyButton);
        buttonPanel.add(Box.createHorizontalStrut(10));
        buttonPanel.add(closeButton);

        // 组装
        mainPanel.add(progressPanel, BorderLayout.NORTH);
        mainPanel.add(logScrollPane, BorderLayout.CENTER);
        mainPanel.add(buttonPanel, BorderLayout.SOUTH);

        setContentPane(mainPanel);
    }

    public void onUploadStarted(int totalFiles, long totalBytes) {
        this.totalFiles = totalFiles;
        this.totalBytes = totalBytes;
        this.startTime = System.currentTimeMillis();
        this.uploadedBytes = 0;
        this.completedFiles = 0;

        overallProgressBar.setValue(0);
        overallLabel.setText(String.format("%d/%d (0%%)", completedFiles, totalFiles));
    }

    public void onProgress(String fileName, int percent, long uploaded, long total) {
        currentFileLabel.setText(fileName + " (" + percent + "%)");
        fileProgressBar.setValue(percent);

        // 计算总体进度
        if (totalBytes > 0) {
            int overallPercent = (int) (uploaded * 100 / totalBytes);
            overallProgressBar.setValue(overallPercent);
            overallLabel.setText(String.format("%d/%d (%d%%)", completedFiles, totalFiles, overallPercent));
        }

        // 计算速度
        long elapsed = System.currentTimeMillis() - startTime;
        long speed = elapsed > 0 ? uploaded * 1000 / elapsed : 0;
        String speedStr = formatSpeed(speed);
        speedLabel.setText(speedStr);

        // 预计剩余时间
        if (speed > 0 && totalBytes > uploaded) {
            long remaining = (totalBytes - uploaded) * elapsed / uploaded;
            timeLabel.setText(formatTime(remaining / 1000));
        }
    }

    public void onFileCompleted(String fileName, long size, boolean success, String errorMessage) {
        completedFiles++;
        uploadedBytes += size;

        int overallPercent = totalBytes > 0 ? (int) (uploadedBytes * 100 / totalBytes) : 0;
        overallProgressBar.setValue(overallPercent);
        overallLabel.setText(String.format("%d/%d (%d%%)", completedFiles, totalFiles, overallPercent));

        if (success) {
            appendLog("[OK] " + fileName + " (" + formatSize(size) + ")");
        } else {
            appendLog("[FAIL] " + fileName + " - " + errorMessage);
        }
    }

    public void onUploadCompleted(int total, int success, long totalBytes, long uploadedBytes) {
        int percent = totalBytes > 0 ? (int) (uploadedBytes * 100 / totalBytes) : 0;
        overallProgressBar.setValue(percent);
        overallLabel.setText(String.format("%d/%d (%d%%)", success, total, percent));

        long elapsed = System.currentTimeMillis() - startTime;
        appendLog("\n" + lm.get("progress.complete"));
        appendLog(lm.get("batch.task.log.files") + ": " + total);
        appendLog(lm.get("batch.task.success") + ": " + success);
        appendLog(lm.get("batch.task.failed") + ": " + (total - success));
        appendLog(lm.get("batch.task.log.size") + ": " + formatSize(totalBytes));
        appendLog(lm.get("batch.task.log.time") + ": " + formatTime(elapsed / 1000));
    }

    public void appendLog(String message) {
        // 添加到UI日志区域
        logArea.append(message + "\n");
        logArea.setCaretPosition(logArea.getDocument().getLength());
        
        // 同时记录到日志文件
        if (logService != null) {
            logService.info("ProgressDialog", message);
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
     * 询问用户上传失败后是否继续
     *
     * @param fileName 文件名
     * @param errorMessage 错误信息
     * @return true=继续上传，false=停止上传
     */
    public boolean askUploadFailedContinue(String fileName, String errorMessage) {
        continueOnUploadFail = false;
        String message = lm.get("upload.failed.ask.continue", fileName, errorMessage);
        String title = lm.get("upload.failed.ask.title");

        Object[] options = {
            lm.get("upload.failed.btn.continue"),
            lm.get("upload.failed.btn.stop")
        };

        int choice = JOptionPane.showOptionDialog(
            this,
            message,
            title,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[0]
        );

        if (choice == JOptionPane.YES_OPTION) {
            continueOnUploadFail = true;
            return true;
        } else {
            continueOnUploadFail = false;
            return false;
        }
    }

    /**
     * 询问用户命令执行失败后是否继续
     *
     * @param command 命令
     * @param errorMessage 错误信息
     * @return true=继续执行，false=停止执行
     */
    public boolean askCommandFailedContinue(String command, String errorMessage) {
        continueOnCommandFail = false;
        String message = lm.get("command.failed.ask.continue", command, errorMessage);
        String title = lm.get("command.failed.ask.title");

        Object[] options = {
            lm.get("command.failed.btn.continue"),
            lm.get("command.failed.btn.stop")
        };

        int choice = JOptionPane.showOptionDialog(
            this,
            message,
            title,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[0]
        );

        if (choice == JOptionPane.YES_OPTION) {
            continueOnCommandFail = true;
            return true;
        } else {
            continueOnCommandFail = false;
            return false;
        }
    }

    /**
     * 检查是否应停止上传（用户选择停止）
     */
    public boolean shouldStopUpload() {
        return !continueOnUploadFail;
    }

    /**
     * 检查是否应停止命令执行（用户选择停止）
     */
    public boolean shouldStopCommand() {
        return !continueOnCommandFail;
    }
}
