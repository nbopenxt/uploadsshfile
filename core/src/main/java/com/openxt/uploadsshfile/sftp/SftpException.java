package com.openxt.uploadsshfile.sftp;

/**
 * SFTP 异常
 */
public class SftpException extends Exception {
    private final SftpErrorCode errorCode;

    public SftpException(SftpErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SftpException(SftpErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public SftpErrorCode getErrorCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        return "[" + errorCode.getDescription() + "] " + super.getMessage();
    }
}
