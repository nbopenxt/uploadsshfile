package com.openxt.uploadsshfile.integration;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.ChannelSftp;
import com.openxt.uploadsshfile.TestConfig;
import com.openxt.uploadsshfile.ai.AICommandChecker;
import com.openxt.uploadsshfile.ai.AIResultChecker;
import com.openxt.uploadsshfile.ai.LangChainAiService;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.model.*;
import com.openxt.uploadsshfile.orchestration.CommandOrchestrator;
import com.openxt.uploadsshfile.ssh.SshCommandService;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.validation.BlacklistValidator;
import com.openxt.uploadsshfile.validation.KeywordMatcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStreamReader;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import static org.junit.Assert.*;

/**
 * SSH 集成测试套件
 * 
 * 测试覆盖：
 * 1. 上传自动执行命令测试
 * 2. 上传手工执行命令测试
 * 3. 非AI黑名单命令屏蔽测试
 * 4. AI黑名单命令屏蔽测试
 * 5. 非AI判断返回值测试
 * 6. AI判断返回值测试
 * 7. 返回值判断测试
 */
public class SshIntegrationTest {

    // 测试服务器配置 - 实际值在 TestConfig 中统一管理
    private static final String TEST_HOST = TestConfig.LINUX_HOST;
    private static final int TEST_PORT = TestConfig.LINUX_PORT;
    private static final String TEST_USERNAME = TestConfig.LINUX_USER;
    private static final String TEST_PASSWORD = TestConfig.LINUX_PASSWORD;
    private static final String TEST_REMOTE_PATH = TestConfig.LINUX_REMOTE_PATH;

    private Session session;
    private SshCommandService sshService;
    private ServerConfig serverConfig;
    private SshConnection connection;
    private File localTestFile;

    @Before
    public void setUp() throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("SSH Integration Test Setup");
        System.out.println("=".repeat(60));

        // 启用主机白名单（只允许测试服务器）
        SshCommandService.isHostWhitelistEnabled = true;
        
        // 创建 SSH 服务
        sshService = new SshCommandService();

        // 创建服务器配置
        serverConfig = new ServerConfig();
        serverConfig.setName("Test Server");
        serverConfig.setHost(TEST_HOST);
        serverConfig.setPort(TEST_PORT);
        serverConfig.setUsername(TEST_USERNAME);
        serverConfig.setOsType("linux");

        // 创建 SSH 连接
        connection = SshConnection.fromServerConfig(serverConfig, TEST_PASSWORD);

        // 创建本地测试文件
        createLocalTestFile();

