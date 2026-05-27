package com.openxt.uploadsshfile.logging;

import com.intellij.openapi.diagnostic.Logger;
import com.openxt.uploadsshfile.util.PluginPathManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 按天滚动的日志服务
 * 日志文件命名: uploadsshfile_{yyyyMMdd}.log
 */
public class DailyLogService {
    
    private static final String LOG_PREFIX = "uploadsshfile_";
    private static final String LOG_SUFFIX = ".log";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    
    private final Path logDirectory;
    private final Logger ideaLogger;
    
    public DailyLogService() {
        this(PluginPathManager.getInstance().getLogPath());
    }
    
    public DailyLogService(@NotNull Path logDirectory) {
        this.logDirectory = logDirectory;
        this.ideaLogger = Logger.getInstance(DailyLogService.class);
        
        // 确保日志目录存在
        try {
            Files.createDirectories(logDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create log directory", e);
        }
    }
    
    /**
     * 获取当天的日志文件路径
     */
    public Path getTodayLogPath() {
        String fileName = LOG_PREFIX + LocalDate.now().format(DATE_FORMATTER) + LOG_SUFFIX;
        return logDirectory.resolve(fileName);
    }
    
    /**
     * 写入日志
     */
    public void log(@NotNull LogLevel level, @NotNull String module, 
                    @NotNull String message, Object... params) {
        
        String formattedMessage = formatMessage(level, module, message, params);
        Path logPath = getTodayLogPath();
        
        try {
            String line = formattedMessage + System.lineSeparator();
            Files.writeString(logPath, line, 
                StandardOpenOption.CREATE, 
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            ideaLogger.warn("Failed to write to log file", e);
        }
        
        // 同时输出到 IDEA 日志
        switch (level) {
            case INFO:
                ideaLogger.info(formattedMessage);
                break;
            case WARN:
                ideaLogger.warn(formattedMessage);
                break;
            case ERROR:
                ideaLogger.error(formattedMessage);
                break;
        }
    }
    
    private @NotNull String formatMessage(LogLevel level, @NotNull String module, 
                                          @NotNull String message, Object... params) {
        String timestamp = LocalDateTime.now().format(TIME_FORMATTER);
        
        // 格式化参数
        String formattedParams = "";
        if (params.length > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < params.length; i += 2) {
                if (i + 1 < params.length) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(params[i]).append("=").append(params[i + 1]);
                }
            }
            if (sb.length() > 0) {
                formattedParams = " - " + sb;
            }
        }
        
        return String.format("[%s] [%s] [%s] %s%s", 
            timestamp, level.name(), module, message, formattedParams);
    }
    
    public void info(String module, String message, Object... params) {
        log(LogLevel.INFO, module, message, params);
    }
    
    public void warn(String module, String message, Object... params) {
        log(LogLevel.WARN, module, message, params);
    }
    
    public void error(String module, String message, Object... params) {
        log(LogLevel.ERROR, module, message, params);
    }
    
    public enum LogLevel {
        INFO, WARN, ERROR
    }
}
