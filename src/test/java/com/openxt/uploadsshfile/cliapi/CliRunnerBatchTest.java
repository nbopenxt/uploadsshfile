package com.openxt.uploadsshfile.cliapi;

import com.openxt.uploadsshfile.ExitCodes;
import com.openxt.uploadsshfile.batch.BatchTask;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * D-32 回归：runBatch 对异步 execute 的完成等待链条中，**同步可测的两段防御**——
 * ①空子任务批次短路 PARAM(2)（否则 finishedLatch.await 永挂：execute 对空任务
 *   直接 return、无任何收尾回调）；②零文件子任务入口点名拦截（D-30B）。
 * 有文件批次的真实等待（网络路径）属真机验收场景，不做自动化。
 */
public class CliRunnerBatchTest {

    @BeforeClass
    public static void injectPaths() throws Exception {
        java.nio.file.Path root = Paths.get(System.getProperty("java.io.tmpdir"), "uploadsshfile-test-inject");
        java.nio.file.Files.createDirectories(root);
        com.openxt.uploadsshfile.util.PluginPathManager.initialize(root, root);
    }

    private CliRunner runner;

    @Before
    public void setUp() {
        LanguageManager lm = LanguageManager.getInstance();
        lm.forceLanguageNoPersist("en");
        EchoGuard echo = new EchoGuard(new PrintStream(new ByteArrayOutputStream()));
        Prompter abortPrompter = question -> false; // 任何询问＝中止
        runner = new CliRunner(echo, abortPrompter, lm, null, false);
    }

    @Test(timeout = 10_000L)
    public void emptySubTaskBatchShortCircuitsInsteadOfHanging() {
        BatchTask t = new BatchTask();
        t.setName("Empty");
        t.setSubTasks(new ArrayList<>());
        // D-32：短路返回 PARAM(2)——修复前此处 await 永挂（超时即失败）
        assertEquals(ExitCodes.PARAM, runner.runBatch(t));

        BatchTask t2 = new BatchTask();
        t2.setName("NullList");
        t2.setSubTasks(null);
        assertEquals(ExitCodes.PARAM, runner.runBatch(t2));
    }

    @Test(timeout = 10_000L)
    public void zeroFileSubTaskBlockedAtEntry() {
        BatchTask t = new BatchTask();
        t.setName("NoFiles");
        java.util.List<com.openxt.uploadsshfile.batch.BatchSubTask> subs = new ArrayList<>();
        com.openxt.uploadsshfile.batch.BatchSubTask s = new com.openxt.uploadsshfile.batch.BatchSubTask();
        s.setOrder(0);
        s.setServerId("srv-x");
        s.setPathId("path-x");
        subs.add(s); // filePaths 为空列表（构造默认）
        t.setSubTasks(subs);
        // D-30B：入口点名拦截，不触网、不进编排器
        assertEquals(ExitCodes.PARAM, runner.runBatch(t));
    }

    // ===================== D-33 目标确认 =====================

    private static BatchTask taskWithFile() {
        BatchTask t = new BatchTask();
        t.setName("WithFile");
        java.util.List<com.openxt.uploadsshfile.batch.BatchSubTask> subs = new ArrayList<>();
        com.openxt.uploadsshfile.batch.BatchSubTask s = new com.openxt.uploadsshfile.batch.BatchSubTask();
        s.setOrder(0);
        s.setServerId("srv-x");
        s.setPathId("path-x");
        s.setFilePaths(java.util.List.of("whatever.txt"));
        subs.add(s);
        t.setSubTasks(subs);
        return t;
    }

    /** 未加 --yes 且 prompter 答否（＝无 stdin 默认中止口径）：确认拦截，返回 USER_ABORT */
    @Test(timeout = 10_000L)
    public void confirmationDeclineAbortsRun() {
        assertEquals(ExitCodes.USER_ABORT, runner.runBatch(taskWithFile()));
    }

    /** --yes（autoConfirm）：直入编排器——无效 serverId 子任务经既有路径转 FAILED→UPLOAD(5) 结论 */
    @Test(timeout = 20_000L)
    public void yesFlagProceedsToOrchestrator() {
        LanguageManager lm = LanguageManager.getInstance();
        lm.forceLanguageNoPersist("en");
        EchoGuard echo = new EchoGuard(new PrintStream(new ByteArrayOutputStream()));
        CliRunner auto = new CliRunner(echo, q -> true, lm, null, false, true);
        // srv-x 不存在 → executeSubTask 内部 serverNotFound → FAILED → runBatch 判 UPLOAD
        assertEquals(ExitCodes.UPLOAD, auto.runBatch(taskWithFile()));
    }
}
