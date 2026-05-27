package com.openxt.uploadsshfile.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 命令组配置
 * 包含一组待执行的命令，关联到特定的服务器和上传路径
 */
public class CommandConfig {
    
    private String id;
    private String name;                    // 命令组名称
    private String serverId;                // 关联的服务器 ID
    private String pathId;                  // 关联的路径 ID
    private ExecuteTiming executeTiming;     // 执行时机
    private List<CommandItem> commands;     // 命令列表
    private int maxRetries;
    private long commandTimeoutMs;
    private long createTime;
    private long updateTime;
    
    public CommandConfig() {
        this.id = UUID.randomUUID().toString();
        this.name = "";
        this.commands = new ArrayList<>();
        this.executeTiming = ExecuteTiming.NONE;
        this.maxRetries = 3;
        this.commandTimeoutMs = 300000; // 5分钟
        this.createTime = System.currentTimeMillis();
        this.updateTime = System.currentTimeMillis();
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
    
    public String getServerId() {
        return serverId;
    }
    
    public void setServerId(String serverId) {
        this.serverId = serverId;
    }
    
    public String getPathId() {
        return pathId;
    }
    
    public void setPathId(String pathId) {
        this.pathId = pathId;
    }
    
    public ExecuteTiming getExecuteTiming() {
        return executeTiming;
    }
    
    public void setExecuteTiming(ExecuteTiming executeTiming) {
        this.executeTiming = executeTiming;
    }
    
    public List<CommandItem> getCommands() {
        return commands;
    }
    
    public void setCommands(List<CommandItem> commands) {
        this.commands = commands;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
    
    public long getCommandTimeoutMs() {
        return commandTimeoutMs;
    }
    
    public void setCommandTimeoutMs(long commandTimeoutMs) {
        this.commandTimeoutMs = commandTimeoutMs;
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
    
    /**
     * 添加命令
     */
    public void addCommand(CommandItem item) {
        if (this.commands == null) {
            this.commands = new ArrayList<>();
        }
        this.commands.add(item);
    }
    
    /**
     * 获取启用的命令列表（按顺序）
     */
    public List<CommandItem> getEnabledCommands() {
        if (commands == null) {
            return new ArrayList<>();
        }
        return commands.stream()
            .filter(CommandItem::isEnabled)
            .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
            .toList();
    }
    
    /**
     * 从字符串列表设置命令
     */
    public void setCommandsFromStrings(List<String> commandStrings) {
        this.commands = new ArrayList<>();
        for (int i = 0; i < commandStrings.size(); i++) {
            CommandItem item = new CommandItem();
            item.setOrder(i);
            item.setCommand(commandStrings.get(i));
            item.setEnabled(true);
            this.commands.add(item);
        }
    }
    
    @Override
    public String toString() {
        return name != null && !name.isEmpty() ? name : ("CommandGroup-" + id.substring(0, 8));
    }
}
