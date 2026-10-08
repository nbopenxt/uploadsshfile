package com.openxt.uploadsshfile.cli;

import com.openxt.uploadsshfile.ExitCodes;
import com.openxt.uploadsshfile.cliapi.CliRunner;
import com.openxt.uploadsshfile.cliapi.ConsoleCaps;
import com.openxt.uploadsshfile.cliapi.EchoGuard;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.logging.DailyLogService;
import com.openxt.uploadsshfile.util.PluginPathManager;

import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * CLI 主入口（1.0.8 / FR-10~FR-14，设计文档 §3.1/§3.3/§5.2；类路径由插件 bat 或 java 直调给出）。
 *
 * <p>主线程单前台顺序：解析 → 定位任务 → 取配置 → 复用 core 管线 → 退出码。
 * 输出全英文 ASCII（AXIOM-B/AC-21）；日志 UTF-8 与 GUI 同源（AC-13）；
 * 禁写 plugin-config.json/secure.dat（G2——本类只读）；锁等待 M4 接入。
 */
public final class Main {

    public static void main(String[] args) {
        int code;
        try {
            code = run(args);
        } catch (Throwable t) {
            System.out.println("[1] UNCLASSIFIED failure: " + t);
            t.printStackTrace();
            code = ExitCodes.UNCLASSIFIED;
        }
        System.exit(code);
    }

    private static int run(String[] args) {
        // D-29（2026-10-04 GBK 控制台中文路径乱码实证）：AXIOM-B"输出全英文 ASCII"管不住
        // Input:/error: 等**回显用户路径**的行——写死 UTF-8 包装在 GBK 控制台上即乱码
        // （鎴戠殑閰风洏型）。跟随控制台实际编码（JEP 400：stdout.encoding，退 native，
        // 再退 UTF-8）；直写 FileDescriptor.out 避免在既有 System.out 编码上二次包装。
        String consoleEncoding = System.getProperty("stdout.encoding",
                System.getProperty("native.encoding", StandardCharsets.UTF_8.name()));
        PrintStream out;
        try {
            out = new PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out),
                    true, consoleEncoding);
        } catch (java.io.UnsupportedEncodingException fallback) {
            out = System.out;
        }

        ArgumentParser.Parsed p = ArgumentParser.parse(args);
        if (p.error != null) {
            out.println("error: " + p.error);
            out.println(ArgumentParser.usage());
            return ExitCodes.PARAM;
        }

        // —— 配置目录（D-21：CLI 由参数/生成 bat 传入；末段名为 uploadsshfile 时取父为 IDEA 配置根） ——
        if (p.configDir == null || p.configDir.trim().isEmpty()) {
            out.println("error: --config-dir is required (normally injected by uploadsshfile-cli.bat;"
                    + " when calling java directly, pass the IDEA config dir)");
            return ExitCodes.PARAM;
        }
        Path cfg;
        try {
            cfg = Paths.get(p.configDir).toAbsolutePath().normalize();
        } catch (Exception e) {
            out.println("error: --config-dir is not a valid path: " + p.configDir);
            return ExitCodes.PARAM;
        }
        Path ideaConfigRoot = cfg.getFileName() != null
                && "uploadsshfile".equalsIgnoreCase(cfg.getFileName().toString())
                ? cfg.getParent() : cfg;
        try {
            PluginPathManager.initialize(ideaConfigRoot, ideaConfigRoot);
        } catch (Throwable t) {
            out.println("error: cannot initialize config paths: " + t.getMessage());
            return ExitCodes.UNKNOWN;
        }

        // 文案固定 en（P-02/D-09），仅内存态、不写配置（G2）
        LanguageManager lm = LanguageManager.getInstance();
        lm.forceLanguageNoPersist("en");

        boolean interactive = ConsoleCaps.hasConsole();
        EchoGuard echo = new EchoGuard(out);
        if (!interactive) {
            echo.println("Non-interactive mode: prompts default to ABORT");
        }

        DailyLogService logService = null;
        try {
            logService = new DailyLogService();
        } catch (Throwable t) {
            echo.println("warning: file logging unavailable: " + t.getMessage());
        }

        // Ctrl+C/退出统一钩子（M4 接入锁释放；当前保证日志冲刷）
        final DailyLogService finalLog = logService;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                if (finalLog != null) {
                    finalLog.info("CLI", "process exiting (exit hook)");
                }
            } catch (Throwable ignore) {
            }
        }, "uploadsshfile-cli-shutdown"));

        // —— 定位任务 ——
        TaskResolver.Resolution r;
        try {
            r = TaskResolver.resolve(p.taskId);
        } catch (Throwable t) {
            out.println("error: cannot read config: " + t.getMessage());
            return ExitCodes.UNKNOWN;
        }
        if (r.error != null) {
            out.println("error: " + r.error);
            return ExitCodes.PARAM;
        }

        // D-38：--file 旗标整体作废（解析器按未知旗标拒绝）；单任务文件清单＝关窗快照（D-37）。
        // —— 单任务：逐文件回显绝对路径+大小（FR-11 口径扩展到多文件）并前置存在性检查
        //（缺失＝PARAM(2)，在抢锁/触网之前点名，防"半截上传"）——
        if (!r.isBatch) {
            for (String fp : r.single.filePaths) {
                File f = fp == null ? null : new File(fp);
                if (f == null || !f.exists()) {
                    out.println("error: saved file not found (stale snapshot entry): " + fp);
                    return ExitCodes.PARAM;
                }
                out.println("Input: " + f.getAbsolutePath() + " (" + sizeOf(f) + " bytes)");
            }
        }

        CliRunner runner = new CliRunner(echo, new ConsoleInteraction(out), lm, logService, p.verbose, p.assumeYes); // D-33：--yes

        int code;
        if (r.isBatch) {
            code = runner.runBatch(r.batchTask);
        } else {
            code = runner.runSingle(r.single);
        }

        // 末行回显日志绝对路径（AC-13；文件当日滚动）
        if (logService != null) {
            try {
                Path logDir = PluginPathManager.getInstance().getLogPath();
                String day = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                out.println("Log: " + logDir.resolve("uploadsshfile_" + day + ".log"));
            } catch (Throwable ignore) {
            }
        }
        out.println("RESULT: exit " + code);
        // D-29c（用户裁定 2026-10-04）：默认成功不 pause（全自动场景），失败由 bat 层 pause；
        // --keep-open ＝人工查看场景，本进程内等一次回车（stdin 被重定向时 read 即 EOF，不卡）
        if (p.keepOpen) {
            out.println("Press Enter to finish (--keep-open)...");
            try {
                System.in.read();
            } catch (java.io.IOException ignore) {
                // stdin 关闭/重定向＝无法交互，径直退出
            }
        }
        return code;
    }

    private static long sizeOf(File f) {
        if (f.isFile()) {
            return f.length();
        }
        long s = 0;
        File[] cs = f.listFiles();
        if (cs != null) {
            for (File c : cs) {
                s += c.isDirectory() ? sizeOf(c) : c.length();
            }
        }
        return s;
    }
}
