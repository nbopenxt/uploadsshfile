package com.openxt.uploadsshfile.sftp;

/**
 * SFTP 错误码
 */
public enum SftpErrorCode {
    CONNECTION_FAILED("连接失败"),
    AUTH_FAILED("认证失败"),
    PATH_NOT_FOUND("路径不存在"),
    PERMISSION_DENIED("权限不足"),
    TRANSFER_FAILED("传输失败"),
    TIMEOUT("连接超时"),
    UNKNOWN("未知错误");

    private final String description;

    SftpErrorCode(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
