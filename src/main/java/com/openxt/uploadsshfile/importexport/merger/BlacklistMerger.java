package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.model.UnifiedPluginConfig.BlacklistConfig;

import java.util.*;

/**
 * 黑名单合并器。
 * 按 OS Key 合并字符串列表取并集去重。
 */
public class BlacklistMerger implements ConfigMerger<BlacklistConfig> {

    private final MergeCounts counts = new MergeCounts();

    @Override
    public BlacklistConfig merge(BlacklistConfig exported, BlacklistConfig current, RemapContext ctx) {
        int mergedCount = 0;

        // 合并 osSpecific 命令黑名单
        if (exported.getOsSpecific() != null && current.getOsSpecific() != null) {
            for (String osKey : exported.getOsSpecific().keySet()) {
                List<String> exportedList = exported.getOsSpecific().get(osKey);
                List<String> currentList = current.getOsSpecific().computeIfAbsent(osKey, k -> new ArrayList<>());
                Set<String> merged = new LinkedHashSet<>(currentList);
                if (exportedList != null) {
                    merged.addAll(exportedList);
                }
                currentList.clear();
                currentList.addAll(merged);
                mergedCount += merged.size();
            }
        }

        // 合并 osSpecificPaths 目录黑名单
        if (exported.getOsSpecificPaths() != null && current.getOsSpecificPaths() != null) {
            for (String osKey : exported.getOsSpecificPaths().keySet()) {
                List<String> exportedList = exported.getOsSpecificPaths().get(osKey);
                List<String> currentList = current.getOsSpecificPaths().computeIfAbsent(osKey, k -> new ArrayList<>());
                Set<String> merged = new LinkedHashSet<>(currentList);
                if (exportedList != null) {
                    merged.addAll(exportedList);
                }
                currentList.clear();
                currentList.addAll(merged);
                mergedCount += merged.size();
            }
        }

        counts.setAdded(mergedCount);
        return current;
    }

    @Override
    public MergeCounts getMergeCounts() { return counts; }
}
