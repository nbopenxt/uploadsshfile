package com.openxt.uploadsshfile.sftp;

import com.openxt.uploadsshfile.i18n.LanguageManager;

/**
 * SFTP error codes
 */
public enum SftpErrorCode {
    CONNECTION_FAILED("sftp.error.connectionFailed"),
    AUTH_FAILED("sftp.error.authFailed"),
    PATH_NOT_FOUND("sftp.error.pathNotFound"),
    PERMISSION_DENIED("sftp.error.permissionDenied"),
    TRANSFER_FAILED("sftp.error.transferFailed"),
    TIMEOUT("sftp.error.timeout"),
    UNKNOWN("sftp.error.unknown");

    private final String i18nKey;

    SftpErrorCode(String i18nKey) {
        this.i18nKey = i18nKey;
    }

    public String getDescription() {
        return LanguageManager.getInstance().get(i18nKey);
    }
}
