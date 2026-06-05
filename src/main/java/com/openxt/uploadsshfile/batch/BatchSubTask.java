package com.openxt.uploadsshfile.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 批处理子任务
 * 组合了 服务器 + 上传路径 + 文件列表 + 命令组
 */
public class BatchSubTask {
    private String id;
    private String serverId;
    private String pathId;
    private String commandConfigId;  // 可为 null（不执行命令组）
    private List<String> filePaths;
    private int order;

    public BatchSubTask() {
        this.id = UUID.randomUUID().toString();
        this.filePaths = new ArrayList<>();
        this.order = 0;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getServerId() { return serverId; }
    public void setServerId(String serverId) { this.serverId = serverId; }

    public String getPathId() { return pathId; }
    public void setPathId(String pathId) { this.pathId = pathId; }

    public String getCommandConfigId() { return commandConfigId; }
    public void setCommandConfigId(String commandConfigId) { this.commandConfigId = commandConfigId; }

    public List<String> getFilePaths() { return filePaths; }
    public void setFilePaths(List<String> filePaths) { this.filePaths = filePaths; }

    public int getOrder() { return order; }
    public void setOrder(int order) { this.order = order; }
}
