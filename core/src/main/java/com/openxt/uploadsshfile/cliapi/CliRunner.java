package com.openxt.uploadsshfile.cliapi;

import com.openxt.uploadsshfile.ExitCodes;
import com.openxt.uploadsshfile.ai.AICommandChecker;
import com.openxt.uploadsshfile.ai.AIResultChecker;
import com.openxt.uploadsshfile.batch.BatchExecutionListener;
import com.openxt.uploadsshfile.batch.BatchExecutionOrchestrator;
import com.openxt.uploadsshfile.batch.BatchSubTask;
import com.openxt.uploadsshfile.batch.BatchSubTaskResult;
import com.openxt.uploadsshfile.batch.BatchTask;
import com.openxt.uploadsshfile.config.ConfigManager;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.logging.DailyLogService;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.ExecuteTiming;
import com.openxt.uploadsshfile.model.SshConnection;
import com.openxt.uploadsshfile.sftp.SftpException;
import com.openxt.uploadsshfile.util.Md5Checksum;
import com.openxt.uploadsshfile.sftp.SftpService;
import com.openxt.uploadsshfile.ssh.SshCommandService;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.sync.LockInfo;
import com.openxt.uploadsshfile.sync.UploadLockManager;
import com.openxt.uploadsshfile.validation.BlacklistValidator;
import com.openxt.uploadsshfile.validation.KeywordMatcher;
import com.openxt.uploadsshfile.validation.SemanticBlacklistChecker;

import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * CLI 编排入口（1.0.8 / FR-10/FR-11/FR-12，设计文档 §1/§3.1/§3.3/§5.2）。
 *
 * <p>职责：取配置 → 上传+校验 → 命令组管线（**复用** core 既有服务与六段管线，
 * 判定口径与 GUI 一致 G3/AC-09），输出全英文 ASCII（AXIOM-B）+ UTF-8 日志（DailyLogService 同源）。
 * 不做：参数解析/任务定位（cli/Main、TaskResolver 负责，已解析完的上下文传入）；
 * 服务器分片锁（M4 接 UploadLockManager，本类预留 tryLock/unlock 调用点在 runSingle/runBatch 开头注释）。
 *
 * <p>单任务 timing 语义（G3）：lastSuccessfulTiming＝AUTO 才执行命令组、MANUAL 不执行——
 * 与 GUI 对话框用户选择同口径；命令组为空/id 空＝跳过。
 */
public class CliRunner {

    /** 单任务执行上下文（cli/TaskResolver 定位后构造） */
    public static final class SingleContext {
        public String serverId;
        public String pathId;
        public String commandConfigId;   // 可 null（GUI 曾"不选命令组"成功）
        public String timing;            // MANUAL/AUTO 字符串（不透明透传，判 AUTO 才执行命令）
        public File file;                // --file 展开后的绝对路径
        public String taskIdForLock;     // .info 展示用（锁占用回显"谁在持锁"）
    }

    private final UnifiedConfigStore store;
    private final ConfigManager configManager;
    private final EchoGuard echo;
    private final Prompter prompter;
    private final LanguageManager lang;
    private final DailyLogService logService;
    private final boolean verbose;

    public CliRunner(EchoGuard echo, Prompter prompter, LanguageManager lang,
                     DailyLogService logService, boolean verbose) {
        this.store = UnifiedConfigStore.getInstance();
        this.configManager = ConfigManager.getInstance();
        this.echo = echo;
        this.prompter = prompter;
        this.lang = lang;
        this.logService = logService;
        this.verbose = verbose;
    }

    // =================== 单任务 ===================

    public int runSingle(SingleContext ctx) {
        // M4 / R50+R42：GUI 与 CLI 共用同一把 serverId 分片锁；抢不到＝无上限排队，
        // 每秒回显持锁方（任务 ID/PID/已等秒）；中断/EOF 视为用户放弃→USER_ABORT(10)
        UploadLockManager.Handle lock;
        try {
            lock = acquireOrWait(ctx.serverId, ctx.taskIdForLock);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ExitCodes.USER_ABORT;
        } catch (java.io.IOException e) {
            // 锁基础设施故障（目录不可写/路径未初始化）＝UNKNOWN(3)，§7 口径；不得伪装成占用死等
            return fail(ExitCodes.UNKNOWN, "lock subsystem error: " + e.getMessage());
        }
        if (lock == null) {
            echo.println("Aborted while waiting for server lock");
            return ExitCodes.USER_ABORT;
        }
        try {
            return runSingleLocked(ctx);
        } finally {
            lock.close(); // RISK-15：try/finally 单一出口释放
        }
    }

