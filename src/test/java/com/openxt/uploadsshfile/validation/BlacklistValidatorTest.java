package com.openxt.uploadsshfile.validation;

import com.openxt.uploadsshfile.model.OperatingSystem;
import com.openxt.uploadsshfile.model.UnifiedPluginConfig;
import com.openxt.uploadsshfile.model.ValidationResult;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.util.PluginPathManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * 黑名单校验器单元测试
 */
public class BlacklistValidatorTest {
    
    private BlacklistValidator validator;
    private UnifiedPluginConfig.BlacklistConfig blacklistConfig;
    
    @Before
    public void setUp() {
        // D-29 修 M1 潜伏雷：本类经 BlacklistValidator→UnifiedConfigStore→PluginPathManager
        // 链路依赖注入态，但从未自行 initialize（1.0.7 前 PathManager 直取恒可用故无感；
        // M1 fail-fast 后单跑本类必炸，此前全绿纯靠其它测试先初始化的顺序运气）。
        // 全测试 JVM 单一约定值（与 BatchExecutionOrchestratorTest 等既有注入同值）——
        // 异值 initialize 会触发"第二控制点"拒绝，跨类必须同值幂等合并
        PluginPathManager.initialize(testInjectRoot(), testInjectRoot());

        // 使用默认黑名单配置
        blacklistConfig = new UnifiedPluginConfig.BlacklistConfig();

        // 创建测试专用的 UnifiedConfigStore
        validator = new BlacklistValidator();
    }
    
    @After
    public void tearDown() {
        UnifiedConfigStore.resetInstance();
    }

    /** 测试 JVM 全局约定的注入根（tmp/uploadsshfile-test-inject，跨类同值） */
    static java.nio.file.Path testInjectRoot() {
        return Paths.get(System.getProperty("java.io.tmpdir"), "uploadsshfile-test-inject");
    }
    
    @Test
    public void testExactMatch_blocked() {
        // 测试默认黑名单中的命令应该被拦截
        // "shutdown" 在默认 Linux 黑名单中
        ValidationResult result = validator.validate("shutdown", null, OperatingSystem.LINUX);
        assertTrue(result.isBlocked());
    }
    
    @Test
    public void testExactMatch_allowed() {
        // 非黑名单命令应该允许
        ValidationResult result = validator.validate("ls -la", null, OperatingSystem.LINUX);
        assertFalse(result.isBlocked());
    }
    
    @Test
    public void testPartialMatch_allowed() {
        // 部分匹配不应被拦截
        ValidationResult result = validator.validate("mkdir /tmp", null, OperatingSystem.LINUX);
        assertFalse(result.isBlocked());
    }
    
    @Test
    public void testCaseInsensitiveMatch() {
        // 大小写不敏感
        ValidationResult result = validator.validate("SHUTDOWN", null, OperatingSystem.LINUX);
        assertTrue(result.isBlocked());
    }
    
    @Test
    public void testEmptyBlacklist() {
        // 清空黑名单后，所有命令应该被允许
        blacklistConfig.setLinuxBlacklist(new ArrayList<>());
        blacklistConfig.setUniversal(new ArrayList<>());
        validator.invalidateCache();
        
        ValidationResult result = validator.validate("echo hello", null, OperatingSystem.LINUX);
        assertFalse(result.isBlocked());
    }
    
    @Test
    public void testWildcardMatching() {
        // 添加带通配符的黑名单
        UnifiedPluginConfig.BlacklistConfig testConfig = new UnifiedPluginConfig.BlacklistConfig();
        testConfig.setLinuxBlacklist(List.of(
            "rm -rf /*",
            "sudo *"
        ));
        testConfig.setUniversal(new ArrayList<>());
        UnifiedConfigStore.getInstance().setBlacklist(testConfig);
        
        // 通配符匹配
        assertTrue(validator.validate("rm -rf /tmp", null, OperatingSystem.LINUX).isBlocked());
        assertTrue(validator.validate("sudo rm /", null, OperatingSystem.LINUX).isBlocked());
        
        // 非匹配
        assertFalse(validator.validate("rm -rf", null, OperatingSystem.LINUX).isBlocked());
    }
    
    @Test
    public void testContainsMatching() {
        // 默认应该支持包含匹配
        UnifiedPluginConfig.BlacklistConfig testConfig = new UnifiedPluginConfig.BlacklistConfig();
        testConfig.setLinuxBlacklist(List.of(
            ":(){ :|: & };:"
        ));
        testConfig.setUniversal(new ArrayList<>());
        UnifiedConfigStore.getInstance().setBlacklist(testConfig);
        
        assertTrue(validator.validate(":(){ :|: & };:", null, OperatingSystem.LINUX).isBlocked());
    }
    
    @Test
    public void testLinuxBlacklist() {
        // Linux 黑名单测试
        assertTrue(validator.validate("shutdown", null, OperatingSystem.LINUX).isBlocked());
        assertFalse(validator.validate("ls -la", null, OperatingSystem.LINUX).isBlocked());
    }
    
    @Test
    public void testWindowsBlacklist() {
        // Windows 黑名单测试
        assertTrue(validator.validate("format", null, OperatingSystem.WINDOWS).isBlocked());
        assertFalse(validator.validate("echo hello", null, OperatingSystem.WINDOWS).isBlocked());
    }
    
    @Test
    public void testRmRfDangerousPatterns() {
        // rm -rf / 的危险模式测试
        assertTrue(validator.validate("rm -rf /", null, OperatingSystem.LINUX).isBlocked());
        assertTrue(validator.validate("rm -rf /*", null, OperatingSystem.LINUX).isBlocked());
        assertTrue(validator.validate("rm -rf *", null, OperatingSystem.LINUX).isBlocked());
    }
    
    @Test
    public void testDdIfPattern() {
        // dd if= 危险模式测试
        assertTrue(validator.validate("dd if=/dev/zero of=/dev/sda", null, OperatingSystem.LINUX).isBlocked());
        assertTrue(validator.validate("dd if=/home/user/file", null, OperatingSystem.LINUX).isBlocked());
    }
}
