package com.openxt.uploadsshfile.ui;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.ssh.TimeoutManager;

import javax.swing.*;
import java.awt.*;

/**
 * 超时询问弹窗
 * 
 * <p>显示内容：
 * <ul>
 *   <li>标题：正在执行: {command}</li>
 *   <li>已执行时间</li>
 *   <li>当前命令内容</li>
 *   <li>已产生的输出内容</li>
 * </ul>
 * 
 * <p>按钮：
 * <ul>
 *   <li>继续等待</li>
 *   <li>终止命令</li>
 * </ul>
 */
public class TimeoutPromptDialog extends JDialog {

    private static final LanguageManager lang = LanguageManager.getInstance();

    private JTextArea outputArea;
    private JButton continueButton;
    private JButton terminateButton;

    private volatile boolean userChoice = false;  // false = 终止, true = 继续等待
    private volatile boolean waitingForResponse = true;

    private final Object lock = new Object();

    public TimeoutPromptDialog(Frame parent, String command) {
        super(parent, lang.get("timeout.title", command), true);
        initComponents(command);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(parent);
    }

    private void initComponents(String command) {
        setLayout(new BorderLayout(10, 10));

        // 设置窗口大小
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        int width = Math.min(700, screenSize.width - 100);
        int height = Math.min(500, screenSize.height - 150);
        setSize(width, height);

        // ===== 顶部：命令信息 =====
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        topPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // 命令内容
        JLabel commandLabel = new JLabel(lang.get("timeout.command", command));
        commandLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
        topPanel.add(commandLabel);

        // 执行时间标签（稍后更新）
        JLabel timeLabel = new JLabel();
        timeLabel.setName("timeLabel");
        timeLabel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        topPanel.add(timeLabel);

        add(topPanel, BorderLayout.NORTH);

        // ===== 中间：输出内容 =====
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setBorder(BorderFactory.createTitledBorder(lang.get("timeout.output")));

        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(outputArea);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        add(centerPanel, BorderLayout.CENTER);

        // ===== 底部：按钮 =====
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

        continueButton = new JButton(lang.get("timeout.btn.continueWait"));
        continueButton.setPreferredSize(new Dimension(120, 35));
        continueButton.addActionListener(e -> onContinueClicked());
        buttonPanel.add(continueButton);

        terminateButton = new JButton(lang.get("timeout.btn.terminate"));
        terminateButton.setPreferredSize(new Dimension(100, 35));
        terminateButton.addActionListener(e -> onTerminateClicked());
        buttonPanel.add(terminateButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // 默认焦点在继续等待按钮
        setDefaultButton(continueButton);
    }

    /**
     * 设置默认按钮
     */
    private void setDefaultButton(JButton button) {
        getRootPane().setDefaultButton(button);
    }

    /**
     * 更新执行时间显示
     *
     * @param elapsedMs 已执行时间（毫秒）
     */
    public void updateElapsedTime(long elapsedMs) {
        SwingUtilities.invokeLater(() -> {
            String timeText = lang.get("timeout.elapsed", TimeoutManager.formatDuration(elapsedMs));
            JLabel timeLabel = (JLabel) ((JPanel) getContentPane().getComponent(0)).getComponent(1);
            timeLabel.setText(timeText);
        });
    }

    /**
     * 更新输出内容
     *
     * @param output 新的输出内容
     */
    public void updateOutput(String output) {
        SwingUtilities.invokeLater(() -> {
            outputArea.setText(output);
            // 滚动到底部
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    /**
     * 追加输出内容
     *
     * @param newOutput 新增的输出内容
     */
    public void appendOutput(String newOutput) {
        SwingUtilities.invokeLater(() -> {
            outputArea.append(newOutput);
            // 滚动到底部
            outputArea.setCaretPosition(outputArea.getDocument().getLength());
        });
    }

    /**
     * 显示弹窗并等待用户响应
     *
     * @return true = 用户选择继续等待，false = 用户选择终止
     */
    public boolean showAndWait() {
        waitingForResponse = true;

        // 在 EDT 线程上显示
        SwingUtilities.invokeLater(() -> {
            setLocationRelativeTo(getParent());
            setVisible(true);
        });

        // 等待用户响应
        synchronized (lock) {
            while (waitingForResponse) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    userChoice = false;
                    break;
                }
            }
        }

        return userChoice;
    }

    /**
     * 继续等待按钮点击
     */
    private void onContinueClicked() {
        synchronized (lock) {
            userChoice = true;
            waitingForResponse = false;
            lock.notifyAll();
        }
        dispose();
    }

    /**
     * 终止命令按钮点击
     */
    private void onTerminateClicked() {
        synchronized (lock) {
            userChoice = false;
            waitingForResponse = false;
            lock.notifyAll();
        }
        dispose();
    }

    /**
     * 关闭弹窗（不等待用户响应）
     */
    public void close() {
        synchronized (lock) {
            waitingForResponse = false;
            lock.notifyAll();
        }
        dispose();
    }
}
