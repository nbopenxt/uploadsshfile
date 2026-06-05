package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.CommandItem;
import com.openxt.uploadsshfile.model.ExecuteTiming;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * CommandConfigMerger 单元测试
 * UT-13: 依赖两层映射后 name 匹配
 */
public class CommandConfigMergerTest {

    // ========== UT-13: 依赖两层映射后 name 匹配 ==========

    @Test
    public void testMatchAfterServerAndPathRemap() {
        CommandConfigMerger merger = new CommandConfigMerger();
        RemapContext ctx = new RemapContext();

        // 模拟 ServerMerger + PathMerger 已完成
        ctx.putServer("exp-srv", "cur-srv");
        ctx.putPath("exp-path", "cur-path");

        // 导出命令配置
        List<CommandConfig> exported = new ArrayList<>();
        CommandConfig expCmd = createCmdConfig("exp-cmd-id", "exp-srv", "exp-path", "Deploy Script", ExecuteTiming.AUTO);
        exported.add(expCmd);

        // 当前命令配置（不同 UUID，同 name）
        List<CommandConfig> current = new ArrayList<>();
        CommandConfig curCmd = createCmdConfig("cur-cmd-id", "cur-srv", "cur-path", "Deploy Script", ExecuteTiming.MANUAL);
        current.add(curCmd);

        List<CommandConfig> result = merger.merge(exported, current, ctx);

        // 应视为同一命令配置 → 覆盖
        assertEquals(1, result.size());
        assertEquals("cur-cmd-id", result.get(0).getId()); // 保留 B 的 ID
        assertEquals("cur-srv", result.get(0).getServerId());
        assertEquals("cur-path", result.get(0).getPathId());
        assertEquals("Deploy Script", result.get(0).getName());
        assertEquals(ExecuteTiming.AUTO, result.get(0).getExecuteTiming()); // 被覆盖

        // 验证映射
        assertEquals("cur-cmd-id", ctx.resolveCommandConfigId("exp-cmd-id"));
        assertEquals(1, merger.getMergeCounts().getUpdated());
    }

    @Test
    public void testServerUnmatchedSkips() {
        CommandConfigMerger merger = new CommandConfigMerger();
        RemapContext ctx = new RemapContext();

        List<CommandConfig> exported = new ArrayList<>();
        exported.add(createCmdConfig("exp-1", "unk-srv", "unk-path", "Test", ExecuteTiming.AUTO));

        List<CommandConfig> current = new ArrayList<>();

        List<CommandConfig> result = merger.merge(exported, current, ctx);

        assertEquals(0, result.size());
        assertEquals(1, merger.getMergeCounts().getSkipped());
    }

    @Test
    public void testAddNewCommandConfig() {
        CommandConfigMerger merger = new CommandConfigMerger();
        RemapContext ctx = new RemapContext();
        ctx.putServer("exp-srv", "cur-srv");
        ctx.putPath("exp-path", "cur-path");

        CommandConfig expCmd = createCmdConfig("exp-new", "exp-srv", "exp-path", "New Script", ExecuteTiming.AUTO);
        CommandItem item = new CommandItem("npm run build");
        item.setOrder(1);
        expCmd.addCommand(item);

        List<CommandConfig> exported = new ArrayList<>();
        exported.add(expCmd);

        List<CommandConfig> current = new ArrayList<>();

        List<CommandConfig> result = merger.merge(exported, current, ctx);

        assertEquals(1, result.size());
        assertEquals("New Script", result.get(0).getName());
        assertEquals("cur-srv", result.get(0).getServerId());
        assertEquals("cur-path", result.get(0).getPathId());
        assertEquals(ExecuteTiming.AUTO, result.get(0).getExecuteTiming());
        assertEquals(1, result.get(0).getCommands().size());
        assertEquals("npm run build", result.get(0).getCommands().get(0).getCommand());

        assertEquals(1, merger.getMergeCounts().getAdded());
    }

    @Test
    public void testDifferentNameCreatesNew() {
        CommandConfigMerger merger = new CommandConfigMerger();
        RemapContext ctx = new RemapContext();
        ctx.putServer("exp-srv", "cur-srv");
        ctx.putPath("exp-path", "cur-path");

        List<CommandConfig> exported = new ArrayList<>();
        exported.add(createCmdConfig("exp-1", "exp-srv", "exp-path", "Script A", ExecuteTiming.AUTO));

        List<CommandConfig> current = new ArrayList<>();
        current.add(createCmdConfig("cur-1", "cur-srv", "cur-path", "Script B", ExecuteTiming.MANUAL));

        List<CommandConfig> result = merger.merge(exported, current, ctx);

        // 不同 name → 新命令配置
        assertEquals(2, result.size());
        assertEquals(1, merger.getMergeCounts().getAdded());
    }

    private CommandConfig createCmdConfig(String id, String serverId, String pathId, String name, ExecuteTiming timing) {
        CommandConfig c = new CommandConfig();
        c.setId(id);
        c.setServerId(serverId);
        c.setPathId(pathId);
        c.setName(name);
        c.setExecuteTiming(timing);
        c.setMaxRetries(3);
        c.setCommandTimeoutMs(300000);
        return c;
    }
}
