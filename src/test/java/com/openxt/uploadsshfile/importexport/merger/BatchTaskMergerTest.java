package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.batch.BatchSubTask;
import com.openxt.uploadsshfile.batch.BatchTask;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * BatchTaskMerger 单元测试
 * UT-14: 三组 ID 全部重映射，无悬空引用
 */
public class BatchTaskMergerTest {

    // ========== UT-14: 三组 ID 全部重映射 ==========

    @Test
    public void testFullIdRemapOnAdd() {
        BatchTaskMerger merger = new BatchTaskMerger();
        RemapContext ctx = new RemapContext();

        // 模拟前三步已建立映射
        ctx.putServer("exp-srv", "cur-srv");
        ctx.putPath("exp-path", "cur-path");
        ctx.putCommandConfig("exp-cmd", "cur-cmd");

        // 导出批处理任务（子任务使用旧 ID）
        BatchTask expTask = new BatchTask();
        expTask.setId("exp-batch-1");
        expTask.setName("Daily Deploy");

        BatchSubTask expSub = new BatchSubTask();
        expSub.setId("exp-sub-1");
        expSub.setServerId("exp-srv");
        expSub.setPathId("exp-path");
        expSub.setCommandConfigId("exp-cmd");
        expSub.setFilePaths(new ArrayList<>(List.of("a.txt", "b.txt")));
        expSub.setOrder(1);
        expTask.setSubTasks(new ArrayList<>(List.of(expSub)));

        List<BatchTask> exported = new ArrayList<>(List.of(expTask));
        List<BatchTask> current = new ArrayList<>();

        List<BatchTask> result = merger.merge(exported, current, ctx);

        // 新增
        assertEquals(1, result.size());
        BatchTask added = result.get(0);
        assertNotEquals("exp-batch-1", added.getId()); // 新 UUID
        assertEquals("Daily Deploy", added.getName());

        // 子任务 ID 全部重映射
        BatchSubTask addedSub = added.getSubTasks().get(0);
        assertEquals("cur-srv", addedSub.getServerId());
        assertEquals("cur-path", addedSub.getPathId());
        assertEquals("cur-cmd", addedSub.getCommandConfigId());
        assertEquals(List.of("a.txt", "b.txt"), addedSub.getFilePaths());
        assertEquals(1, addedSub.getOrder());

        // 验证批处理任务 ID 映射
        assertEquals(added.getId(), ctx.resolveBatchTaskId("exp-batch-1"));
        assertEquals(1, merger.getMergeCounts().getAdded());
    }

    @Test
    public void testNameMatchUpdates() {
        BatchTaskMerger merger = new BatchTaskMerger();
        RemapContext ctx = new RemapContext();
        ctx.putServer("exp-srv", "cur-srv");
        ctx.putPath("exp-path", "cur-path");

        // 导出
        BatchTask expTask = createSimpleTask("exp-batch", "My Task");
        BatchSubTask expSub = new BatchSubTask();
        expSub.setId("exp-sub");
        expSub.setServerId("exp-srv");
        expSub.setPathId("exp-path");
        expSub.setFilePaths(List.of("newfile.txt"));
        expSub.setOrder(5);
        expTask.setSubTasks(List.of(expSub));

        // 当前（同名）
        BatchTask curTask = createSimpleTask("cur-batch", "My Task");
        BatchSubTask curSub = new BatchSubTask();
        curSub.setId("cur-sub");
        curSub.setServerId("cur-srv");
        curSub.setPathId("cur-path");
        curSub.setFilePaths(List.of("oldfile.txt"));
        curSub.setOrder(0);
        curTask.setSubTasks(List.of(curSub));

        List<BatchTask> exported = new ArrayList<>(List.of(expTask));
        List<BatchTask> current = new ArrayList<>(List.of(curTask));

        List<BatchTask> result = merger.merge(exported, current, ctx);

        assertEquals(1, result.size());
        assertEquals("cur-batch", result.get(0).getId()); // 保留 B 的 ID
        // 子任务被覆盖
        assertEquals(1, result.get(0).getSubTasks().size());
        assertEquals("newfile.txt", result.get(0).getSubTasks().get(0).getFilePaths().get(0));
        assertEquals(5, result.get(0).getSubTasks().get(0).getOrder());

        assertEquals(0, merger.getMergeCounts().getAdded());
        assertEquals(1, merger.getMergeCounts().getUpdated());
    }

    @Test
    public void testNullCommandConfigIdHandled() {
        BatchTaskMerger merger = new BatchTaskMerger();
        RemapContext ctx = new RemapContext();
        ctx.putServer("exp-srv", "cur-srv");
        ctx.putPath("exp-path", "cur-path");

        BatchTask expTask = new BatchTask();
        expTask.setId("exp-batch");
        expTask.setName("No Cmd Task");

        BatchSubTask expSub = new BatchSubTask();
        expSub.setId("exp-sub");
        expSub.setServerId("exp-srv");
        expSub.setPathId("exp-path");
        expSub.setCommandConfigId(null); // 无命令组
        expSub.setFilePaths(List.of("data.csv"));
        expSub.setOrder(1);
        expTask.setSubTasks(List.of(expSub));

        List<BatchTask> result = merger.merge(
            new ArrayList<>(List.of(expTask)),
            new ArrayList<>(),
            ctx
        );

        assertEquals(1, result.size());
        assertNull(result.get(0).getSubTasks().get(0).getCommandConfigId());
    }

    @Test
    public void testDifferentNameAddsNew() {
        BatchTaskMerger merger = new BatchTaskMerger();
        RemapContext ctx = new RemapContext();

        BatchTask expTask = createSimpleTask("exp-1", "Task Alpha");
        BatchTask curTask = createSimpleTask("cur-1", "Task Beta");

        List<BatchTask> result = merger.merge(
            new ArrayList<>(List.of(expTask)),
            new ArrayList<>(List.of(curTask)),
            ctx
        );

        // 不同 name → 新增
        assertEquals(2, result.size());
        assertEquals(1, merger.getMergeCounts().getAdded());
    }

    private BatchTask createSimpleTask(String id, String name) {
        BatchTask t = new BatchTask();
        t.setId(id);
        t.setName(name);
        t.setSubTasks(new ArrayList<>());
        return t;
    }
}
