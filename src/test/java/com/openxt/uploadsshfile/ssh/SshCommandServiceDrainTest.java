package com.openxt.uploadsshfile.ssh;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 * D-35 回归：drainPendingExitCode——弹窗等输入期间命令已结束的收尾路径。
 * （2026-10-04 AC-22 首跑实证：sleep 90 于答 y 前已完成、__EXIT_CODE__ 字节积压未读
 * →错过退出→误判失败；修复＝答后先排空 available 字节并整体扫描标记。）
 */
public class SshCommandServiceDrainTest {

    @Test
    public void detectsMarkerInPendingBytes() throws Exception {
        SshCommandService svc = new SshCommandService();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        String pending = "Copied: a.txt -> a_20261004.txt\r\n__EXIT_CODE__=0\r\n";
        ByteArrayInputStream in = new ByteArrayInputStream(pending.getBytes(StandardCharsets.UTF_8));

        assertEquals("应检出积压的退出码 0", 0, svc.drainPendingExitCode(in, baos));
        assertEquals("字节须被消费干净", 0, in.available());
    }

    @Test
    public void returnsMinusOneWithoutMarker() throws Exception {
        SshCommandService svc = new SshCommandService();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream("still working...".getBytes(StandardCharsets.UTF_8));

        assertEquals(-1, svc.drainPendingExitCode(in, baos));
        // 无标记时字节仍已入 baos（下次扫描/最终解码不丢输出）
        assertEquals("still working...", baos.toString("UTF-8"));
    }

    /** 标记被分包边界劈开：两次 drain 靠 baos 累积仍能检出（读流循环每 50ms 一轮的真实形态） */
    @Test
    public void markerSplitAcrossChunksDetected() throws Exception {
        SshCommandService svc = new SshCommandService();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ByteArrayInputStream first = new ByteArrayInputStream("out line\n__EXIT_CO".getBytes(StandardCharsets.UTF_8));
        assertEquals(-1, svc.drainPendingExitCode(first, baos));

        ByteArrayInputStream second = new ByteArrayInputStream("DE__=7\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("跨 chunk 拼接后应检出 7", 7, svc.drainPendingExitCode(second, baos));
    }
}
