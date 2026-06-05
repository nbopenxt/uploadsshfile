package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.config.ServerConfig;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * ServerMerger 单元测试
 * UT-10: host+port 匹配正确（UUID 不同但认为是同一台）
 * UT-11: host+port 不同 → 新增
 */
public class ServerMergerTest {

    // ========== UT-10: host+port 匹配（UUID 不同但视为同一台） ==========

    @Test
    public void testMatchByHostAndPort() {
        ServerMerger merger = new ServerMerger();
        RemapContext ctx = new RemapContext();

        // 导出配置 A（旧 UUID）
        List<ServerConfig> exported = new ArrayList<>();
        ServerConfig expServer = new ServerConfig();
        expServer.setId("old-uuid-123");
        expServer.setName("My Server");
        expServer.setHost("192.168.1.100");
        expServer.setPort(22);
        expServer.setUsername("admin");
        expServer.setPassword("encrypted-pass-base64");
        expServer.setOsType("linux");
        exported.add(expServer);

        // 当前配置 B（不同 UUID，同 host+port）
        List<ServerConfig> current = new ArrayList<>();
        ServerConfig curServer = new ServerConfig();
        String curId = curServer.getId(); // 新 UUID
        curServer.setName("Old Name");
        curServer.setHost("192.168.1.100");
        curServer.setPort(22);
        curServer.setUsername("olduser");
        curServer.setOsType("windows");
        current.add(curServer);

        List<ServerConfig> result = merger.merge(exported, current, ctx);

        // 应该视为同一台服务器 → 覆盖（保留 B 的 ID）
        assertEquals(1, result.size());
        assertEquals(curId, result.get(0).getId()); // ID 不变
        assertEquals("My Server", result.get(0).getName()); // 属性被覆盖
        assertEquals("admin", result.get(0).getUsername());
        assertEquals("linux", result.get(0).getOsType());

        // 验证映射
        assertEquals(curId, ctx.resolveServerId("old-uuid-123"));

        // 验证统计
        assertEquals(0, merger.getMergeCounts().getAdded());
        assertEquals(1, merger.getMergeCounts().getUpdated());
    }

    // ========== UT-11: host+port 不同 → 新增 ==========

    @Test
    public void testDifferentHostAndPortAddsNew() {
        ServerMerger merger = new ServerMerger();
        RemapContext ctx = new RemapContext();

        List<ServerConfig> exported = new ArrayList<>();
        ServerConfig expServer = new ServerConfig();
        expServer.setId("exp-id-new");
        expServer.setName("New Server");
        expServer.setHost("10.0.0.1");
        expServer.setPort(2222);
        expServer.setUsername("root");
        expServer.setPassword("enc-pass");
        expServer.setOsType("linux");
        exported.add(expServer);

        List<ServerConfig> current = new ArrayList<>();
        ServerConfig curServer = new ServerConfig();
        curServer.setId("cur-existing");
        curServer.setHost("192.168.1.1");
        curServer.setPort(22);
        curServer.setUsername("other");
        current.add(curServer);

        List<ServerConfig> result = merger.merge(exported, current, ctx);

        // 应新增一台服务器
        assertEquals(2, result.size());

        // 原有服务器保留
        assertEquals("cur-existing", result.get(0).getId());
        assertEquals("192.168.1.1", result.get(0).getHost());

        // 新服务器已添加
        ServerConfig added = result.get(1);
        assertNotEquals("exp-id-new", added.getId()); // 新 UUID
        assertEquals("New Server", added.getName());
        assertEquals("10.0.0.1", added.getHost());
        assertEquals(2222, added.getPort());

        // 验证映射
        assertEquals(added.getId(), ctx.resolveServerId("exp-id-new"));

        // 验证统计
        assertEquals(1, merger.getMergeCounts().getAdded());
        assertEquals(0, merger.getMergeCounts().getUpdated());
    }

    @Test
    public void testSameHostDifferentPort() {
        ServerMerger merger = new ServerMerger();
        RemapContext ctx = new RemapContext();

        List<ServerConfig> exported = new ArrayList<>();
        ServerConfig expServer = new ServerConfig();
        expServer.setId("exp-1");
        expServer.setHost("10.0.0.1");
        expServer.setPort(2222);
        expServer.setName("SSH Port 2222");
        expServer.setPassword("pwd1");
        exported.add(expServer);

        List<ServerConfig> current = new ArrayList<>();
        ServerConfig curServer = new ServerConfig();
        curServer.setId("cur-1");
        curServer.setHost("10.0.0.1");
        curServer.setPort(22); // 不同端口
        current.add(curServer);

        List<ServerConfig> result = merger.merge(exported, current, ctx);

        // 不同端口 → 新增
        assertEquals(2, result.size());
        assertEquals(1, merger.getMergeCounts().getAdded());
    }

    @Test
    public void testMergeMultipleServers() {
        ServerMerger merger = new ServerMerger();
        RemapContext ctx = new RemapContext();

        List<ServerConfig> exported = new ArrayList<>();
        ServerConfig s1 = createExportedServer("exp-1", "srv1", "10.0.0.1", 22, "pass1");
        ServerConfig s2 = createExportedServer("exp-2", "srv2", "10.0.0.2", 22, "pass2");
        ServerConfig s3 = createExportedServer("exp-3", "srv3", "10.0.0.3", 22, "pass3");
        exported.add(s1);
        exported.add(s2);
        exported.add(s3);

        List<ServerConfig> current = new ArrayList<>();
        ServerConfig c1 = createCurrentServer("cur-1", "10.0.0.1", 22); // 匹配 s1
        ServerConfig c4 = createCurrentServer("cur-4", "10.0.0.4", 22); // 不匹配
        current.add(c1);
        current.add(c4);

        List<ServerConfig> result = merger.merge(exported, current, ctx);

        // s1 覆盖 c1, s2/s3 新增, c4 保留
        assertEquals(4, result.size());
        assertEquals(2, merger.getMergeCounts().getAdded());
        assertEquals(1, merger.getMergeCounts().getUpdated());
    }

    private ServerConfig createExportedServer(String id, String name, String host, int port, String password) {
        ServerConfig s = new ServerConfig();
        s.setId(id);
        s.setName(name);
        s.setHost(host);
        s.setPort(port);
        s.setUsername("root");
        s.setPassword(password);
        s.setOsType("linux");
        return s;
    }

    private ServerConfig createCurrentServer(String id, String host, int port) {
        ServerConfig s = new ServerConfig();
        s.setId(id);
        s.setHost(host);
        s.setPort(port);
        s.setUsername("admin");
        s.setOsType("windows");
        return s;
    }
}
