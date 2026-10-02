package com.openxt.uploadsshfile.config;

import java.util.UUID;

/**
 * 路径配置实体
 */
public class PathConfig {
    private String id;
    private String serverId;
    private String remotePath;
    private long createTime;
    private long updateTime;

    /**
     * 1.0.8 / FR-16（P-03 目录属性、清单④）：上传前若远端目录不存在则逐级自动创建（mkdirsRemote）。
     * 默认 false＝保持 1.0.7 "cd 失败即上传失败"行为（G5）；老 JSON 缺字段时 Gson 补 false，无需迁移。
     */
    private boolean autoCreateRemoteDir = false;

    public PathConfig() {
        this.id = UUID.randomUUID().toString();
        this.createTime = System.currentTimeMillis();
        this.updateTime = this.createTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getRemotePath() {
        return remotePath;
    }

    public void setRemotePath(String remotePath) {
        this.remotePath = remotePath;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }

    public boolean isAutoCreateRemoteDir() {
        return autoCreateRemoteDir;
    }

    public void setAutoCreateRemoteDir(boolean autoCreateRemoteDir) {
        this.autoCreateRemoteDir = autoCreateRemoteDir;
    }

    @Override
    public String toString() {
        return remotePath != null ? remotePath : "";
    }
}
