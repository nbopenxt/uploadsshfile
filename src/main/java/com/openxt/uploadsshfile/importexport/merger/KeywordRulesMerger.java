package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.model.KeywordRules;

import java.util.*;

/**
 * 关键词规则合并器。
 * 各关键词列表取 A ∪ B 并集去重。
 */
public class KeywordRulesMerger implements ConfigMerger<KeywordRules> {

    private final MergeCounts counts = new MergeCounts();

    @Override
    public KeywordRules merge(KeywordRules exported, KeywordRules current, RemapContext ctx) {
        if (exported == null) return current;
        if (current == null) current = new KeywordRules();

        int mergedCount = 0;

        // 合并失败关键词
        if (exported.getFailKeywords() != null) {
            Set<String> merged = new LinkedHashSet<>(current.getFailKeywords() != null ? current.getFailKeywords() : List.of());
            merged.addAll(exported.getFailKeywords());
            current.setFailKeywords(new ArrayList<>(merged));
            mergedCount += merged.size();
        }

        // 合并成功关键词
        if (exported.getSuccessKeywords() != null) {
            Set<String> merged = new LinkedHashSet<>(current.getSuccessKeywords() != null ? current.getSuccessKeywords() : List.of());
            merged.addAll(exported.getSuccessKeywords());
            current.setSuccessKeywords(new ArrayList<>(merged));
            mergedCount += merged.size();
        }

        counts.setAdded(mergedCount);
        return current;
    }

    @Override
    public MergeCounts getMergeCounts() { return counts; }
}
