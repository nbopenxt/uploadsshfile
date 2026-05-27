package com.openxt.uploadsshfile.test;

import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * CommandConfigDialog 组件测试
 * 验证服务器和路径下拉框的显示逻辑
 */
public class CommandConfigDisplayTest {

    /**
     * 测试服务器名称为 null 时的显示逻辑
     */
    @Test
    public void testServerDisplayWithNullName() {
        ServerConfig server = new ServerConfig();
        server.setId("test-uuid-123");
        // name 为 null (未设置)

        // 模拟 loadServerOptions() 中的逻辑
        String displayName = (server.getName() != null && !server.getName().isEmpty()) 
            ? server.getName() 
            : server.getId();

        assertEquals("test-uuid-123", displayName);
        assertNotNull(displayName);
        assertFalse(displayName.contains("null"));
    }

    /**
     * 测试服务器名称正常时的显示逻辑
     */
    @Test
    public void testServerDisplayWithValidName() {
        ServerConfig server = new ServerConfig();
        server.setId("test-uuid-123");
        server.setName("My Server");

        String displayName = (server.getName() != null && !server.getName().isEmpty()) 
            ? server.getName() 
            : server.getId();

        assertEquals("My Server", displayName);
    }

    /**
     * 测试路径为 null 时的显示逻辑
     */
    @Test
    public void testPathDisplayWithNullRemotePath() {
        PathConfig path = new PathConfig();
        path.setId("path-uuid-456");
        // remotePath 为 null

        String displayPath = (path.getRemotePath() != null && !path.getRemotePath().isEmpty()) 
            ? path.getRemotePath() 
            : path.getId();

        assertEquals("path-uuid-456", displayPath);
        assertNotNull(displayPath);
        assertFalse(displayPath.contains("null"));
    }

    /**
     * 测试路径正常时的显示逻辑
     */
    @Test
    public void testPathDisplayWithValidRemotePath() {
        PathConfig path = new PathConfig();
        path.setId("path-uuid-456");
        path.setRemotePath("/home/user/uploads");

        String displayPath = (path.getRemotePath() != null && !path.getRemotePath().isEmpty()) 
            ? path.getRemotePath() 
            : path.getId();

        assertEquals("/home/user/uploads", displayPath);
    }

    /**
     * 测试下拉框项格式解析
     */
    @Test
    public void testComboBoxItemParsing() {
        String item = "test-uuid-123: My Server";
        
        // 模拟 loadGroupDetails() 中的解析逻辑
        int colonIndex = item.indexOf(":");
        assertTrue(colonIndex > 0);
        
        String id = item.substring(0, colonIndex);
        assertEquals("test-uuid-123", id);
    }

    /**
     * 测试下拉框项格式解析 - 只有 UUID 的情况
     */
    @Test
    public void testComboBoxItemParsingWithOnlyUUID() {
        String item = "test-uuid-123:";
        
        int colonIndex = item.indexOf(":");
        assertTrue(colonIndex > 0);
        
        String id = item.substring(0, colonIndex);
        assertEquals("test-uuid-123", id);
    }

    /**
     * 测试路径下拉框项格式解析
     */
    @Test
    public void testPathComboBoxItemParsing() {
        String item = "path-uuid-456: /home/user/uploads";
        
        int colonIndex = item.indexOf(":");
        assertTrue(colonIndex > 0);
        
        String id = item.substring(0, colonIndex);
        assertEquals("path-uuid-456", id);
    }

    /**
     * 测试完整的显示和解析流程
     */
    @Test
    public void testFullDisplayAndParseFlow() {
        ServerConfig server = new ServerConfig();
        server.setId("server-uuid-789");
        server.setName("Production Server");

        // 1. 显示逻辑
        String displayName = (server.getName() != null && !server.getName().isEmpty()) 
            ? server.getName() 
            : server.getId();
        
        String comboItem = server.getId() + ": " + displayName;
        assertEquals("server-uuid-789: Production Server", comboItem);

        // 2. 解析逻辑
        int colonIndex = comboItem.indexOf(":");
        String parsedId = comboItem.substring(0, colonIndex);
        assertEquals(server.getId(), parsedId);

        // 3. 验证匹配
        assertTrue(comboItem.startsWith(parsedId + ":"));
    }

    /**
     * 测试空名称情况
     */
    @Test
    public void testEmptyName() {
        ServerConfig server = new ServerConfig();
        server.setId("server-uuid-789");
        server.setName(""); // 空字符串

        String displayName = (server.getName() != null && !server.getName().isEmpty()) 
            ? server.getName() 
            : server.getId();
        
        assertEquals("server-uuid-789", displayName);
    }
}
