package com.openxt.uploadsshfile.cliapi;

/**
 * CLI 询问能力抽象（1.0.8 / D-13/D-16；设计文档 §3.2/§3.3）。
 *
 * <p>core/cliapi 只定义接口；实现方为 cli 模块 ConsoleInteraction（阻塞读 System.in，
 * EOF/无 stdin 一律返回 false＝默认中止，R36）。
 * 询问在调用线程就地阻塞（与 GUI 匿名子类覆写 askUserXxx 弹 Swing 同构——AXIOM-A 允许终端人工确认）。
 * 询问期间实现方必须持有 {@link EchoGuard} 独占权（提问不被心跳/进度行撕开）。
 */
@FunctionalInterface
public interface Prompter {

    /**
     * 英文 yes/no 提问。
     * @param question 完整英文问句（ASCII，bundle en 净化后）
     * @return true＝用户答"是"；false＝答"否"、EOF、无 stdin（默认中止口径）
     */
    boolean askYesNo(String question);
}
