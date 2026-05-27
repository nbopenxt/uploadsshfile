package com.openxt.uploadsshfile.ssh;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.openxt.uploadsshfile.TestConfig;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SSH Shell Channel 测试类
 */
public class ShellChannelTest {
    
    private static InputStream in;
    private static OutputStream out;
    private static Pattern EXIT_CODE_PATTERN = Pattern.compile("__EXIT_CODE__=(\\d+)");
    
    public static void main(String[] args) {
        // ===== 服务器配置（实际值在 TestConfig 中统一管理） =====
        String HOST = TestConfig.LINUX_HOST;
        int PORT = TestConfig.LINUX_PORT;
        String USER = TestConfig.LINUX_USER;
        String PASSWORD = TestConfig.LINUX_PASSWORD;
        // ================================
        
        System.out.println("=== SSH Shell Channel 测试 ===");
        System.out.println("目标服务器: " + HOST + ":" + PORT);
        
        JSch jsch = new JSch();
        Session session = null;
        ChannelShell channel = null;
        
        try {
            // 1. 创建 Session
            session = jsch.getSession(USER, HOST, PORT);
            session.setPassword(PASSWORD);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setServerAliveInterval(30000);
            session.setServerAliveCountMax(10);
            session.connect(5000);
            System.out.println("Session 连接成功");
            
            // 2. 获取服务器编码
            String serverLang = getServerLang(jsch, HOST, PORT, USER, PASSWORD);
            System.out.println("服务器编码: " + serverLang);
            
            // 3. 创建 ChannelShell
            channel = (ChannelShell) session.openChannel("shell");
            channel.setPty(true);
            channel.setPtyType("xterm-256color", 120, 40, 0, 0);
            channel.setInputStream(null);
            channel.setOutputStream(null);
            channel.connect();
            System.out.println("ChannelShell 连接成功");
            
            // 4. 获取输入输出流
            in = channel.getInputStream();
            out = channel.getOutputStream();
            
            // 5. 初始化环境（使用服务器实际编码）
            System.out.println("正在初始化 Shell 环境...");
            execCommand("export LANG=" + serverLang);
            execCommand("export LC_ALL=" + serverLang);
            execCommand("source /etc/profile 2>/dev/null");
            execCommand("source ~/.bashrc 2>/dev/null");
            String pwd = execCommand("cd /app/tomcat8/warhis && pwd");
            System.out.println("当前目录: " + pwd);
            
            // 6. 执行 copywar.sh
            System.out.println("\n=== 开始执行 ./copywar.sh ===");
            String rawOutput = execCommand("./copywar.sh");
            
            System.out.println("\n=== 原始输出 ===");
            System.out.println(rawOutput);
            System.out.println("=== 原始输出结束 ===");
            
            // 解析 exitcode
            Matcher matcher = EXIT_CODE_PATTERN.matcher(rawOutput);
            int exitCode = -1;
            if (matcher.find()) {
                exitCode = Integer.parseInt(matcher.group(1));
                System.out.println("Exit code: " + exitCode);
            } else {
                System.out.println("未找到 __EXIT_CODE__ 标记，使用 Channel exit status");
                exitCode = channel.getExitStatus();
                System.out.println("Channel exit status: " + exitCode);
            }
            
            System.out.println("\n=== 测试结果 ===");
            if (exitCode == 0) {
                System.out.println("测试通过！脚本执行成功");
            } else {
                System.out.println("测试失败！Exit code: " + exitCode);
            }
            
        } catch (Exception e) {
            System.err.println("测试异常: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (channel != null) channel.disconnect();
                if (session != null) session.disconnect();
            } catch (Exception ignored) {}
        }
    }
    
    /**
     * 执行命令并等待结果
     * 约定：每个命令后追加 echo __EXIT_CODE__=$?
     * 如果收到了 __EXIT_CODE__=数字，则命令执行完成，返回结果
     */
    private static String execCommand(String command) throws Exception {
        // 清空残留输出
        Thread.sleep(100);
        while (in.available() > 0) {
            in.read();
        }
        
        // 发送命令 + exitcode 检测
        String fullCommand = command + "; echo __EXIT_CODE__=$?\n";
        out.write(fullCommand.getBytes(StandardCharsets.UTF_8));
        out.flush();
        
        System.out.println("执行命令: " + command);
        
        // 读取输出，直到收到 __EXIT_CODE__=数字
        StringBuilder output = new StringBuilder();
        long startTime = System.currentTimeMillis();
        long maxWaitTime = 180000; // 3 分钟超时
        
        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            if (in.available() > 0) {
                int b = in.read();
                if (b == -1) break;
                output.append((char) b);
                
                // 检查是否收到了 exitcode
                String current = output.toString();
                Matcher matcher = EXIT_CODE_PATTERN.matcher(current);
                if (matcher.find()) {
                    System.out.println("命令执行完成，耗时: " + (System.currentTimeMillis() - startTime) + "ms");
                    return current;
                }
            } else {
                Thread.sleep(50);
            }
        }
        
        System.out.println("命令执行超时（3分钟）");
        return output.toString();
    }
    
    private static String getServerLang(JSch jsch, String host, int port, String user, String password) {
        Session langSession = null;
        try {
            langSession = jsch.getSession(user, host, port);
            langSession.setPassword(password);
            langSession.setConfig("StrictHostKeyChecking", "no");
            langSession.connect(3000);
            
            ChannelExec channel = (ChannelExec) langSession.openChannel("exec");
            channel.setCommand("echo $LANG");
            
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            channel.setOutputStream(outputStream);
            channel.connect(1500);
            
            while (!channel.isClosed()) {
                Thread.sleep(10);
            }
            
            String lang = outputStream.toString(StandardCharsets.UTF_8.name()).trim();
            channel.disconnect();
            langSession.disconnect();
            
            return lang.isEmpty() ? "en_US.UTF-8" : lang;
        } catch (Exception e) {
            return "en_US.UTF-8";
        } finally {
            if (langSession != null && langSession.isConnected()) {
                langSession.disconnect();
            }
        }
    }
}
