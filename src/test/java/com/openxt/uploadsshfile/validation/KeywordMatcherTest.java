package com.openxt.uploadsshfile.validation;

import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.model.EvaluationResult;
import com.openxt.uploadsshfile.model.OperatingSystem;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.util.PluginPathManager;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * 关键词匹配器单元测试
 */
public class KeywordMatcherTest {
    
    private KeywordMatcher matcher;
    
    @Before
    public void setUp() {
        // D-29 修 M1 潜伏雷（同 BlacklistValidatorTest）：本类经 KeywordMatcher→
        // UnifiedConfigStore→PluginPathManager 依赖注入态却从未自行 initialize——
        // 此前"全绿"依赖其它测试先置全局态；采用测试 JVM 单一约定值跨类同值幂等合并
        PluginPathManager.initialize(
                java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "uploadsshfile-test-inject"),
                java.nio.file.Paths.get(System.getProperty("java.io.tmpdir"), "uploadsshfile-test-inject"));
        // D-29：本类断言依赖英文 reason 文案（"Exit code: 1"）——语言单例是记录在案的全量跑
        // 污染源（tmp 遗留 json/前序测试切语言均可致 reason 本地化），测试自带语言态、
        // 与全局隔离（forceLanguageNoPersist 仅内存不落盘，CLI 同源用法 Main.java:75）
        com.openxt.uploadsshfile.i18n.LanguageManager.getInstance().forceLanguageNoPersist("en");
        matcher = new KeywordMatcher();
    }
    
    @After
    public void tearDown() {
        UnifiedConfigStore.resetInstance();
    }
    
    @Test
    public void testExitCodeFailure() {
        // 返回码非0判定失败
        CommandResult result = new CommandResult(1, "done", "", 100);
        EvaluationResult evaluation = matcher.evaluate(result, OperatingSystem.LINUX);
        
        assertFalse(evaluation.isSuccess());
        assertTrue(evaluation.getReason().contains("Exit code: 1"));
    }
    
    @Test
    public void testExitCodeSuccess() {
        // 返回码0 = 成功
        CommandResult result = new CommandResult(0, "task completed successfully", "", 100);
        EvaluationResult evaluation = matcher.evaluate(result, OperatingSystem.LINUX);
        
        assertTrue(evaluation.isSuccess());
    }
    
    @Test
    public void testFailKeywordDetection() {
        // 失败关键词检测
        CommandResult result = new CommandResult(0, "error occurred during execution", "", 100);
        EvaluationResult evaluation = matcher.evaluate(result, OperatingSystem.LINUX);
        
        assertFalse(evaluation.isSuccess());
        assertTrue(evaluation.getReason().contains("error"));
    }
    
    @Test
    public void testCaseInsensitiveMatching() {
        // 关键词匹配应该忽略大小写
        CommandResult result = new CommandResult(0, "ERROR detected", "", 100);
        EvaluationResult evaluation = matcher.evaluate(result, OperatingSystem.LINUX);
        
        assertFalse(evaluation.isSuccess());
    }
    
    @Test
    public void testStderrIncluded() {
        // stderr 也应该被检查
        CommandResult result = new CommandResult(0, "stdout output", "error in stderr", 100);
        EvaluationResult evaluation = matcher.evaluate(result, OperatingSystem.LINUX);
        
        assertFalse(evaluation.isSuccess());
        assertTrue(evaluation.getReason().contains("error"));
    }
    
    @Test
    public void testWindowsOsType() {
        // Windows 操作系统测试
        CommandResult result = new CommandResult(0, "operation completed successfully", "", 100);
        EvaluationResult evaluation = matcher.evaluate(result, OperatingSystem.WINDOWS);
        
        assertTrue(evaluation.isSuccess());
    }
    
    @Test
    public void testUniversalFailKeywords() {
        // 通用失败关键词测试
        CommandResult result = new CommandResult(0, "permission denied", "", 100);
        EvaluationResult evaluation = matcher.evaluate(result, OperatingSystem.LINUX);
        
        assertFalse(evaluation.isSuccess());
    }
    
    @Test
    public void testNoOsTypeSpecified() {
        // 未指定 OS 类型，使用默认行为
        CommandResult result = new CommandResult(0, "done", "", 100);
        EvaluationResult evaluation = matcher.evaluate(result, (OperatingSystem) null);
        
        assertTrue(evaluation.isSuccess());
    }
}
