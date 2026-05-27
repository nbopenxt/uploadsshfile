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
    private static String logDirectory = "E:\\code\\ideaplugin\\uploadsshfile\\logs\\";

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
                dir.mkdirs();
            }
            String fileName = "uploadsshfile_" + FILE_DATE_FORMAT.format(new Date()) + ".log";
            currentLogFile = new File(dir, fileName);
        }
    }

    /**
     * 打印 DEBUG 级别日志
     */
    public static void debug(String tag, String message) {
        if (IsLogOutput) {
            log("DEBUG", tag, message);
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
            System.err.println("Failed to write log: " + e.getMessage());
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
     * 获取日志目录
     */
    public static String getLogDirectory() {
        return logDirectory;
    }
}
