package com.openxt.uploadsshfile.cliapi;

import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * CLI 启动器 bat 模板（1.0.8 / D-23→D-39 反转，设计文档 §5.1/§9/§5.8 流程 H）。
 *
 * <p>D-39（2026-10-05 用户裁定）：**bat 随包发布**（zip 内插件根目录，标准包与 store 包两通道），
 * 内容永久静态、全机器字节一致——插件装/升级（含平台动态热载）后 CLI 立即可用，
 * 不再依赖"先启动一次 IDEA / 先点开一次菜单"生成。D-23"不随包＋首启生成 java.home 实值"
 * 裁定就此反转；D-31 入口自愈保留，但仅作误删/篡改兜底（期望 MD5 恒等静态模板字节，
 * dist 基准文件与内存模板由测试钉死一致）。
 *
 * <p>java 定位三段探测（纯 ASCII bat，零实值）：
 * <ol>
 *   <li>{@code java-home.txt}（bat 旁数据文件，IDE 进程在 appStarted/入口兜底时写入其
 *       自带 JBR 的 java.exe 绝对路径；仅纯 ASCII 路径可写，非 ASCII＝删除回落）；</li>
 *   <li>{@code %JAVA_HOME%\bin\java.exe}；</li>
 *   <li>PATH 上的 {@code java}（版本需 21+，README 双语已注）。装/升级后 IDE 从未启动过
 *       且无 JAVA_HOME 的窗口期依赖 ②③——原 RISK-18 的"bat 不存在"由此收敛为
 *       "bat 恒在、java 靠回落链"。</li>
 * </ol>
 *
 * <p>配置目录推导保留 {@code %~dp0..\\..\\uploadsshfile}（§5.1/R34 唯一可靠路线），
 * 以 {@code --config-dir} 显式传给 Main。
 */
public final class BatScriptTemplate {

    public static final String FILE_NAME = "uploadsshfile-cli.bat";
    /** D-39：bat 旁 java 探测数据文件（IDE 进程写、bat 读；内容单行＝java.exe 绝对路径，纯 ASCII） */
    public static final String JAVA_HOME_FILE_NAME = "java-home.txt";
    public static final String MAIN_CLASS = "com.openxt.uploadsshfile.cli.Main";

    private BatScriptTemplate() {
    }

    /**
     * 静态模板全文（D-39：不再收 javaHome 参数——字节全机器一致，方可随包）。
     * CRLF 行尾；逐字节等于仓根 {@code dist/uploadsshfile-cli.bat}（测试钉死）。
     */
    public static String content() {
        StringBuilder sb = new StringBuilder();
        sb.append("@echo off\r\n");
        sb.append("rem UploadSSHFile CLI launcher -- shipped inside the plugin package (static content, byte-identical everywhere).\r\n");
        sb.append("rem Do not edit: the plugin integrity check restores this exact file (missing/tampered/stale).\r\n");
        sb.append("rem Java resolution order: 1) java-home.txt beside this bat (written by the IDEA process, its own JBR)\r\n");
        sb.append("rem                      2) %JAVA_HOME%\\bin\\java.exe  3) java on PATH (JDK 21+ required).\r\n");
        sb.append("rem lib = %~dp0lib ; IDEA config dir derived from %~dp0.\r\n");
        sb.append("setlocal\r\n");
        sb.append("set \"JAVA=\"\r\n");
        sb.append("if exist \"%~dp0java-home.txt\" set /p JAVA=<\"%~dp0java-home.txt\"\r\n");
        sb.append("if not defined JAVA if defined JAVA_HOME if exist \"%JAVA_HOME%\\bin\\java.exe\" set \"JAVA=%JAVA_HOME%\\bin\\java.exe\"\r\n");
        sb.append("if not defined JAVA set \"JAVA=java\"\r\n");
        sb.append("set \"CFG=%~dp0..\\..\\uploadsshfile\"\r\n");
        sb.append("\"%JAVA%\" -cp \"%~dp0lib\\*\" ").append(MAIN_CLASS).append(" --config-dir \"%CFG%\" %*\r\n");
        sb.append("set \"EC=%ERRORLEVEL%\"\r\n");
        // D-29c（用户裁定 2026-10-04）：默认成功不 pause（CLI＝全自动场景，人工干预走 GUI）；
        // 仅失败 pause 供查看；--keep-open＝人工显式要求停留（由 Main 自己等回车，bat 不重复 pause）；
        // NO_PAUSE 环境变量绝对覆盖（设置后任何情形不 pause）
        sb.append("echo %* | findstr /c:\"--keep-open\" >nul 2>&1 && set \"KEEP=1\"\r\n");
        sb.append("if not defined NO_PAUSE if not \"%EC%\"==\"0\" if not defined KEEP pause\r\n");
        sb.append("exit /b %EC%\r\n");
        return sb.toString();
    }

    /** 期望 MD5（静态模板运行时推导——模板改则期望值自动跟随；D-39 起与打包内 dist 文件同源字节） */
    public static String expectedMd5() {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(content().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    /** 磁盘 bat 字节 MD5（null＝文件不存在） */
    public static String actualMd5(java.nio.file.Path batFile) {
        if (batFile == null || !java.nio.file.Files.exists(batFile)) {
            return null;
        }
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(java.nio.file.Files.readAllBytes(batFile));
            return HexFormat.of().withUpperCase().formatHex(digest);
        } catch (Exception e) {
            return ""; // 读失败＝不合法，触发重生成
        }
    }

    /** java-home.txt 内容单行（CRLF 行尾，set /p 读取语义）；仅纯 ASCII 路径允许落盘 */
    public static String javaHomeFileContent(String javaExePath) {
        return javaExePath + "\r\n";
    }

    public static boolean isPureAscii(String s) {
        if (s == null) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) < 32 || s.charAt(i) > 126) {
                return false;
            }
        }
        return true;
    }
}
