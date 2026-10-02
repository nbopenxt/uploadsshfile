package com.openxt.uploadsshfile.cliapi;

import java.io.PrintStream;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 回显独占闸（1.0.8 / D-16/D-24；设计文档 §3.3）。
 *
 * <p>CLI 前台进程中"排队心跳/上传进度刷新"与"询问读键盘"共用主线程与同一段 stdout，
 * 本闸保证任一 askYesNo 期间（置位后）排队/进度回显静默，防止提问被心跳行撕开。
 * 心跳与 \r 进度行输出前必须调用 {@link #println}/{@link #progress} 统一入口（内部自查标志）。
 */
public class EchoGuard {

    private final AtomicBoolean asking = new AtomicBoolean(false);
    private final PrintStream out;

    public EchoGuard(PrintStream out) {
        this.out = out;
    }

    /** 询问开始置位/结束复位（由 CliCommandOrchestrator/TimeoutPrompter 实现包裹） */
    public boolean begin() {
        return asking.compareAndSet(false, true);
    }

    public void end() {
        asking.set(false);
    }

    public boolean isAsking() {
        return asking.get();
    }

    /** 行输出：询问期静默丢弃（心跳行），非询问期正常打印 */
    public void println(String line) {
        if (asking.get()) {
            return;
        }
        out.println(line);
    }

    /**
     * \r 覆盖式进度刷新（D-24）：询问期静默；无控制台场景由调用方（ConsoleInteraction 判定）
     * 改用 println 降级形态，本方法只负责"独占行刷新"这一动作。
     */
    public void progress(String carrierLine) {
        if (asking.get()) {
            return;
        }
        out.print('\r');
        out.print(carrierLine);
        out.flush();
    }

    /** 定格当前 \r 行（进度结束/询问前调用，换行落定） */
    public void freeze() {
        if (!asking.get()) {
            out.println();
            out.flush();
        }
    }
}
