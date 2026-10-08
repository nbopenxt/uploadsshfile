package com.openxt.uploadsshfile.cliapi;

import com.openxt.uploadsshfile.ExitCodes;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * D-38 单任务多文件（关窗快照清单）回归——同步可测面：
 * ①D-33 目标确认对空/非空清单都成型且拒答即 USER_ABORT（不触网不抢锁）；
 * ②清单行 "(k file[s])" 新口径（原单文件 file=名 作废）；
 * ③空清单在 runSingleLocked 入口即 PARAM（早于密码/网络）；
 * ④未知 serverId＝PARAM 点名。真实 SFTP 上传属真机验收（AC-06~09）。
 */
public class CliRunnerSingleTest {

    @BeforeClass
    public static void injectPaths() throws Exception {
        java.nio.file.Path root = Paths.get(System.getProperty("java.io.tmpdir"), "uploadsshfile-test-inject");
        java.nio.file.Files.createDirectories(root);
        com.openxt.uploadsshfile.util.PluginPathManager.initialize(root, root);
    }

    private ByteArrayOutputStream sink;
    private PrintStream ps;

    @Before
    public void setUp() {
        LanguageManager lm = LanguageManager.getInstance();
        lm.forceLanguageNoPersist("en");
        sink = new ByteArrayOutputStream();
        ps = new PrintStream(sink);
    }

    private CliRunner runner(boolean autoConfirm, boolean answerYes) {
        LanguageManager lm = LanguageManager.getInstance();
        lm.forceLanguageNoPersist("en");
        return new CliRunner(new EchoGuard(ps), q -> answerYes, lm, null, false, autoConfirm);
    }

    private CliRunner.SingleContext ctx(List<String> files) {
        CliRunner.SingleContext c = new CliRunner.SingleContext();
        c.serverId = "srv-nonexistent";
        c.pathId = "path-nonexistent";
        c.commandConfigId = null;
        c.timing = "MANUAL";
        c.filePaths = files;
        c.taskIdForLock = "7000000000000000001";
        return c;
    }

    @Test(timeout = 10_000L)
    public void declineAtConfirmationAbortsEvenWithFiles() {
        // 拒答确认＝USER_ABORT，且清单行已回显（k files 新口径；null 清单不炸）
        assertEquals(ExitCodes.USER_ABORT, runner(false, false).runSingle(ctx(null)));
        String out = new String(sink.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("清单行须含 (0 file[s]) 口径: " + out, out.contains("(0 file[s])"));
    }

    @Test(timeout = 10_000L)
    public void targetsLineListsFileCountForSnapshot() {
        CliRunner.SingleContext c = ctx(List.of("E:\\a.txt", "E:\\b.txt"));
        assertEquals(ExitCodes.PARAM, runner(true, true).runSingle(c));
        String out = new String(sink.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue("清单行须含 (2 file[s]): " + out, out.contains("(2 file[s])"));
        // autoConfirm 过确认后走锁+runSingleLocked：srv-nonexistent 不存在＝PARAM 点名
        assertTrue(out.contains("server config not found for id: srv-nonexistent"));
    }

    @Test(timeout = 10_000L)
    public void emptySnapshotFilesRejectedAtEntry() {
        // 构造真实存在的服务器/路径，把执行流推进到"清单空"判据（早于密码/网络）
        UnifiedConfigStore store = UnifiedConfigStore.getInstance();
        ServerConfig s = new ServerConfig();
        s.setId("srv-snap-empty");
        s.setHost("invalid.invalid");
        s.setUsername("u");
        PathConfig p = new PathConfig();
        p.setId("path-snap-empty");
        p.setServerId(s.getId());
        p.setRemotePath("/tmp/x");
        store.addServer(s);
        store.addPath(p);
        try {
            CliRunner.SingleContext c = ctx(new ArrayList<>());
            c.serverId = s.getId();
            c.pathId = p.getId();
            assertEquals(ExitCodes.PARAM, runner(true, true).runSingle(c));
            String out = new String(sink.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            assertTrue("应点名快照无文件: " + out, out.contains("single-task snapshot has no saved files"));
        } finally {
            store.deleteServer(s.getId()); // 级联删其路径
        }
    }
}
