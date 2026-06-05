package com.openxt.uploadsshfile.importexport.merger;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * RemapContext 单元测试
 * UT-22: 四层 ID 映射表正确串联
 */
public class RemapContextTest {

    // ========== Server ID 映射 ==========

    @Test
    public void testServerIdMapping() {
        RemapContext ctx = new RemapContext();
        ctx.putServer("old-srv-1", "new-srv-1");

        assertEquals("new-srv-1", ctx.resolveServerId("old-srv-1"));
        assertNull(ctx.resolveServerId("nonexistent"));
        assertEquals(1, ctx.getServerIdMap().size());
    }

    @Test
    public void testServerIdMapIsolation() {
        RemapContext ctx = new RemapContext();
        ctx.putServer("old-a", "new-a");
        ctx.putServer("old-b", "new-b");

        assertEquals("new-a", ctx.resolveServerId("old-a"));
        assertEquals("new-b", ctx.resolveServerId("old-b"));
        assertNull(ctx.resolveServerId("old-c"));
    }

    // ========== Path ID 映射 ==========

    @Test
    public void testPathIdMapping() {
        RemapContext ctx = new RemapContext();
        ctx.putPath("old-path-1", "new-path-1");

        assertEquals("new-path-1", ctx.resolvePathId("old-path-1"));
        assertNull(ctx.resolvePathId("nonexistent"));
    }

    // ========== Command Config ID 映射 ==========

    @Test
    public void testCommandConfigIdMapping() {
        RemapContext ctx = new RemapContext();
        ctx.putCommandConfig("old-cmd-1", "new-cmd-1");

        assertEquals("new-cmd-1", ctx.resolveCommandConfigId("old-cmd-1"));
        assertNull(ctx.resolveCommandConfigId("nonexistent"));
    }

    // ========== Batch Task ID 映射 ==========

    @Test
    public void testBatchTaskIdMapping() {
        RemapContext ctx = new RemapContext();
        ctx.putBatchTask("old-batch-1", "new-batch-1");

        assertEquals("new-batch-1", ctx.resolveBatchTaskId("old-batch-1"));
        assertNull(ctx.resolveBatchTaskId("nonexistent"));
    }

    // ========== UT-22: 四层 ID 映射串联 ==========

    @Test
    public void testFourLayerMappingChain() {
        RemapContext ctx = new RemapContext();

        // 模拟导入流程中的渐进式 ID 注册
        // 步骤1: ServerMerger 注册
        ctx.putServer("exp-srv-1", "cur-srv-a");
        ctx.putServer("exp-srv-2", "cur-srv-b");

        // 步骤2: PathMerger 注册
        ctx.putPath("exp-path-1", "cur-path-x");
        ctx.putPath("exp-path-2", "cur-path-y");

        // 步骤3: CommandConfigMerger 注册
        ctx.putCommandConfig("exp-cmd-1", "cur-cmd-i");

        // 步骤4: BatchTaskMerger 注册
        ctx.putBatchTask("exp-batch-1", "cur-batch-alpha");

        // 验证四层映射互不干扰
        assertEquals("cur-srv-a", ctx.resolveServerId("exp-srv-1"));
        assertEquals("cur-path-x", ctx.resolvePathId("exp-path-1"));
        assertEquals("cur-cmd-i", ctx.resolveCommandConfigId("exp-cmd-1"));
        assertEquals("cur-batch-alpha", ctx.resolveBatchTaskId("exp-batch-1"));

        // 验证未注册的 ID 返回 null
        assertNull(ctx.resolveServerId("exp-srv-99"));
        assertNull(ctx.resolvePathId("exp-path-99"));
        assertNull(ctx.resolveCommandConfigId("exp-cmd-99"));
        assertNull(ctx.resolveBatchTaskId("exp-batch-99"));
    }

    @Test
    public void testOverwriteExistingMapping() {
        RemapContext ctx = new RemapContext();
        ctx.putServer("old-1", "new-1");
        assertEquals("new-1", ctx.resolveServerId("old-1"));

        // 覆盖映射
        ctx.putServer("old-1", "new-override");
        assertEquals("new-override", ctx.resolveServerId("old-1"));
    }
}
