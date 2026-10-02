package com.openxt.uploadsshfile.config;

import com.openxt.uploadsshfile.model.OperatingSystem;

import java.util.UUID;

/**
 * 服务器配置实体
 */
public class ServerConfig {
    private String id;
    private String name;
    private String host;
    private int port = 22;
    private String username;
    private String password;
    private String osType = "linux"; // 操作系统类型，默认 linux
    private long createTime;
    private long updateTime;

    public ServerConfig() {
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
    
    public String getOsType() {
        return osType;
    }
    
    public void setOsType(String osType) {
        this.osType = osType;
    }
    
    /**
     * 获取操作系统枚举
     */
    public OperatingSystem getOperatingSystem() {
        return OperatingSystem.fromKey(this.osType);
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

    @Override
    public String toString() {
        return name;
    }
}
