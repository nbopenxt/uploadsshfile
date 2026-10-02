package com.openxt.uploadsshfile.ui;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.sync.LockInfo;
import com.openxt.uploadsshfile.sync.UploadLockManager;

import javax.swing.*;
import java.awt.*;
import java.util.Optional;

/**
 * 锁占用提示对话框（1.0.8 / R50/U-05/RISK-15，设计文档 §6.2 页面 4）。
 *
 * <p>GUI 单任务上传抢锁失败时弹出：展示持锁方 任务ID/PID/已运行秒数（双条件判活陈旧提示），
 * 提供"等待 / 取消"。**等待走后台轮询线程，不冻结 EDT**（RISK-15）；取消＝中止且不改任何配置。
 * 返回已持有的 {@link UploadLockManager.Handle}（调用方 try/finally 释放）或 null（取消）。
 */
public final class LockConflictDialog extends JDialog {

    private UploadLockManager.Handle resultHandle; // 非 null＝已抢得
    private volatile boolean polling;

    private LockConflictDialog(Window parent, String serverId, String taskId, LanguageManager lm) {
        super(parent, lm.get("lock.title"), ModalityType.APPLICATION_MODAL);
        setName("lockConflictDialog");
        setResizable(false);

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));

        JLabel infoLabel = new JLabel("<html>" + escape(lm.get("lock.holding")) + "<br>"
                + formatHolder(UploadLockManager.getInstance().readInfo(serverId)) + "</html>");
        content.add(infoLabel, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        JButton waitBtn = new JButton(lm.get("lock.wait"));
        JButton cancelBtn = new JButton(lm.get("execution.btn.cancel"));
        btns.add(waitBtn);
        btns.add(cancelBtn);
        content.add(btns, BorderLayout.SOUTH);
        setContentPane(content);

        waitBtn.addActionListener(e -> {
            waitBtn.setEnabled(false);
            startPolling(serverId, taskId, infoLabel, parent, lm);
        });
        cancelBtn.addActionListener(e -> {
            polling = false;
            dispose();
        });

        pack();
        setLocationRelativeTo(parent);
    }

    private void startPolling(String serverId, String taskId, JLabel infoLabel, Window parent, LanguageManager lm) {
        polling = true;
        Thread poll = new Thread(() -> {
            long start = System.currentTimeMillis();
            while (polling) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!polling) {
                    return;
                }
                Optional<UploadLockManager.Handle> h;
                try {
                    h = UploadLockManager.getInstance().tryLock(serverId, taskId);
                } catch (java.io.IOException infraFail) {
                    // 故障＝中止（不得伪装成占用继续死等）
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(null,
                                "Lock subsystem error: " + infraFail.getMessage(),
                                lm.get("common.warning"), JOptionPane.ERROR_MESSAGE);
                        polling = false;
                        dispose();
                    });
                    return;
                }
                if (h.isPresent()) {
                    SwingUtilities.invokeLater(() -> {
                        resultHandle = h.get();
                        dispose();
                    });
                    return;
                }
                long waited = (System.currentTimeMillis() - start) / 1000;
                String line = "<html>" + escape(lm.get("lock.holding")) + "<br>"
                        + formatHolder(UploadLockManager.getInstance().readInfo(serverId))
                        + "<br>" + escape(lm.get("lock.waited", waited)) + "</html>";
                SwingUtilities.invokeLater(() -> infoLabel.setText(line));
            }
        }, "uploadsshfile-lock-wait");
        poll.setDaemon(true);
        poll.start();
    }

    /** 设计页面 4 的展示行：task / pid / running */
    private static String formatHolder(LockInfo info) {
        if (info == null) {
            return "task: ?&nbsp;&nbsp;pid: ?&nbsp;&nbsp;running: ?";
        }
        String stale = info.holderLooksAlive() ? "" : "&nbsp;&nbsp;<i>(holder process not found)</i>";
        return "task: " + nvl(info.taskId)
                + "&nbsp;&nbsp;pid: " + info.pid
                + "&nbsp;&nbsp;running: " + formatSec(info.heldSeconds()) + stale;
    }

    private static String nvl(String s) {
        return s == null || s.isEmpty() ? "?" : s;
    }

    private static String formatSec(long sec) {
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * 静态入口：先无锁成本快速抢一次（成功不打扰用户）；被占才弹窗。
     * @return 已持有的句柄；null＝用户取消（调用方须中止且**不改任何配置**，流程 C）
     */
    public static UploadLockManager.Handle acquireWithUiWait(Component parentComponent,
                                                             String serverId, String taskId,
                                                             LanguageManager lm) {
        Optional<UploadLockManager.Handle> quick;
        try {
            quick = UploadLockManager.getInstance().tryLock(serverId, taskId);
        } catch (java.io.IOException infraFail) {
            // 锁基础设施故障≠占用（M4 契约）：明确报错并按"未拿到锁"返回（调用方中止、不改配置），
            // 绝不进入排队分支
            JOptionPane.showMessageDialog(parentComponent,
                    "Lock subsystem error: " + infraFail.getMessage(),
                    lm.get("common.warning"), JOptionPane.ERROR_MESSAGE);
            return null;
        }
        if (quick.isPresent()) {
            return quick.get();
        }
        Window win = SwingUtilities.getWindowAncestor(parentComponent);
        LockConflictDialog dlg = new LockConflictDialog(win, serverId, taskId, lm);
        dlg.setVisible(true); // modal 阻塞至 dispose
        return dlg.resultHandle;
    }
}
