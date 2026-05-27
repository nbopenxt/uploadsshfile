package com.openxt.uploadsshfile.ssh;

import com.jcraft.jsch.ChannelShell;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.model.SshConnection;
import com.openxt.uploadsshfile.util.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SSH 命令执行服务
 * 
 * <p>核心设计：整个上传会话中，所有命令都共用同一个 Shell Channel，
 * 确保命令执行的连贯性（如先 cd 再执行脚本，脚本会在正确的目录下执行）。
 * 
 * <p>命令完成判断逻辑（静默确认策略）：
 * <ul>
 *   <li>等待命令产生输出</li>
 *   <li>输出完成后，等待 2 秒静默确认命令完成</li>
 *   <li>如果长时间没有收到任何输出，认为命令已完成（可能是无输出命令）</li>
 * </ul>
 * 
 * <p><b>异常处理</b>：
 * <ul>
 *   <li>连接断开、连接超时 → ConnectionException（与命令输出无关）</li>
 *   <li>命令执行结果 → CommandResult（exitCode, stdout, stderr）</li>
 * </ul>
 */
public class SshCommandService {
    
    /**
     * Shell 类型枚举
     */
    public enum ShellType {
        BASH,       // Linux Bash / Git Bash / WSL
        ZSH,        // Z Shell
        SH,         // POSIX Shell
        CMD,        // Windows CMD
        POWERSHELL, // Windows PowerShell
        UNKNOWN      // 未知类型
    }
    
    /**
     * 获取退出码的特殊标记（用于区分命令输出和退出码）
     */
    private static final String EXIT_CODE_MARKER = "__EXIT_CODE__=";
    
    /** Exit code 解析正则 */
    private static final Pattern EXIT_CODE_PATTERN = Pattern.compile("__EXIT_CODE__=(\\d+)");
    
    private final JSch jsch;
    
    /** 连接池：serverId -> Session */
    private final Map<String, Session> sessionPool;
    
    /** Shell Channel 池：serverId -> ChannelShell（保持 shell 会话状态） */
    private final Map<String, ChannelShell> shellChannelPool;
    
    /** Shell 类型池：serverId -> ShellType */
    private final Map<String, ShellType> shellTypePool;
    
    /** 服务器编码池：serverId -> LANG (如 en_US.UTF-8) */
    private final Map<String, String> serverLangPool;
    
