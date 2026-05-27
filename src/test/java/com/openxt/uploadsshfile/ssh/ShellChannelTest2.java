package com.openxt.uploadsshfile.ssh;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.openxt.uploadsshfile.TestConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.sftp.SftpService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SSH Shell Channel 测试类 - Windows 服务器版本
 *
 * 功能：生成时间戳文件 → SFTP上传 → 执行批处理
 */
public class ShellChannelTest2 {

    // 服务器配置 - 实际值在 TestConfig 中统一管理
    private static final String HOST = TestConfig.WINDOWS_B_HOST;
    private static final int PORT = TestConfig.WINDOWS_B_PORT;
    private static final String USER = TestConfig.WINDOWS_B_USER;
    private static final String PASSWORD = TestConfig.WINDOWS_B_PASSWORD;
    private static final String REMOTE_PATH = TestConfig.WINDOWS_B_REMOTE_PATH;  // Windows OpenSSH SFTP 路径格式

    private static final Charset SSH_CHARSET = StandardCharsets.UTF_8;
    private static final Pattern EXIT_CODE_PATTERN = Pattern.compile("__EXIT_CODE__=(\\d+)");

    private static InputStream rawIn;
    private static OutputStream rawOut;

    public static void main(String[] args) {
        try {
            // 1. 生成时间戳文件
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            File localFile = createTimestampFile(timestamp);
            System.out.println("本地文件已生成: " + localFile.getAbsolutePath());
            System.out.println("时间戳内容: " + timestamp);

            // 2. 连接服务器
            System.out.println("\n=== 连接服务器 ===");
            JSch jsch = new JSch();
            Session session = jsch.getSession(USER, HOST, PORT);
            session.setPassword(PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setServerAliveInterval(30000);
            session.setServerAliveCountMax(10);
            session.connect(10000);
            System.out.println("Session 连接成功");

            // 3. SFTP 上传文件
            System.out.println("\n=== SFTP 上传文件 ===");
            ServerConfig config = new ServerConfig();
            config.setHost(HOST);
            config.setPort(PORT);
            config.setUsername(USER);

            SftpService sftpService = new SftpService();
            sftpService.connect(config, PASSWORD);

            // 先 cd 到目标目录
            String currentDir = sftpService.pwd();
            System.out.println("SFTP 当前目录: " + currentDir);
            sftpService.cd(REMOTE_PATH);

            sftpService.uploadFile(localFile, ".", new SftpService.UploadProgressCallback() {
                @Override
                public void onProgress(String fileName, int percent, long uploaded, long total) {
                    System.out.println("上传进度: " + percent + "%");
                }

                @Override
                public void onFileCompleted(String fileName, long size, boolean success, String errorMessage) {
                    if (success) {
                        System.out.println("上传成功: " + fileName + " (" + size + " bytes)");
                    } else {
                        System.out.println("上传失败: " + errorMessage);
                    }
                }
            });

            // 4. Shell 执行命令
            System.out.println("\n=== Shell 执行命令 ===");
            ChannelShell channel = (ChannelShell) session.openChannel("shell");
            channel.setPtyType("xterm", 120, 30, 0, 0);
            channel.setPtySize(120, 30, 0, 0);
            channel.setInputStream(null);
            channel.setOutputStream(null);
            channel.connect();
            System.out.println("ChannelShell 连接成功");

            rawIn = channel.getInputStream();
            rawOut = channel.getOutputStream();

            // 初始化
            Thread.sleep(1000);
            drainInput();
            execCommand("@echo off");
            Thread.sleep(500);
            drainInput();

            // 执行命令组
            execCommand("e:");
            Thread.sleep(300);

            execCommand("cd e:\\temp");
            Thread.sleep(300);

            execCommand("copyfile.bat a.txt");

            // 清理
            sftpService.disconnect();
            channel.disconnect();
            session.disconnect();
            localFile.delete();

            System.out.println("\n=== 测试完成 ===");

        } catch (Exception e) {
            System.err.println("测试异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 生成时间戳文件
     */
    private static File createTimestampFile(String timestamp) throws Exception {
        File file = new File("a.txt");
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(timestamp);
        }
        return file;
    }

    /**
     * 清空输入流
     */
    private static void drainInput() throws Exception {
        Thread.sleep(100);
        while (rawIn.available() > 0) {
            rawIn.read();
        }
    }

    /**
     * 执行命令并打印输出
     */
    private static void execCommand(String command) throws Exception {
        String fullCommand = command + " & echo __EXIT_CODE__=%ERRORLEVEL%\r\n";
        rawOut.write(fullCommand.getBytes(SSH_CHARSET));
        rawOut.flush();

        System.out.println(">>> " + command);

        long startTime = System.currentTimeMillis();
        long maxWaitTime = 60000;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            while (rawIn.available() > 0) {
                baos.write(rawIn.read());
            }

            String output = baos.toString(SSH_CHARSET);
            Matcher matcher = EXIT_CODE_PATTERN.matcher(output);
            if (matcher.find()) {
                System.out.println("完成，耗时: " + (System.currentTimeMillis() - startTime) + "ms");
                printFilteredOutput(output.substring(0, matcher.start()));
                return;
            }

            Thread.sleep(50);
        }

        System.out.println("命令执行超时（60秒）");
    }

    /**
     * 过滤并打印输出
     */
    private static void printFilteredOutput(String output) {
        String normalized = output.replace("\r", "");
        StringBuilder filtered = new StringBuilder();
        boolean lastWasEmpty = false;

        for (String line : normalized.split("\n")) {
            if (line.trim().isEmpty()) {
                if (!lastWasEmpty) {
                    filtered.append("\n");
                    lastWasEmpty = true;
                }
            } else {
                filtered.append(line).append("\n");
                lastWasEmpty = false;
            }
        }

        String result = filtered.toString().trim();
        if (!result.isEmpty()) {
            System.out.println("输出:");
            System.out.println(result);
        }
    }
}
