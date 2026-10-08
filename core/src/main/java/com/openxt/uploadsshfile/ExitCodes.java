package com.openxt.uploadsshfile;

/**
 * CLI 退出码表（1.0.8 / FR-12/AC-25，设计文档 §3.2/§7，SRS §8.2）。
 *
 * <p>消费路径仅两条（思想实验 #8）：用户直敲 bat 或直调 java——
 * cmd /c start /wait 不传播子进程退出码（Windows 语义），且构建侧 R33 无码消费者。
 * 码 1 与码 3 分工：1＝逻辑走到"既非成功也未被分类"的兜底分支；3＝捕获到异常栈。
 */
public final class ExitCodes {

    public static final int OK = 0;                     // 全部成功（上传+校验+命令）
    public static final int UNCLASSIFIED = 1;           // 未归类失败兜底（出现必记日志）
    public static final int PARAM = 2;                  // 参数错误（ID 不存在/--file 缺失或重复/单任务上下文不全）
    public static final int UNKNOWN = 3;                // 未知异常（含配置/凭证不可读）
    public static final int CONNECT = 4;                // 连接/认证失败
    public static final int UPLOAD = 5;                 // 上传失败（含目录不存在且未开自动创建、建目录失败）
    public static final int VERIFY = 6;                 // 完整性校验不通过（MD5/尺寸）
    public static final int CMD_FAIL = 7;               // 远端命令退出码非 0
    public static final int CMD_KEYWORD = 8;            // exitCode=0 但命中失败关键词（现实现改写 -2）
    public static final int BLOCKED = 9;                // 被黑名单/语义/AI 拦截
    public static final int USER_ABORT = 10;            // 用户选择中止 / 等待中 Ctrl+C
    public static final int TIMEOUT_OR_NO_STDIN = 11;   // 超时后答"不再等待"，或无 stdin 默认中止
    /**
     * D-43（2026-10-08）：bat 前置 java 探测未找到任何 Java 21+ 运行时（三段候选全部不过版本闸门）。
     * 该码由启动器 bat 自身返回——JVM 从未启动、Main 永不产生它，故与上表不冲突；
     * 出现即意味着"装完从未启动过 IDEA 且 JAVA_HOME/PATH 只指向 Java 8/11/17"这类环境。
     */
    public static final int JAVA_RUNTIME = 12;

    private ExitCodes() {
    }
}
