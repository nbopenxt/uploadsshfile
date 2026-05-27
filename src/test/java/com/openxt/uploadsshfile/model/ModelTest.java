package com.openxt.uploadsshfile.model;

import com.openxt.uploadsshfile.model.UnifiedPluginConfig.BlacklistConfig;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * 模型类单元测试
 */
public class ModelTest {
    
    // ========== CommandResult 测试 ==========
    
    @Test
    public void testCommandResultSuccess() {
        CommandResult result = new CommandResult(0, "output", "", 100);
        
        assertEquals(0, result.getExitCode());
        assertEquals("output", result.getStdout());
        assertEquals("", result.getStderr());
        assertEquals(100, result.getDurationMs());
        assertTrue(result.isSuccess());
    }
    
    @Test
    public void testCommandResultFailed() {
        CommandResult result = new CommandResult(1, "output", "error", 50);
        
        assertEquals(1, result.getExitCode());
        assertEquals("error", result.getStderr());
        assertFalse(result.isSuccess());
    }
    
    @Test
    public void testCommandResultStaticFactory() {
        CommandResult success = CommandResult.success("done", 100);
        CommandResult failed = CommandResult.failed(1, "out", "err", 50);
        
        assertTrue(success.isSuccess());
        assertFalse(failed.isSuccess());
    }
    
    // ========== ExecutionSummary 测试 ==========
    
    @Test
    public void testExecutionSummary() {
        ExecutionSummary summary = new ExecutionSummary(10, 7, 2, 1);
        
        assertEquals(10, summary.getTotal());
        assertEquals(7, summary.getSuccessCount());
        assertEquals(2, summary.getFailedCount());
        assertEquals(1, summary.getBlockedCount());
    }
    
    // ========== ValidationResult 测试 ==========
    
    @Test
    public void testValidationResultBlocked() {
        ValidationResult result = ValidationResult.blocked("test reason");
        
        assertTrue(result.isBlocked());
        assertEquals("test reason", result.getReason());
    }
    
    @Test
    public void testValidationResultAllowed() {
        ValidationResult result = ValidationResult.allowed();
        
        assertFalse(result.isBlocked());
        assertNull(result.getReason());
    }
    
    // ========== EvaluationResult 测试 ==========
    
    @Test
    public void testEvaluationResultSuccess() {
        EvaluationResult result = EvaluationResult.success(0);
        
        assertTrue(result.isSuccess());
        assertEquals(0, result.getExitCode());
        assertNull(result.getReason());
    }
    
    @Test
    public void testEvaluationResultFailed() {
        EvaluationResult result = EvaluationResult.failed("keyword not found", 0);
        
        assertFalse(result.isSuccess());
        assertEquals("keyword not found", result.getReason());
        assertEquals(0, result.getExitCode());
    }
    
    // ========== CommandConfig 测试 ==========
    
    @Test
    public void testCommandConfig() {
        CommandConfig config = new CommandConfig();
        config.setName("Test Group");
        config.setServerId("server-1");
        config.setPathId("path-1");
        config.setExecuteTiming(ExecuteTiming.AUTO);
        config.setCommandTimeoutMs(60000);
        
        assertNotNull(config.getId());
        assertEquals("Test Group", config.getName());
        assertEquals("server-1", config.getServerId());
        assertEquals("path-1", config.getPathId());
        assertEquals(ExecuteTiming.AUTO, config.getExecuteTiming());
        assertEquals(60000, config.getCommandTimeoutMs());
    }
    
    @Test
    public void testCommandConfigWithItems() {
        CommandConfig config = new CommandConfig();
        
        CommandItem item1 = new CommandItem("cmd1");
        item1.setOrder(1);
        CommandItem item2 = new CommandItem("cmd2");
        item2.setOrder(2);
        item2.setEnabled(false);
        
        config.addCommand(item1);
        config.addCommand(item2);
        
        assertEquals(2, config.getCommands().size());
        assertEquals(1, config.getEnabledCommands().size());
        assertEquals("cmd1", config.getEnabledCommands().get(0).getCommand());
    }
    
    // ========== BlacklistConfig 测试 ==========
    // 注意: getEffectiveBlacklist 和 isBlocked 方法已移至 BlacklistValidator
    
    @Test
    public void testBlacklistConfig() {
        BlacklistConfig config = new BlacklistConfig();
        
        assertNotNull(config.getLinuxBlacklist());
        assertNotNull(config.getWindowsBlacklist());
        assertNotNull(config.getUniversal());
        assertFalse(config.getLinuxBlacklist().isEmpty());
        assertFalse(config.getWindowsBlacklist().isEmpty());
        
        config.setLinuxBlacklist(List.of("cmd1", "cmd2"));
        assertEquals(2, config.getLinuxBlacklist().size());
        
        config.setWindowsBlacklist(List.of("cmd3"));
        assertEquals(1, config.getWindowsBlacklist().size());
        
        config.setUniversal(List.of("universal-cmd"));
        assertEquals(1, config.getUniversal().size());
    }
    
    // ========== KeywordRules 测试 ==========
    
