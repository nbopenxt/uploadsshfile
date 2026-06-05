package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.model.UnifiedPluginConfig.BlacklistConfig;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.*;

/**
 * BlacklistMerger 单元测试
 * UT-16: OS Key 合并去重，B 独有 Key 保留
 */
public class BlacklistMergerTest {

    // ========== UT-16: OS Key 合并去重 ==========

    @Test
    public void testOsKeyMergeWithDedup() {
        BlacklistMerger merger = new BlacklistMerger();
        RemapContext ctx = new RemapContext();

        BlacklistConfig exported = new BlacklistConfig();
        // 模拟导出的黑名单数据
        exported.getOsSpecific().put("linux", new ArrayList<>(List.of("rm -rf /", "mkfs", "shutdown")));

        BlacklistConfig current = new BlacklistConfig();
        // 模拟当前的默认黑名单
        current.getOsSpecific().put("linux", new ArrayList<>(List.of("rm -rf /", "dd if=")));

        BlacklistConfig result = merger.merge(exported, current, ctx);

        // 去重合并: rm -rf / 出现两次，只保留一次
        List<String> merged = result.getOsSpecific().get("linux");
        assertNotNull(merged);
        assertTrue(merged.contains("rm -rf /"));
        assertTrue(merged.contains("mkfs"));
        assertTrue(merged.contains("shutdown"));
        assertTrue(merged.contains("dd if=")); // B 独有的保留
    }

    @Test
    public void testBsUniqueKeysPreserved() {
        BlacklistMerger merger = new BlacklistMerger();
        RemapContext ctx = new RemapContext();

        BlacklistConfig exported = new BlacklistConfig();
        exported.getOsSpecific().put("linux", new ArrayList<>(List.of("cmd-A")));

        BlacklistConfig current = new BlacklistConfig();
        // B 有 windows 黑名单，A 没有
        current.getOsSpecific().put("linux", new ArrayList<>(List.of("cmd-B")));
        current.getOsSpecific().put("windows", new ArrayList<>(List.of("format", "del /f")));

        BlacklistConfig result = merger.merge(exported, current, ctx);

        // B 的 windows 黑名单不受影响
        assertNotNull(result.getOsSpecific().get("windows"));
        assertTrue(result.getOsSpecific().get("windows").contains("format"));
        assertTrue(result.getOsSpecific().get("windows").contains("del /f"));

        // linux 合并了
        List<String> linuxMerged = result.getOsSpecific().get("linux");
        assertTrue(linuxMerged.contains("cmd-A"));
        assertTrue(linuxMerged.contains("cmd-B"));
    }

    @Test
    public void testDangerousPathsMerge() {
        BlacklistMerger merger = new BlacklistMerger();
        RemapContext ctx = new RemapContext();

        BlacklistConfig exported = new BlacklistConfig();
        exported.getOsSpecificPaths().put("linux", new ArrayList<>(List.of("/etc", "/boot")));

        BlacklistConfig current = new BlacklistConfig();
        current.getOsSpecificPaths().put("linux", new ArrayList<>(List.of("/sys", "/proc")));

        BlacklistConfig result = merger.merge(exported, current, ctx);

        List<String> merged = result.getOsSpecificPaths().get("linux");
        assertTrue(merged.contains("/etc"));
        assertTrue(merged.contains("/boot"));
        assertTrue(merged.contains("/sys"));
        assertTrue(merged.contains("/proc"));
    }

    @Test
    public void testEmptyExportDoesNothing() {
        BlacklistMerger merger = new BlacklistMerger();
        RemapContext ctx = new RemapContext();

        // 构造"空"导出配置：清空构造函数填充的默认值
        BlacklistConfig exported = new BlacklistConfig();
        exported.getOsSpecific().clear();
        exported.getOsSpecificPaths().clear();

        BlacklistConfig current = new BlacklistConfig();
        current.getOsSpecific().clear();
        current.getOsSpecificPaths().clear();
        current.getOsSpecific().put("linux", new ArrayList<>(List.of("original-cmd")));

        BlacklistConfig result = merger.merge(exported, current, ctx);

        // B 不变
        assertEquals(List.of("original-cmd"), result.getOsSpecific().get("linux"));
    }
}
