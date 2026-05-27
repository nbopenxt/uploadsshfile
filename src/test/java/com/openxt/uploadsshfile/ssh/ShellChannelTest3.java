package com.openxt.uploadsshfile.ssh;

import com.openxt.uploadsshfile.TestConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.model.SshConnection;
import com.openxt.uploadsshfile.sftp.SftpService;
import com.openxt.uploadsshfile.util.Md5Checksum;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * ShellChannelTest3 - 验证 Linux 和 Windows 服务器上传与命令执行
 *
 * <p>使用项目中已实现的 SshCommandService 和 SftpService 进行测试
 *
 * <p>测试内容：
 * <ul>
 *   <li>Linux: SFTP上传 + MD5校验 + Shell执行命令</li>
 *   <li>Windows: SFTP上传 + 文件大小校验 + Shell执行命令</li>
 * </ul>
 */
public class ShellChannelTest3 {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║    ShellChannelTest3 - Linux & Windows 上传命令测试         ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝\n");

        // ===== Linux 服务器测试 =====
        testLinuxServer();

        System.out.println("\n");

        // ===== Windows 服务器测试 =====
        testWindowsServer();
    }

    /**
     * 测试 Linux 服务器
     * 配置: 10.55.3.21, /home/temp, ./copyfile.sh
     */
    private static void testLinuxServer() {
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  测试 Linux 服务器                                          │");
        System.out.println("│  主机: " + TestConfig.LINUX_HOST + "                                          │");
        System.out.println("│  路径: " + TestConfig.LINUX_REMOTE_PATH + "                                          │");
        System.out.println("│  命令: cd " + TestConfig.LINUX_REMOTE_PATH + " && ./copyfile.sh a.txt                │");
        System.out.println("│  校验: MD5 校验                                            │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");

        // ===== 服务器配置（实际值在 TestConfig 中统一管理） =====
        final String HOST = TestConfig.LINUX_HOST;
        final int PORT = TestConfig.LINUX_PORT;
        final String USER = TestConfig.LINUX_USER;
        final String PASSWORD = TestConfig.LINUX_PASSWORD;
        final String REMOTE_PATH = TestConfig.LINUX_REMOTE_PATH;
        // ============================

        SshCommandService sshService = new SshCommandService();
        SftpService sftpService = new SftpService();
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setId("linux-test");
        serverConfig.setName("Linux测试服务器");
        serverConfig.setHost(HOST);
        serverConfig.setPort(PORT);
        serverConfig.setUsername(USER);
        serverConfig.setPassword(PASSWORD);
        serverConfig.setOsType("linux");

        SshConnection connection = SshConnection.fromServerConfig(serverConfig, PASSWORD);

        File localFile = null;

        try {
            // 1. 生成测试文件
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            localFile = createTestFile("a.txt", timestamp);
            String localMd5 = Md5Checksum.calculate(localFile);
            long localSize = localFile.length();

            System.out.println("[1] 测试文件已生成");
            System.out.println("    文件名: " + localFile.getName());
            System.out.println("    文件大小: " + localSize + " bytes");
            System.out.println("    MD5: " + localMd5 + "\n");

            // 2. SFTP 连接并上传
            System.out.println("[2] SFTP 连接服务器...");
            sftpService.connect(serverConfig, PASSWORD);
            System.out.println("    连接成功，当前目录: " + sftpService.pwd());
            System.out.println("    操作系统检测: " + (sftpService.isWindows() ? "Windows" : "Linux") + "\n");

            // 3. 上传文件（带自动 MD5 校验）
            System.out.println("[3] 上传文件到 " + REMOTE_PATH + " ...");
            final boolean[] uploadSuccess = {false};
            final String[] uploadError = {null};

            sftpService.uploadFileWithMd5(localFile, REMOTE_PATH, localMd5, new SftpService.UploadProgressCallback() {
                @Override
                public void onProgress(String fileName, int percent, long uploaded, long total) {
                    System.out.println("    上传进度: " + percent + "% (" + uploaded + "/" + total + " bytes)");
                }

                @Override
                public void onFileCompleted(String fileName, long size, boolean success, String errorMessage) {
                    uploadSuccess[0] = success;
                    uploadError[0] = errorMessage;
                    if (success) {
                        System.out.println("    文件上传成功: " + size + " bytes");
                    } else {
                        System.out.println("    文件上传失败: " + errorMessage);
                    }
                }
            });

            if (!uploadSuccess[0]) {
                System.out.println("\n[错误] 上传失败，测试终止");
                return;
            }

            // 显示 MD5 校验详情
            String remoteFilePath = REMOTE_PATH + "/" + localFile.getName();
            SftpService.Md5VerifyResult md5Result = sftpService.verifyRemoteMd5(remoteFilePath, localMd5);
            System.out.println("    MD5 校验详情:");
            System.out.println("      本地 MD5:  " + md5Result.getLocalMd5());
            System.out.println("      远程 MD5:  " + md5Result.getRemoteMd5());
            System.out.println("      校验结果:  " + (md5Result.isMatched() ? "✓ 匹配" : "✗ 不匹配") + "\n");

            // 4. Shell 执行命令（分开执行）
            sshService.isHostWhitelistEnabled = false;  // 测试环境禁用白名单

            // 4.1 切换目录
            System.out.println("[4] Shell 执行命令（分开执行）:");
            System.out.println("    [4.1] cd /home/temp");
            CommandResult result1 = sshService.executeWithShell(connection, "cd /home/temp");
            System.out.println("         退出码: " + result1.getExitCode());
            System.out.println("         时间: " + result1.getDurationMs() + "ms");
            if (!result1.getStdout().isEmpty()) {
                System.out.println("         输出: " + result1.getStdout().trim());
            }

            // 4.2 执行脚本
            System.out.println("    [4.2] ./copyfile.sh a.txt");
            CommandResult result2 = sshService.executeWithShell(connection, "./copyfile.sh a.txt");
            System.out.println("         退出码: " + result2.getExitCode());
            System.out.println("         时间: " + result2.getDurationMs() + "ms");
            if (!result2.getStdout().isEmpty()) {
                System.out.println("         输出:\n" + indentOutput(result2.getStdout()));
            }
            if (!result2.getStderr().isEmpty()) {
                System.out.println("         错误:\n" + indentOutput(result2.getStderr()));
            }

            CommandResult result = result2;

            // 5. 结果判定
            System.out.println("\n[5] 测试结果: " + (result.getExitCode() == 0 ? "✓ 通过" : "✗ 失败"));

        } catch (Exception e) {
            System.err.println("\n[错误] " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 清理
            sshService.disconnect(connection);
            sftpService.disconnect();
            if (localFile != null && localFile.exists()) {
                localFile.delete();
            }
        }
    }

    /**
     * 测试 Windows 服务器
     * 配置: 192.168.1.101, e:/temp, copyfile.bat
     */
    private static void testWindowsServer() {
        System.out.println("┌─────────────────────────────────────────────────────────────┐");
        System.out.println("│  测试 Windows 服务器                                         │");
        System.out.println("│  主机: " + TestConfig.WINDOWS_C_HOST + "                                        │");
        System.out.println("│  路径: e:/temp                                             │");
        System.out.println("│  命令: cd /d e:\\temp && copyfile.bat a.txt                 │");
        System.out.println("│  校验: 文件大小校验                                          │");
        System.out.println("└─────────────────────────────────────────────────────────────┘\n");

        // ===== 请填写以下配置 =====
        final int PORT = TestConfig.WINDOWS_C_PORT;

/* 
        final String HOST = TestConfig.WINDOWS_B_HOST;
        final String USER = TestConfig.WINDOWS_B_USER;
        final String PASSWORD = TestConfig.WINDOWS_B_PASSWORD;
        final String REMOTE_PATH = TestConfig.WINDOWS_B_REMOTE_PATH;
 */
        final String HOST = TestConfig.WINDOWS_C_HOST;
        final String USER = TestConfig.WINDOWS_C_USER;
        final String PASSWORD = TestConfig.WINDOWS_C_PASSWORD;
        final String REMOTE_PATH = TestConfig.WINDOWS_C_REMOTE_PATH;
        // ============================

        SshCommandService sshService = new SshCommandService();
        SftpService sftpService = new SftpService();
        ServerConfig serverConfig = new ServerConfig();
        serverConfig.setId("windows-test");
        serverConfig.setName("Windows测试服务器");
        serverConfig.setHost(HOST);
        serverConfig.setPort(PORT);
        serverConfig.setUsername(USER);
        serverConfig.setPassword(PASSWORD);
        serverConfig.setOsType("windows");

        SshConnection connection = SshConnection.fromServerConfig(serverConfig, PASSWORD);

        File localFile = null;

        try {
            // 1. 生成测试文件
            String timestamp = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            localFile = createTestFile("a.txt", timestamp);
            long localSize = localFile.length();
            String localMd5 = Md5Checksum.calculate(localFile);

            System.out.println("[1] 测试文件已生成");
            System.out.println("    文件名: " + localFile.getName());
            System.out.println("    文件大小: " + localSize + " bytes");
            System.out.println("    MD5: " + localMd5 + "\n");

            // 2. SFTP 连接并上传
            System.out.println("[2] SFTP 连接服务器...");
            sftpService.connect(serverConfig, PASSWORD);
            System.out.println("    连接成功，当前目录: " + sftpService.pwd());
            System.out.println("    操作系统检测: " + (sftpService.isWindows() ? "Windows" : "Linux") + "\n");

            // 3. 上传文件（带 MD5 校验，Linux 和 Windows 都支持）
            System.out.println("[3] 上传文件到 " + REMOTE_PATH + " ...");
            System.out.flush();

            // 检查上传前的连接状态
            System.out.println("    [DEBUG] 远程目录路径: " + REMOTE_PATH);
            System.out.flush();
            final boolean[] uploadSuccess = {false};
            final String[] uploadError = {null};

            sftpService.uploadFileWithMd5(localFile, REMOTE_PATH, localMd5, new SftpService.UploadProgressCallback() {
                @Override
                public void onProgress(String fileName, int percent, long uploaded, long total) {
                    System.out.println("    上传进度: " + percent + "% (" + uploaded + "/" + total + " bytes)");
                    System.out.flush();
                }

                @Override
                public void onFileCompleted(String fileName, long size, boolean success, String errorMessage) {
                    uploadSuccess[0] = success;
                    uploadError[0] = errorMessage;
                    System.out.println("    [回调] 上传完成: success=" + success + ", size=" + size + ", error=" + errorMessage);
                    System.out.flush();
                }
            });

            System.out.println("    [主线程] uploadFileWithMd5 返回，uploadSuccess=" + uploadSuccess[0]);
            System.out.flush();

            if (!uploadSuccess[0]) {
                System.out.println("\n[错误] 上传失败: " + uploadError[0]);
                return;
            }

            // 显示 MD5 校验详情
            String remoteFilePath = REMOTE_PATH.replace("/", "\\") + "\\" + localFile.getName();
            System.out.println("    [主线程] 正在校验文件: " + remoteFilePath);
            System.out.flush();
            SftpService.Md5VerifyResult md5Result = sftpService.verifyRemoteMd5(remoteFilePath, localMd5);
            System.out.println("    [主线程] MD5 校验详情:");
            System.out.println("      本地 MD5:  " + md5Result.getLocalMd5());
            System.out.println("      远程 MD5:  " + md5Result.getRemoteMd5());
            System.out.println("      校验结果:  " + (md5Result.isMatched() ? "✓ 匹配" : "✗ 不匹配") + "\n");
            System.out.flush();

            // 4. Shell 执行命令（分开执行）
            sshService.isHostWhitelistEnabled = false;  // 测试环境禁用白名单

            // 4.1 切换目录
            System.out.println("[4] Shell 执行命令（分开执行）:");
            System.out.println("    [4.1] cd /d e:\\temp");
            CommandResult result1 = sshService.executeWithShell(connection, "cd /d e:\\temp");
            System.out.println("         退出码: " + result1.getExitCode());
            System.out.println("         时间: " + result1.getDurationMs() + "ms");
            if (!result1.getStdout().isEmpty()) {
                System.out.println("         输出: " + result1.getStdout().trim());
            }

            // 4.2 执行批处理
            System.out.println("    [4.2] copyfile.bat a.txt");
            CommandResult result2 = sshService.executeWithShell(connection, "copyfile.bat a.txt");
            System.out.println("         退出码: " + result2.getExitCode());
            System.out.println("         时间: " + result2.getDurationMs() + "ms");
            if (!result2.getStdout().isEmpty()) {
                System.out.println("         输出:\n" + indentOutput(result2.getStdout()));
            }
            if (!result2.getStderr().isEmpty()) {
                System.out.println("         错误:\n" + indentOutput(result2.getStderr()));
            }

            CommandResult result = result2;

            // 5. 结果判定
            System.out.println("\n[5] 测试结果: " + (result.getExitCode() == 0 ? "✓ 通过" : "✗ 失败"));

        } catch (Exception e) {
            System.err.println("\n[错误] " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 清理
            sshService.disconnect(connection);
            sftpService.disconnect();
            if (localFile != null && localFile.exists()) {
                localFile.delete();
            }
        }
    }

    /**
     * 创建测试文件
     */
    private static File createTestFile(String fileName, String content) throws Exception {
        File file = new File(fileName);
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(content);
        }
        return file;
    }

    /**
     * 缩进输出
     */
    private static String indentOutput(String output) {
        StringBuilder sb = new StringBuilder();
        for (String line : output.split("\n")) {
            sb.append("        ").append(line).append("\n");
        }
        return sb.toString();
    }
}