    private int runSingleLocked(SingleContext ctx) {
        ServerConfig server = configManager.getServer(ctx.serverId);
        if (server == null) {
            return fail(ExitCodes.PARAM, "server config not found for id: " + ctx.serverId);
        }
        PathConfig path = findPath(ctx.serverId, ctx.pathId);
        if (path == null) {
            return fail(ExitCodes.PARAM, "path config not found for id: " + ctx.pathId);
        }
        if (ctx.file == null || !ctx.file.exists()) {
            return fail(ExitCodes.PARAM, "file not found: " + ctx.file);
        }
        String password = configManager.getPassword(ctx.serverId);
        if (password == null || password.isEmpty()) {
            return fail(ExitCodes.UNKNOWN, "no stored password for server (save it in IDEA once first)");
        }

        // —— 上传+校验（与 UploadAction 成功路径同序同口径） ——
        SftpService sftp = new SftpService();
        int uploadCode;
        try {
            sftp.connect(server, password);
            uploadCode = doUploadAndVerify(sftp, server, path, ctx.file);
        } catch (SftpException e) {
            return fail(ExitCodes.CONNECT, "connect/upload failed: " + e.getMessage());
        } catch (Exception e) {
            return fail(ExitCodes.UNKNOWN, "unexpected: " + e.getMessage());
        } finally {
            try { sftp.disconnect(); } catch (Exception ignore) { }
        }
        if (uploadCode != ExitCodes.OK) {
            return uploadCode; // 文件失败即止（U-01：不落盘 ID 由 Main 成功路径控制）
        }

        // —— 命令组（timing==AUTO 且 id 非空才执行；G3 同口径） ——
        return runCommandGroupIfAuto(ctx.serverId, ctx.commandConfigId, ctx.timing, server, password);
    }

    /**
     * 无上限排队（R42）：每 1s 回显一行持锁方信息（U-05 双条件提示陈旧）。
     * @return 抢到的句柄；null＝用户放弃（EOF/中断；Ctrl+C 走进程终止+OS 释放，退出码由 OS 定）
     */
    private UploadLockManager.Handle acquireOrWait(String serverId, String taskId)
            throws InterruptedException, java.io.IOException {
        long start = System.currentTimeMillis();
        while (true) {
            java.util.Optional<UploadLockManager.Handle> h =
                    UploadLockManager.getInstance().tryLock(serverId, taskId);
            if (h.isPresent()) {
                if (start != System.currentTimeMillis() - 1000) {
                    echo.println("Lock acquired after " + (System.currentTimeMillis() - start) / 1000 + "s");
                }
                return h.get();
            }
            LockInfo info = UploadLockManager.getInstance().readInfo(serverId);
            String who = info == null ? "unknown holder"
                    : "task=" + (info.taskId == null || info.taskId.isEmpty() ? "?" : info.taskId)
                    + " PID=" + info.pid
                    + (info.holderLooksAlive() ? "" : " (stale: holder process gone)");
            echo.println("Waiting for " + serverId + " (" + who + ") ... waited "
                    + ((System.currentTimeMillis() - start) / 1000) + "s");
            Thread.sleep(1000);
            if (Thread.currentThread().isInterrupted()) {
                return null;
            }
        }
    }

    // =================== 批处理任务 ===================

    public int runBatch(BatchTask task) {
        // M4 接线点：批处理按子任务逐台抢放锁（U-02）——BatchExecutionOrchestrator 内部子任务序列前接 UploadLockManager

        CliBatchListener listener = new CliBatchListener();
        BatchExecutionOrchestrator orchestrator = new BatchExecutionOrchestrator();
        orchestrator.setListener(listener);
        echo.println("== Batch task: " + ascii(task.getName()) + " ("
                + (task.getSubTasks() == null ? 0 : task.getSubTasks().size()) + " sub-task[s]) ==");
        orchestrator.execute(task);

        List<BatchSubTaskResult> results = listener.results;
        if (results.isEmpty()) {
            return fail(ExitCodes.UNCLASSIFIED, "batch produced no results");
        }
        for (BatchSubTaskResult r : results) {
            if (r.getStatus() != null && !r.getStatus().name().equals("SUCCESS")) {
                // 批处理结论映射：沿用子任务错误类别语义，无法细分时取 UPLOAD(5)
                String msg = r.getErrorMessage() == null ? "" : r.getErrorMessage();
                echo.println("Sub-task failed: " + ascii(r.getTaskDescription()) + " - " + ascii(msg));
                return ExitCodes.UPLOAD;
            }
        }
        echo.println("== Batch task completed: all " + results.size() + " sub-task(s) success ==");
        return ExitCodes.OK;
    }

