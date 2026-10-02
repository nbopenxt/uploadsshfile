package com.openxt.uploadsshfile.ssh;

/**
 * 递进超时询问回调（1.0.8 / D-17，设计文档 §5.7/§1 表）。
 *
 * <p>由 {@link SshCommandService#executeWithShell(com.openxt.uploadsshfile.ssh.SshConnection, String, TimeoutPrompter)}
 * 在读流循环到达既有 {@link TimeoutManager} 阈值（30s→1min→2min→每 5min）时回调；
 * 实现方二选一：GUI＝弹 TimeoutPromptDialog（既有 ExecutionProgressDialog.promptContinueWait），
 * CLI＝cmd 英文提问（EchoGuard 抑制回显后读 stdin）。
 *
 * <p>不并入 ExecutionListener——既有 4 个实现者零改动（设计 §1）。
 * 不传实现（null）时服务层保持 180s 硬停老行为（清单⑩可回退兜底）。
 */
public interface TimeoutPrompter {

    /**
     * @param command   正在等待的远端命令
     * @param elapsedMs 已耗时（毫秒）
     * @return true＝继续等待（进入下一档阈值，不设总上限）；false＝放弃等待（该命令按超时失败处理）
     */
    boolean keepWaiting(String command, long elapsedMs);
}
