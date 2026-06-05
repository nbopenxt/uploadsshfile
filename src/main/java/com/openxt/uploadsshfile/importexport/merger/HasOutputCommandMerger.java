package com.openxt.uploadsshfile.importexport.merger;

import com.openxt.uploadsshfile.model.UnifiedPluginConfig.CommandOutputConfig;

import java.util.*;

/**
 * 有返回命令合并器。
 * 按 OS Key 合并字符串列表取并集去重。
 */
public class HasOutputCommandMerger implements ConfigMerger<CommandOutputConfig> {

    private final MergeCounts counts = new MergeCounts();

    @Override
    public CommandOutputConfig merge(CommandOutputConfig exported, CommandOutputConfig current, RemapContext ctx) {
        if (exported == null) return current;
        if (current == null) current = new CommandOutputConfig();

        int totalMerged = 0;

        String[] osKeys = {"linux", "windows", "macos", "ubuntu", "debian", "centos", "rocky", "alpine", "rhel"};
        for (String osKey : osKeys) {
            List<String> exportedList = exported.getCommandsForOsKey(osKey);
            List<String> currentList = current.getCommandsForOsKey(osKey);
            Set<String> merged = new LinkedHashSet<>(currentList);
            merged.addAll(exportedList);
            currentList.clear();
            currentList.addAll(merged);
            totalMerged += merged.size();
        }

        counts.setAdded(totalMerged);
        return current;
    }

    @Override
    public MergeCounts getMergeCounts() { return counts; }
}
