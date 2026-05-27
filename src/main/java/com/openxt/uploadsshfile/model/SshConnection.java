package com.openxt.uploadsshfile.model;

import com.openxt.uploadsshfile.config.ServerConfig;

/**
 * SSH 连接信息
 * 封装 SSH 连接所需的参数
 */
public class SshConnection {
    
    private String serverId;
    private String host;
    private int port;
    private String username;
    private String password;
    private String osType; // 操作系统类型
    
    public SshConnection() {
    }
    
    public SshConnection(String serverId, String host, int port, String username, String password) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
    }
    
    public SshConnection(String serverId, String host, int port, String username, String password, String osType) {
        this.serverId = serverId;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.osType = osType;
    }
    
    /**
     * 从 ServerConfig 创建 SshConnection
     */
    public static SshConnection fromServerConfig(ServerConfig config, String password) {
        return new SshConnection(
            config.getId(),
            config.getHost(),
            config.getPort(),
            config.getUsername(),
            password,
            config.getOsType()
        );
    }
    
    public String getServerId() {
        return serverId;
    }
    
    public void setServerId(String serverId) {
        this.serverId = serverId;
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
    
    @Override
    public String toString() {
        return "SshConnection{" +
                "serverId='" + serverId + '\'' +
                ", host='" + host + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                '}';
    }
}
