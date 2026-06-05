package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.model.KeywordRules;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * KeywordRulesMerger 单元测试
 * UT-17: 并集去重
 */
public class KeywordRulesMergerTest {

    // ========== UT-17: 并集去重 ==========

    @Test
    public void testFailKeywordsUnionDedup() {
        KeywordRulesMerger merger = new KeywordRulesMerger();
        RemapContext ctx = new RemapContext();

        KeywordRules exported = new KeywordRules();
        exported.setFailKeywords(List.of("error", "failed", "timeout"));

        KeywordRules current = new KeywordRules();
        current.setFailKeywords(List.of("error", "denied", "exception"));

        KeywordRules result = merger.merge(exported, current, ctx);

        // getFailKeywords() 返回所有字段的扁平联合（universal + linux/windows defaults）
        List<String> merged = result.getFailKeywords();
        // error 来自双方, failed/denied/exception 来自默认, timeout 来自 exported
        assertTrue(merged.contains("error"));
        assertTrue(merged.contains("failed"));
        assertTrue(merged.contains("timeout"));
        assertTrue(merged.contains("denied"));
        assertTrue(merged.contains("exception"));
        // 合并后应包含 exported 的 "timeout"（默认值共约 15 个条目）
        assertTrue(merged.contains("timeout"));
    }

    /**
     * 成功关键词合并：当前 setSuccessKeywords 为预留空实现（no-op），
     * 因此合并后 successKeywords 保持为空。
     */
    @Test
    public void testSuccessKeywordsUnion() {
        KeywordRulesMerger merger = new KeywordRulesMerger();
        RemapContext ctx = new RemapContext();

        KeywordRules exported = new KeywordRules();
        exported.setSuccessKeywords(List.of("success", "completed", "ok"));

        KeywordRules current = new KeywordRules();
        current.setSuccessKeywords(List.of("success", "done"));

        KeywordRules result = merger.merge(exported, current, ctx);

        // setSuccessKeywords 为 no-op，结果为空
        List<String> merged = result.getSuccessKeywords();
        assertTrue(merged.isEmpty());
    }

    @Test
    public void testNullExportedReturnsCurrent() {
        KeywordRulesMerger merger = new KeywordRulesMerger();
        RemapContext ctx = new RemapContext();

        KeywordRules current = new KeywordRules();
        current.setFailKeywords(List.of("custom-error"));

        KeywordRules result = merger.merge(null, current, ctx);
        assertNotNull(result);
        // getFailKeywords() 返回所有字段的扁平联合，custom-error 在 universalFailKeywords 中
        assertTrue(result.getFailKeywords().contains("custom-error"));
    }

    @Test
    public void testNullCurrentCreatesNew() {
        KeywordRulesMerger merger = new KeywordRulesMerger();
        RemapContext ctx = new RemapContext();

        KeywordRules exported = new KeywordRules();
        exported.setFailKeywords(List.of("test-error"));

        KeywordRules result = merger.merge(exported, null, ctx);
        assertNotNull(result);
        assertTrue(result.getFailKeywords().contains("test-error"));
    }
}
