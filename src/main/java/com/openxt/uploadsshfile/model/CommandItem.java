package com.openxt.uploadsshfile.model;

import java.util.UUID;

/**
 * 命令项
 * 表示一个待执行的命令
 */
public class CommandItem {
    
    private String id;
    private String command;
    private boolean enabled;
    private int order;
    
    public CommandItem() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
        this.order = 0;
    }
    
    public CommandItem(String command) {
        this();
        this.command = command;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getCommand() {
        return command;
    }
    
    public void setCommand(String command) {
        this.command = command;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getOrder() {
        return order;
    }
    
    public void setOrder(int order) {
        this.order = order;
    }
}
