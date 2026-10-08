package com.openxt.uploadsshfile.cli;

/**
 * CLI 手写参数解析（1.0.8 / D-04/FR-10，设计文档 §3.1；零第三方库）。
 *
 * <p>文法：{@code run <任务ID> [--verbose] [--keep-open] [--yes] [--config-dir <绝对路径>]}。
 * 首个非旗标参数必须为 run；其后唯一非旗标＝任务 ID（不透明字符串匹配）。
 * 旗标可在任意位置出现（bat 会前置注入 --config-dir）。
 * 违规即 PARAM(2)：run 缺失/多位置参数/--config-dir 无值或重复/未知旗标。
 * D-38（2026-10-05 用户裁定）：**--file 旗标整体作废**——单任务文件清单＝GUI 关窗快照
 * （D-37，TaskResolver 按任务 ID 读取），批任务本就读子任务 filePaths；两类任务一律
 * 不接受 --file（按未知旗标拒绝，旧 D-36 片段升级后需重新复制一次——README/变更历史已注）。
 */
public final class ArgumentParser {

    public static final class Parsed {
        public String taskId;
        public boolean verbose;
        public boolean keepOpen;        // D-29c：结束前等待回车（人工查看场景；bat 层据此不再二次 pause）
        public boolean assumeYes;       // D-33：跳过执行前目标确认（构建钩子自动场景；不加则交互 y/N、无 stdin＝默认中止）
        public String configDir;        // 原样字符串（Main 规范化）
        public String error;            // 非 null＝PARAM(2) 且附用法
    }

    private ArgumentParser() {
    }

    public static Parsed parse(String[] args) {
        Parsed p = new Parsed();
        if (args == null || args.length == 0) {
            p.error = "first argument must be the 'run' subcommand";
            return p;
        }
        // D-28（2026-10-04 CLI 端到端）：run 校验从 args[0] 硬检改为"首个位置参数"——
        // bat 模板按本类文法注释的既定语义前置注入 --config-dir（BatScriptTemplate:47），
        // 原实现使 bat 路线必炸（PARAM 2）；旗标任意位置合法，位置参数恒为 run [taskId]。
        // 0 未收 run，1 run 已收未收 taskId，2 两位置参数齐
        int positional = 0;
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (a == null) {
                p.error = "null argument";
                return p;
            }
            switch (a) {
                // D-38：--file 旗标删除（落到 default 分支＝未知旗标拒绝，报错文案自明）
                case "--verbose":
                    p.verbose = true;
                    break;
                case "--keep-open":
                    p.keepOpen = true;
                    break;
                case "--yes":
                    p.assumeYes = true;
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
                    if (positional == 0) {
                        if (!"run".equals(a)) {
                            p.error = "first argument must be the 'run' subcommand";
                            return p;
                        }
                        positional = 1;
                    } else if (positional == 1) {
                        p.taskId = a;
                        positional = 2;
                    } else {
                        p.error = "too many positional arguments (expected: run <taskId>)";
                        return p;
                    }
                }
            }
        }
        if (positional == 0) {
            p.error = "first argument must be the 'run' subcommand";
            return p;
        }
        if (p.taskId == null || p.taskId.trim().isEmpty()) {
            p.error = "task id is required";
        }
        return p;
    }

    public static String usage() {
        return "Usage: uploadsshfile-cli.bat run <taskId> [--verbose] [--keep-open] [--yes] [--config-dir <path>]"
                + "  (D-38: no --file anymore; the file list comes from the task saved in the GUI)";
    }
}
