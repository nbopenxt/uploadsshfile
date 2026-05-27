package com.openxt.uploadsshfile.ui;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.model.ExecutionSummary;
import com.openxt.uploadsshfile.orchestration.ExecutionListener;

import javax.swing.*;
import java.awt.*;

/**
 * 命令执行进度对话框
 */
public class ExecutionProgressDialog extends JDialog implements ExecutionListener {
    
    private static final LanguageManager lang = LanguageManager.getInstance();
    private JProgressBar progressBar;
    private JTextArea logArea;
    private JButton cancelButton;
    private JButton continueButton;
    private JButton cancelAllButton;
    private JButton closeButton;
    
    private volatile boolean cancelled = false;
    private boolean waitingForContinue = false;
    private boolean waitingForRiskContinue = false;
    private boolean waitingForWarningContinue = false;
    private boolean waitingForCautionContinue = false;
    private String lastRiskInfo = "";
    private String lastWarningInfo = "";
    private String lastCautionInfo = "";
    
    /** 当前正在执行的命令 */
    private String currentCommand = "";
    
    /** 当前命令的输出内容 */
    private StringBuilder currentOutput = new StringBuilder();
    
    private final Object lock = new Object();
    
    public ExecutionProgressDialog(Frame parent) {
        super(parent, lang.get("execution.title"), false);
        initComponents();
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
    }
    
    private void initComponents() {
        setLayout(new BorderLayout(10, 10));
        // 设置窗口大小为最大尺寸，充分利用屏幕空间
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(1200, screenSize.width - 100);
        int height = Math.min(800, screenSize.height - 150);
        setSize(width, height);
        
        // 进度条
        progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setString(lang.get("execution.status.ready"));
        add(progressBar, BorderLayout.NORTH);
        
        // 日志区域
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(logArea);
        add(scrollPane, BorderLayout.CENTER);
        
        // 按钮区域
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        
        continueButton = new JButton(lang.get("execution.btn.continue"));
        continueButton.setVisible(false);
        continueButton.addActionListener(e -> onContinueClicked());
        buttonPanel.add(continueButton);
        
        cancelAllButton = new JButton(lang.get("execution.btn.cancelAll"));
        cancelAllButton.setVisible(false);
        cancelAllButton.addActionListener(e -> onCancelAllClicked());
        buttonPanel.add(cancelAllButton);
        
        cancelButton = new JButton(lang.get("execution.btn.cancel"));
        cancelButton.addActionListener(e -> onCancelClicked());
        buttonPanel.add(cancelButton);
        
        closeButton = new JButton(lang.get("execution.btn.close"));
        closeButton.setVisible(false);
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);
        
