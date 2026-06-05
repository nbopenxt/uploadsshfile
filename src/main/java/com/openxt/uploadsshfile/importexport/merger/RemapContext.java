package com.openxt.uploadsshfile.importexport.merger;

import java.util.HashMap;
import java.util.Map;

/**
 * ID 重映射上下文。
 * 在导入流程中逐步建立 {A旧ID → B新ID} 映射表，供后续合并步骤使用。
 */
public class RemapContext {
    private final Map<String, String> serverIdMap = new HashMap<>();
    private final Map<String, String> pathIdMap = new HashMap<>();
    private final Map<String, String> commandConfigIdMap = new HashMap<>();
    private final Map<String, String> batchTaskIdMap = new HashMap<>();

    public void putServer(String oldId, String newId) { serverIdMap.put(oldId, newId); }
    public String resolveServerId(String oldId) { return serverIdMap.get(oldId); }

    public void putPath(String oldId, String newId) { pathIdMap.put(oldId, newId); }
    public String resolvePathId(String oldId) { return pathIdMap.get(oldId); }

    public void putCommandConfig(String oldId, String newId) { commandConfigIdMap.put(oldId, newId); }
    public String resolveCommandConfigId(String oldId) { return commandConfigIdMap.get(oldId); }

    public void putBatchTask(String oldId, String newId) { batchTaskIdMap.put(oldId, newId); }
    public String resolveBatchTaskId(String oldId) { return batchTaskIdMap.get(oldId); }

    public Map<String, String> getServerIdMap() { return serverIdMap; }
    public Map<String, String> getPathIdMap() { return pathIdMap; }
    public Map<String, String> getCommandConfigIdMap() { return commandConfigIdMap; }
    public Map<String, String> getBatchTaskIdMap() { return batchTaskIdMap; }
}