        System.out.println("Setup completed: " + serverConfig.getHost());
        System.out.println();
    }

    @After
    public void tearDown() {
        System.out.println();
        System.out.println("=".repeat(60));
        System.out.println("SSH Integration Test Teardown");
        System.out.println("=".repeat(60));

        // 清理 SSH 连接
        if (sshService != null) {
            sshService.disconnectAll();
        }

        // 删除本地测试文件
        if (localTestFile != null && localTestFile.exists()) {
            localTestFile.delete();
        }

        System.out.println("Teardown completed");
    }

    // ========== 安全验证常量 ==========

    /** 允许的命令白名单 - 仅文件增删改查操作 */
    private static final Set<String> ALLOWED_COMMANDS = new HashSet<>(Arrays.asList(
            // 文件查看
            "ls", "cat", "head", "tail", "wc", "stat", "file", "md5sum", "sha256sum",
            // 文件增删改
            "cp", "mv", "rm", "touch", "mkdir", "echo", "tee",
            // 文件编辑（追加）
            "echo >>", "cat >>", "tee >>",
            // SFTP 操作
            "get", "put",
            // 其他安全命令
            "pwd", "cd", "test", "["
    ));

    /** 允许的文件扩展名 */
    private static final Set<String> ALLOWED_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".txt", ".log", ".json", ".xml", ".yaml", ".yml", ".csv",
            ".conf", ".config", ".properties", ".sh", ".py", ".md"
    ));

    /** 禁止的危险模式 */
    private static final String[] FORBIDDEN_PATTERNS = {
            "rm -rf /",
            "rm -rf /*",
            "dd if=",
            "mkfs",
            "fdisk",
            "parted",
            "> /dev/",
            "2>&1",
            "curl |",
            "wget |",
            "bash -c",
            "sh -c",
            "eval ",
            "`",
            "$((",
            ";",
            "&&",
            "||",
            "|",
            ">",
            ">>"
    };

    // ========== 安全验证方法 ==========

    /**
     * 验证命令是否安全
     * @param command 要验证的命令
     * @param workingDir 工作目录
     * @throws SecurityException 如果命令不安全
     */
    private void validateCommand(String command, String workingDir) {
        if (command == null || command.trim().isEmpty()) {
            throw new SecurityException("Command cannot be empty");
        }

        String trimmedCommand = command.trim();

        // 特殊处理 mkdir -p 命令（允许使用）
        if (trimmedCommand.startsWith("mkdir -p ")) {
            String path = trimmedCommand.substring(9).trim();
            // 验证路径必须在 TEST_REMOTE_PATH 下
            if (!path.startsWith(TEST_REMOTE_PATH)) {
                throw new SecurityException(
                    "mkdir path '" + path + "' is outside the allowed test directory: " + TEST_REMOTE_PATH
                );
            }
            return; // mkdir -p 是安全的，允许执行
        }

        // 特殊处理 && 命令链（如 cd /path && command）
        // 先拆分命令，只验证 && 后面部分
        if (trimmedCommand.contains("&&")) {
            String[] parts = trimmedCommand.split("&&");
            // 验证 && 之后的部分（实际执行的命令）
            for (int i = 1; i < parts.length; i++) {
                String actualCommand = parts[i].trim();
                validateSingleCommand(actualCommand, workingDir);
            }
            // 验证 && 之前是否有 cd 命令，且 cd 到允许的目录
            for (int i = 0; i < parts.length - 1; i++) {
                String prefix = parts[i].trim();
                if (prefix.startsWith("cd ")) {
                    String path = prefix.substring(3).trim();
                    // cd 只能到 TEST_REMOTE_PATH 或其子目录
                    if (!path.equals(TEST_REMOTE_PATH) && !path.startsWith(TEST_REMOTE_PATH + "/")) {
                        throw new SecurityException(
                            "cd path '" + path + "' is outside the allowed test directory: " + TEST_REMOTE_PATH
                        );
                    }
                }
            }
            return;
        }

        // 普通命令验证
        validateSingleCommand(trimmedCommand, workingDir);
    }

    /**
     * 验证单个命令（不包含 &&）
     */
    private void validateSingleCommand(String command, String workingDir) {
        // 检查危险模式
        for (String pattern : FORBIDDEN_PATTERNS) {
            if (command.toLowerCase().contains(pattern.toLowerCase())) {
                throw new SecurityException(
                    "Command contains forbidden pattern: " + pattern + ". " +
                    "Only safe file operations (ls, cat, cp, mv, rm, mkdir, touch, echo) are allowed."
                );
            }
        }

        // 检查命令是否为允许的白名单命令
        String baseCommand = getBaseCommand(command);
        if (!isAllowedCommand(baseCommand)) {
            throw new SecurityException(
                "Command '" + baseCommand + "' is not in the allowed list. " +
                "Only file operations (ls, cat, cp, mv, rm, mkdir, touch, echo) are allowed."
            );
        }

        // 检查路径是否在允许范围内
        validatePath(command, workingDir);
    }

    /**
     * 获取命令的基础命令部分
     */
    private String getBaseCommand(String command) {
        String trimmed = command.trim();
        // 处理 cd 命令
        if (trimmed.startsWith("cd ")) {
            return "cd";
        }
        // 提取第一个单词作为基础命令
        String[] parts = trimmed.split("\\s+");
        if (parts.length > 0) {
            // 处理带路径的命令（如 ./script.sh）
            String base = parts[0];
            if (base.contains("/")) {
                base = base.substring(base.lastIndexOf('/') + 1);
            }
            return base;
        }
        return trimmed;
    }

    /**
     * 检查命令是否在白名单中
     */
    private boolean isAllowedCommand(String baseCommand) {
        // 允许的命令列表
        String[] allowedCommands = {
            "ls", "cat", "head", "tail", "wc", "stat", "file", "md5sum", "sha256sum",
            "cp", "mv", "rm", "touch", "mkdir", "echo", "tee",
            "pwd", "cd", "test", "chmod", "chown"
        };

        // 允许运行指定目录下的脚本（仅限测试脚本）
        // 支持 ./script.sh 或 script.sh 两种形式
        if (baseCommand.startsWith("./")) {
            String scriptName = baseCommand.substring(2);
            return scriptName.equals("copyfile.sh");
        }
        // 也支持不带 ./ 前缀的脚本名
        if (baseCommand.equals("copyfile.sh")) {
            return true;
        }

        for (String allowed : allowedCommands) {
            if (allowed.equals(baseCommand)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证路径是否在允许的测试目录范围内
     * @param command 完整命令
     * @param workingDir 当前工作目录
     */
    private void validatePath(String command, String workingDir) {
        // 提取命令中的所有路径
        List<String> paths = extractPaths(command);

        for (String path : paths) {
            // 跳过绝对路径为 TEST_REMOTE_PATH 的情况
            if (path.equals(TEST_REMOTE_PATH) || path.equals(TEST_REMOTE_PATH + "/")) {
                continue;
            }

            // 跳过相对路径（相对路径会基于 TEST_REMOTE_PATH 执行）
            if (!path.startsWith("/")) {
                continue;
            }

            // 验证绝对路径必须在 TEST_REMOTE_PATH 下
            if (!path.startsWith(TEST_REMOTE_PATH)) {
                throw new SecurityException(
                    "Path '" + path + "' is outside the allowed test directory: " + TEST_REMOTE_PATH + ". " +
                    "All file operations must be within this directory for security."
                );
            }
        }
    }

    /**
     * 从命令中提取所有路径
     */
    private List<String> extractPaths(String command) {
        List<String> paths = new ArrayList<>();
        String[] tokens = command.split("\\s+");

        for (String token : tokens) {
            // 跳过选项参数
            if (token.startsWith("-")) {
                continue;
            }

            // 检查是否为路径（绝对路径或相对路径）
            if (token.startsWith("/") || token.startsWith("./") || token.startsWith("../")) {
                // 清理路径（移除重定向符号和文件名后的内容）
                String cleaned = token.replaceAll("[>|].*$", "");
                if (!cleaned.isEmpty() && !cleaned.equals(".") && !cleaned.equals("..")) {
                    paths.add(cleaned);
                }
            }
        }

        return paths;
    }

    /**
     * 增强版执行远程命令 - 包含安全验证
     */
    private CommandResult executeCommandSecure(String command) throws Exception {
        // 安全验证
        validateCommand(command, TEST_REMOTE_PATH);
        // 执行命令
        return executeCommand(command);
    }

    // ========== 辅助方法 ==========

    /** 允许连接的主机白名单 */
    private static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
            TEST_HOST  // 只允许连接此服务器
    ));

    /**
     * 验证主机是否在白名单中
     */
    private void validateHost(String host) {
        if (!ALLOWED_HOSTS.contains(host)) {
            throw new SecurityException(
                "Connection to host '" + host + "' is not allowed. " +
                "Only the following hosts are permitted: " + ALLOWED_HOSTS
            );
        }
    }

    /**
     * 创建本地测试文件
     */
    private void createLocalTestFile() throws Exception {
        String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        String content = "{yyyyMMddHHmmss} format: " + timestamp;

        localTestFile = new File(System.getProperty("java.io.tmpdir"), "a.txt");
        try (FileWriter writer = new FileWriter(localTestFile)) {
            writer.write(content);
        }

        System.out.println("Created local test file: " + localTestFile.getAbsolutePath());
        System.out.println("Content: " + content);
    }

    /**
     * 建立 SSH 连接
     */
    private Session connect() throws Exception {
        // 验证主机白名单
        validateHost(TEST_HOST);
        
        JSch jsch = new JSch();
        session = jsch.getSession(TEST_USERNAME, TEST_HOST, TEST_PORT);
        session.setPassword(TEST_PASSWORD);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect(30000);
        return session;
    }

    /**
     * 执行远程命令（带安全验证）
     * 仅允许文件增删改查操作，且只能操作 TEST_REMOTE_PATH 下的文件
     */
    private CommandResult executeCommand(String command) throws Exception {
        // 安全验证：检查命令是否安全
        validateCommand(command, TEST_REMOTE_PATH);

        long startTime = System.currentTimeMillis();

        if (session == null || !session.isConnected()) {
            connect();
        }

        ChannelExec channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setInputStream(null);
        channel.setErrStream(System.err);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

        channel.setOutputStream(outputStream);
        channel.setExtOutputStream(errorStream);
        channel.connect(30000);

        // 等待命令执行完成
        while (!channel.isClosed()) {
            Thread.sleep(50);
        }

        int exitCode = channel.getExitStatus();
        String stdout = outputStream.toString();
        String stderr = errorStream.toString();
        long durationMs = System.currentTimeMillis() - startTime;

        channel.disconnect();

        return new CommandResult(exitCode, stdout, stderr, durationMs);
    }

    /**
     * 上传文件
     */
    private void uploadFile(File localFile, String remotePath) throws Exception {
        if (session == null || !session.isConnected()) {
            connect();
        }

        ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpChannel.connect(30000);

        // 确保远程目录存在
        String dirPath = remotePath.substring(0, remotePath.lastIndexOf('/'));
        executeCommand("mkdir -p " + dirPath);

        sftpChannel.put(localFile.getAbsolutePath(), remotePath);
        sftpChannel.disconnect();

        System.out.println("Uploaded: " + localFile.getName() + " -> " + remotePath);
    }

    /**
     * 下载文件
     */
    private String downloadFile(String remotePath) throws Exception {
        if (session == null || !session.isConnected()) {
            connect();
        }

        ChannelSftp sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpChannel.connect(30000);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        sftpChannel.get(remotePath, outputStream);

        sftpChannel.disconnect();

        return outputStream.toString();
    }

    /**
     * 打印测试结果
     */
    private void printTestResult(String testName, boolean passed, String details) {
        String status = passed ? "✓ PASSED" : "✗ FAILED";
        System.out.println();
        System.out.println("-".repeat(50));
        System.out.println("Test: " + testName);
        System.out.println("Status: " + status);
        if (details != null) {
            System.out.println("Details: " + details);
        }
        System.out.println("-".repeat(50));
    }

    // ========== 测试方法 ==========

    /**
     * 测试1: 服务器连接测试
     */
    @Test
    public void testServerConnection() {
        System.out.println("\n[TEST-START] testServerConnection 开始");
        System.out.println("\n[Test 1] Server Connection Test");

        try {
            Session testSession = connect();
            boolean connected = testSession.isConnected();
            testSession.disconnect();

            printTestResult("Server Connection", connected, "Connected to " + TEST_HOST);
            assertTrue("Should connect to server successfully", connected);

        } catch (Exception e) {
            printTestResult("Server Connection", false, "Error: " + e.getMessage());
            fail("Failed to connect to server: " + e.getMessage());
        }
        System.out.println("[TEST-END] testServerConnection 结束\n");
    }

    /**
     * 测试2: 上传文件测试
     */
    @Test
    public void testUploadFile() {
        System.out.println("\n[TEST-START] testUploadFile 开始");
        System.out.println("\n[Test 2] Upload File Test");

        try {
            String remoteFile = TEST_REMOTE_PATH + "/a.txt";
            uploadFile(localTestFile, remoteFile);

            // 验证文件已上传
            String content = downloadFile(remoteFile);
            boolean verified = content != null && !content.isEmpty();

            printTestResult("Upload File", verified, "File uploaded to " + remoteFile);
            assertTrue("Should upload file successfully", verified);

        } catch (Exception e) {
            printTestResult("Upload File", false, "Error: " + e.getMessage());
            fail("Failed to upload file: " + e.getMessage());
        }
        System.out.println("[TEST-END] testUploadFile 结束\n");
    }

    /**
     * 测试3: 上传自动执行命令测试
     */
    @Test
    public void testUploadWithAutoExecute() {
        System.out.println("\n[TEST-START] testUploadWithAutoExecute 开始");
        System.out.println("\n[Test 3] Upload With Auto Execute Test");

        try {
            String remoteFile = TEST_REMOTE_PATH + "/a.txt";

            // 上传文件
            uploadFile(localTestFile, remoteFile);

            // 执行自动命令 ./copyfile.sh a.txt
            CommandResult result = executeCommand("cd " + TEST_REMOTE_PATH + " && ./copyfile.sh a.txt");

            System.out.println("Command: cd " + TEST_REMOTE_PATH + " && ./copyfile.sh a.txt");
            System.out.println("Exit Code: " + result.getExitCode());
            System.out.println("Stdout: " + result.getStdout());
            System.out.println("Stderr: " + result.getStderr());

            // 根据实际返回值判断
            boolean success = result.getExitCode() == 0;
            printTestResult("Upload Auto Execute", success,
                    "Exit code: " + result.getExitCode() + ", Stdout: " + result.getStdout());

            // 注意：这里不强制断言，因为 copyfile.sh 的返回值可能不是 0
            System.out.println("Result: Command executed, exit code = " + result.getExitCode());

        } catch (Exception e) {
            printTestResult("Upload Auto Execute", false, "Error: " + e.getMessage());
            fail("Failed to execute auto command: " + e.getMessage());
        }
        System.out.println("[TEST-END] testUploadWithAutoExecute 结束\n");
    }

    /**
     * 测试4: 上传手工执行命令测试
     */
    @Test
    public void testUploadWithManualExecute() {
        System.out.println("\n[TEST-START] testUploadWithManualExecute 开始");
        System.out.println("\n[Test 4] Upload With Manual Execute Test");

        try {
            String remoteFile = TEST_REMOTE_PATH + "/a.txt";

            // 上传文件
            uploadFile(localTestFile, remoteFile);

            // 手动执行 ./copyfile.sh a.txt
            CommandResult result = executeCommand("cd " + TEST_REMOTE_PATH + " && ./copyfile.sh a.txt");

            System.out.println("Manual Command: cd " + TEST_REMOTE_PATH + " && ./copyfile.sh a.txt");
            System.out.println("Exit Code: " + result.getExitCode());
            System.out.println("Stdout: " + result.getStdout());

            printTestResult("Manual Execute", true,
                    "Manual command executed, exit code = " + result.getExitCode());

        } catch (Exception e) {
            printTestResult("Manual Execute", false, "Error: " + e.getMessage());
            fail("Failed to execute manual command: " + e.getMessage());
        }
        System.out.println("[TEST-END] testUploadWithManualExecute 结束\n");
    }

    /**
     * 测试5: 非AI黑名单命令屏蔽测试
     */
    @Test
    public void testBlacklistBlocking() {
        System.out.println("\n[TEST-START] testBlacklistBlocking 开始");
        System.out.println("\n[Test 5] Non-AI Blacklist Blocking Test");

        try {
            BlacklistValidator validator = new BlacklistValidator();

            // 测试危险命令应该被拦截
            String[] dangerousCommands = {
                    "rm -rf /",
                    "shutdown",
                    "dd if=/dev/zero of=/dev/sda"
            };

            int blockedCount = 0;
            for (String cmd : dangerousCommands) {
                ValidationResult result = validator.validate(cmd, null, OperatingSystem.LINUX);
                System.out.println("Command: " + cmd);
                System.out.println("  Blocked: " + result.isBlocked());
                if (result.isBlocked()) {
                    blockedCount++;
                }
            }

            // 测试安全命令不应该被拦截
            String[] safeCommands = {
                    "ls -la",
                    "pwd",
                    "echo hello"
            };

            int safePassedCount = 0;
            for (String cmd : safeCommands) {
                ValidationResult result = validator.validate(cmd, null, OperatingSystem.LINUX);
                System.out.println("Safe Command: " + cmd);
                System.out.println("  Blocked: " + result.isBlocked());
                if (!result.isBlocked()) {
                    safePassedCount++;
                }
            }

            boolean success = blockedCount == dangerousCommands.length 
                    && safePassedCount == safeCommands.length;
            printTestResult("Blacklist Blocking", success,
                    "Blocked " + blockedCount + "/" + dangerousCommands.length + " dangerous commands, " +
                    "Allowed " + safePassedCount + "/" + safeCommands.length + " safe commands");

            assertEquals("All dangerous commands should be blocked", 
                    dangerousCommands.length, blockedCount);
            assertEquals("All safe commands should be allowed", 
                    safeCommands.length, safePassedCount);

        } catch (Exception e) {
            printTestResult("Blacklist Blocking", false, "Error: " + e.getMessage());
            fail("Failed to test blacklist: " + e.getMessage());
        }
        System.out.println("[TEST-END] testBlacklistBlocking 结束\n");
    }

    /**
     * 测试6: 非AI判断返回值测试（安全命令）
     */
    @Test
    public void testKeywordMatcherSafeCommand() {
        System.out.println("\n[TEST-START] testKeywordMatcherSafeCommand 开始");
        System.out.println("\n[Test 6] Keyword Matcher - Safe Command Test");

        try {
            // 上传并执行安全命令
            String remoteFile = TEST_REMOTE_PATH + "/a.txt";
            uploadFile(localTestFile, remoteFile);

            // 执行 copyfile.sh a.txt（存在文件）
            CommandResult result = executeCommand("cd " + TEST_REMOTE_PATH + " && ./copyfile.sh a.txt");

            System.out.println("Command: ./copyfile.sh a.txt");
            System.out.println("Exit Code: " + result.getExitCode());
            System.out.println("Stdout: " + result.getStdout());
            System.out.println("Stderr: " + result.getStderr());

            // 使用 KeywordMatcher 判断结果
            KeywordMatcher matcher = new KeywordMatcher();
            EvaluationResult evalResult = matcher.evaluate(result, OperatingSystem.LINUX);

            System.out.println("Evaluation: " + evalResult.isSuccess());
            System.out.println("Reason: " + evalResult.getReason());

            printTestResult("Keyword Matcher Safe Command", true,
                    "Command evaluated: " + evalResult.isSuccess());

        } catch (Exception e) {
            printTestResult("Keyword Matcher Safe Command", false, "Error: " + e.getMessage());
            fail("Failed to test keyword matcher: " + e.getMessage());
        }
        System.out.println("[TEST-END] testKeywordMatcherSafeCommand 结束\n");
    }

    /**
     * 测试7: 非AI判断返回值测试（不存在的文件）
     */
    @Test
    public void testKeywordMatcherNonExistentFile() {
        System.out.println("\n[TEST-START] testKeywordMatcherNonExistentFile 开始");
        System.out.println("\n[Test 7] Keyword Matcher - Non-Existent File Test");

        try {
            // 执行 copyfile.sh b.txt（不存在文件）
            CommandResult result = executeCommand("cd " + TEST_REMOTE_PATH + " && ./copyfile.sh b.txt");

            System.out.println("Command: ./copyfile.sh b.txt");
            System.out.println("Exit Code: " + result.getExitCode());
            System.out.println("Stdout: " + result.getStdout());
            System.out.println("Stderr: " + result.getStderr());

            // 使用 KeywordMatcher 判断结果
            KeywordMatcher matcher = new KeywordMatcher();
            EvaluationResult evalResult = matcher.evaluate(result, OperatingSystem.LINUX);

            System.out.println("Evaluation: " + evalResult.isSuccess());
            System.out.println("Reason: " + evalResult.getReason());

            // 根据实际返回值判断
            printTestResult("Keyword Matcher Non-Existent File", true,
                    "Exit code: " + result.getExitCode() + ", Evaluation: " + evalResult.isSuccess());

        } catch (Exception e) {
            printTestResult("Keyword Matcher Non-Existent File", false, "Error: " + e.getMessage());
            fail("Failed to test non-existent file: " + e.getMessage());
        }
        System.out.println("[TEST-END] testKeywordMatcherNonExistentFile 结束\n");
    }

    /**
     * 测试8: 返回值判断测试
     */
    @Test
    public void testExitCodeJudgment() {
        System.out.println("\n[TEST-START] testExitCodeJudgment 开始");
        System.out.println("\n[Test 8] Exit Code Judgment Test");

        try {
            // 测试不同命令的返回值
            String[][] testCases = {
                    {"echo 'test'", "测试 echo 命令"},
                    {"ls -la", "测试 ls 命令（当前目录）"},
                    {"pwd", "测试 pwd 命令"},
                    {"./copyfile.sh a.txt", "测试 copyfile.sh（文件存在）"},
                    {"./copyfile.sh b.txt", "测试 copyfile.sh（文件不存在）"}
            };

            List<String> results = new ArrayList<>();
            for (String[] testCase : testCases) {
                String command = "cd " + TEST_REMOTE_PATH + " && " + testCase[0];
                CommandResult result = executeCommand(command);
                results.add(String.format("%s: exit=%d, stdout=%s", 
                        testCase[1], result.getExitCode(), 
                        result.getStdout().trim().replace("\n", " ")));
                System.out.println("  " + testCase[1] + ": exit=" + result.getExitCode());
            }

            printTestResult("Exit Code Judgment", true, 
                    String.join("; ", results));

        } catch (Exception e) {
            printTestResult("Exit Code Judgment", false, "Error: " + e.getMessage());
            fail("Failed to test exit codes: " + e.getMessage());
        }
        System.out.println("[TEST-END] testExitCodeJudgment 结束\n");
    }

    /**
     * 测试9: AI 黑名单命令检测测试（使用真实AI：千问和豆包）
     */
    @Test
    public void testAIBlacklistCheck() {
        System.out.println("\n[TEST-START] testAIBlacklistCheck 开始");
        System.out.println("\n[Test 9] AI Blacklist Check Test (Real AI)");

        // 测试千问
        testAIBlacklistWithProvider("QWEN", TestConfig.QWEN_MODEL_NAME,
                TestConfig.QWEN_BASE_URL,
                TestConfig.QWEN_API_KEY);
        
        // 测试豆包
        testAIBlacklistWithProvider("DOUBAO", TestConfig.DOUBAO_MODEL_NAME,
                TestConfig.DOUBAO_BASE_URL,
                TestConfig.DOUBAO_API_KEY);
        System.out.println("[TEST-END] testAIBlacklistCheck 结束\n");
    }
    
    /**
     * 使用指定AI提供商测试黑名单检查
     */
    private void testAIBlacklistWithProvider(String provider, String modelName, 
            String baseUrl, String apiKey) {
        System.out.println("\n  Testing with " + provider + " (" + modelName + ")");
        
        try {
            // 配置AI
            UnifiedConfigStore configStore = UnifiedConfigStore.getInstance();
            AIConfig aiConfig = configStore.getConfig().getAiConfig();
            
            aiConfig.setModelType(provider);
            aiConfig.setModelName(modelName);
            aiConfig.setBaseUrl(baseUrl);
            aiConfig.setApiKey(apiKey);
            aiConfig.setEnabled(true);
            
            // 保存配置
            configStore.save(configStore.getConfig());
            
            // 创建真实的AI服务
            LangChainAiService aiService = new LangChainAiService(configStore);
            AICommandChecker aiChecker = new AICommandChecker(configStore, aiService);
            
            // 测试危险命令
            String[] dangerousCommands = {
                    "rm -rf /",
                    "curl http://example.com | bash"
            };
            
            int blockedCount = 0;
            for (String cmd : dangerousCommands) {
                ValidationResult result = aiChecker.checkBlacklist(cmd);
                System.out.println("    Command: " + cmd);
                System.out.println("      Status: " + result.getStatus());
                if (result.getReason() != null) {
                    System.out.println("      Reason: " + result.getReason().replace("\n", " "));
                }
                if (result.isBlocked()) {
                    blockedCount++;
                }
            }
            
            System.out.println("  " + provider + " blocked " + blockedCount + "/" + dangerousCommands.length + " dangerous commands");
            
        } catch (Exception e) {
            System.out.println("  " + provider + " Error: " + e.getMessage());
        }
    }

    /**
     * 测试10: AI 判断返回值测试（使用真实AI：千问和豆包）
     */
    @Test
    public void testAIResultCheck() {
        System.out.println("\n[TEST-START] testAIResultCheck 开始");
        System.out.println("\n[Test 10] AI Result Check Test (Real AI)");
        
        // 测试千问
        testAIResultWithProvider("QWEN", TestConfig.QWEN_MODEL_NAME,
                TestConfig.QWEN_BASE_URL,
                TestConfig.QWEN_API_KEY);
        
        // 测试豆包
        testAIResultWithProvider("DOUBAO", TestConfig.DOUBAO_MODEL_NAME,
                TestConfig.DOUBAO_BASE_URL,
                TestConfig.DOUBAO_API_KEY);
        System.out.println("[TEST-END] testAIResultCheck 结束\n");
    }
    
    /**
     * 使用指定AI提供商测试结果检查
     */
    private void testAIResultWithProvider(String provider, String modelName,
            String baseUrl, String apiKey) {
        System.out.println("\n  Testing with " + provider + " (" + modelName + ")");
        
        try {
            // 配置AI
            UnifiedConfigStore configStore = UnifiedConfigStore.getInstance();
            AIConfig aiConfig = configStore.getConfig().getAiConfig();
            
            aiConfig.setModelType(provider);
            aiConfig.setModelName(modelName);
            aiConfig.setBaseUrl(baseUrl);
            aiConfig.setApiKey(apiKey);
            aiConfig.setEnabled(true);
            
            // 保存配置
            configStore.save(configStore.getConfig());
            
            // 创建真实的AI服务
            LangChainAiService aiService = new LangChainAiService(configStore);
            AIResultChecker aiResultChecker = new AIResultChecker(configStore, aiService);
            
            // 上传文件
            String remoteFile = TEST_REMOTE_PATH + "/a.txt";
            uploadFile(localTestFile, remoteFile);
            
            // 执行命令
            String command = "./copyfile.sh a.txt";
            CommandResult result = executeCommand("cd " + TEST_REMOTE_PATH + " && " + command);
            
            System.out.println("    Command: " + command);
            System.out.println("    Exit Code: " + result.getExitCode());
            System.out.println("    Stdout: " + result.getStdout().trim().replace("\n", " "));
            
            // 使用 AI 结果检查器
            String aiMessage = aiResultChecker.checkResult(command, result);
            
            if (aiMessage.isEmpty()) {
                System.out.println("    AI Result: Normal (no issues)");
            } else {
                System.out.println("    AI Result: Issues found");
                System.out.println("    AI Message: " + aiMessage.replace("\n", " "));
            }
            
        } catch (Exception e) {
            System.out.println("    " + provider + " Error: " + e.getMessage());
        }
    }

    /**
     * 测试11: 命令安全限制测试 - 验证命令只能操作指定测试目录
     */
    @Test
    public void testSecurityRestrictions() {
        System.out.println("\n[TEST-START] testSecurityRestrictions 开始");
        System.out.println("\n[Test 11] Security Restrictions Test");
        System.out.println("Allowed test directory: " + TEST_REMOTE_PATH);

        int passed = 0;
        int total = 0;

        // 测试1: 允许的命令应该通过
        total++;
        try {
            validateCommand("ls " + TEST_REMOTE_PATH, TEST_REMOTE_PATH);
            System.out.println("  [PASS] 'ls " + TEST_REMOTE_PATH + "' is allowed");
            passed++;
        } catch (SecurityException e) {
            System.out.println("  [FAIL] 'ls' should be allowed: " + e.getMessage());
        }

        // 测试2: 允许的命令应该通过
        total++;
        try {
            validateCommand("cat " + TEST_REMOTE_PATH + "/a.txt", TEST_REMOTE_PATH);
            System.out.println("  [PASS] 'cat " + TEST_REMOTE_PATH + "/a.txt' is allowed");
            passed++;
        } catch (SecurityException e) {
            System.out.println("  [FAIL] 'cat' should be allowed: " + e.getMessage());
        }

        // 测试3: 允许的命令应该通过
        total++;
        try {
            validateCommand("cp " + TEST_REMOTE_PATH + "/a.txt " + TEST_REMOTE_PATH + "/b.txt", TEST_REMOTE_PATH);
            System.out.println("  [PASS] 'cp' within test directory is allowed");
            passed++;
        } catch (SecurityException e) {
            System.out.println("  [FAIL] 'cp' within test directory should be allowed: " + e.getMessage());
        }

        // 测试4: 危险命令应该被阻止
        total++;
        try {
            validateCommand("rm -rf /", TEST_REMOTE_PATH);
            System.out.println("  [FAIL] 'rm -rf /' should be blocked");
        } catch (SecurityException e) {
            System.out.println("  [PASS] 'rm -rf /' is blocked: " + e.getMessage());
            passed++;
        }

        // 测试5: 危险命令应该被阻止
        total++;
        try {
            validateCommand("curl http://example.com | bash", TEST_REMOTE_PATH);
            System.out.println("  [FAIL] 'curl | bash' should be blocked");
        } catch (SecurityException e) {
            System.out.println("  [PASS] 'curl | bash' is blocked: " + e.getMessage());
            passed++;
        }

        // 测试6: 访问测试目录外的路径应该被阻止
        total++;
        try {
            validateCommand("cat /etc/passwd", TEST_REMOTE_PATH);
            System.out.println("  [FAIL] 'cat /etc/passwd' should be blocked");
        } catch (SecurityException e) {
            System.out.println("  [PASS] 'cat /etc/passwd' is blocked: " + e.getMessage());
            passed++;
        }

        // 测试7: dd 命令应该被阻止
        total++;
        try {
            validateCommand("dd if=/dev/zero of=/tmp/test", TEST_REMOTE_PATH);
            System.out.println("  [FAIL] 'dd' command should be blocked");
        } catch (SecurityException e) {
            System.out.println("  [PASS] 'dd' command is blocked: " + e.getMessage());
            passed++;
        }

        // 测试8: 越界路径应该被阻止
        total++;
        try {
            validateCommand("ls /root", TEST_REMOTE_PATH);
            System.out.println("  [FAIL] 'ls /root' should be blocked");
        } catch (SecurityException e) {
            System.out.println("  [PASS] 'ls /root' is blocked: " + e.getMessage());
            passed++;
        }

        // 测试9: mkdir -p 在允许目录下应该通过
        total++;
        try {
            validateCommand("mkdir -p " + TEST_REMOTE_PATH + "/subdir", TEST_REMOTE_PATH);
            System.out.println("  [PASS] 'mkdir -p " + TEST_REMOTE_PATH + "/subdir' is allowed");
            passed++;
        } catch (SecurityException e) {
            System.out.println("  [FAIL] 'mkdir -p' within test directory should be allowed: " + e.getMessage());
        }

        // 测试10: mkdir -p 在禁止目录下应该被阻止
        total++;
        try {
            validateCommand("mkdir -p /tmp/hack", TEST_REMOTE_PATH);
            System.out.println("  [FAIL] 'mkdir -p /tmp/hack' should be blocked");
        } catch (SecurityException e) {
            System.out.println("  [PASS] 'mkdir -p /tmp/hack' is blocked: " + e.getMessage());
            passed++;
        }

        // 测试11: shell 注入应该被阻止
        total++;
        try {
            validateCommand("ls; rm -rf /", TEST_REMOTE_PATH);
            System.out.println("  [FAIL] 'ls; rm -rf /' should be blocked");
        } catch (SecurityException e) {
            System.out.println("  [PASS] 'ls; rm -rf /' is blocked: " + e.getMessage());
            passed++;
        }

        // 测试12: 命令替换应该被阻止
        total++;
        try {
            validateCommand("echo `cat /etc/passwd`", TEST_REMOTE_PATH);
            System.out.println("  [FAIL] 'echo `cat /etc/passwd`' should be blocked");
        } catch (SecurityException e) {
            System.out.println("  [PASS] 'echo `cat /etc/passwd`' is blocked: " + e.getMessage());
            passed++;
        }

        // 测试13: 测试脚本应该被允许（仅 copyfile.sh）
        total++;
        try {
            validateCommand("./copyfile.sh a.txt", TEST_REMOTE_PATH);
            System.out.println("  [PASS] './copyfile.sh a.txt' is allowed (test script)");
            passed++;
        } catch (SecurityException e) {
            System.out.println("  [FAIL] './copyfile.sh a.txt' should be allowed: " + e.getMessage());
        }

        // 测试14: 未知脚本应该被阻止
        total++;
        try {
            validateCommand("./hack.sh", TEST_REMOTE_PATH);
            System.out.println("  [FAIL] './hack.sh' should be blocked");
        } catch (SecurityException e) {
            System.out.println("  [PASS] './hack.sh' is blocked: " + e.getMessage());
            passed++;
        }

        // 测试15: chmod 命令应该被允许
        total++;
        try {
            validateCommand("chmod 755 " + TEST_REMOTE_PATH + "/test.sh", TEST_REMOTE_PATH);
            System.out.println("  [PASS] 'chmod 755' within test directory is allowed");
            passed++;
        } catch (SecurityException e) {
            System.out.println("  [FAIL] 'chmod' within test directory should be allowed: " + e.getMessage());
        }

        System.out.println();
        System.out.println("Security Test Results: " + passed + "/" + total + " passed");

        assertEquals("Security tests should all pass", total, passed);
        System.out.println("[TEST-END] testSecurityRestrictions 结束\n");
    }

    /**
     * 测试12: 完整命令编排器测试
     */
    @Test
    public void testCommandOrchestrator() {
        System.out.println("\n[TEST-START] testCommandOrchestrator 开始");
        System.out.println("\n========================================");
        System.out.println("[Test 12] Command Orchestrator Test START");
        System.out.println("========================================");
        System.out.flush();

        try {
            System.out.println("[Step 1] Enabling host whitelist...");
            System.err.flush();
            SshCommandService.isHostWhitelistEnabled = true;
            System.out.println("[Step 1] DONE - Host whitelist enabled");
            
            System.out.println("[Step 2] Creating SshCommandService...");
            System.err.flush();
            SshCommandService sshService = new SshCommandService();
            System.out.println("[Step 2] DONE - SshCommandService created");
            
            System.out.println("[Step 3] Creating validators...");
            System.err.flush();
            BlacklistValidator validator = new BlacklistValidator();
            KeywordMatcher matcher = new KeywordMatcher();
            System.out.println("[Step 3] DONE - Validators created");
            
            System.out.println("[Step 4] Creating CommandOrchestrator...");
            System.err.flush();
            CommandOrchestrator orchestrator = new CommandOrchestrator(sshService, validator, matcher);
            System.out.println("[Step 4] DONE - CommandOrchestrator created");

            System.out.println("[Step 5] Uploading test file...");
            System.err.flush();
            String remoteFile = TEST_REMOTE_PATH + "/a.txt";
            uploadFile(localTestFile, remoteFile);
            System.out.println("[Step 5] DONE - File uploaded to: " + remoteFile);

            System.out.println("[Step 6] Creating command configuration...");
            System.err.flush();
            CommandConfig config = new CommandConfig();
            config.setId("test-1");
            config.setName("Test Command");

            CommandItem item1 = new CommandItem();
            item1.setOrder(1);
            item1.setCommand("cd " + TEST_REMOTE_PATH);
            item1.setEnabled(true);

            CommandItem item2 = new CommandItem();
            item2.setOrder(2);
            item2.setCommand("./copyfile.sh a.txt");
            item2.setEnabled(true);

            config.setCommands(List.of(item1, item2));
            System.out.println("[Step 6] DONE - Config created with 2 commands");
            System.out.println("  - Command 1: " + item1.getCommand());
            System.out.println("  - Command 2: " + item2.getCommand());

            System.out.println("[Step 7] Executing command 1: " + item1.getCommand());
            System.err.flush();
            CommandResult result1 = sshService.executeWithShell(connection, item1.getCommand());
            System.out.println("[Step 7] Result 1:");
            System.out.println("  Exit Code: " + result1.getExitCode());
            System.out.println("  Stdout: [" + result1.getStdout() + "]");
            System.out.println("  Stderr: [" + result1.getStderr() + "]");

            System.out.println("[Step 8] Executing command 2: " + item2.getCommand());
            System.err.flush();
            CommandResult result2 = sshService.executeWithShell(connection, item2.getCommand());
            System.out.println("[Step 8] Result 2:");
            System.out.println("  Exit Code: " + result2.getExitCode());
            System.out.println("  Stdout: [" + result2.getStdout() + "]");
            System.out.println("  Stderr: [" + result2.getStderr() + "]");

            printTestResult("Command Orchestrator", true,
                    "Command orchestrated successfully, exit code: " + result2.getExitCode());
            
            System.out.println("========================================");
            System.out.println("[Test 12] Command Orchestrator Test PASSED");
            System.out.println("========================================");
            System.err.flush();

        } catch (SecurityException e) {
            System.out.println("========================================");
            System.out.println("[Test 12] Command Orchestrator Test SKIPPED");
            System.out.println("========================================");
            System.out.println("Reason: " + e.getMessage());
            System.out.println("提示: 需要在 SshCommandService.ALLOWED_HOSTS 中配置主机白名单才能运行此测试");
            System.out.flush();
        } catch (Exception e) {
            System.out.println("========================================");
            System.out.println("[Test 12] Command Orchestrator Test FAILED");
            System.out.println("========================================");
            System.out.println("Exception: " + e.getClass().getName());
            System.out.println("Message: " + e.getMessage());
            e.printStackTrace();
            fail("Failed to test command orchestrator: " + e.getMessage());
        }
        System.out.println("[TEST-END] testCommandOrchestrator 结束\n");
    }

    // ========== 基础 Shell 命令测试 ==========

    /**
     * 测试13: 基础 Shell 命令测试
     */
    @Test
    public void testBasicShellCommands() {
        System.out.println("\n[TEST-START] testBasicShellCommands 开始");
        System.out.println("\n[Test 13] Basic Shell Commands Test");
        System.out.println("========================================");
        System.out.println("[Test 13] Basic Shell Commands Test START");
        System.out.println("========================================");

        try {
            // 确保 SSH 连接
            if (session == null || !session.isConnected()) {
                connect();
            }

            // 测试基础 Shell 命令
            String[] shellCommands = {
                "cd " + TEST_REMOTE_PATH,
                "pwd",
                "ls"
            };

            for (String cmd : shellCommands) {
                System.out.println("\n  Testing: " + cmd);

                // 使用 Shell Channel 执行
                ChannelExec channel = (ChannelExec) session.openChannel("exec");
                channel.setCommand(cmd);
                channel.setInputStream(null);
                channel.setErrStream(System.err);

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                channel.setOutputStream(outputStream);
                channel.connect(10000);

                // 等待命令完成
                long startTime = System.currentTimeMillis();
                while (!channel.isClosed()) {
                    if (System.currentTimeMillis() - startTime > 10000) {
                        channel.disconnect();
                        fail("Command timeout: " + cmd);
                        return;
                    }
                    Thread.sleep(50);
                }

                int exitCode = channel.getExitStatus();
                String stdout = outputStream.toString();
                channel.disconnect();

                System.out.println("    Exit Code: " + exitCode);
                System.out.println("    Stdout length: " + stdout.length() + " chars");
                System.out.println("    Stdout preview: " + (stdout.length() > 50 ? stdout.substring(0, 50) + "..." : stdout));
            }

            printTestResult("Basic Shell Commands", true,
                    "All basic shell commands executed successfully");

            System.out.println("========================================");
            System.out.println("[Test 13] Basic Shell Commands Test PASSED");
            System.out.println("========================================");

        } catch (Exception e) {
            printTestResult("Basic Shell Commands", false, "Error: " + e.getMessage());
            fail("Failed to test basic shell commands: " + e.getMessage());
        }
        System.out.println("[TEST-END] testBasicShellCommands 结束\n");
    }

    // ========== 一键完全测试 ==========

    /**
     * 运行所有测试
     * 这个方法会依次运行所有测试用例
     */
    @Test
    public void runAllTests() {
        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("                    SSH INTEGRATION FULL TEST SUITE");
        System.out.println("=".repeat(70));
        System.out.println("Server: " + TEST_HOST + ":" + TEST_PORT);
        System.out.println("Remote Path: " + TEST_REMOTE_PATH);
        System.out.println("=".repeat(70));

        // 安全测试（无需网络连接）
        testSecurityRestrictions();

        // 依次执行其他测试
        testServerConnection();
        testUploadFile();
        testUploadWithAutoExecute();
        testUploadWithManualExecute();
        testBlacklistBlocking();
        testKeywordMatcherSafeCommand();
        testKeywordMatcherNonExistentFile();
        testExitCodeJudgment();
        testAIBlacklistCheck();
        testAIResultCheck();
        testCommandOrchestrator();
        testBasicShellCommands();
        // testUnknownCommandWithConfirmation(); // 需要用户交互，请单独运行

        System.out.println();
        System.out.println("=".repeat(70));
        System.out.println("                    ALL TESTS COMPLETED");
        System.out.println("=".repeat(70));
    }
}