        add(buttonPanel, BorderLayout.SOUTH);
    }
    
    // ========== ExecutionListener 实现 ==========
    
    @Override
    public void onStart(int totalCommands) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setMaximum(totalCommands);
            progressBar.setValue(0);
            progressBar.setString(lang.get("execution.status.starting"));
            logArea.setText("");
            appendLog(lang.get("execution.started", totalCommands) + "\n");
            appendLog("=".repeat(50) + "\n\n");
        });
    }
    
    @Override
    public void onCommandStart(int index, int total, String command) {
        // 记录当前命令和清空输出
        currentCommand = command;
        currentOutput = new StringBuilder();
        
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(index - 1);
            progressBar.setString(String.format("Executing %d/%d", index, total));
            appendLog(String.format("[%d/%d] Executing: %s\n", index, total, command));
        });
    }
    
    @Override
    public void onCommandSuccess(int index, int total, String command, CommandResult result) {
        SwingUtilities.invokeLater(() -> {
            appendLog(String.format("  [%s] %s: %d, %s: %dms\n",
                lang.get("result.success"), lang.get("result.exitcode", result.getExitCode(), result.getDurationMs())));
            if (!result.getStdout().isEmpty()) {
                appendLog("  " + lang.get("result.output") + "\n");
                appendLog(result.getStdout());
                if (!result.getStdout().endsWith("\n")) {
                    appendLog("\n");
                }
            }
            progressBar.setValue(index);
            progressBar.setString(String.format("Completed %d/%d", index, total));
        });
    }
    
    @Override
    public void onCommandFailed(int index, int total, String command, CommandResult result) {
        SwingUtilities.invokeLater(() -> {
            appendLog(String.format("  [%s] %s: %d, %s: %dms\n",
                lang.get("result.failed"), lang.get("result.exitcode", result.getExitCode(), result.getDurationMs())));
            if (!result.getStdout().isEmpty()) {
                appendLog("  " + lang.get("result.output") + "\n");
                appendLog(result.getStdout());
                if (!result.getStdout().endsWith("\n")) {
                    appendLog("\n");
                }
            }
            if (!result.getStderr().isEmpty()) {
                appendLog("  " + lang.get("result.error") + "\n");
                appendLog(result.getStderr());
                if (!result.getStderr().endsWith("\n")) {
                    appendLog("\n");
                }
            }
        });
    }
    
    @Override
    public void onBlocked(int index, int total, String reason) {
        SwingUtilities.invokeLater(() -> {
            appendLog(String.format("  [%s] %s\n", lang.get("result.blocked"), reason));
            progressBar.setValue(index);
        });
    }
    
    @Override
    public void onComplete(ExecutionSummary summary) {
        SwingUtilities.invokeLater(() -> {
            appendLog("\n" + "=".repeat(50) + "\n");
            appendLog(lang.get("summary.title") + "\n");
            appendLog("  " + lang.get("summary.total", summary.getTotal()) + "\n");
            appendLog("  " + lang.get("summary.success", summary.getSuccessCount()) + "\n");
            appendLog("  " + lang.get("summary.failed", summary.getFailedCount()) + "\n");
            appendLog("  " + lang.get("summary.blocked", summary.getBlockedCount()) + "\n");
            
            progressBar.setValue(progressBar.getMaximum());
            progressBar.setString(lang.get("execution.status.completed"));
            
            continueButton.setVisible(false);
            cancelAllButton.setVisible(false);
            cancelButton.setVisible(false);
            closeButton.setVisible(true);
            
            // 自动选中 Close 按钮
            getRootPane().setDefaultButton(closeButton);
        });
    }
    
    @Override
    public void onError(String errorMessage) {
        SwingUtilities.invokeLater(() -> {
            appendLog("\n[ERROR] " + errorMessage + "\n");
            cancelButton.setVisible(false);
            closeButton.setVisible(true);
        });
    }
    
    // ========== 询问用户 ==========
    
    /**
     * 询问用户是否继续执行
     */
    public boolean askUserContinue(String message) {
        waitingForContinue = true;
        
        SwingUtilities.invokeLater(() -> {
            appendLog("\n" + "=".repeat(50) + "\n");
            appendLog(message + "\n");
            
            continueButton.setText(lang.get("execution.btn.continue"));
            continueButton.setVisible(true);
            cancelAllButton.setVisible(true);
            cancelButton.setVisible(false);
            
            // 自动选中 Continue 按钮（Enter 键）
            getRootPane().setDefaultButton(continueButton);
        });
        
        // 等待用户响应
        synchronized (lock) {
            while (waitingForContinue) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        return !cancelled;
    }
    
    /**
     * 询问用户是否继续执行危险命令
     */
    public boolean askUserRiskContinue(String command, String riskInfo) {
        waitingForRiskContinue = true;
        this.lastRiskInfo = riskInfo;
        
        SwingUtilities.invokeLater(() -> {
            appendLog("\n" + "=".repeat(50) + "\n");
            appendLog("⚠️  " + lang.get("ai.risk.warning") + ":\n");
            appendLog("Command: " + command + "\n\n");
            appendLog(riskInfo + "\n");
            appendLog("\n" + lang.get("ai.execute.prompt") + "\n");
            
            continueButton.setText(lang.get("execution.btn.executeAnyway"));
            continueButton.setVisible(true);
            cancelAllButton.setText(lang.get("execution.btn.cancelAll"));
            cancelAllButton.setVisible(true);
            cancelButton.setVisible(false);
            
            getRootPane().setDefaultButton(continueButton);
        });
        
        synchronized (lock) {
            while (waitingForRiskContinue) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        return !cancelled;
    }
    
    /**
     * 询问用户是否继续执行后续命令
     */
    public boolean askUserWarningContinue(String command, String warningInfo) {
        waitingForWarningContinue = true;
        this.lastWarningInfo = warningInfo;
        
        SwingUtilities.invokeLater(() -> {
            appendLog("\n" + "=".repeat(50) + "\n");
            appendLog("⚠️  " + lang.get("ai.result.warning") + ":\n");
            appendLog("Command: " + command + "\n\n");
            appendLog(warningInfo + "\n");
            appendLog("\n" + lang.get("ai.continue.prompt") + "\n");
            
            continueButton.setText(lang.get("execution.btn.continue"));
            continueButton.setVisible(true);
            cancelAllButton.setText(lang.get("execution.btn.cancelAll"));
            cancelAllButton.setVisible(true);
            cancelButton.setVisible(false);
            
            getRootPane().setDefaultButton(continueButton);
        });
        
        synchronized (lock) {
            while (waitingForWarningContinue) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        return !cancelled;
    }
    
    /**
     * 询问用户是否继续执行存在语义风险的命令
     */
    public boolean askUserCautionContinue(String command, String cautionInfo) {
        waitingForCautionContinue = true;
        this.lastCautionInfo = cautionInfo;
        
        SwingUtilities.invokeLater(() -> {
            appendLog("\n" + "=".repeat(50) + "\n");
            appendLog("⚠️  " + lang.get("ai.caution.warning") + ":\n");
            appendLog("Command: " + command + "\n\n");
            appendLog(cautionInfo + "\n");
            appendLog("\n" + lang.get("ai.execute.prompt") + "\n");
            
            continueButton.setText(lang.get("execution.btn.executeAnyway"));
            continueButton.setVisible(true);
            cancelAllButton.setText(lang.get("execution.btn.cancelAll"));
            cancelAllButton.setVisible(true);
            cancelButton.setVisible(false);
            
            getRootPane().setDefaultButton(continueButton);
        });
        
        synchronized (lock) {
            while (waitingForCautionContinue) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        return !cancelled;
    }
    
    private void onContinueClicked() {
        synchronized (lock) {
            waitingForContinue = false;
            waitingForRiskContinue = false;
            waitingForWarningContinue = false;
            waitingForCautionContinue = false;
            cancelled = false;
            lock.notifyAll();
        }
    }
    
    private void onCancelAllClicked() {
        synchronized (lock) {
            waitingForContinue = false;
            waitingForRiskContinue = false;
            waitingForWarningContinue = false;
            waitingForCautionContinue = false;
            cancelled = true;
            lock.notifyAll();
        }
    }
    
    private void onCancelClicked() {
        cancelled = true;
        dispose();
    }
    
    private void appendLog(String text) {
        logArea.append(text);
        logArea.setCaretPosition(logArea.getDocument().getLength());
    }
    
    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        text = text.replace("\n", " ").replace("\r", "");
        if (text.length() <= maxLength) return text;
        return text.substring(0, maxLength) + "...";
    }
    
    // ========== 用户交互方法 ==========
    
    /**
     * 询问用户是否继续等待命令完成
     * 
     * <p>根据需求，弹窗应显示：
     * <ul>
     *   <li>标题：正在执行: {command}</li>
     *   <li>已执行时间</li>
     *   <li>命令内容</li>
     *   <li>已产生的输出内容</li>
     * </ul>
     */
    public boolean promptContinueWait(String command, long elapsedMs) {
        // 在 EDT 线程上显示弹窗
        final boolean[] result = new boolean[1];
        
        try {
            SwingUtilities.invokeAndWait(() -> {
                TimeoutPromptDialog dialog = new TimeoutPromptDialog((Frame) SwingUtilities.getWindowAncestor(this), command);
                dialog.updateElapsedTime(elapsedMs);
                dialog.updateOutput(currentOutput.toString());
                result[0] = dialog.showAndWait();
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.lang.reflect.InvocationTargetException e) {
            return false;
        }
        
        return result[0];
    }
    
    /**
     * 获取当前命令的输出内容
     */
    public String getCurrentOutput() {
        return currentOutput.toString();
    }
    
    /**
     * 追加输出内容（用于实时显示）
     */
    public void appendCommandOutput(String output) {
        currentOutput.append(output);
    }
}
