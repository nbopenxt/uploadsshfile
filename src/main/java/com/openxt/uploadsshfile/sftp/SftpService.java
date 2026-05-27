package com.openxt.uploadsshfile.sftp;

import com.jcraft.jsch.*;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.util.Logger;
import com.openxt.uploadsshfile.util.Md5Checksum;

import java.io.File;
import java.io.FileInputStream;
import java.util.Vector;

/**
 * SFTP 服务
 *
 * 【安全限制】
 * 本服务仅允许上传文件或目录到服务端，除此之外：
 * - ❌ 禁止删除服务端的任何文件或目录
 * - ❌ 禁止修改服务端的任何文件
 * - ❌ 禁止创建除上传操作必需之外的目录
 * - ✅ 仅允许：上传文件、上传目录（创建必要子目录）、路径写入验证
 */
public class SftpService {
    private Session session;
    private ChannelSftp channel;
    private boolean isWindows = false;

    /**
     * MD5 校验结果（用于 Linux 服务器）
     */
    public static class Md5VerifyResult {
        private final String localMd5;
        private final String remoteMd5;
        private final boolean matched;
        private final String errorMessage;

        public Md5VerifyResult(String localMd5, String remoteMd5, boolean matched) {
            this.localMd5 = localMd5;
            this.remoteMd5 = remoteMd5;
            this.matched = matched;
            this.errorMessage = null;
        }

        public Md5VerifyResult(String errorMessage) {
            this.localMd5 = null;
            this.remoteMd5 = null;
            this.matched = false;
            this.errorMessage = errorMessage;
        }

        public String getLocalMd5() { return localMd5; }
        public String getRemoteMd5() { return remoteMd5; }
        public boolean isMatched() { return matched; }
        public String getErrorMessage() { return errorMessage; }
        public boolean hasError() { return errorMessage != null; }
    }

    /**
     * 文件大小校验结果（用于 Windows 服务器）
     */
    public static class SizeVerifyResult {
        private final long localSize;
        private final long remoteSize;
        private final boolean matched;
        private final String errorMessage;

        public SizeVerifyResult(long localSize, long remoteSize, boolean matched) {
            this.localSize = localSize;
            this.remoteSize = remoteSize;
            this.matched = matched;
            this.errorMessage = null;
        }

        public SizeVerifyResult(String errorMessage) {
            this.localSize = -1;
            this.remoteSize = -1;
            this.matched = false;
            this.errorMessage = errorMessage;
        }

        public long getLocalSize() { return localSize; }
        public long getRemoteSize() { return remoteSize; }
        public boolean isMatched() { return matched; }
        public String getErrorMessage() { return errorMessage; }
        public boolean hasError() { return errorMessage != null; }
    }

    /**
     * 连接 SFTP 服务器
     */
    public void connect(ServerConfig config, String password) throws SftpException {
        try {
            Logger.debug("SftpService", "Connecting to " + config.getHost() + ":" + config.getPort());
            
            JSch jsch = new JSch();
            session = jsch.getSession(config.getUsername(), config.getHost(), config.getPort());
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.setConfig("PreferredAuthentications", "password");
            
            // 设置超时时间
            session.setTimeout(30000);
            
            Logger.debug("SftpService", "Opening session...");
            session.connect(15000);
            Logger.debug("SftpService", "Session connected, isConnected=" + session.isConnected());

            if (!session.isConnected()) {
                throw new SftpException(SftpErrorCode.CONNECTION_FAILED, "SSH Session connection failed");
            }

            Logger.debug("SftpService", "Opening SFTP channel...");
            Channel channel = session.openChannel("sftp");
            channel.connect(15000);
            
            if (!channel.isConnected()) {
                throw new SftpException(SftpErrorCode.CONNECTION_FAILED, "SFTP channel connection failed");
            }
            
            this.channel = (ChannelSftp) channel;
            Logger.debug("SftpService", "SFTP channel connected successfully");

            // 检测操作系统类型
            // Windows OpenSSH SFTP 返回 /C:/Users/... 格式（路径中包含 /盘符:）
            // Linux 返回 /home/user 或 /root 等格式
            String pwd = this.channel.pwd();
            boolean detectedWindows = pwd.matches("^/[A-Z]:/.*");  // 匹配 /C:/, /D:/ 等Windows路径格式
            Logger.debug("SftpService", "检测到操作系统: " + (detectedWindows ? "Windows" : "Linux/Unix") + ", pwd=" + pwd);

            // 校验：配置中的 osType 与实际检测结果必须一致
            boolean configuredWindows = "windows".equalsIgnoreCase(config.getOsType());
            if (configuredWindows != detectedWindows) {
                String errorMsg = String.format(
                    "服务器类型配置不一致！配置为 [%s]，实际检测为 [%s]。\n" +
                    "请检查服务器 '%s' 的操作系统类型配置是否正确。",
                    config.getOsType(), detectedWindows ? "windows" : "linux", config.getName()
                );
                Logger.error("SftpService", errorMsg);
                disconnect();
                throw new SftpException(SftpErrorCode.CONNECTION_FAILED, errorMsg);
            }
            this.isWindows = detectedWindows;

        } catch (JSchException e) {
            Logger.error("SftpService", "JSch error", e);
            throw new SftpException(SftpErrorCode.CONNECTION_FAILED, "SSH连接失败: " + e.getMessage());
        } catch (SftpException e) {
            throw e;
        } catch (Exception e) {
            Logger.error("SftpService", "Connection error", e);
            throw new SftpException(SftpErrorCode.CONNECTION_FAILED, "连接失败: " + e.getMessage());
        }
    }