    /** 允许连接的主机白名单（请在需要时填写实际 IP 地址） */
    private static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
            // "10.55.3.21",
            // "192.168.1.101",
            // "10.55.3.18"
    ));
    
    /** 
     * 是否启用主机白名单验证
     * 正式环境默认禁用，在测试代码中，直接设置为true，即可限制连接
     */
    public static boolean isHostWhitelistEnabled=false;

    /**
     * Windows OpenSSH ConPTY 强制输出的编码
     */
    private static final java.nio.charset.Charset WINDOWS_CONPTY_CHARSET = java.nio.charset.StandardCharsets.UTF_8;
    
    /** Shell Channel 的静默确认时间（毫秒）：输出完成后等待 2 秒确认命令完成 */
    private static final long SILENT_CONFIRM_TIMEOUT_MS = 2000;
    
    public SshCommandService() {
        this.jsch = new JSch();
        this.sessionPool = new ConcurrentHashMap<>();
        this.shellChannelPool = new ConcurrentHashMap<>();
        this.shellTypePool = new ConcurrentHashMap<>();
        this.serverLangPool = new ConcurrentHashMap<>();
    }
    
    /**
     * 验证主机是否在白名单中
     */
    private void validateHost(@NotNull String host) throws SecurityException {
        if (isHostWhitelistEnabled && !ALLOWED_HOSTS.contains(host)) {
            throw new SecurityException(
                "Connection to host '" + host + "' is not allowed. " +
                "Only the following hosts are permitted: " + ALLOWED_HOSTS
            );
        }
    }
    
    /**
     * 验证 SSH 连接是否安全
     */
    private void validateConnection(@NotNull SshConnection connection) throws SecurityException {
        validateHost(connection.getHost());
        
        // 禁止 root 用户远程连接（安全考虑）
        if ("root".equalsIgnoreCase(connection.getUsername())) {
            Logger.warn("[Security Warning] Root user SSH detected. Consider using a non-root user for better security.");
        }
    }

    /**
     * 判断是否为 Windows 服务器
     */
    private boolean isWindowsServer(@NotNull SshConnection connection) {
        String osType = connection.getOsType();
        if (osType != null && !osType.isEmpty()) {
            return "windows".equalsIgnoreCase(osType);
        }
        // 如果没有明确指定，默认为 Linux
        return false;
    }
    
    /**
     * 使用 Shell Channel 执行命令（保持 shell 会话状态）
     * 
     * <p><b>核心设计</b>：整个上传会话中，所有命令都共用同一个 Shell Channel，
     * 确保命令执行的连贯性。
     *
     * <p><b>命令完成判断</b>：
     * <ul>
     *   <li>等待命令产生输出</li>
     *   <li>输出完成后，等待 2 秒静默确认命令完成</li>
     *   <li>如果长时间没有收到任何输出，认为命令已完成（可能是无输出命令）</li>
     * </ul>
     *
     * <p><b>异常处理</b>：
     * <ul>
     *   <li>连接断开、连接超时 → ConnectionException（与命令输出无关）</li>
     *   <li>命令执行结果 → CommandResult（exitCode, stdout, stderr）</li>
     * </ul>
     *
     * @param connection SSH 连接信息
     * @param command    要执行的命令
     * @return 命令执行结果
     */
    public @NotNull CommandResult executeWithShell(@NotNull SshConnection connection, 
                                                   @NotNull String command) throws Exception {
        Logger.debug("====== executeWithShell 开始 ======");
        Logger.debug("连接: " + connection.getHost() + ":" + connection.getPort());
        Logger.debug("用户: " + connection.getUsername());
        Logger.debug("serverId: " + connection.getServerId());
        
        validateConnection(connection);
        
        // 统一使用 Shell Channel：所有命令都通过同一个 Shell Channel 执行
        String key = connection.getServerId();
        ChannelShell channel = shellChannelPool.get(key);
        
        // 如果没有可用的 Shell Channel，创建一个新的
        if (channel == null || !channel.isConnected()) {
            Logger.debug("需要创建新的 Channel");
            Session session = getOrCreateSession(connection);
            Logger.debug("Session 已建立");
            
            // 获取服务器编码（动态获取）
            String serverLang = getServerLang(session);
            serverLangPool.put(key, serverLang);
            Logger.debug("服务器编码: " + serverLang);
            
            channel = (ChannelShell) session.openChannel("shell");
            
            // ========== 关键配置：模拟 MobaXterm 终端环境，解决 exit code 8 ==========
            // 开启 PTY 伪终端（必加，解决交互式命令、脚本执行报错）
            channel.setPty(true);
            // 设置终端类型、行列
            channel.setPtyType("xterm-256color", 120, 40, 0, 0);
            // =========================================================================
            
            channel.setInputStream(null);
            channel.setOutputStream(null);
            Logger.debug("正在连接 Channel...");
            channel.connect();
            Logger.debug("Channel 已连接: " + channel.isConnected());
            shellChannelPool.put(key, channel);
            
            // 检测 Shell 类型
            ShellType shellType = detectShellType(channel);
            shellTypePool.put(key, shellType);
            Logger.debug("检测到 Shell 类型: " + shellType);

            // ========== 初始化 Shell 环境（区分 Windows 和 Linux） ==========
            Logger.debug("正在初始化 Shell 环境...");
            OutputStream initOut = channel.getOutputStream();

            boolean isWindows = isWindowsServer(connection);
            if (isWindows) {
                // Windows: 关闭回显，避免命令回显干扰输出解析
                initOut.write("@echo off\r\n".getBytes(WINDOWS_CONPTY_CHARSET));
            } else {
                // Linux: 使用动态编码（通过 export 而非 setEnv，因为 sshd 通常不接受 AcceptEnv）
                initOut.write(("export LANG=" + serverLang + "\n").getBytes(StandardCharsets.UTF_8));
                initOut.write(("export LC_ALL=" + serverLang + "\n").getBytes(StandardCharsets.UTF_8));
                // source 配置文件补全 PATH
                initOut.write("source /etc/profile 2>/dev/null\n".getBytes(StandardCharsets.UTF_8));
                initOut.write("source ~/.bashrc 2>/dev/null\n".getBytes(StandardCharsets.UTF_8));
            }
            initOut.flush();

            // 等待初始化完成
            Thread.sleep(500);

            // 消费初始化输出
            InputStream initIn = channel.getInputStream();
            StringBuilder initOutput = new StringBuilder();
            while (initIn.available() > 0) {
                int b = initIn.read();
                if (b == -1) break;
                initOutput.append((char) b);
            }
            Logger.debug("Shell 环境初始化完成 (Windows=" + isWindows + ")");
            // =================================================================
        } else {
            Logger.debug("复用已有 Channel: " + channel.isConnected());
        }

        // 获取 Shell 类型
        ShellType shellType = shellTypePool.get(key);
        boolean isWindows = isWindowsServer(connection);

        Logger.debug("准备执行命令 (Windows=" + isWindows + ")");
        // 使用 Shell Channel 执行命令
        CommandResult result = doExecuteWithShell(channel, command, shellType, isWindows);
        Logger.debug("====== executeWithShell 结束 ======");
        return result;
    }

    /**
     * 通过 Shell Channel 执行命令
     *
     * <p>命令完成判断逻辑（基于约定）：
     * 1. 发送组合命令：Linux 用 <code>command; echo __EXIT_CODE__=$?</code>，Windows 用 <code>command & echo __EXIT_CODE__=%ERRORLEVEL%</code>
     * 2. 持续读取输出，直到收到 __EXIT_CODE__=数字
     * 3. 解析数字作为 exitcode
     */
    private @NotNull CommandResult doExecuteWithShell(ChannelShell channel, @NotNull String command, ShellType shellType, boolean isWindows) throws Exception {
        long startTime = System.currentTimeMillis();

        Logger.debug("====== 开始执行命令 ======");
        Logger.debug("命令: " + command);
        Logger.debug("Shell 类型: " + shellType + ", Windows=" + isWindows);

        // 检查 Channel 连接状态
        if (!channel.isConnected()) {
            Logger.debug("Channel 未连接，抛出异常");
            throw new ConnectionException("Shell channel is disconnected");
        }
        
        // 清空残留输出
        Thread.sleep(100);
        InputStream inputStream = channel.getInputStream();
        while (inputStream.available() > 0) {
            inputStream.read();
        }
        
        OutputStream outputStream = channel.getOutputStream();

        // 发送命令 + exitcode 检测（区分 Windows 和 Linux）
        String fullCommand;
        byte[] commandBytes;
        if (isWindows) {
            // Windows: 使用 & 连接命令，%ERRORLEVEL% 获取退出码，\r\n 换行
            fullCommand = command + " & echo " + EXIT_CODE_MARKER + "%ERRORLEVEL%\r\n";
            commandBytes = fullCommand.getBytes(WINDOWS_CONPTY_CHARSET);
        } else {
            // Linux: 使用 ; 连接命令，$? 获取退出码，\n 换行
            fullCommand = command + "; echo " + EXIT_CODE_MARKER + "$?\n";
            commandBytes = fullCommand.getBytes(StandardCharsets.UTF_8);
        }
        outputStream.write(commandBytes);
        outputStream.flush();
        Logger.debug("命令已发送，等待响应...");

        // 读取输出，直到收到 __EXIT_CODE__=数字
        long maxWaitTime = 180000; // 3 分钟超时
        int exitCode = -1;
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

        while (System.currentTimeMillis() - startTime < maxWaitTime) {
            if (inputStream.available() > 0) {
                int b = inputStream.read();
                if (b == -1) break;
                baos.write(b);

                // 用 UTF-8 解码并检查是否包含退出码
                String current = baos.toString(WINDOWS_CONPTY_CHARSET);
                Matcher matcher = EXIT_CODE_PATTERN.matcher(current);
                if (matcher.find()) {
                    exitCode = Integer.parseInt(matcher.group(1));
                    Logger.debug("检测到 exitcode: " + exitCode);
                    break;
                }
            } else {
                Thread.sleep(50);
            }
        }

        long durationMs = System.currentTimeMillis() - startTime;
        String rawOutput = baos.toString(WINDOWS_CONPTY_CHARSET);

        if (exitCode < 0) {
            Logger.debug("未检测到 exitcode，可能超时");
            exitCode = channel.getExitStatus();
            Logger.debug("Channel exit status: " + exitCode);
        }

        Logger.debug("====== 命令执行结束 ======");

        // 解析命令结果（去除 exitcode 部分和 shell 提示符，Windows 特殊处理）
        String result = extractCommandResult(rawOutput, EXIT_CODE_MARKER, isWindows);
        
        if (exitCode < 0) {
            String errorMsg = result.isEmpty() ? LanguageManager.getInstance().get("ssh.error.noExitCode") : result;
            return new CommandResult(-1, "", errorMsg, durationMs);
        } else if (exitCode == 0) {
            return new CommandResult(0, result, "", durationMs);
        } else {
            return new CommandResult(exitCode, "", (result.isEmpty() ? LanguageManager.getInstance().get("ssh.error.commandFailed") : result), durationMs);
        }
    }
    
    /**
     * 根据 Shell 类型获取退出码命令（已废弃，使用约定格式）
     */
    private String getExitCodeCommand(ShellType shellType) {
        // 使用约定格式：echo __EXIT_CODE__=$?
        return "echo " + EXIT_CODE_MARKER + "$?";
    }
    
    /**
     * 解析退出码
     * @deprecated 使用新的检测逻辑，直接在读取循环中检测 __EXIT_CODE__=数字
     */
    private int parseExitCode(String rawOutput, ShellType shellType) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return -1;
        }
        
        // 使用正则解析 __EXIT_CODE__=数字
        Matcher matcher = EXIT_CODE_PATTERN.matcher(rawOutput);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }
        return -1;
    }
    
    /**
     * 去除 shell 提示符行
     * 
     * <p>Shell 提示符通常格式：
     * - Linux: [user@host dir]$
     * - Linux root: [root@host dir]#
     * - Git Bash: /c/Users/user (MINGW64) $
     * - 普通提示符: user@host:~$ 
     */
    private String removeShellPromptLine(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        
        String[] lines = text.split("\n");
        StringBuilder result = new StringBuilder();
        
        for (String line : lines) {
            String trimmed = line.trim();
            // 跳过常见的 shell 提示符模式
            if (isShellPromptLine(trimmed)) {
                continue;
            }
            if (result.length() > 0) {
                result.append("\n");
            }
            result.append(line);
        }
        
        return result.toString();
    }
    
    /**
     * 判断一行是否为 shell 提示符
     */
    private boolean isShellPromptLine(String line) {
        if (line.isEmpty()) {
            return true;
        }
        
        // 跳过空行
        if (line.trim().isEmpty()) {
            return true;
        }
        
        // 常见 shell 提示符模式：
        // - [xxx@xxx xxx]#  或  [xxx@xxx xxx]$
        // - xxx@xxx:~$ 
        // - /c/Users/... (MINGW64)
        // - 以 [ 开头且包含 ]# 或 ]$ 结尾
        // - 以 # 或 $ 结尾的单行
        
        if (line.startsWith("[") && (line.endsWith("#") || line.endsWith("$"))) {
            return true;
        }
        
        // 检查是否以常见提示符结尾（排除命令输出）
        if (line.endsWith("#") || line.endsWith("$")) {
            // 如果行中有空格且提示符前面是有效的目录/主机格式，大概率是提示符
            if (line.contains("@") || line.contains("/")) {
                // 这可能是提示符行，但也可能是命令输出
                // 保守处理：如果行中有多个单词且最后一个是提示符，跳过
                String trimmed = line.trim();
                if (trimmed.endsWith("#") || trimmed.endsWith("$")) {
                    // 检查提示符前是否是有效的路径或用户名格式
                    int promptPos = trimmed.length() - 1;
                    String before = trimmed.substring(0, promptPos).trim();
                    if (before.contains(" ") && (before.contains("@") || before.startsWith("/"))) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * 检测 Shell 类型
     */
    private ShellType detectShellType(ChannelShell channel) throws Exception {
        Logger.debug("开始检测 Shell 类型...");
        
        InputStream inputStream = channel.getInputStream();
        OutputStream outputStream = channel.getOutputStream();
        
        // 方法1: 检查环境变量 $SHELL
        Logger.debug("检测方法1: 检查 $SHELL");
        outputStream.write("echo $SHELL\n".getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        
        Thread.sleep(500);
        
        StringBuilder output = new StringBuilder();
        while (inputStream.available() > 0) {
            int b = inputStream.read();
            if (b == -1) break;
            output.append((char) b);
        }
        
        String shellPath = output.toString().trim();
        Logger.debug("$SHELL = " + shellPath);
        
        if (shellPath.contains("/bash") || shellPath.contains("/sh")) {
            return ShellType.BASH;
        }
        if (shellPath.contains("/zsh")) {
            return ShellType.ZSH;
        }
        
        // 方法2: 检查 $0 或检查 OSTYPE
        output.setLength(0);
        Logger.debug("检测方法2: 检查 $0");
        outputStream.write("echo $0\n".getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        
        Thread.sleep(500);
        
        while (inputStream.available() > 0) {
            int b = inputStream.read();
            if (b == -1) break;
            output.append((char) b);
        }
        
        String shell0 = output.toString().trim();
        Logger.debug("$0 = " + shell0);
        
        if (shell0.contains("bash")) {
            return ShellType.BASH;
        }
        if (shell0.contains("zsh")) {
            return ShellType.ZSH;
        }
        
        // 方法3: 检查 Windows 环境
        output.setLength(0);
        Logger.debug("检测方法3: 检查 %OS%");
        outputStream.write("echo %OS%\n".getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
        
        Thread.sleep(500);
        
        while (inputStream.available() > 0) {
            int b = inputStream.read();
            if (b == -1) break;
            output.append((char) b);
        }
        
        String osEnv = output.toString().trim();
        Logger.debug("%OS% = " + osEnv);
        
        if ("Windows_NT".equals(osEnv)) {
            // 可能是 CMD 或 PowerShell，尝试检查 PowerShell
            output.setLength(0);
            Logger.debug("检测方法4: 检查 PowerShell");
            outputStream.write("echo $PSVersionTable.PSVersion.Major 2>/dev/null || echo NOT_POWERSHELL\n".getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
            
            Thread.sleep(500);
            
            while (inputStream.available() > 0) {
                int b = inputStream.read();
                if (b == -1) break;
                output.append((char) b);
            }
            
            String psVersion = output.toString().trim();
            Logger.debug("PowerShell检测 = " + psVersion);
            
            if (!psVersion.contains("NOT_POWERSHELL") && !psVersion.isEmpty()) {
                try {
                    Integer.parseInt(psVersion);
                    return ShellType.POWERSHELL;
                } catch (NumberFormatException ignored) {}
            }
            
            return ShellType.CMD;
        }
        
        // 默认为 Bash
        return ShellType.BASH;
    }
    
    /**
     * 从原始输出中提取命令执行结果
     * <p>去除：命令回显、exitcode 标记行、shell 提示符行、ANSI转义序列
     *
     * @param rawOutput 原始输出
     * @param exitCodeMarker 退出码标记
     * @param isWindows 是否为 Windows 服务器
     */
    private String extractCommandResult(String rawOutput, String exitCodeMarker, boolean isWindows) {
        if (rawOutput == null || rawOutput.isEmpty()) {
            return "";
        }

        // Windows 特殊处理：移除多余的 \r
        // Windows SSH ConPTY 会输出 \r\r\n，导致多余空行
        if (isWindows) {
            rawOutput = rawOutput.replace("\r", "");
        }

        // Windows: 清理 ANSI 转义序列
        // 匹配格式: ESC[...字母 或 ESC]...BEL 或 ESC[...
        // 例如: [?25l[H[K[K -> [H[K -> [H
        //       ]0;管理员: C:\WINDOWS\system32\conhost.exe -  (窗口标题序列)
        if (isWindows) {
            rawOutput = removeAnsiEscapeSequences(rawOutput);
        }

        // 找到 exitcode 标记的位置
        int markerIndex = rawOutput.lastIndexOf(exitCodeMarker);
        String content;
        if (markerIndex >= 0) {
            // 取标记之前的内容
            content = rawOutput.substring(0, markerIndex);
        } else {
            content = rawOutput;
        }

        // 去掉命令回显（第一行通常是命令本身）
        int firstNewline = content.indexOf('\n');
        if (firstNewline >= 0) {
            content = content.substring(firstNewline + 1);
        } else {
            content = "";
        }

        // Windows: 过滤连续空行（每个只保留一个）
        if (isWindows) {
            content = filterConsecutiveEmptyLines(content);
        }

        // 去掉 shell 提示符行
        content = removeShellPromptLine(content);

        return content.trim();
    }

    /**
     * 清理 ANSI 转义序列
     *
     * <p>Windows ConPTY 会输出各种 ANSI escape codes，包括：
     * <ul>
     *   <li>窗口标题序列: ]0;...BEL</li>
     *   <li>光标控制: [?25h, [?25l</li>
     *   <li>光标位置: [H, [3;1H</li>
     *   <li>清除命令: [K, [J</li>
     *   <li>组合序列: [25l[H[K[K...[K...</li>
     * </ul>
     */
    private String removeAnsiEscapeSequences(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // ANSI escape sequence 正则: ESC[...字母 或 ESC]...BEL
        // ESC = \x1b 或 \033
        // 格式: \x1b[...字母 或 \x1b]... BEL(\x07)
        String result = text;

        // 移除 ]0;...BEL 窗口标题序列
        result = result.replaceAll("\u001b\\]0;[^\u0007]*\u0007", "");

        // 移除所有 ESC[...字母 格式的转义序列
        // 包括 [?25h, [?25l, [H, [K, [J, [1;1H 等
        result = result.replaceAll("\u001b\\[[^A-Za-z]*[A-Za-z]", "");

        // 移除纯 ESC
        result = result.replaceAll("\u001b", "");

        return result;
    }

    /**
     * 过滤连续空行，每个连续的空行组只保留一个
     */
    private String filterConsecutiveEmptyLines(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String[] lines = content.split("\n");
        StringBuilder filtered = new StringBuilder();
        boolean lastWasEmpty = false;

        for (String line : lines) {
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

        return filtered.toString();
    }
    
    private @NotNull Session getOrCreateSession(@NotNull SshConnection connection) throws Exception {
        String key = connection.getServerId();
        Logger.debug("getOrCreateSession: key=" + key);
        Session session = sessionPool.get(key);
        
        if (session == null || !session.isConnected()) {
            Logger.debug("Session 不存在或未连接，需要创建");
            session = createSession(connection);
            sessionPool.put(key, session);
        } else {
            Logger.debug("复用已有 Session");
        }
        return session;
    }
    
    private @NotNull Session createSession(@NotNull SshConnection connection) throws Exception {
        Logger.debug("====== createSession 开始 ======");
        validateConnection(connection);
        
        Logger.debug("正在创建 JSch Session...");
        Session session = jsch.getSession(
            connection.getUsername(),
            connection.getHost(),
            connection.getPort()
        );
        
        session.setPassword(connection.getPassword());
        session.setConfig("StrictHostKeyChecking", "no");
        
        // 防止连接僵死：每 30 秒发送心跳，连续 10 次无回应则断开
        session.setServerAliveInterval(30000);
        session.setServerAliveCountMax(10);
        
        Logger.debug("正在连接 Session (可能需要几秒钟)...");
        session.connect();
        Logger.debug("Session 已连接: " + session.isConnected());
        Logger.debug("====== createSession 结束 ======");
        
        return session;
    }
    
    /**
     * 获取服务器的 LANG 环境变量
     */
    private String getServerLang(Session session) {
        try {
            com.jcraft.jsch.ChannelExec exec = (com.jcraft.jsch.ChannelExec) session.openChannel("exec");
            exec.setCommand("echo $LANG");
            
            java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
            exec.setOutputStream(outputStream);
            exec.connect(1500);
            
            while (!exec.isClosed()) {
                Thread.sleep(10);
            }
            
            String lang = outputStream.toString(StandardCharsets.UTF_8.name()).trim();
            exec.disconnect();
            
            return lang.isEmpty() ? "en_US.UTF-8" : lang;
        } catch (Exception e) {
            Logger.debug("获取服务器编码失败: " + e.getMessage() + "，使用默认值 en_US.UTF-8");
            return "en_US.UTF-8";
        }
    }
    
    /**
     * 检查连接状态
     */
    public boolean isConnected(@NotNull SshConnection connection) {
        String key = connection.getServerId();
        Session session = sessionPool.get(key);
        return session != null && session.isConnected();
    }
    
    /**
     * 断开连接
     */
    public void disconnect(@NotNull SshConnection connection) {
        String key = connection.getServerId();
        
        // 先关闭 Shell Channel
        ChannelShell shell = shellChannelPool.remove(key);
        if (shell != null && shell.isConnected()) {
            shell.disconnect();
        }
        
        // 再关闭 Session
        Session session = sessionPool.remove(key);
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
    
    /**
     * 关闭所有连接
     */
    public void disconnectAll() {
        shellChannelPool.values().forEach(ChannelShell::disconnect);
        shellChannelPool.clear();
        
        sessionPool.values().forEach(Session::disconnect);
        sessionPool.clear();
    }
    
    /**
     * 连接异常
     * 
     * <p>连接断开或连接超时时抛出此异常。
     * 此异常与命令返回输出没有关系。
     */
    public static class ConnectionException extends RuntimeException {
        public ConnectionException(String message) {
            super(message);
        }
        
        public ConnectionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
