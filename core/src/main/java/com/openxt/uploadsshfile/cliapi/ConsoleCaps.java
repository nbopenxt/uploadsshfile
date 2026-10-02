package com.openxt.uploadsshfile.cliapi;

/**
 * 控制台能力探测（1.0.8 / D-24/R36，设计文档 §3.3 表"无 stdin 判定"）。
 *
 * <p>启动时一次性判定（System.console()）：
 * hasConsole()＝true → 进度用 \r 覆盖式单行 ASCII 条（D-24）、询问读键盘；
 * false（输出被重定向/无终端）→ 进度降级每文件一行、询问默认答"否"中止（R36，退出码 11 由调用方决定）。
 * 静态初始化只求值一次，全 CLI 生命周期一致。
 */
public final class ConsoleCaps {

    private static final boolean CONSOLE = System.console() != null;

    private ConsoleCaps() {
    }

    /** 是否存在可交互控制台（\r 刷新条与键盘提问的前提） */
    public static boolean hasConsole() {
        return CONSOLE;
    }
}
