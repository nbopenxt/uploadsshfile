package com.openxt.uploadsshfile.store;

import com.openxt.uploadsshfile.model.KeywordRules;
import com.openxt.uploadsshfile.model.OperatingSystem;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

/**
 * UnifiedConfigStore 单元测试
 */
public class JsonStoreTest {
    
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();
    
    private Path tempDir;
    private UnifiedConfigStore configStore;
    
    @Before
    public void setUp() throws Exception {
        tempDir = tempFolder.newFolder("test").toPath();
        // 重置单例以使用测试路径
        UnifiedConfigStore.resetInstance();
        // 临时创建配置目录
        Files.createDirectories(tempDir);
        // 由于 UnifiedConfigStore 使用 PathManager，需要用反射或创建临时测试
        // 这里直接测试模型类的默认行为
    }
    
    @After
    public void tearDown() {
        UnifiedConfigStore.resetInstance();
    }
    
    // ========== BlacklistConfig 测试 ==========
    
    @Test
    public void testBlacklistConfigDefaults() {
        UnifiedPluginConfig.BlacklistConfig config = new UnifiedPluginConfig.BlacklistConfig();
        
        // 验证默认 Linux 黑名单
        assertNotNull(config.getLinuxBlacklist());
        assertFalse(config.getLinuxBlacklist().isEmpty());
        assertTrue(config.getLinuxBlacklist().contains("rm -rf /"));
        
        // 验证默认 Windows 黑名单
        assertNotNull(config.getWindowsBlacklist());
        assertFalse(config.getWindowsBlacklist().isEmpty());
        assertTrue(config.getWindowsBlacklist().contains("format"));
        
        // 验证默认通用黑名单为空
        assertNotNull(config.getUniversal());
        assertTrue(config.getUniversal().isEmpty());
    }
    
    @Test
    public void testBlacklistConfigSetters() {
        UnifiedPluginConfig.BlacklistConfig config = new UnifiedPluginConfig.BlacklistConfig();
        
        // 设置 Linux 黑名单
        config.setLinuxBlacklist(java.util.List.of("test1", "test2"));
        assertEquals(2, config.getLinuxBlacklist().size());
        assertTrue(config.getLinuxBlacklist().contains("test1"));
        
        // 设置 Windows 黑名单
        config.setWindowsBlacklist(java.util.List.of("cmd1"));
        assertEquals(1, config.getWindowsBlacklist().size());
        
        // 设置通用黑名单
        config.setUniversal(java.util.List.of("universal1"));
        assertEquals(1, config.getUniversal().size());
    }
    
    // ========== KeywordRules 测试 ==========
    
    @Test
    public void testKeywordRulesDefaults() {
        KeywordRules rules = new KeywordRules();
        
        // 验证默认 Linux 失败关键词
        assertNotNull(rules.getLinuxDefaultFailKeywords());
        assertFalse(rules.getLinuxDefaultFailKeywords().isEmpty());
        assertTrue(rules.getLinuxDefaultFailKeywords().contains("error"));
        
        // 验证默认 Windows 失败关键词
        assertNotNull(rules.getWindowsDefaultFailKeywords());
        
        // 验证自定义失败关键词为空
        assertTrue(rules.getLinuxCustomFailKeywords().isEmpty());
    }
    
    @Test
    public void testEffectiveFailKeywords() {
        KeywordRules rules = new KeywordRules();
        rules.setUniversalFailKeywords(java.util.List.of("universal-fail"));
        rules.setLinuxCustomFailKeywords(java.util.List.of("custom-error"));
        
        // 测试 Linux 生效失败关键词
        java.util.List<String> effectiveFail = rules.getEffectiveFailKeywords(OperatingSystem.LINUX);
        assertTrue(effectiveFail.contains("universal-fail"));
        assertTrue(effectiveFail.contains("custom-error"));
        assertTrue(effectiveFail.contains("error")); // 默认关键词
    }
    
    @Test
    public void testResetKeywordRules() {
        KeywordRules rules = new KeywordRules();
        rules.setLinuxCustomFailKeywords(java.util.List.of("custom-error"));
        
        // 重置为默认值
        rules.resetToDefault();
        
        // 验证自定义失败关键词已清空
        assertTrue(rules.getLinuxCustomFailKeywords().isEmpty());
        
        // 验证默认失败关键词仍然存在
        assertFalse(rules.getLinuxDefaultFailKeywords().isEmpty());
    }
    
    @Test
    public void testKeywordRulesSetters() {
        KeywordRules rules = new KeywordRules();
        
        rules.setLinuxDefaultFailKeywords(java.util.List.of("err1", "err2"));
        assertEquals(2, rules.getLinuxDefaultFailKeywords().size());
    }
}
