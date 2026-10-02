package com.openxt.uploadsshfile.cliapi;

import com.openxt.uploadsshfile.ExitCodes;
import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.model.ExecutionSummary;
import com.openxt.uploadsshfile.orchestration.ExecutionListener;

import java.io.PrintStream;

/**
 * CLI 命令管线监听器（1.0.8 / D-13，设计文档 §1/§3.2 补定 #3）。
 *
 * <p>复用既有 ExecutionListener 扩展点——接口与现有 4 个实现者零改动；
 * 英文步骤行（AXIOM-B，全 ASCII）经 EchoGuard 输出并写 UTF-8 日志；
 * 聚合最终命令阶段退出码（7/8/9/10 映射见 {@link #getExitCode()}）。
 */
public class CliExecutionListener implements ExecutionListener {

    private final EchoGuard echo;
    private final PrintStream log;
    private volatile int exitCode = ExitCodes.OK;

    public CliExecutionListener(EchoGuard echo, PrintStream log) {
        this.echo = echo;
        this.log = log;
    }

    private void line(String s) {
        echo.println(s);
        if (log != null) {
            log.println(s);
        }
    }

    /** 命令阶段结论退出码（OK 除非被失败/拦截/中止改写） */
    public int getExitCode() {
        return exitCode;
    }

    @Override
    public void onStart(int totalCommands) {
        line("== Command group started: " + totalCommands + " command(s) ==");
    }

    @Override
    public void onCommandStart(int index, int total, String command) {
        line("[" + index + "/" + total + "] " + command);
    }

    @Override
    public void onCommandSuccess(int index, int total, String command, CommandResult result) {
        line("[" + index + "/" + total + "] OK (exit " + result.getExitCode() + ")");
    }

    @Override
    public void onCommandFailed(int index, int total, String command, CommandResult result) {
        // KeywordMatcher 判失败的现有语义：exitCode 被改写为 -2（§7 异常表→退出码 8）
        int mapped = result.getExitCode() == -2 ? ExitCodes.CMD_KEYWORD : ExitCodes.CMD_FAIL;
        exitCode = mapped;
        line("[" + index + "/" + total + "] FAILED (mapped exit " + mapped + "): " + safeOneLine(result.getStderr()));
    }

    @Override
    public void onBlocked(int index, int total, String reason) {
        exitCode = ExitCodes.BLOCKED;
        line("[" + index + "/" + total + "] BLOCKED: " + safeOneLine(reason));
    }

    @Override
    public void onComplete(ExecutionSummary summary) {
        line("== Command group finished: total=" + summary.getTotal()
                + " success=" + summary.getSuccessCount()
                + " failed=" + summary.getFailedCount()
                + " blocked=" + summary.getBlockedCount() + " ==");
    }

    @Override
    public void onError(String errorMessage) {
        // onError 不覆盖更具体的失败码（若已 7/8/9 保持）
        if (exitCode == ExitCodes.OK) {
            exitCode = ExitCodes.CMD_FAIL;
        }
        line("== Command group error: " + safeOneLine(errorMessage) + " ==");
    }

    /** 远端输出可能含控制符/多行：进入本地 ASCII 行前折行净化（AXIOM-B 边界，原文已由 DailyLogService 落 UTF-8） */
    static String safeOneLine(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\r", " ").replace("\n", " | ").replace("　", " ");
    }
}
