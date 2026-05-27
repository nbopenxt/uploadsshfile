package com.openxt.uploadsshfile.validation;

import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.model.EvaluationResult;
import com.openxt.uploadsshfile.model.OperatingSystem;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
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