    @Test
    public void testKeywordRules() {
        KeywordRules rules = new KeywordRules();
        
        assertNotNull(rules.getLinuxDefaultFailKeywords());
        assertNotNull(rules.getWindowsDefaultFailKeywords());
        assertFalse(rules.getLinuxDefaultFailKeywords().isEmpty());
        assertFalse(rules.getWindowsDefaultFailKeywords().isEmpty());
    }
    
    @Test
    public void testKeywordRulesEffective() {
        KeywordRules rules = new KeywordRules();
        rules.setLinuxCustomFailKeywords(List.of("custom-fail"));
        
        List<String> effectiveFail = rules.getEffectiveFailKeywords(OperatingSystem.LINUX);
        
        assertTrue(effectiveFail.contains("custom-fail"));
    }
    
    @Test
    public void testKeywordRulesReset() {
        KeywordRules rules = new KeywordRules();
        rules.setLinuxCustomFailKeywords(List.of("custom"));
        
        rules.resetToDefault();
        
        assertTrue(rules.getLinuxCustomFailKeywords().isEmpty());
    }
    
    // ========== AIConfig 测试 ==========
    
    @Test
    public void testAIConfig() {
        AIConfig config = new AIConfig();
        
        assertFalse(config.isEnabled());
        assertTrue(config.isEnablePreExecutionCheck());
        assertTrue(config.isEnablePostResultCheck());
    }
    
    @Test
    public void testAIConfigIsConfigured() {
        AIConfig config = new AIConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://api.example.com");
        config.setApiKey("secret");
        
        assertTrue(config.isConfigured());
    }
    
    @Test
    public void testAIConfigNotConfigured() {
        AIConfig config = new AIConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://api.example.com");
        // 没有设置 API key
        
        assertFalse(config.isConfigured());
    }
    
    // ========== SshConnection 测试 ==========
    
    @Test
    public void testSshConnection() {
        SshConnection conn = new SshConnection("id", "host", 22, "user", "pass");
        
        assertEquals("id", conn.getServerId());
        assertEquals("host", conn.getHost());
        assertEquals(22, conn.getPort());
        assertEquals("user", conn.getUsername());
        assertEquals("pass", conn.getPassword());
    }
    
    // ========== ExecuteTiming 测试 ==========
    
    @Test
    public void testExecuteTimingValues() {
        assertEquals(3, ExecuteTiming.values().length);
        assertEquals(ExecuteTiming.NONE, ExecuteTiming.valueOf("NONE"));
        assertEquals(ExecuteTiming.AUTO, ExecuteTiming.valueOf("AUTO"));
        assertEquals(ExecuteTiming.MANUAL, ExecuteTiming.valueOf("MANUAL"));
    }
    
    @Test
    public void testExecuteTimingGetValue() {
        assertEquals(0, ExecuteTiming.NONE.getValue());
        assertEquals(1, ExecuteTiming.AUTO.getValue());
        assertEquals(2, ExecuteTiming.MANUAL.getValue());
    }
    
    @Test
    public void testExecuteTimingFromValue() {
        assertEquals(ExecuteTiming.NONE, ExecuteTiming.fromValue(0));
        assertEquals(ExecuteTiming.AUTO, ExecuteTiming.fromValue(1));
        assertEquals(ExecuteTiming.MANUAL, ExecuteTiming.fromValue(2));
    }
    
    @Test
    public void testExecuteTimingFromValueInvalid() {
        // 无效值应返回 NONE
        assertEquals(ExecuteTiming.NONE, ExecuteTiming.fromValue(-1));
        assertEquals(ExecuteTiming.NONE, ExecuteTiming.fromValue(99));
    }
    
    // ========== CommandConfig setCommandsFromStrings 测试 ==========
    
    @Test
    public void testSetCommandsFromStrings() {
        CommandConfig config = new CommandConfig();
        List<String> commands = List.of("cd /app", "npm install", "npm run build");
        
        config.setCommandsFromStrings(commands);
        
        assertEquals(3, config.getCommands().size());
        assertEquals("cd /app", config.getCommands().get(0).getCommand());
        assertEquals("npm install", config.getCommands().get(1).getCommand());
        assertEquals("npm run build", config.getCommands().get(2).getCommand());
    }
    
    @Test
    public void testSetCommandsFromStringsOrder() {
        CommandConfig config = new CommandConfig();
        List<String> commands = List.of("cmd1", "cmd2", "cmd3");
        
        config.setCommandsFromStrings(commands);
        
        // 验证顺序
        assertEquals(0, config.getCommands().get(0).getOrder());
        assertEquals(1, config.getCommands().get(1).getOrder());
        assertEquals(2, config.getCommands().get(2).getOrder());
    }
    
    @Test
    public void testSetCommandsFromStringsEnabled() {
        CommandConfig config = new CommandConfig();
        List<String> commands = List.of("cmd1", "cmd2");
        
        config.setCommandsFromStrings(commands);
        
        // 验证所有命令默认为自己启用
        assertTrue(config.getCommands().get(0).isEnabled());
        assertTrue(config.getCommands().get(1).isEnabled());
        assertEquals(2, config.getEnabledCommands().size());
    }
    
    @Test
    public void testSetCommandsFromStringsEmpty() {
        CommandConfig config = new CommandConfig();
        
        config.setCommandsFromStrings(List.of());
        
        assertTrue(config.getCommands().isEmpty());
    }
}
