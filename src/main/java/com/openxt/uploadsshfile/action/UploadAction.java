package com.openxt.uploadsshfile.action;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.openxt.uploadsshfile.ai.AICommandChecker;
import com.openxt.uploadsshfile.ai.AIResultChecker;
import com.openxt.uploadsshfile.config.ConfigManager;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.logging.DailyLogService;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.model.ExecuteTiming;
import com.openxt.uploadsshfile.model.ExecutionSummary;
import com.openxt.uploadsshfile.model.SshConnection;
import com.openxt.uploadsshfile.orchestration.CommandOrchestrator;
import com.openxt.uploadsshfile.orchestration.ExecutionListener;
import com.openxt.uploadsshfile.persistence.SecureStorage;
import com.openxt.uploadsshfile.sftp.SftpException;
import com.openxt.uploadsshfile.store.StoreManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.sftp.SftpService;
import com.openxt.uploadsshfile.ssh.SshCommandService;
import com.openxt.uploadsshfile.util.Md5Checksum;
import com.openxt.uploadsshfile.ui.ExecutionProgressDialog;
import com.openxt.uploadsshfile.ui.ProgressDialog;
import com.openxt.uploadsshfile.ui.UploadDialog;
import com.openxt.uploadsshfile.util.Logger;
import com.openxt.uploadsshfile.validation.BlacklistValidator;
import com.openxt.uploadsshfile.validation.KeywordMatcher;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 上传动作
 * 右键点击文件/目录时显示，支持多选
 *
 * L3 执行编排层 - 任务编排
 * L1 信息边界层 - 入口定义
 */
public class UploadAction extends AnAction {

    private SftpService sftpService;
    private ConfigManager configManager;
    private LanguageManager lm;
    
    private CommandConfig selectedCommandConfig;
    private ExecuteTiming selectedTiming;

    public UploadAction() {
        super();
        this.sftpService = new SftpService();
        this.configManager = ConfigManager.getInstance();
        this.lm = LanguageManager.getInstance();
        Logger.debug("UploadAction", "UploadAction created");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 设置菜单文本（支持国际化）
        e.getPresentation().setText(lm.get("menu.upload"));
        e.getPresentation().setDescription(lm.get("menu.upload.desc"));
        // 菜单始终可用
        e.getPresentation().setEnabled(true);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Logger.debug("UploadAction", "actionPerformed called");
        Project project = e.getProject();
        if (project == null) {
            Logger.debug("UploadAction", "Project is null!");
            return;
        }

        Logger.debug("UploadAction", "Project: " + project.getName());

        // 获取选中的文件/目录
        List<String> selectedPaths = getSelectedPaths(e);
        Logger.debug("UploadAction", "Selected paths count: " + selectedPaths.size());

        if (selectedPaths.isEmpty()) {
            Messages.showInfoMessage(project, lm.get("action.upload.select.files"), lm.get("warning.title"));
            return;
        }

        // 过滤有效的文件（使用 VirtualFile 的 exists 检查）
        List<String> validPaths = new ArrayList<>();
        DataContext dataContext = e.getDataContext();
        VirtualFile[] virtualFiles = LangDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);

        if (virtualFiles != null) {
            Logger.debug("UploadAction", "Virtual files count: " + virtualFiles.length);
            for (VirtualFile vf : virtualFiles) {
                Logger.debug("UploadAction", "  File: " + vf.getPath() + ", exists=" + vf.exists());
                if (vf.exists()) {
                    validPaths.add(vf.getPath());
                }
            }
        }

        Logger.debug("UploadAction", "Valid paths count: " + validPaths.size());

        if (validPaths.isEmpty()) {
            Messages.showInfoMessage(project, lm.get("action.upload.no.valid.files"), lm.get("warning.title"));
            return;
        }

        // 显示上传目标选择对话框
        Logger.debug("UploadAction", "Creating UploadDialog...");
        Window window = WindowManager.getInstance().getFrame(project);
        UploadDialog uploadDialog = new UploadDialog(window, validPaths);
        
        // 设置执行命令组回调处理"执行命令组"按钮
        uploadDialog.setExecuteCommandsCallback(ctx -> {
            if (ctx != null) {
                executeCommandsOnly(project, ctx.server, ctx.commandConfig);
            }
        });
        