    /** 批执行 CLI 监听：英文步骤行 + 结果收集（询问点沿用现状——批处理无 ask 语义，§3.2 补定 #3） */
    private final class CliBatchListener implements BatchExecutionListener {
        final java.util.List<BatchSubTaskResult> results = new java.util.ArrayList<>();

        @Override
        public void onBatchStart(BatchTask task, int totalSubTasks) {
        }

        @Override
        public void onSubTaskStart(BatchSubTask subTask, int index, int total) {
            echo.println("[" + index + "/" + total + "] sub-task start");
        }

        @Override
        public void onUploadProgress(String fileName, int percent, long uploaded, long total) {
            // 批处理子任务上传进度：非 \r 刷新通道（批内多文件交错），verbose 才逐行
            if (verbose) {
                echo.println("   [progress] " + ascii(fileName) + " " + percent + "%");
            }
        }

        @Override
        public void onSubTaskCompleted(BatchSubTaskResult result) {
            results.add(result);
            echo.println("   -> " + (result.getStatus() == null ? "?" : result.getStatus().name())
                    + " (" + result.getDurationMs() + " ms)");
        }

        @Override
        public void onBatchCompleted(java.util.List<BatchSubTaskResult> all) {
        }

        @Override
        public void onBatchCancelled() {
            echo.println("== Batch cancelled ==");
        }

        @Override
        public void onLog(String message) {
            echo.println(ascii(message));
        }
    }

    // =================== 共用环节 ===================

    private int runCommandGroupIfAuto(String serverId, String cmdConfigId, String timing,
                                      ServerConfig server, String password) {
        if (cmdConfigId == null || cmdConfigId.trim().isEmpty()) {
            return ExitCodes.OK; // 无命令组（GUI 允许"不选命令组"成功）
        }
        if (timing == null || !ExecuteTiming.AUTO.name().equalsIgnoreCase(timing.trim())) {
            echo.println("-- Command group present but timing=" + timing + " (AUTO required); skipped");
            return ExitCodes.OK;
        }
        CommandConfig cmdConfig = store.getCommandConfig(cmdConfigId).orElse(null);
        if (cmdConfig == null) {
            return fail(ExitCodes.PARAM, "command config not found: " + cmdConfigId);
        }

        SshConnection connection;
        try {
            connection = SshConnection.fromServerConfig(server, password);
        } catch (Exception e) {
            return fail(ExitCodes.CONNECT, "ssh connection build failed: " + e.getMessage());
        }

        CliExecutionListener cliListener = new CliExecutionListener(echo,
                logService == null ? null : logPrintSink());
        CliCommandOrchestrator orchestrator = new CliCommandOrchestrator(
                new SshCommandService(),
                new BlacklistValidator(),
                new KeywordMatcher(),
                new SemanticBlacklistChecker(),
                new AICommandChecker(),
                new AIResultChecker(),
                logService,
                prompter, echo, lang);
        orchestrator.executeQueue(cmdConfig, connection, cliListener);
        int code = cliListener.getExitCode();
        if (code != ExitCodes.OK) {
            echo.println("== Command group result: exit " + code + " ==");
        }
        return code;
    }

