package com.openxt.uploadsshfile.sftp;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;
import com.openxt.uploadsshfile.i18n.LanguageManager;

/**
 * SFTP 连接验证器
 */
public class SftpValidator {

    private static final int CONNECT_TIMEOUT = 10000;

    /**
     * 测试 SFTP 连接
     */
    public static boolean testConnection(String host, int port, String username, String password) {
        try {
            JSch jsch = new JSch();
            Session session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(CONNECT_TIMEOUT);
            boolean connected = session.isConnected();
            session.disconnect();
            return connected;
        } catch (JSchException e) {
            return false;
        }
    }

    /**
     * 验证路径是否存在（通过 SFTP 连接验证）
     */
    public static boolean validatePath(String host, int port, String username, String password, String path) {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(CONNECT_TIMEOUT);

            Channel channel = session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT);
            sftp = (ChannelSftp) channel;

            // 尝试进入目标目录
            sftp.cd(path);
            return true;
        } catch (JSchException | SftpException e) {
            // 路径不存在或无法访问
            return false;
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * 检查目录是否可写
     */
    public static boolean checkPermission(String host, int port, String username, String password, String path) {
        Session session = null;
        ChannelSftp sftp = null;
        try {
            JSch jsch = new JSch();
            session = jsch.getSession(username, host, port);
            session.setPassword(password);
            session.setConfig("StrictHostKeyChecking", "no");
            session.connect(CONNECT_TIMEOUT);

            Channel channel = session.openChannel("sftp");
            channel.connect(CONNECT_TIMEOUT);
            sftp = (ChannelSftp) channel;

            // 尝试在目标目录创建测试文件来验证写权限
            // 【安全】仅用于验证路径写入权限，测试完成后立即删除
            String testFile = path + "/.write_test_" + System.currentTimeMillis();
            try {
                sftp.put(new java.io.ByteArrayInputStream(new byte[0]), testFile);
                sftp.rm(testFile); // 删除测试文件
                return true;
            } catch (SftpException e) {
                return false;
            }
        } catch (JSchException e) {
            return false;
        } finally {
            if (sftp != null && sftp.isConnected()) {
                sftp.disconnect();
            }
            if (session != null && session.isConnected()) {
                session.disconnect();
            }
        }
    }

    /**
     * 验证服务器配置是否有效
     */
    public static ValidationResult validateServer(String host, int port, String username, String password) {
        LanguageManager lm = LanguageManager.getInstance();
        if (host == null || host.trim().isEmpty()) {
            return new ValidationResult(false, lm.get("sftp.validation.hostEmpty"));
        }
        if (port <= 0 || port > 65535) {
            return new ValidationResult(false, lm.get("sftp.validation.portInvalid"));
        }
        if (username == null || username.trim().isEmpty()) {
            return new ValidationResult(false, lm.get("sftp.validation.usernameEmpty"));
        }
        if (password == null || password.isEmpty()) {
            return new ValidationResult(false, lm.get("sftp.validation.passwordEmpty"));
        }

        // Test connection
        if (!testConnection(host, port, username, password)) {
            return new ValidationResult(false, lm.get("sftp.validation.cannotConnect"));
        }

        return new ValidationResult(true, lm.get("sftp.validation.passed"));
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        private final boolean valid;
        private final String message;

        public ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
