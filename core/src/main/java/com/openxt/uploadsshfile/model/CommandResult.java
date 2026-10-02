package com.openxt.uploadsshfile.model;

/**
 * 命令执行结果
 */
public class CommandResult {
    
    private int exitCode;
    private String stdout;
    private String stderr;
    private long durationMs;
    
    public CommandResult() {
    }
    
    public CommandResult(int exitCode, String stdout, String stderr, long durationMs) {
        this.exitCode = exitCode;
        this.stdout = stdout;
        this.stderr = stderr;
        this.durationMs = durationMs;
    }
    
    /**
     * 创建成功结果
     */
    public static CommandResult success(String stdout, long durationMs) {
        return new CommandResult(0, stdout, "", durationMs);
    }
    
    /**
     * 创建失败结果
     */
    public static CommandResult failed(int exitCode, String stdout, String stderr, long durationMs) {
        return new CommandResult(exitCode, stdout, stderr, durationMs);
    }
    
    public int getExitCode() {
        return exitCode;
    }
    
    public void setExitCode(int exitCode) {
        this.exitCode = exitCode;
    }
    
    public String getStdout() {
        return stdout;
    }
    
    public void setStdout(String stdout) {
        this.stdout = stdout;
    }
    
    public String getStderr() {
        return stderr;
    }
    
    public void setStderr(String stderr) {
        this.stderr = stderr;
    }
    
    public long getDurationMs() {
        return durationMs;
    }
    
    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
    
    public boolean isSuccess() {
        return exitCode == 0;
    }
    
    @Override
    public String toString() {
        return "CommandResult{" +
                "exitCode=" + exitCode +
                ", stdout='" + stdout + '\'' +
                ", stderr='" + stderr + '\'' +
                ", durationMs=" + durationMs +
                '}';
    }
}
