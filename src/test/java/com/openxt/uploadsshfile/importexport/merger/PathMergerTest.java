package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.config.PathConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * PathMerger 单元测试
 * UT-12: serverId 重映射后 remotePath 匹配
 */
public class PathMergerTest {

    // ========== UT-12: serverId 重映射后 remotePath 匹配 ==========

    @Test
    public void testMatchAfterServerIdRemap() {
        PathMerger merger = new PathMerger();
        RemapContext ctx = new RemapContext();

        // 模拟 ServerMerger 已注册映射
        ctx.putServer("old-srv-id", "new-srv-id");

        // 导出路径（使用旧 serverId）
        List<PathConfig> exported = new ArrayList<>();
        PathConfig expPath = new PathConfig();
        expPath.setId("old-path-id");
        expPath.setServerId("old-srv-id"); // 旧 serverId
        expPath.setRemotePath("/opt/app");
        exported.add(expPath);

        // 当前路径（使用新 serverId）
        List<PathConfig> current = new ArrayList<>();
        PathConfig curPath = new PathConfig();
        String curId = curPath.getId();
        curPath.setServerId("new-srv-id"); // 新 serverId
        curPath.setRemotePath("/opt/app"); // 相同 remotePath
        current.add(curPath);

        List<PathConfig> result = merger.merge(exported, current, ctx);

        // 应视为同一路径 → 覆盖
        assertEquals(1, result.size());
        assertEquals(curId, result.get(0).getId()); // 保留 B 的 ID
        assertEquals("new-srv-id", result.get(0).getServerId());
        assertEquals("/opt/app", result.get(0).getRemotePath());

        // 验证 ID 映射
        assertEquals(curId, ctx.resolvePathId("old-path-id"));

        // 验证统计
        assertEquals(0, merger.getMergeCounts().getAdded());
        assertEquals(1, merger.getMergeCounts().getUpdated());
    }

    @Test
    public void testServerNotMatchedSkips() {
        PathMerger merger = new PathMerger();
        RemapContext ctx = new RemapContext();
        // 未注册 serverId 映射

        List<PathConfig> exported = new ArrayList<>();
        PathConfig expPath = new PathConfig();
        expPath.setId("path-1");
        expPath.setServerId("unmatched-server"); // 无映射
        expPath.setRemotePath("/tmp");
        exported.add(expPath);

        List<PathConfig> current = new ArrayList<>();

        List<PathConfig> result = merger.merge(exported, current, ctx);

        // 服务器未匹配 → 跳过
        assertEquals(0, result.size());
        assertEquals(0, merger.getMergeCounts().getAdded());
        assertEquals(1, merger.getMergeCounts().getSkipped());
    }

    @Test
    public void testAddNewPath() {
        PathMerger merger = new PathMerger();
        RemapContext ctx = new RemapContext();
        ctx.putServer("old-srv", "new-srv");

        List<PathConfig> exported = new ArrayList<>();
        PathConfig expPath = new PathConfig();
        expPath.setId("exp-path-new");
        expPath.setServerId("old-srv");
        expPath.setRemotePath("/var/www");
        exported.add(expPath);

        // 当前没有匹配的路径
        List<PathConfig> current = new ArrayList<>();
        PathConfig otherPath = new PathConfig();
        otherPath.setId("other-id");
        otherPath.setServerId("new-srv");
        otherPath.setRemotePath("/opt/app"); // 不同路径
        current.add(otherPath);

        List<PathConfig> result = merger.merge(exported, current, ctx);

        // 应新增
        assertEquals(2, result.size());
        PathConfig added = result.get(1);
        assertNotEquals("exp-path-new", added.getId()); // 新 UUID
        assertEquals("new-srv", added.getServerId()); // 使用重映射后的 serverId
        assertEquals("/var/www", added.getRemotePath());

        assertEquals(1, merger.getMergeCounts().getAdded());
    }

    @Test
    public void testSameRemotePathDifferentServer() {
        PathMerger merger = new PathMerger();
        RemapContext ctx = new RemapContext();
        ctx.putServer("old-srv-1", "new-srv-1");
        ctx.putServer("old-srv-2", "new-srv-2");

        List<PathConfig> exported = new ArrayList<>();
        PathConfig expPath = new PathConfig();
        expPath.setId("exp-p1");
        expPath.setServerId("old-srv-2");
        expPath.setRemotePath("/same/path");
        exported.add(expPath);

        List<PathConfig> current = new ArrayList<>();
        PathConfig curPath = new PathConfig();
        curPath.setId("cur-p1");
        curPath.setServerId("new-srv-1"); // 不同 server
        curPath.setRemotePath("/same/path"); // 相同路径名
        current.add(curPath);

        List<PathConfig> result = merger.merge(exported, current, ctx);

        // 不同服务器 → 不同路径 → 新增
        assertEquals(2, result.size());
        assertEquals(1, merger.getMergeCounts().getAdded());
    }
}