    /** 上传+按 OS 分支校验（Linux MD5 / Windows 尺寸），与 GUI 同口径；校验失败询问是否继续 */
    private int doUploadAndVerify(SftpService sftp, ServerConfig server, PathConfig path, File file) {
        boolean windows = server.getOsType() != null
                && server.getOsType().trim().toLowerCase().startsWith("windows");
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger total = new AtomicInteger();
        AtomicLong doneBytes = new AtomicLong();
        long fileTotalBytes = file.isDirectory() ? safeDirSize(file) : file.length();
        String remoteDir = path.getRemotePath();

        SftpService.UploadProgressCallback cb = new SftpService.UploadProgressCallback() {
            @Override
            public void onProgress(String fileName, int percent, long uploaded, long totalBytes) {
                if (ConsoleCaps.hasConsole()) {
                    echo.progress(String.format("[%s] [%s%s] %d%% %d/%d bytes",
                            ascii(fileName),
                            "#".repeat(Math.max(0, percent / 10)),
                            "-".repeat(Math.max(0, 10 - percent / 10)),
                            percent, uploaded, totalBytes));
                } // 无控制台降级：仅 onFileCompleted 行（D-24）
            }

            @Override
            public void onFileCompleted(String fileName, long size, boolean success, String errorMessage) {
                total.incrementAndGet();
                if (success) {
                    ok.incrementAndGet();
                    doneBytes.addAndGet(size);
                    echo.println("[OK] " + ascii(fileName) + " (" + size + " bytes)");
                } else {
                    echo.println("[FAIL] " + ascii(fileName) + ": " + ascii(errorMessage));
                }
            }
        };

        try {
            // 1.0.8/FR-16：目录属性勾选→上传前逐级创建（与 GUI/批处理同口径，G3）
            if (path.isAutoCreateRemoteDir()) {
                sftp.mkdirsRemote(remoteDir);
            }
            if (file.isDirectory()) {
                sftp.uploadDirectory(file, remoteDir, cb);
            } else {
                String localMd5 = windows ? null : safeMd5(file);
                sftp.uploadFile(file, remoteDir, cb);
                String remoteFilePath = remoteDir + "/" + file.getName();
                if (windows) {
                    SftpService.SizeVerifyResult v = sftp.verifyRemoteFileSize(remoteFilePath, file.length());
                    if (!v.isMatched()) {
                        return verifyBranch("size", file.getName(), v.getErrorMessage(), v.isMatched());
                    }
                } else {
                    SftpService.Md5VerifyResult v = sftp.verifyRemoteMd5(remoteFilePath, localMd5);
                    if (!v.isMatched()) {
                        return verifyBranch("MD5", file.getName(),
                                v.getErrorMessage() != null ? v.getErrorMessage() : "remote=" + v.getRemoteMd5(),
                                v.isMatched());
                    }
                }
                echo.println("[VERIFIED] " + ascii(file.getName()) + " (" + (windows ? "size" : "MD5") + ")");
            }
        } catch (SftpException e) {
            echo.println("[UPLOAD FAILED] " + ascii(e.getMessage()));
            return ExitCodes.UPLOAD;
        }

        // 目录上传逐项结果：有失败项→询问继续（GUI askUploadFailedContinue 同口径，答否按失败码 5）
        if (file.isDirectory() && ok.get() < total.get()) {
            boolean cont = askWithGuard("Uploaded " + ok.get() + "/" + total.get()
                    + " file(s) in directory; treat as success and continue? (y/n) ");
            if (!cont) {
                return ExitCodes.USER_ABORT;
            }
        }
        return ExitCodes.OK;
    }

    private int verifyBranch(String kind, String fileName, String detail, boolean matched) {
        echo.println("[VERIFY " + kind + " MISMATCH] " + ascii(fileName) + ": " + ascii(detail));
        boolean cont = askWithGuard("Integrity verification failed for " + ascii(fileName)
                + ". Continue anyway? (y/n) ");
        return cont ? ExitCodes.OK : ExitCodes.VERIFY;
    }

    private boolean askWithGuard(String q) {
        echo.freeze();
        echo.begin();
        try {
            return prompter.askYesNo(q);
        } finally {
            echo.end();
        }
    }

    private PathConfig findPath(String serverId, String pathId) {
        List<PathConfig> paths = configManager.getPathsByServer(serverId);
        if (paths != null) {
            for (PathConfig p : paths) {
                if (p.getId().equals(pathId)) {
                    return p;
                }
            }
        }
        return null;
    }

    private String safeMd5(File f) {
        try {
            return Md5Checksum.calculate(f);
        } catch (Exception e) {
            return "";
        }
    }

    private long safeDirSize(File dir) {
        long s = 0;
        File[] cs = dir.listFiles();
        if (cs != null) {
            for (File c : cs) {
                s += c.isDirectory() ? safeDirSize(c) : c.length();
            }
        }
        return s;
    }

    private PrintStream logPrintSink() {
        // 日志镜像 sink：CLI 步骤行已由 DailyLogService.log(...) 落 UTF-8 文件；
        // 此处返回把行写入 logService 的适配器（复用 info(module,msg)）
        return new PrintStream(new java.io.OutputStream() {
            final StringBuilder buf = new StringBuilder();
            @Override
            public void write(int b) {
                if (b == '\n') {
                    logService.info("CLI", buf.toString());
                    buf.setLength(0);
                } else if (b != '\r') {
                    buf.append((char) b);
                }
            }
        }, true, java.nio.charset.StandardCharsets.UTF_8);
    }

    private String ascii(String s) {
        if (s == null) {
            return "";
        }
        return CliCommandOrchestrator.asciiText(s);
    }

    private int fail(int code, String msg) {
        echo.println("[" + code + "] " + ascii(msg));
        if (logService != null) {
            logService.error("CLI", msg);
        }
        return code;
    }
}
