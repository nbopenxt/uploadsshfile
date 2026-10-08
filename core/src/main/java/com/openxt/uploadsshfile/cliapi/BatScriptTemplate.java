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
 *   <li>PATH 上的 {@code java}。装/升级后 IDE 从未启动过且无 JAVA_HOME 的窗口期依赖
 *       ②③——原 RISK-18 的"bat 不存在"由此收敛为"bat 恒在、java 靠回落链"。</li>
 * </ol>
 *
 * <p><b>D-43 版本闸门（2026-10-08 用户实发 JNI 错后裁定）</b：三段候选一律经
 * {@code java.specification.version} 校验（{@code :check} 子程序），&lt;21（含 1.x 形态）
 * 的候选<b>跳过而非使用</b>；①段另加文件存在性检查（IDEA 搬家/卸载致 java-home.txt 失效时
 * 原行为＝报"系统找不到指定的路径"且不回落，闸门后＝继续往下探）。三候选全不合格时以
 * 纯 ASCII 明确提示（打印当前 java-home.txt 值与 JAVA_HOME 值＋两条处置办法）并
 * {@code exit /b 12}（{@link com.openxt.uploadsshfile.ExitCodes#JAVA_RUNTIME}，
 * JVM 未启动、Main 永不产生该码），不再让使用者看到 {@code UnsupportedClassVersionError}。
 * 代价实测：每个候选一次 {@code -XshowSettings:properties -version} 探测 ≈0.1 秒（正常路径
 * 一次＝①段命中），相对 SSH/SFTP 耗时可忽略。禁"只验存在不验版本"回潮——本机
 * {@code JAVA_HOME}/{@code PATH} 默认 Java 8 时，无闸门即必现 class 65.0/52.0 报错。
 *
 * <p><b>D-44 缺记录＝只说"重启 IDEA"（2026-10-08 用户三次明令的最终口径）</b>：
 * {@code java-home.txt} 缺失或为空时只输出一行
 * {@code [UploadSSHFile CLI] Please restart IntelliJ IDEA to run this task.} 随即
 * {@code exit /b 12}。用户明令不得写入 CLI 文案的内容（不得自行加回）：①不得提
 * {@code java-home.txt}——使用者会误判为系统故障，而实情只是"IDEA 需要重启一次"；
 * ②不得写"或点开一次插件菜单"；③不得写"then run this command again"这类尾巴；
 * ④不得叠 Fix A/Fix B 长文；⑤不得继续回落 {@code JAVA_HOME}/PATH（该回落设计已被否）。
 * "点一次插件菜单也会写回记录"这一运维事实只保留在操作手册，不进 CLI。
 * 实现细节：{@code set /p} 读不到内容时 {@code JHB} 仍为 undefined，故空文件与缺失同路；
 * 分支用 {@code goto probehit} 而非 {@code (...)} 块（块内 echo 含引号/括号易踩解析坑，
 * 且整体须纯 ASCII）。
 * <b>须留档的代价</b>：我曾建议"提示后照常回落"（{@code JAVA_HOME}/PATH 本身是 21+ 的机器上
 * 缺记录并不影响运行），<b>该建议被用户否掉</b>，故此类机器在"IDEA 尚未跑过一次"的窗口内
 * CLI 由"可用"变"须先启动一次 IDEA"；缓解口径＝点开一次插件右键菜单即写回记录（四处幂等触发
 * 点，无需重启）。D-43 的三候选闸门与拦截长文仍然保留，进入条件变为"记录存在、但其中的 java
 * 不可用且回落亦不可用"。该文件之所以会消失＝安装/升级清空插件目录；无纯 bat 手段可动态得知
 * IDEA 安装位置（{@code %~dp0} 只能推出 config 目录，推不出安装目录——本机安装 D 盘、配置却被
 * {@code idea.properties} 重定向到 E 盘即为证据）。
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
        sb.append("rem                      2) %JAVA_HOME%\\bin\\java.exe  3) java on PATH.\r\n");
        sb.append("rem D-44: if the IDEA runtime record beside this bat is absent, the launcher prints one line\r\n");
        sb.append("rem - restart IntelliJ IDEA to run this task - and stops; it does not hunt for another Java.\r\n");
        sb.append("rem D-43: every candidate that is used is version-checked (Java 21+, class file 65) and skipped if older;\r\n");
        sb.append("rem if no candidate qualifies this launcher prints a plain message and exits with code 12.\r\n");
        sb.append("rem lib = %~dp0lib ; IDEA config dir derived from %~dp0.\r\n");
        sb.append("setlocal\r\n");
        sb.append("set \"JAVA=\"\r\n");
        sb.append("set \"JHB=\"\r\n");
        sb.append("if exist \"%~dp0java-home.txt\" set /p JHB=<\"%~dp0java-home.txt\"\r\n");
        // D-44（2026-10-08 用户三次明令，最终口径）：缺记录时**只说"重启 IDEA 才能执行本任务"**——
        // 不提 java-home.txt（会被当成系统故障）、不提点菜单的替代路径、不加"再执行一次"尾巴；
        // 一行提示后 exit /b 12，不探 JAVA_HOME/PATH。空文件与缺失同路（JHB 仍 undefined）。
        sb.append("if defined JHB goto probehit\r\n");
        sb.append("echo [UploadSSHFile CLI] Please restart IntelliJ IDEA to run this task.\r\n");
        sb.append("if not defined NO_PAUSE pause\r\n");
        sb.append("exit /b 12\r\n");
        sb.append(":probehit\r\n");
        sb.append("call :check \"%JHB%\"\r\n");
        sb.append("if not defined JAVA if defined JAVA_HOME if exist \"%JAVA_HOME%\\bin\\java.exe\" call :check \"%JAVA_HOME%\\bin\\java.exe\"\r\n");
        sb.append("if not defined JAVA call :check java\r\n");
        sb.append("if defined JAVA goto run\r\n");
        sb.append("echo [UploadSSHFile CLI] ERROR: no Java 21+ runtime found. Checked: java-home.txt, JAVA_HOME, PATH.\r\n");
        sb.append("echo   java-home.txt = \"%JHB%\"\r\n");
        sb.append("echo   JAVA_HOME     = \"%JAVA_HOME%\"\r\n");
        sb.append("echo   Fix A: start IntelliJ IDEA once, or open the \"Upload SSH File\" context menu once (no restart needed);");
        sb.append(" the plugin then writes java-home.txt with IDEA's own Java 21+ runtime.\r\n");
        sb.append("echo   Fix B: point JAVA_HOME to a JDK 21+ install (or put its bin first on PATH), then run again.\r\n");
        sb.append("if not defined NO_PAUSE pause\r\n");
        sb.append("exit /b 12\r\n");
        sb.append(":run\r\n");
        sb.append("set \"CFG=%~dp0..\\..\\uploadsshfile\"\r\n");
        sb.append("\"%JAVA%\" -cp \"%~dp0lib\\*\" ").append(MAIN_CLASS).append(" --config-dir \"%CFG%\" %*\r\n");
        sb.append("set \"EC=%ERRORLEVEL%\"\r\n");
        // D-29c（用户裁定 2026-10-04）：默认成功不 pause（CLI＝全自动场景，人工干预走 GUI）；
        // 仅失败 pause 供查看；--keep-open＝人工显式要求停留（由 Main 自己等回车，bat 不重复 pause）；
        // NO_PAUSE 环境变量绝对覆盖（设置后任何情形不 pause）
        sb.append("echo %* | findstr /c:\"--keep-open\" >nul 2>&1 && set \"KEEP=1\"\r\n");
        sb.append("if not defined NO_PAUSE if not \"%EC%\"==\"0\" if not defined KEEP pause\r\n");
        sb.append("exit /b %EC%\r\n");
        // D-43 版本闸门子程序：候选必须可执行且 java.specification.version>=21，否则跳过（不使用）
        sb.append(":check\r\n");
        sb.append("set \"CAND=%~1\"\r\n");
        sb.append("if not defined CAND exit /b 0\r\n");
        sb.append("if not \"%CAND%\"==\"java\" if not exist \"%CAND%\" exit /b 0\r\n");
        sb.append("set \"SPEC=\"\r\n");
        // 禁管道写法：for /f 命令集内嵌 "… 2^>^&1 ^| findstr" 在本机 cmd 实测必失败
        // （子 cmd 报"系统找不到指定的路径"且 SPEC 恒空＝所有候选被误拒）；改整段交给 for 逐行匹配
        sb.append("for /f \"tokens=1,3\" %%a in ('\"%CAND%\" -XshowSettings:properties -version 2^>^&1') "
                + "do if \"%%a\"==\"java.specification.version\" set \"SPEC=%%b\"\r\n");
        sb.append("if not defined SPEC exit /b 0\r\n");
        sb.append("set \"MAJ=\"\r\n");
        sb.append("for /f \"tokens=1 delims=.\" %%a in (\"%SPEC%\") do set \"MAJ=%%a\"\r\n");
        sb.append("if not defined MAJ exit /b 0\r\n");
        sb.append("if \"%MAJ%\"==\"1\" exit /b 0\r\n");
        sb.append("if %MAJ% LSS 21 exit /b 0\r\n");
        sb.append("set \"JAVA=%CAND%\"\r\n");
        sb.append("exit /b 0\r\n");
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