    /**
     * 断开连接
     */
    public void disconnect() {
        if (channel != null) {
            try {
                channel.disconnect();
            } catch (Exception ignored) {}
        }
        if (session != null) {
            try {
                session.disconnect();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 是否已连接
     */
    public boolean isConnected() {
        return session != null && session.isConnected() && channel != null && channel.isConnected();
    }

    /**
     * 获取当前目录
     */
    public String pwd() {
        try {
            return channel.pwd();
        } catch (Exception e) {
            return "未知";
        }
    }

    /**
     * 判断是否为 Windows 服务器
     */
    public boolean isWindows() {
        return isWindows;
    }

    /**
     * 将 Windows 路径转换为 SFTP 路径格式
     *
     * <p>Windows 路径格式: e:\temp 或 e:/temp
     * <p>SFTP 路径格式: /e:/temp
     *
     * @param windowsPath Windows 路径
     * @return SFTP 路径格式
     */
    public static String toSftpPath(String windowsPath) {
        if (windowsPath == null || windowsPath.isEmpty()) {
            return windowsPath;
        }
        // 将反斜杠替换为正斜杠
        String normalized = windowsPath.replace("\\", "/");
        // 如果是驱动器路径（如 e:/temp），转换为 /e:/temp
        if (normalized.matches("^[a-zA-Z]:/.*")) {
            return "/" + normalized;
        }
        return normalized;
    }

    /**
     * 切换到指定目录（自动处理 Windows 路径）
     */
    public void cd(String path) throws SftpException {
        try {
            String sftpPath = isWindows ? toSftpPath(path) : path;
            Logger.debug("SftpService", "cd: " + path + " -> " + sftpPath);
            channel.cd(sftpPath);
        } catch (Exception e) {
            throw new SftpException(SftpErrorCode.TRANSFER_FAILED, "切换目录失败: " + path + " - " + e.getMessage());
        }
    }

    /**
     * 上传文件（无 MD5 校验，自动处理 Windows 路径）
     */
    public void uploadFile(File file, String remotePath, UploadProgressCallback callback) throws SftpException {
        String sftpPath = isWindows ? toSftpPath(remotePath) : remotePath;
        try {
            // 确保目录存在
            cd(sftpPath);
            // 上传到当前目录
            uploadFileToCurrentDir(file, callback);
        } catch (SftpException e) {
            throw e;
        }
    }

    /**
     * 上传到当前目录
     */
    private void uploadFileToCurrentDir(File file, UploadProgressCallback callback) throws SftpException {
        try {
            long fileSize = file.length();

            callback.onProgress(file.getName(), 0, 0, fileSize);

            channel.put(new FileInputStream(file), file.getName(), new SftpProgressMonitor() {
                private long transferred = 0;

                @Override
                public void init(int op, String src, String dest, long max) {
                }

                @Override
                public boolean count(long count) {
                    transferred += count;
                    int percent = (int) (transferred * 100 / fileSize);
                    callback.onProgress(file.getName(), percent, transferred, fileSize);
                    return true;
                }

                @Override
                public void end() {
                    callback.onFileCompleted(file.getName(), fileSize, true, null);
                }
            });

            Logger.debug("SftpService", "File uploaded: " + file.getName() + ", size=" + fileSize);

        } catch (Exception e) {
            callback.onFileCompleted(file.getName(), file.length(), false, e.getMessage());
            throw new SftpException(SftpErrorCode.TRANSFER_FAILED, e.getMessage());
        }
    }

    /**
     * 上传文件（自动校验）
     *
     * <p>上传成功后自动进行校验：
     * <ul>
     *   <li>Linux: MD5 校验（需要提供 localMd5）</li>
     *   <li>Windows: 文件大小校验</li>
     * </ul>
     *
     * @param file 本地文件
     * @param remotePath 远程目录路径
     * @param localMd5 本地文件 MD5（Linux 服务器必需，Windows 可传 null）
     * @param callback 进度回调
     * @throws SftpException 上传失败或校验失败
     */
    public void uploadFileWithMd5(File file, String remotePath, String localMd5, UploadProgressCallback callback) throws SftpException {
        String remoteFilePath = (isWindows ? toSftpPath(remotePath) : remotePath) + "/" + file.getName();
        long fileSize = file.length();

        try {
            callback.onProgress(file.getName(), 0, 0, fileSize);

            // 上传到 SFTP（使用不含文件名的目录路径）
            String remoteDir = isWindows ? toSftpPath(remotePath) : remotePath;
            cd(remoteDir);

            channel.put(new FileInputStream(file), file.getName(), new SftpProgressMonitor() {
                private long transferred = 0;

                @Override
                public void init(int op, String src, String dest, long max) {
                }

                @Override
                public boolean count(long count) {
                    transferred += count;
                    int percent = (int) (transferred * 100 / fileSize);
                    callback.onProgress(file.getName(), percent, transferred, fileSize);
                    return true;
                }

                @Override
                public void end() {
                    // 上传完成
                }
            });

            Logger.debug("SftpService", "文件上传成功: " + file.getName() + ", size=" + fileSize);

            // ===== 自动校验 =====
            boolean verifySuccess = false;
            String verifyError = null;

            if (isWindows) {
                // Windows: 文件大小校验
                SizeVerifyResult sizeResult = verifyRemoteFileSize(remoteFilePath, fileSize);
                if (sizeResult.hasError()) {
                    verifyError = "文件大小校验失败: " + sizeResult.getErrorMessage();
                    Logger.error("SftpService", verifyError);
                } else if (!sizeResult.isMatched()) {
                    verifyError = String.format("文件大小不匹配！本地=%d 字节，远程=%d 字节",
                        sizeResult.getLocalSize(), sizeResult.getRemoteSize());
                    Logger.error("SftpService", verifyError);
                } else {
                    verifySuccess = true;
                    Logger.debug("SftpService", "文件大小校验成功");
                }
            } else {
                // Linux: MD5 校验
                if (localMd5 == null || localMd5.isEmpty()) {
                    Logger.warn("SftpService", "Linux服务器未提供MD5，跳过校验");
                    verifySuccess = true;
                } else {
                    Md5VerifyResult md5Result = verifyRemoteMd5(remoteFilePath, localMd5);
                    if (md5Result.hasError()) {
                        verifyError = "MD5校验失败: " + md5Result.getErrorMessage();
                        Logger.error("SftpService", verifyError);
                    } else if (!md5Result.isMatched()) {
                        verifyError = String.format("MD5不匹配！本地=%s，远程=%s",
                            md5Result.getLocalMd5(), md5Result.getRemoteMd5());
                        Logger.error("SftpService", verifyError);
                    } else {
                        verifySuccess = true;
                        Logger.debug("SftpService", "MD5校验成功");
                    }
                }
            }

            // 校验失败则抛异常
            if (!verifySuccess && verifyError != null) {
                callback.onFileCompleted(file.getName(), fileSize, false, verifyError);
                throw new SftpException(SftpErrorCode.TRANSFER_FAILED, verifyError);
            }

            callback.onFileCompleted(file.getName(), fileSize, true, null);

        } catch (SftpException e) {
            throw e;
        } catch (Exception e) {
            callback.onFileCompleted(file.getName(), file.length(), false, e.getMessage());
            throw new SftpException(SftpErrorCode.TRANSFER_FAILED, e.getMessage());
        }
    }

    /**
     * 获取远程文件的 MD5 值（Linux 和 Windows 都支持）
     *
     * <p>Linux: 使用 md5sum 命令
     * <p>Windows: 使用 cmd 的 certutil -hashfile MD5 命令
     *
     * @param remoteFilePath 远程文件路径
     * @return MD5 十六进制字符串（小写），或 null 如果失败
     */
    public String getRemoteFileMd5(String remoteFilePath) {
        try {
            // 检查 session 连接状态
            if (session == null || !session.isConnected()) {
                Logger.debug("SftpService", "Session未连接，无法获取远程MD5");
                return null;
            }

            Logger.debug("SftpService", "正在打开exec channel获取MD5, isWindows=" + isWindows);
            ChannelExec execChannel = (ChannelExec) session.openChannel("exec");
            String command;

            if (isWindows) {
                // Windows: 使用 cmd 的 certutil 命令
                String winPath = remoteFilePath.replace("/", "\\");
                command = "cmd /c certutil -hashfile \"" + winPath + "\" MD5";
            } else {
                // Linux: 使用 md5sum 命令
                command = "md5sum \"" + remoteFilePath.replace("\"", "\\\"") + "\"";
            }

            execChannel.setCommand(command);
            Logger.debug("SftpService", "获取MD5命令: " + command);

            java.io.InputStream in = execChannel.getInputStream();
            java.io.InputStream err = execChannel.getExtInputStream();
            execChannel.connect(15000);

            Logger.debug("SftpService", "exec channel已连接, isClosed=" + execChannel.isClosed());

            // 读取输出
            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[1024];

            // Windows OpenSSH ConPTY 强制使用 UTF-8 编码，Linux 使用 UTF-8
            java.nio.charset.Charset charset = java.nio.charset.StandardCharsets.UTF_8;

            // 等待命令完成（最多30秒）
            long startTime = System.currentTimeMillis();
            while (System.currentTimeMillis() - startTime < 30000) {
                while (in.available() > 0) {
                    int i = in.read(buffer, 0, 1024);
                    if (i < 0) break;
                    output.append(new String(buffer, 0, i, charset));
                }
                if (execChannel.isClosed()) {
                    break;
                }
                Thread.sleep(50);
            }

            Logger.debug("SftpService", "exec channel已关闭, 输出长度=" + output.length());

            // 检查错误输出
            StringBuilder errorOutput = new StringBuilder();
            while (err.available() > 0) {
                int i = err.read(buffer, 0, 1024);
                if (i < 0) break;
                errorOutput.append(new String(buffer, 0, i, charset));
            }

            int exitCode = execChannel.getExitStatus();
            Logger.debug("SftpService", "exec exitCode=" + exitCode + ", errorOutput长度=" + errorOutput.length());
            execChannel.disconnect();

            if (errorOutput.length() > 0) {
                Logger.debug("SftpService", "MD5命令错误: " + errorOutput.toString());
                return null;
            }

            String result = output.toString().trim();
            Logger.debug("SftpService", "MD5输出: " + result);

            if (result.isEmpty()) {
                Logger.debug("SftpService", "MD5输出为空");
                return null;
            }

            if (isWindows) {
                // Windows certutil 输出格式:
                // ----- 省略若干行 -----
                // MD5 hash(...): 6dc41f9268b2652918c5d0cb3449ff34
                // CertUtil: -hashfile 命令成功完成。
                // 解析 MD5 哈希值
                String[] lines = result.split("\n");
                Logger.debug("SftpService", "MD5输出行数: " + lines.length);
                for (String line : lines) {
                    line = line.trim();
                    Logger.debug("SftpService", "处理行: " + line);
                    // 查找包含32位MD5的行
                    if (line.matches(".*[0-9a-f]{32}.*")) {
                        String[] parts = line.split("\\s+");
                        for (String part : parts) {
                            if (part.matches("^[0-9a-f]{32}$")) {
                                Logger.debug("SftpService", "解析到MD5: " + part);
                                return part.toLowerCase();
                            }
                        }
                    }
                }
                Logger.debug("SftpService", "未能从certutil输出中解析MD5");
                return null;
            } else {
                // Linux md5sum 输出格式: "md5hash  filename"
                String[] parts = result.split("\\s+");
                if (parts.length > 0) {
                    return parts[0].toLowerCase();
                }
                return null;
            }

        } catch (Exception e) {
            Logger.debug("SftpService", "Failed to get remote MD5: " + e.getMessage());
            return null;
        }
    }

    /**
     * 获取远程文件的大小（Windows 和 Linux 都支持）
     *
     * <p>Windows: 使用 SFTP stat 或 cmd 的 dir 命令
     * <p>Linux: 使用 SFTP stat 或 stat -c %s
     *
     * @param remoteFilePath 远程文件路径
     * @return 文件大小（字节），或 -1 如果失败
     */
    public long getRemoteFileSize(String remoteFilePath) {
        try {
            // 优先使用 SFTP Channel 的 stat 方法（跨平台最可靠）
            try {
                SftpATTRS attrs = this.channel.stat(remoteFilePath);
                if (attrs != null) {
                    long size = attrs.getSize();
                    Logger.debug("SftpService", "通过SFTP stat获取文件大小: " + size);
                    return size;
                }
            } catch (Exception e) {
                Logger.debug("SftpService", "SFTP stat获取文件大小失败: " + e.getMessage());
            }

            // Fallback: 使用 exec channel 执行命令
            String command;
            if (isWindows) {
                // Windows: 使用 cmd 的 dir 命令
                String escapedPath = remoteFilePath.replace("/", "\\");
                command = "cmd /c dir \"" + escapedPath + "\" | findstr \".txt\"";
            } else {
                // Linux
                command = "stat -c %s \"" + remoteFilePath.replace("\"", "\\\"") + "\"";
            }

            Logger.debug("SftpService", "获取文件大小命令: " + command);

            ChannelExec execChannel = (ChannelExec) session.openChannel("exec");
            execChannel.setCommand(command);

            java.io.InputStream in = execChannel.getInputStream();
            java.io.InputStream err = execChannel.getExtInputStream();
            execChannel.connect();

            // 读取输出
            StringBuilder output = new StringBuilder();
            byte[] buffer = new byte[1024];
            while (true) {
                while (in.available() > 0) {
                    int i = in.read(buffer, 0, 1024);
                    if (i < 0) break;
                    output.append(new String(buffer, 0, i, java.nio.charset.StandardCharsets.UTF_8));
                }
                if (execChannel.isClosed()) {
                    break;
                }
                Thread.sleep(50);
            }

            execChannel.disconnect();

            String result = output.toString().trim();
            Logger.debug("SftpService", "文件大小输出: " + result);

            if (result.isEmpty()) {
                return -1;
            }

            // 解析 Windows dir 命令输出
            if (isWindows) {
                // dir 输出格式: 05/17/2026  03:41PM                14 a.txt
                String[] parts = result.split("\\s+");
                if (parts.length >= 4) {
                    try {
                        // 文件大小在倒数第二列
                        return Long.parseLong(parts[parts.length - 2].replace(",", ""));
                    } catch (NumberFormatException e) {
                        Logger.debug("SftpService", "解析Windows文件大小失败: " + result);
                    }
                }
                return -1;
            }

            return Long.parseLong(result);

        } catch (Exception e) {
            Logger.debug("SftpService", "获取远程文件大小失败: " + e.getMessage());
            return -1;
        }
    }

    /**
     * 验证远程文件 MD5（Linux 和 Windows 都支持）
     *
     * @param remoteFilePath 远程文件路径
     * @param expectedMd5 期望的 MD5 值
     * @return 校验结果
     */
    public Md5VerifyResult verifyRemoteMd5(String remoteFilePath, String expectedMd5) {
        String remoteMd5 = getRemoteFileMd5(remoteFilePath);

        if (remoteMd5 == null) {
            return new Md5VerifyResult("Failed to calculate remote file MD5");
        }

        boolean matched = Md5Checksum.verify(expectedMd5, remoteMd5);
        Logger.debug("SftpService", "MD5 verification: local=" + expectedMd5 + ", remote=" + remoteMd5 + ", matched=" + matched);

        return new Md5VerifyResult(expectedMd5, remoteMd5, matched);
    }

    /**
     * 验证远程文件大小（Windows 和 Linux 都支持）
     *
     * <p>Windows: 使用文件大小比对
     * <p>Linux: 也可使用此方法替代 MD5 校验
     *
     * @param remoteFilePath 远程文件路径
     * @param expectedSize 期望的文件大小
     * @return 校验结果
     */
    public SizeVerifyResult verifyRemoteFileSize(String remoteFilePath, long expectedSize) {
        long remoteSize = getRemoteFileSize(remoteFilePath);

        if (remoteSize < 0) {
            return new SizeVerifyResult("Failed to get remote file size");
        }

        boolean matched = (expectedSize == remoteSize);
        Logger.debug("SftpService", "文件大小校验: local=" + expectedSize + ", remote=" + remoteSize + ", matched=" + matched);

        return new SizeVerifyResult(expectedSize, remoteSize, matched);
    }

    /**
     * 上传目录
     */
    public void uploadDirectory(File dir, String remotePath, UploadProgressCallback callback) throws SftpException {
        String dirName = dir.getName();
        String targetPath = remotePath + "/" + dirName;

        try {
            // 创建远程目录
            channel.mkdir(targetPath);
        } catch (Exception e) {
            // 目录可能已存在
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    uploadDirectory(file, targetPath, callback);
                } else {
                    uploadFile(file, targetPath, callback);
                }
            }
        }
    }

    /**
     * 上传进度回调
     */
    public interface UploadProgressCallback {
        void onProgress(String fileName, int percent, long uploaded, long total);
        void onFileCompleted(String fileName, long size, boolean success, String errorMessage);
    }
}
