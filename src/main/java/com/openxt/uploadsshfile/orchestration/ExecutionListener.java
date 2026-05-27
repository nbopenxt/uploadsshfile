package com.openxt.uploadsshfile.orchestration;

import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.model.ExecutionSummary;

/**
 * 执行监听器接口
 * 用于接收命令执行过程中的事件回调
 */
public interface ExecutionListener {
    
    /**
     * 开始执行
     * @param totalCommands 命令总数
     */
    void onStart(int totalCommands);
    
    /**
     * 命令开始执行
     * @param index    当前命令索引（从1开始）
     * @param total    命令总数
     * @param command  执行的命令
     */
    void onCommandStart(int index, int total, String command);
    
    /**
     * 命令执行成功
     * @param index   当前命令索引
     * @param total   命令总数
     * @param command 执行的命令
     * @param result  执行结果
     */
    void onCommandSuccess(int index, int total, String command, CommandResult result);
    
    /**
     * 命令执行失败
     * @param index   当前命令索引
     * @param total   命令总数
     * @param command 执行的命令
     * @param result  执行结果
     */
    void onCommandFailed(int index, int total, String command, CommandResult result);
    
    /**
     * 命令被拦截（黑名单）
     * @param index   当前命令索引
     * @param total   命令总数
     * @param reason  拦截原因
     */
    void onBlocked(int index, int total, String reason);
    
    /**
     * 执行完成
     * @param summary 执行摘要
     */
    void onComplete(ExecutionSummary summary);
    
    /**
     * 执行错误
     * @param errorMessage 错误信息
     */
    void onError(String errorMessage);
}
