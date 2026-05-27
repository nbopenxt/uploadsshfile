package com.openxt.uploadsshfile.validation;

import com.openxt.uploadsshfile.model.OperatingSystem;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import com.openxt.uploadsshfile.model.ValidationResult;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * SemanticBlacklistChecker 单元测试
 * <p>
 * 测试新的黑名单检查逻辑：
 * 1. 用户自定义危险命令黑名单检查
 * 2. 用户自定义危险目录黑名单检查
 * 3. 操作系统区分测试
 * 4. 恢复默认值测试
 */
public class SemanticBlacklistCheckerTest {
    
    private SemanticBlacklistChecker checker;
    private UnifiedConfigStore configStore;
    private Path testConfigDir;
    
    @Before
    public void setUp() throws IOException {
        // 创建测试专用配置目录
        testConfigDir = Files.createTempDirectory("uploadsshfile-test");
        configStore = UnifiedConfigStore.createForTest(testConfigDir);
        checker = new SemanticBlacklistChecker();
    }
    
    @After
    public void tearDown() throws IOException {
        // 清理测试配置目录
        UnifiedConfigStore.resetInstance();
        if (testConfigDir != null && Files.exists(testConfigDir)) {
            Files.walk(testConfigDir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                });
        }
    }
    
    // ==================== 默认黑名单命令测试 ====================
    
    @Test
    public void testDefaultLinuxBlacklist_blocked() {
        // 默认 Linux 黑名单中的命令应被拦截
        // "shutdown" 在默认 Linux 黑名单中
        ValidationResult result = checker.validate("shutdown", "linux");
        assertTrue("shutdown 应该被拦截", result.isBlocked());
    }
    
    @Test
    public void testDefaultWindowsBlacklist_blocked() {
        // 默认 Windows 黑名单中的命令应被拦截
        // "format" 在默认 Windows 黑名单中
        ValidationResult result = checker.validate("format c:", "windows");
        assertTrue("format 应该被拦截", result.isBlocked());
    }
    
    @Test
    public void testRmRfPatterns_blocked() {
        // rm -rf 相关危险模式应被拦截
        assertTrue("rm -rf / 应该被拦截", checker.validate("rm -rf /", "linux").isBlocked());
        assertTrue("rm -rf /* 应该被拦截", checker.validate("rm -rf /*", "linux").isBlocked());
        assertTrue("rm -rf * 应该被拦截", checker.validate("rm -rf *", "linux").isBlocked());
    }
    
    @Test
    public void testDdIfPattern_blocked() {
        // dd if= 危险模式应被拦截
        assertTrue("dd if=/dev/zero 应该被拦截", 
            checker.validate("dd if=/dev/zero of=/dev/sda", "linux").isBlocked());
        assertTrue("dd if=/home/user/file 应该被拦截", 
            checker.validate("dd if=/home/user/file", "linux").isBlocked());
    }
    
    @Test
    public void testForkBomb_blocked() {
        // Fork炸弹应被拦截
        assertTrue(":(){ :|: & };: 应该被拦截", 
            checker.validate(":(){ :|: & };:", "linux").isBlocked());
    }
    
    @Test
    public void testSafeCommand_allowed() {
        // 安全命令应被允许
        assertFalse("ls 应该被允许", checker.validate("ls -la", "linux").isBlocked());
        assertFalse("pwd 应该被允许", checker.validate("pwd", "linux").isBlocked());
        assertFalse("echo 应该被允许", checker.validate("echo hello", "linux").isBlocked());
    }
    
    // ==================== 用户自定义黑名单测试 ====================
    
    @Test
    public void testUserDefinedBlacklist_blocked() {
        // 添加用户自定义黑名单
        UnifiedPluginConfig.BlacklistConfig config = configStore.getBlacklist();
        config.addCommand("linux", "custom-dangerous");
        config.addCommand("linux", "rm -rf /home");
        configStore.save();
        
        // 重新创建 checker 确保使用最新配置
        checker = new SemanticBlacklistChecker();
        
        assertTrue("用户自定义命令应该被拦截", 
            checker.validate("custom-dangerous test", "linux").isBlocked());
        assertTrue("用户自定义命令应该被拦截", 
            checker.validate("rm -rf /home/user", "linux").isBlocked());
    }
    
    @Test
    public void testUserDefinedBlacklist_removed() {
        // 移除用户自定义黑名单后应被允许
        UnifiedPluginConfig.BlacklistConfig config = configStore.getBlacklist();
        config.addCommand("linux", "test-dangerous");
        configStore.save();
        
        // 重新创建 checker 确保使用最新配置
        checker = new SemanticBlacklistChecker();
        
        assertTrue("添加后应该被拦截", 
            checker.validate("test-dangerous", "linux").isBlocked());
        
        // 移除
        config.removeCommand("linux", "test-dangerous");
        configStore.save();
        
        // 重新创建 checker 确保使用最新配置
        checker = new SemanticBlacklistChecker();
        
        assertFalse("移除后应该被允许", 
            checker.validate("test-dangerous", "linux").isBlocked());
    }
    
    // ==================== 危险目录黑名单测试 ====================
    
    @Test
    public void testDefaultDangerousPaths_blocked() {
        // 默认危险目录应被拦截
        assertTrue("/etc 应该被拦截", 
            checker.validate("rm -rf /etc", "linux").isBlocked());
        assertTrue("/var/log 应该被拦截", 
            checker.validate("rm -rf /var/log", "linux").isBlocked());
        assertTrue("/boot 应该被拦截", 
            checker.validate("cd /boot", "linux").isBlocked());
    }
    
    @Test
    public void testWindowsDangerousPaths_blocked() {
        // Windows 默认危险目录应被拦截
        assertTrue("C:\\Windows 应该被拦截", 
            checker.validate("rd /s /q C:\\Windows", "windows").isBlocked());
        assertTrue("C:\\Program Files 应该被拦截", 
            checker.validate("rmdir /s /q C:\\Program Files", "windows").isBlocked());
    }
    
    @Test
    public void testUserDefinedDangerousPaths_blocked() {
        // 添加用户自定义危险目录
        UnifiedPluginConfig.BlacklistConfig config = configStore.getBlacklist();
        config.addDangerousPath("linux", "/data/important");
        configStore.save();
        
        // 重新创建 checker 确保使用最新配置
        checker = new SemanticBlacklistChecker();
        
        assertTrue("用户自定义危险目录应该被拦截", 
            checker.validate("rm -rf /data/important", "linux").isBlocked());
    }
    
    @Test
    public void testSafePath_allowed() {
        // 测试：用户清空目录黑名单后，安全目录应该被允许
        // 根据用户设计：用户配置的黑名单是最高标准，清空配置 = 不做拦截
        
        UnifiedPluginConfig.BlacklistConfig config = configStore.getBlacklist();
        // 清空所有目录黑名单（Linux 特定 + 通用）
        config.setLinuxDangerousPaths(new ArrayList<>());
        config.setUniversalPaths(new ArrayList<>());
        configStore.save();
        
        // 重新创建 checker 确保使用最新配置
        checker = new SemanticBlacklistChecker();
        
        // 清空配置后，所有路径都应该被允许
        assertFalse("/data 应该被允许", checker.validate("ls /data", "linux").isBlocked());
        assertFalse("/workspace 应该被允许", checker.validate("cat /workspace/file", "linux").isBlocked());
        assertFalse("/projects 应该被允许", checker.validate("cp /projects/*", "linux").isBlocked());
        
        // 恢复默认值
        configStore.resetBlacklist();
    }
    
    // ==================== 操作系统区分测试 ====================
    
    @Test
    public void testOsSpecificBlacklist() {
        // Linux 黑名单命令在 Windows 上应不被拦截
        UnifiedPluginConfig.BlacklistConfig config = configStore.getBlacklist();
        config.setLinuxBlacklist(List.of("linux-only-danger"));
        config.setWindowsBlacklist(new ArrayList<>());
        configStore.save();
        
        // 重新创建 checker 确保使用最新配置
        checker = new SemanticBlacklistChecker();
        
        assertTrue("Linux黑名单应该拦截", 
            checker.validate("linux-only-danger test", "linux").isBlocked());
        assertFalse("Windows不共享Linux黑名单", 
            checker.validate("linux-only-danger test", "windows").isBlocked());
    }
    
    @Test
    public void testCaseInsensitive() {
        // 大小写不敏感
        assertTrue("SHUTDOWN 应该被拦截", checker.validate("SHUTDOWN", "linux").isBlocked());
        assertTrue("ShuTdOwN 应该被拦截", checker.validate("ShuTdOwN", "linux").isBlocked());
    }
    
    // ==================== 恢复默认值测试 ====================
    
    @Test
    public void testRestoreDefaults() {
        // 修改黑名单
        UnifiedPluginConfig.BlacklistConfig config = configStore.getBlacklist();
        config.setLinuxBlacklist(new ArrayList<>());  // 清空
        config.setUniversal(new ArrayList<>());  // 清空通用黑名单（shutdown 在其中）
        configStore.save();
        
        // 重新创建 checker 确保使用最新配置
        checker = new SemanticBlacklistChecker();
        
        // 清空后 shutdown 应该被允许
        assertFalse("清空后 shutdown 应该被允许", 
            checker.validate("shutdown", "linux").isBlocked());
        
        // 恢复默认值
        configStore.resetBlacklist();
        
        // 重新创建 checker 确保使用最新配置
        checker = new SemanticBlacklistChecker();
        
        // 恢复后 shutdown 应该再次被拦截
        assertTrue("恢复默认值后 shutdown 应该被拦截", 
            checker.validate("shutdown", "linux").isBlocked());
    }
    
    @Test
    public void testRestoreDefaults_dangerousPaths() {
        // 修改危险目录黑名单 - 同时清空 Linux 目录和通用目录黑名单
        UnifiedPluginConfig.BlacklistConfig config = configStore.getBlacklist();
        config.setLinuxDangerousPaths(new ArrayList<>());  // 清空 Linux 目录黑名单
        config.setUniversalPaths(new ArrayList<>());        // 清空通用目录黑名单
        configStore.save();
        
        // 重新创建 checker 确保使用最新配置
        checker = new SemanticBlacklistChecker();
        
        // 清空后，所有路径都应该被允许（用户配置为空 = 不做拦截）
        assertFalse("清空后 /data 应该被允许", 
            checker.validate("ls /data", "linux").isBlocked());
        assertFalse("清空后 /opt 应该被允许", 
            checker.validate("ls /opt", "linux").isBlocked());
        
        // 恢复默认值
        configStore.resetBlacklist();
        
        // 重新创建 checker 确保使用最新配置
        checker = new SemanticBlacklistChecker();
        
        // 恢复后 /etc 应该再次被拦截
        assertTrue("恢复默认值后 /etc 应该被拦截", 
            checker.validate("rm -rf /etc", "linux").isBlocked());
    }
    
    // ==================== 无操作系统参数测试 ====================
    
    @Test
    public void testDefaultOs() {
        // 不指定操作系统时默认使用 Linux
        // shutdown 在默认 Linux 黑名单中
        assertTrue("默认应该使用 Linux 黑名单", 
            checker.validate("shutdown").isBlocked());
        assertFalse("安全命令应该被允许", 
            checker.validate("ls").isBlocked());
    }
    
    // ==================== 空黑名单测试 ====================
    
    @Test
    public void testEmptyBlacklist_allowed() {
        // 清空所有黑名单后所有命令应被允许
        UnifiedPluginConfig.BlacklistConfig config = configStore.getBlacklist();
        config.setLinuxBlacklist(new ArrayList<>());
        config.setWindowsBlacklist(new ArrayList<>());
        config.setLinuxDangerousPaths(new ArrayList<>());
        config.setWindowsDangerousPaths(new ArrayList<>());
        config.setUniversal(new ArrayList<>());
        config.setUniversalPaths(new ArrayList<>());
        configStore.save();
        
        // 重新创建 checker 确保使用最新配置
        checker = new SemanticBlacklistChecker();
        
        assertFalse("清空黑名单后 shutdown 应该被允许", 
            checker.validate("shutdown", "linux").isBlocked());
        assertFalse("清空黑名单后 /data 应该被允许", 
            checker.validate("ls /data", "linux").isBlocked());
    }
    
    // ==================== null 操作系统测试 ====================
    
    @Test
    public void testNullOsType() {
        // null 操作系统类型时使用默认 Linux
        assertTrue("null OS 应该使用默认 Linux 黑名单", 
            checker.validate("shutdown", null).isBlocked());
        assertFalse("安全命令应该被允许", 
            checker.validate("ls", null).isBlocked());
    }
}