        uploadDialog.show();
        Logger.debug("UploadAction", "UploadDialog closed, confirmed=" + uploadDialog.isConfirmed());

        if (!uploadDialog.isConfirmed()) {
            return;
        }

        ServerConfig server = uploadDialog.getSelectedServer();
        PathConfig path = uploadDialog.getSelectedPath();
        selectedCommandConfig = uploadDialog.getSelectedCommandConfig();
        selectedTiming = uploadDialog.getSelectedTiming();

        if (server == null || path == null) {
            return;
        }

        // 获取密码
        String password = configManager.getPassword(server.getId());
        if (password == null) {
            password = promptForPassword(project, server);
            if (password == null || password.isEmpty()) {
                Messages.showInfoMessage(project, lm.get("action.upload.no.password"), lm.get("warning.title"));
                return;
            }
        }

        // 执行上传
        executeUpload(project, validPaths, server, path, password);
    }

    /**
     * 仅执行命令（不执行上传）
     */
    private void executeCommandsOnly(Project project, ServerConfig server, CommandConfig commandConfig) {
        if (commandConfig == null || commandConfig.getEnabledCommands() == null || commandConfig.getEnabledCommands().isEmpty()) {
            JOptionPane.showMessageDialog(null, lm.get("upload.error.noCommand"), lm.get("warning.title"), JOptionPane.WARNING_MESSAGE);
            return;
        }
        
        // 获取密码
        String password = configManager.getPassword(server.getId());
        if (password == null) {
            password = promptForPassword(project, server);
            if (password == null || password.isEmpty()) {
                Messages.showInfoMessage(project, lm.get("action.upload.no.password"), lm.get("warning.title"));
                return;
            }
        }
        
        // 创建进度对话框
        ProgressDialog progressDialog = new ProgressDialog(null);
        progressDialog.setTitle(lm.get("progress.title.execute"));
        progressDialog.setVisible(true);

        // 在后台线程执行命令
        final ServerConfig finalServer = server;
        final String finalPassword = password;
        new Thread(() -> {
            try {
                ApplicationManager.getApplication().invokeLater(() ->
                        progressDialog.appendLog(lm.get("progress.connecting", finalServer.getHost())));
                
                // 创建 SSH 连接
                SshConnection connection = SshConnection.fromServerConfig(finalServer, finalPassword);
                
                // 创建服务实例
                SshCommandService sshService = new SshCommandService();
                BlacklistValidator blacklistValidator = new BlacklistValidator();
                KeywordMatcher keywordMatcher = new KeywordMatcher();
                AICommandChecker aiCommandChecker = new AICommandChecker();
                AIResultChecker aiResultChecker = new AIResultChecker();
                DailyLogService logService = new DailyLogService();
                
                // 创建命令编排器
                CommandOrchestrator orchestrator = new CommandOrchestrator(
                    sshService,
                    blacklistValidator,
                    keywordMatcher,
                    aiCommandChecker,
                    aiResultChecker,
                    logService
                );
                
                // 执行命令序列
                orchestrator.executeQueue(commandConfig, connection, new ExecutionListener() {
                    @Override
                    public void onStart(int totalCommands) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                progressDialog.appendLog(lm.get("execution.start.total", totalCommands)));
                    }
                    
                    @Override
                    public void onCommandStart(int index, int total, String command) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                progressDialog.appendLog("\n" + lm.get("execution.start", index, command)));
                    }
                    
                    @Override
                    public void onCommandSuccess(int index, int total, String command, CommandResult result) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            progressDialog.appendLog("\n" + lm.get("result.success") + ": " + command);
                            if (result.getStdout() != null && !result.getStdout().isEmpty()) {
                                progressDialog.appendLog("\n" + lm.get("result.output") + ":");
                                progressDialog.appendLog("\n" + result.getStdout());
                            }
                        });
                    }
                    
                    @Override
                    public void onCommandFailed(int index, int total, String command, CommandResult result) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            progressDialog.appendLog("\n" + lm.get("result.failed") + ": " + command);
                            if (result.getStderr() != null && !result.getStderr().isEmpty()) {
                                progressDialog.appendLog("\n" + lm.get("result.error") + ":");
                                progressDialog.appendLog("\n" + result.getStderr());
                            }

                            // 命令失败，询问用户是否继续
                            boolean userChoice = progressDialog.askCommandFailedContinue(command, result.getStderr());
                            if (!userChoice) {
                                progressDialog.appendLog("\n" + lm.get("execution.user.stopped"));
                            }
                        });
                    }
                    
                    @Override
                    public void onBlocked(int index, int total, String reason) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                progressDialog.appendLog("\n" + lm.get("result.blocked") + ": " + reason));
                    }
                    
                    @Override
                    public void onComplete(ExecutionSummary summary) {
                        // 记住本次成功的服务器和命令组选择（仅执行命令，无路径和执行时机）
                        saveLastSelection(finalServer, null, commandConfig, null);
                        
                        ApplicationManager.getApplication().invokeLater(() -> {
                            progressDialog.appendLog("\n" + lm.get("summary.title"));
                            progressDialog.appendLog("\n" + lm.get("summary.total", summary.getTotal()));
                            progressDialog.appendLog(" " + lm.get("summary.success", summary.getSuccessCount()));
                            progressDialog.appendLog(" " + lm.get("summary.failed", summary.getFailedCount()));
                            progressDialog.appendLog(" " + lm.get("summary.blocked", summary.getBlockedCount()));
                        });
                    }
                    
                    @Override
                    public void onError(String errorMessage) {
                        ApplicationManager.getApplication().invokeLater(() ->
                                progressDialog.appendLog("\n" + lm.get("execution.error", errorMessage)));
                    }
                });
                
            } catch (Exception ex) {
                final String errorMsg = ex.getMessage();
                ApplicationManager.getApplication().invokeLater(() ->
                        progressDialog.appendLog("\n" + lm.get("execution.error", errorMsg)));
            }
        }, "ExecuteCommandsThread").start();
    }

    /**
     * 获取选中的文件/目录路径
     */
    private List<String> getSelectedPaths(AnActionEvent e) {
        List<String> paths = new ArrayList<>();

        // 获取选中文件
        DataContext dataContext = e.getDataContext();
        VirtualFile[] virtualFiles = LangDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);

        if (virtualFiles != null) {
            for (VirtualFile vf : virtualFiles) {
                paths.add(vf.getPath());
            }
        }

        return paths;
    }

    /**
     * 提示用户输入密码
     */
    private String promptForPassword(Project project, ServerConfig server) {
        JPasswordField passwordField = new JPasswordField();
        passwordField.setEchoChar('*');

        String message = lm.get("action.upload.enter.password", server.getName());
        int result = JOptionPane.showConfirmDialog(
                null,
                passwordField,
                lm.get("action.upload.password.title"),
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );

        if (result == JOptionPane.OK_OPTION) {
            return new String(passwordField.getPassword());
        }
        return null;
    }

    /**
     * 执行上传
     */
    private void executeUpload(Project project, List<String> paths,
                               ServerConfig server, PathConfig pathConfig,
                               String password) {
        // 创建进度对话框
        ProgressDialog progressDialog = new ProgressDialog(null);
        progressDialog.setVisible(true);

        // 在后台线程执行上传
        new Thread(() -> {
            try {
                // 收集文件信息
                List<File> files = new ArrayList<>();
                long totalBytes = 0;
                for (String path : paths) {
                    collectFiles(new File(path), files);
                    totalBytes += calculateSize(new File(path));
                }

                final int totalFiles = files.size();
                final long totalBytesFinal = totalBytes;

                // 连接服务器
                ApplicationManager.getApplication().invokeLater(() ->
                        progressDialog.appendLog(lm.get("progress.connecting", server.getHost())));

                sftpService.connect(server, password);

                ApplicationManager.getApplication().invokeLater(() ->
                        progressDialog.appendLog(lm.get("progress.connected")));

                // 开始上传
                progressDialog.onUploadStarted(totalFiles, totalBytesFinal);

                AtomicInteger uploadedFiles = new AtomicInteger(0);
                AtomicLong uploadedBytes = new AtomicLong(0);

                for (String path : paths) {
                    File file = new File(path);

                    if (file.isDirectory()) {
                        sftpService.uploadDirectory(file, pathConfig.getRemotePath(),
                                new SftpService.UploadProgressCallback() {
                                    @Override
                                    public void onProgress(String fileName, int percent,
                                                           long uploaded, long total) {
                                        progressDialog.onProgress(fileName, percent,
                                                uploadedBytes.get() + uploaded, totalBytesFinal);
                                    }

                                    @Override
                                    public void onFileCompleted(String fileName,
                                                                long size, boolean success,
                                                                String errorMessage) {
                                        progressDialog.onFileCompleted(fileName, size,
                                                success, errorMessage);
                                        if (success) {
                                            uploadedFiles.incrementAndGet();
                                            uploadedBytes.addAndGet(size);
                                        }
                                    }
                                });
                    } else {
                        // 计算本地文件 MD5
                        String localMd5=null;
                        try {
                            localMd5 = Md5Checksum.calculate(file);
                            final String finalLocalMd5V = localMd5;
                            ApplicationManager.getApplication().invokeLater(() ->
                                    progressDialog.appendLog(lm.get("upload.md5.calculating", file.getName(), finalLocalMd5V)));
                        } catch (Exception e) {
                            Logger.debug("UploadAction", "Failed to calculate local MD5: " + e.getMessage());
                        }

                        final String finalLocalMd5 = localMd5;
                        final String remoteFilePath = pathConfig.getRemotePath() + "/" + file.getName();

                        sftpService.uploadFile(file, pathConfig.getRemotePath(),
                                new SftpService.UploadProgressCallback() {
                                    @Override
                                    public void onProgress(String fileName, int percent,
                                                           long uploaded, long total) {
                                        progressDialog.onProgress(fileName, percent,
                                                uploadedBytes.get() + uploaded, totalBytesFinal);
                                    }

                                    @Override
                                    public void onFileCompleted(String fileName,
                                                                long size, boolean success,
                                                                String errorMessage) {
                                        boolean uploadSuccess = success;
                                        String uploadError = errorMessage;

                                        if (success && finalLocalMd5 != null) {
                                            // MD5 校验
                                            SftpService.Md5VerifyResult verifyResult = sftpService.verifyRemoteMd5(remoteFilePath, finalLocalMd5);
                                            if (verifyResult.hasError()) {
                                                ApplicationManager.getApplication().invokeLater(() ->
                                                        progressDialog.appendLog("\n" + lm.get("upload.md5.verify.error", fileName, verifyResult.getErrorMessage())));
                                                // MD5校验失败，询问用户
                                                final String errMsg = verifyResult.getErrorMessage();
                                                boolean userChoice = progressDialog.askUploadFailedContinue(fileName, lm.get("upload.md5.verify.error", fileName, errMsg));
                                                if (!userChoice) {
                                                    uploadSuccess = false;
                                                    uploadError = lm.get("upload.md5.verify.error", fileName, errMsg);
                                                }
                                            } else if (!verifyResult.isMatched()) {
                                                ApplicationManager.getApplication().invokeLater(() ->
                                                        progressDialog.appendLog("\n" + lm.get("upload.md5.verify.failed", fileName, verifyResult.getRemoteMd5())));
                                                // MD5不匹配，询问用户
                                                final String localMd5Str = finalLocalMd5;
                                                final String remoteMd5Str = verifyResult.getRemoteMd5();
                                                String mismatchMsg = String.format("MD5不匹配！本地=%s，远程=%s", localMd5Str, remoteMd5Str);
                                                boolean userChoice = progressDialog.askUploadFailedContinue(fileName, mismatchMsg);
                                                if (!userChoice) {
                                                    uploadSuccess = false;
                                                    uploadError = mismatchMsg;
                                                }
                                            } else {
                                                ApplicationManager.getApplication().invokeLater(() ->
                                                        progressDialog.appendLog("\n" + lm.get("upload.md5.verify.success", fileName, verifyResult.getRemoteMd5())));
                                            }
                                        }

                                        progressDialog.onFileCompleted(fileName, size,
                                                uploadSuccess, uploadError);
                                        if (uploadSuccess) {
                                            uploadedFiles.incrementAndGet();
                                            uploadedBytes.addAndGet(size);
                                        }
                                    }
                                });
                    }
                }

                // 完成
                progressDialog.onUploadCompleted(totalFiles, uploadedFiles.get(),
                        totalBytesFinal, uploadedBytes.get());

                ApplicationManager.getApplication().invokeLater(() ->
                        progressDialog.appendLog("\n" + lm.get("progress.complete")));

                // 记住本次成功的服务器、路径、命令组和执行时机选择
                saveLastSelection(server, pathConfig, selectedCommandConfig, selectedTiming);

                // 检查是否需要执行命令
                if (selectedTiming == ExecuteTiming.AUTO
                    && selectedCommandConfig != null
                    && !selectedCommandConfig.getEnabledCommands().isEmpty()) {

                    // 汇总给命令执行方法
                    final Project finalProject = project;
                    final String finalPassword = password;

                    // 自动执行
                    ApplicationManager.getApplication().invokeLater(() ->
                            progressDialog.appendLog("\n" + lm.get("progress.executing.commands")));
                    doExecuteCommands(selectedCommandConfig, server, finalPassword, progressDialog, null);
                } else if (selectedTiming == ExecuteTiming.MANUAL) {
                    // 手动执行 - 只提示上传完成，不关闭窗口
                    ApplicationManager.getApplication().invokeLater(() -> {
                        progressDialog.appendLog("\n" + lm.get("progress.manual.upload.complete"));
                        JOptionPane.showMessageDialog(
                            progressDialog,
                            lm.get("progress.upload.complete.message"),
                            lm.get("progress.upload.complete.title"),
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    });
                } else {
                    // 自动执行但没有命令组可执行，或 NONE 模式，提示上传完成
                    ApplicationManager.getApplication().invokeLater(() -> {
                        progressDialog.appendLog("\n" + lm.get("progress.manual.upload.complete"));
                        JOptionPane.showMessageDialog(
                            progressDialog,
                            lm.get("progress.upload.complete.message"),
                            lm.get("progress.upload.complete.title"),
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    });
                }

            } catch (SftpException ex) {
                final String errorMsg = ex.getMessage();
                ApplicationManager.getApplication().invokeLater(() -> {
                    progressDialog.appendLog("\n" + lm.get("progress.failed") + " " + errorMsg);
                    Messages.showErrorDialog(project, errorMsg, lm.get("error.title"));
                });
            } catch (Exception ex) {
                final String errorMsg = lm.get("msg.error.add") + " " + ex.getMessage();
                ApplicationManager.getApplication().invokeLater(() -> {
                    progressDialog.appendLog("\n" + errorMsg);
                    Messages.showErrorDialog(project, errorMsg, lm.get("error.title"));
                });
            } finally {
                sftpService.disconnect();
            }
        }, "UploadThread").start();
    }

    /**
     * 递归收集文件
     */
    private void collectFiles(File dir, List<File> files) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    collectFiles(child, files);
                }
            }
        } else {
            files.add(dir);
        }
    }

    /**
     * 计算目录大小
     */
    private long calculateSize(File file) {
        if (file.isFile()) {
            return file.length();
        }

        long size = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                size += calculateSize(child);
            }
        }
        return size;
    }

    /**
     * 记住上次成功的服务器、路径、命令组和执行时机选择，以便下次打开对话框时自动选中
     */
    private void saveLastSelection(ServerConfig server, PathConfig path, CommandConfig commandConfig, ExecuteTiming timing) {
        try {
            String serverId = server != null ? server.getId() : null;
            String pathId = path != null ? path.getId() : null;
            String commandConfigId = commandConfig != null ? commandConfig.getId() : null;
            String timingStr = timing != null ? timing.name() : null;
            StoreManager.getInstance().getUnifiedConfigStore()
                    .saveLastSuccessfulSelection(serverId, pathId, commandConfigId, timingStr);
        } catch (Exception e) {
            Logger.debug("UploadAction", "Failed to save last selection: " + e.getMessage());
        }
    }

    /**
     * 执行命令方法
     */
    private void doExecuteCommands(CommandConfig commandConfig, ServerConfig server, 
                                   String password, ProgressDialog progressDialog,
                                   ExecutionProgressDialog executionDialog) {
        new Thread(() -> {
            try {
                // 创建 SSH 连接
                SshConnection connection = SshConnection.fromServerConfig(server, password);
                
                // 创建服务实例
                SshCommandService sshService = new SshCommandService();
                BlacklistValidator blacklistValidator = new BlacklistValidator();
                KeywordMatcher keywordMatcher = new KeywordMatcher();
                AICommandChecker aiCommandChecker = new AICommandChecker();
                AIResultChecker aiResultChecker = new AIResultChecker();
                DailyLogService logService = new DailyLogService();
                
                // 创建命令编排器
                CommandOrchestrator orchestrator = new CommandOrchestrator(
                    sshService,
                    blacklistValidator,
                    keywordMatcher,
                    aiCommandChecker,
                    aiResultChecker,
                    logService
                ) {
                    @Override
                    protected boolean askUserContinue(String message) {
                        if (executionDialog != null) {
                            return executionDialog.askUserContinue(message);
                        }
                        return true;
                    }
                    
                    @Override
                    protected boolean askUserRiskContinue(String command, String riskInfo) {
                        if (executionDialog != null) {
                            return executionDialog.askUserRiskContinue(command, riskInfo);
                        }
                        return false;
                    }
                    
                    @Override
                    protected boolean askUserWarningContinue(String command, String warningInfo) {
                        if (executionDialog != null) {
                            return executionDialog.askUserWarningContinue(command, warningInfo);
                        }
                        return true;
                    }
                    
                    @Override
                    protected boolean askUserCautionContinue(String command, String cautionInfo) {
                        if (executionDialog != null) {
                            return executionDialog.askUserCautionContinue(command, cautionInfo);
                        }
                        return false;
                    }
                };
                
                // 执行命令序列
                if (executionDialog != null) {
                    orchestrator.executeQueue(commandConfig, connection, executionDialog);
                } else {
                    // 使用简单的回调方式执行
                    orchestrator.executeQueue(commandConfig, connection, new ExecutionListener() {
                        @Override
                        public void onStart(int totalCommands) {
                        }
                        
                        @Override
                        public void onCommandStart(int index, int total, String command) {
                            ApplicationManager.getApplication().invokeLater(() ->
                                    progressDialog.appendLog("\n" + lm.get("execution.start", index, command)));
                        }
                        
                        @Override
                        public void onCommandSuccess(int index, int total, String command, CommandResult result) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                progressDialog.appendLog("\n" + lm.get("result.success") + ": " + command);
                                if (result.getStdout() != null && !result.getStdout().isEmpty()) {
                                    progressDialog.appendLog("\n" + lm.get("result.output") + ":");
                                    progressDialog.appendLog("\n" + result.getStdout());
                                }
                            });
                        }
                        
                        @Override
                        public void onCommandFailed(int index, int total, String command, CommandResult result) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                progressDialog.appendLog("\n" + lm.get("result.failed") + ": " + command);
                                String errorInfo = "";
                                if (result.getStderr() != null && !result.getStderr().isEmpty()) {
                                    progressDialog.appendLog("\n" + lm.get("result.error") + ":");
                                    progressDialog.appendLog("\n" + result.getStderr());
                                    errorInfo = result.getStderr();
                                } else if (result.getStdout() != null && !result.getStdout().isEmpty()) {
                                    errorInfo = result.getStdout();
                                }

                                // 命令失败，询问用户是否继续
                                boolean userChoice = progressDialog.askCommandFailedContinue(command, errorInfo);
                                if (!userChoice) {
                                    progressDialog.appendLog("\n用户选择停止执行");
                                }
                            });
                        }
                        
                        @Override
                        public void onBlocked(int index, int total, String reason) {
                            ApplicationManager.getApplication().invokeLater(() ->
                                    progressDialog.appendLog("\n" + lm.get("result.blocked") + ": " + reason));
                        }
                        
                        @Override
                        public void onComplete(ExecutionSummary summary) {
                            ApplicationManager.getApplication().invokeLater(() -> {
                                progressDialog.appendLog("\n" + lm.get("summary.title"));
                                progressDialog.appendLog("\n" + lm.get("summary.total", summary.getTotal()));
                                progressDialog.appendLog(" " + lm.get("summary.success", summary.getSuccessCount()));
                                progressDialog.appendLog(" " + lm.get("summary.failed", summary.getFailedCount()));
                                progressDialog.appendLog(" " + lm.get("summary.blocked", summary.getBlockedCount()));
                            });
                        }
                        
                        @Override
                        public void onError(String errorMessage) {
                            ApplicationManager.getApplication().invokeLater(() ->
                                    progressDialog.appendLog("\n" + lm.get("execution.error", errorMessage)));
                        }
                    });
                }
                
            } catch (Exception ex) {
                final String errorMsg = ex.getMessage();
                ApplicationManager.getApplication().invokeLater(() ->
                        progressDialog.appendLog("\n" + lm.get("execution.error", errorMsg)));
            }
        }).start();
    }

}
