package com.openxt.uploadsshfile.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 日志工具类
 */
public class Logger {
    /** 是否输出日志到文件 */
    public static boolean IsLogOutput = false;

    /** 日志文件目录 */
    private static String logDirectory = System.getProperty("user.home") + File.separator + ".uploadsshfile" + File.separator + "logs" + File.separator;

    /** 当前日志文件 */
    private static File currentLogFile;

    /** 日期格式化 */
    private static final SimpleDateFormat FILE_DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmmss");
    private static final SimpleDateFormat LOG_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 初始化日志文件
     */
    private static synchronized void initLogFile() {
        if (currentLogFile == null) {
            File dir = new File(logDirectory);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                System.out.println("[UploadSSHFile] Creating log directory: " + dir.getAbsolutePath() + " -> " + created);
            }
            String fileName = "uploadsshfile_" + FILE_DATE_FORMAT.format(new Date()) + ".log";
            currentLogFile = new File(dir, fileName);
            // 在控制台打印日志文件的绝对路径，方便查找
            System.out.println("[UploadSSHFile] Log file location: " + currentLogFile.getAbsolutePath());
            System.out.println("[UploadSSHFile] IsLogOutput is set to: " + IsLogOutput);
        }
    }

    /**
     * 打印 DEBUG 级别日志
     */
    public static void debug(String tag, String message) {
        if (IsLogOutput) {
            log("DEBUG", tag, message);
        } else {
            System.out.println("[UploadSSHFile] [DEBUG] [" + tag + "] " + message + " (Skipped due to IsLogOutput=false)");
        }
    }

    /**
     * 打印 DEBUG 级别日志（使用默认标签）
     */
    public static void debug(String message) {
        debug("SSH", message);
    }

    /**
     * 打印 INFO 级别日志
     */
    public static void info(String tag, String message) {
        if (IsLogOutput) {
            log("INFO", tag, message);
        } else {
            System.out.println("[UploadSSHFile] [INFO] [" + tag + "] " + message + " (Skipped due to IsLogOutput=false)");
        }
    }

    /**
     * 打印 INFO 级别日志（使用默认标签）
     */
    public static void info(String message) {
        info("SSH", message);
    }

    /**
     * 打印 WARN 级别日志
     */
    public static void warn(String tag, String message) {
        if (IsLogOutput) {
            log("WARN", tag, message);
        }
    }

    /**
     * 打印 WARN 级别日志（使用默认标签）
     */
    public static void warn(String message) {
        warn("SSH", message);
    }

    /**
     * 打印 ERROR 级别日志
     */
    public static void error(String tag, String message) {
        if (IsLogOutput) {
            log("ERROR", tag, message);
        } else {
            System.err.println("[UploadSSHFile] [ERROR] [" + tag + "] " + message + " (Skipped due to IsLogOutput=false)");
        }
    }

    /**
     * 打印 ERROR 级别日志（使用默认标签）
     */
    public static void error(String message) {
        error("SSH", message);
    }

    /**
     * 打印 ERROR 级别日志（带异常）
     */
    public static void error(String tag, String message, Throwable e) {
        if (IsLogOutput) {
            log("ERROR", tag, message + "\n" + getStackTrace(e));
        }
    }

    /**
     * 内部日志方法
     */
    private static void log(String level, String tag, String message) {
        initLogFile();

        String timestamp = LOG_TIME_FORMAT.format(new Date());
        String logLine = String.format("[%s] [%s] [%s] %s", timestamp, level, tag, message);

        // 输出到控制台（IDEA Run 面板可见）
        System.out.println(logLine);

        // 输出到日志文件
        try (FileWriter fw = new FileWriter(currentLogFile, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(logLine);
            pw.flush();
        } catch (IOException e) {
            System.err.println("Failed to write log to " + currentLogFile.getAbsolutePath() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取异常的堆栈信息
     */
    private static String getStackTrace(Throwable t) {
        StringBuilder sb = new StringBuilder();
        for (StackTraceElement element : t.getStackTrace()) {
            sb.append("    at ").append(element.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 设置日志目录
     */
    public static void setLogDirectory(String directory) {
        Logger.logDirectory = directory;
        currentLogFile = null; // 重置，以便下次创建新文件
    }

    /**
     * 设置日志开关
     */
    public static void setLogOutput(boolean enabled) {
        IsLogOutput = enabled;
    }
}
