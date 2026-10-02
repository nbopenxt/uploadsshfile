package com.openxt.uploadsshfile.cli;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Paths;

/**
 * CLI 手写参数解析（1.0.8 / D-04/FR-10，设计文档 §3.1；零第三方库）。
 *
 * <p>文法：{@code run <任务ID> [--file <单个文件或目录>] [--verbose] [--config-dir <绝对路径>]}。
 * 首个非旗标参数必须为 run；其后唯一非旗标＝任务 ID（不透明字符串匹配）。
 * 旗标可在任意位置出现（bat 会前置注入 --config-dir）。
 * 违规即 PARAM(2)：run 缺失/多位置参数/--file 出现两次或无值/--config-dir 无值或重复/未知旗标。
 * --file 对单任务必填、对批处理不得传——类型相关校验在 Main（此处只保证"至多一个"，FR-10）。
 */
public final class ArgumentParser {

    public static final class Parsed {
        public String taskId;
        public File file;               // 展开为绝对路径并 normalize（FR-11）
        public boolean fileSeen;
        public boolean verbose;
        public String configDir;        // 原样字符串（Main 规范化）
        public String error;            // 非 null＝PARAM(2) 且附用法
    }

    private ArgumentParser() {
    }

    public static Parsed parse(String[] args) {
        Parsed p = new Parsed();
        if (args == null || args.length == 0 || !isRun(args[0])) {
            p.error = "first argument must be the 'run' subcommand";
            return p;
        }
        int positional = 0; // 0 未收 taskId，1 已收，≥2 非法
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a == null) {
                p.error = "null argument";
                return p;
            }
            switch (a) {
                case "--file": {
                    if (p.fileSeen) {
                        p.error = "--file may appear at most once";
                        return p;
                    }
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        p.error = "--file requires a value";
                        return p;
                    }
                    p.fileSeen = true;
                    p.file = expand(args[++i]);
                    if (p.file == null) {
                        p.error = "--file value is not a valid path";
                        return p;
                    }
                    break;
                }
                case "--verbose":
                    p.verbose = true;
                    break;
                case "--config-dir": {
                    if (p.configDir != null) {
                        p.error = "--config-dir may appear only once";
                        return p;
                    }
                    if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                        p.error = "--config-dir requires a value";
                        return p;
                    }
                    p.configDir = args[++i];
                    break;
                }
                default: {
                    if (a.startsWith("--") || a.startsWith("-")) {
                        p.error = "unknown flag: " + a;
                        return p;
                    }
                    if (positional >= 1) {
                        p.error = "too many positional arguments (expected: run <taskId>)";
                        return p;
                    }
                    positional = 1;
                    p.taskId = a;
                }
            }
        }
        if (p.taskId == null || p.taskId.trim().isEmpty()) {
            p.error = "task id is required";
        }
        return p;
    }

    private static boolean isRun(String first) {
        return "run".equals(first);
    }

    /** 相对值按进程工作目录展开为绝对并 normalize（FR-11） */
    private static File expand(String raw) {
        try {
            return Paths.get(raw).toAbsolutePath().normalize().toFile();
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public static String usage() {
        return "Usage: uploadsshfile-cli.bat run <taskId> [--file <file-or-dir>] [--verbose] [--config-dir <path>]";
    }
}
