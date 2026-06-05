package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.model.UnifiedPluginConfig.CommandOutputConfig;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * HasOutputCommandMerger 单元测试
 * UT-18: OS Key 合并去重
 */
public class HasOutputCommandMergerTest {

    // ========== UT-18: OS Key 合并去重 ==========

    @Test
    public void testOsKeyMergeDedup() {
        HasOutputCommandMerger merger = new HasOutputCommandMerger();
        RemapContext ctx = new RemapContext();

        CommandOutputConfig exported = new CommandOutputConfig();
        exported.getLinux().clear();
        exported.getLinux().addAll(List.of("docker", "kubectl", "custom-tool"));

        CommandOutputConfig current = new CommandOutputConfig();
        // 默认已经有很多命令（包括 docker）

        CommandOutputConfig result = merger.merge(exported, current, ctx);

        // docker 去重，custom-tool 新增
        List<String> linuxCmds = result.getLinux();
        assertTrue(linuxCmds.contains("docker")); // 保留（去重）
        assertTrue(linuxCmds.contains("kubectl"));
        assertTrue(linuxCmds.contains("custom-tool")); // 新增
    }

    @Test
    public void testWindowsOsKeyMerge() {
        HasOutputCommandMerger merger = new HasOutputCommandMerger();
        RemapContext ctx = new RemapContext();

        CommandOutputConfig exported = new CommandOutputConfig();
        exported.getWindows().clear();
        exported.getWindows().addAll(List.of("custom-win-cmd"));

        CommandOutputConfig current = new CommandOutputConfig();

        CommandOutputConfig result = merger.merge(exported, current, ctx);

        List<String> winCmds = result.getWindows();
        assertTrue(winCmds.contains("cd")); // 默认保留
        assertTrue(winCmds.contains("custom-win-cmd"));
    }

    @Test
    public void testNullExportedReturnsCurrent() {
        HasOutputCommandMerger merger = new HasOutputCommandMerger();
        RemapContext ctx = new RemapContext();

        CommandOutputConfig current = new CommandOutputConfig();
        int originalSize = current.getLinux().size();

        CommandOutputConfig result = merger.merge(null, current, ctx);
        assertEquals(originalSize, result.getLinux().size());
    }

    @Test
    public void testAllOsKeysCovered() {
        HasOutputCommandMerger merger = new HasOutputCommandMerger();
        RemapContext ctx = new RemapContext();

        CommandOutputConfig exported = new CommandOutputConfig();
        CommandOutputConfig current = new CommandOutputConfig();

        CommandOutputConfig result = merger.merge(exported, current, ctx);

        // 所有 9 个 OS Key 都有非空列表
        assertFalse(result.getLinux().isEmpty());
        assertFalse(result.getWindows().isEmpty());
        assertFalse(result.getMacos().isEmpty());
        // ubuntu/debian/centos/rocky/alpine/rhel 可能为空（默认如此），但存在
        assertNotNull(result.getUbuntu());
        assertNotNull(result.getDebian());
    }
}
