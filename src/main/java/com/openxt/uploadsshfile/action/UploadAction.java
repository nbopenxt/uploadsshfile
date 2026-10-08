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
import com.openxt.uploadsshfile.startup.IdeBootstrap;
import com.openxt.uploadsshfile.store.StoreManager;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.sftp.SftpService;
import com.openxt.uploadsshfile.sftp.SftpValidator;
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
        // D-26：action 实例化可发生在 projectOpened/appStarted 之前（会话恢复后首次右键菜单，
        // 2026-10-04 实测红气球），构造体触达存储单例前先幂等注入路径
        // D-31：顺带 bat 自愈（热载半生态下点一次菜单即补齐，外部直敲 CLI 不再踩空）
        IdeBootstrap.ensureReady();
        this.sftpService = new SftpService();
        this.configManager = ConfigManager.getInstance();
        this.lm = LanguageManager.getInstance();
        Logger.debug("UploadAction", "UploadAction created");
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 设置菜单文本（支持国际化）
        e.getPresentation().setText(lm.get("menu.upload.single"));
        e.getPresentation().setDescription(lm.get("menu.upload.single.desc"));
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

        // D-37：开窗清单来源——右键新选（存在时覆盖默认）；空选回放关窗快照清单；
        // 空选且无已存清单＝维持旧拦截提示。
        List<String> initialPaths;
        if (selectedPaths.isEmpty()) {
            com.openxt.uploadsshfile.model.SingleUploadTask snap =
                    StoreManager.getInstance().getUnifiedConfigStore().getSingleUploadTask();
            if (snap == null || snap.getFilePaths().isEmpty()) {
                Messages.showInfoMessage(project, lm.get("action.upload.select.files"), lm.get("warning.title"));
                return;
            }
            Logger.debug("UploadAction", "Empty selection with saved snapshot: replay " + snap.getFilePaths().size() + " file(s)");
            initialPaths = new ArrayList<>(snap.getFilePaths());
        } else {
            // 过滤有效的文件（使用 VirtualFile 的 exists 检查）
            List<String> validPaths = new ArrayList<>();
            VirtualFile[] virtualFiles = e.getData(LangDataKeys.VIRTUAL_FILE_ARRAY);

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
            initialPaths = validPaths;
        }

        // 显示上传目标选择对话框
        Logger.debug("UploadAction", "Creating UploadDialog...");
        Window window = WindowManager.getInstance().getFrame(project);
        UploadDialog uploadDialog = new UploadDialog(window, initialPaths);
        
        // 设置执行命令组回调处理"执行命令组"按钮
        uploadDialog.setExecuteCommandsCallback(ctx -> {
            if (ctx != null) {
                executeCommandsOnly(project, ctx.server, ctx.commandConfig);
            }
        });
        
        if (!uploadDialog.showAndGet()) {
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

        // 1.0.8/M4（R50/页面4）：GUI 与 CLI 共用同一把 serverId 分片锁——执行前抢锁；
        // 被占弹"等待/取消"（等待后台轮询不冻结 EDT）；取消＝直接返回、不改任何配置（流程 C）。
        // 句柄移交上传线程，其 finally 单一出口释放（RISK-15）
        com.openxt.uploadsshfile.sync.UploadLockManager.Handle lockHandle =
                com.openxt.uploadsshfile.ui.LockConflictDialog.acquireWithUiWait(
                        window, server.getId(), uploadDialog.getTaskIdValue(), lm);
        if (lockHandle == null) {
            return;
        }

        // 执行上传（1.0.8/FR-06：携带对话框任务 ID 值，上传全部成功且校验通过后落盘——U-01；
        // D-37：清单以对话框实态为单点数据源——右键新选或空选回放快照）
        executeUpload(project, uploadDialog.getUploadPaths(), server, path, password, uploadDialog.getTaskIdValue(), lockHandle);
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
        VirtualFile[] virtualFiles = e.getData(LangDataKeys.VIRTUAL_FILE_ARRAY);

        if (virtualFiles != null) {
            for (VirtualFile vf : virtualFiles) {
                paths.add(vf.getPath());
            }
        }

        return paths;
    }

    /**
     * 提示用户输入密码，输入后先做连接测试，测试成功则自动保存密码
     */
    private String promptForPassword(Project project, ServerConfig server) {
        while (true) {
            JPasswordField passwordField = new JPasswordField(20);
            passwordField.setEchoChar('*');

            JPanel panel = new JPanel(new BorderLayout(5, 5));
            panel.add(new JLabel(lm.get("action.upload.enter.password", server.getName())), BorderLayout.NORTH);
            panel.add(passwordField, BorderLayout.CENTER);

            int result = JOptionPane.showConfirmDialog(
                    null,
                    panel,
                    lm.get("action.upload.password.title"),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.QUESTION_MESSAGE
            );

            if (result != JOptionPane.OK_OPTION) {
                return null;
            }

            String password = new String(passwordField.getPassword());
            if (password.isEmpty()) {
                continue;
            }

            // 测试连接
            boolean testSuccess = SftpValidator.testConnection(
                    server.getHost(), server.getPort(), server.getUsername(), password);

            if (testSuccess) {
                // 保存密码到安全存储，下次无需再次输入
                configManager.savePassword(server.getId(), password);
                return password;
            } else {
                JOptionPane.showMessageDialog(
                        null,
                        lm.get("msg.test.connection.failed"),
                        lm.get("error.title"),
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    /**
     * 执行上传
     */
    private void executeUpload(Project project, List<String> paths,
                               ServerConfig server, PathConfig pathConfig,
                               String password, String pendingTaskId,
                               com.openxt.uploadsshfile.sync.UploadLockManager.Handle lockHandle) {
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

                // 1.0.8/FR-16（P-08/清单④配套）：勾选目录属性→上传前逐级创建 remotePath
                if (pathConfig.isAutoCreateRemoteDir()) {
                    sftpService.mkdirsRemote(pathConfig.getRemotePath());
                }

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
                                                String mismatchMsg = lm.get("sftp.error.md5Mismatch", localMd5Str, remoteMd5Str);
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

                // 1.0.8/FR-03（U-01）：本次选中文件全部上传且校验通过 → 落盘单例任务 ID；
                // 任一文件失败/校验不过不落盘；命令组执行失败不影响（此处尚未执行命令）
                if (uploadedFiles.get() == totalFiles) {
                    saveSingleTaskIdOnSuccess(pendingTaskId);
                }

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
                lockHandle.close(); // M4/RISK-15：锁释放单一出口（try/finally 保证）
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
     * 1.0.8/FR-03：上传全部成功且校验通过时落盘单例任务 ID（U-01，与 lastSuccessful* 并存——清单⑨）。
     * 规则：对话框值优先；空则沿用已落盘值；都无→生成雪花并查重（#13，撞则重生成）；
     * 候选与命名空间冲突→保留现值仅记日志（执行期不打断上传流，UI 冲突提示由面板 persist 路径负责）。
     */
    private void saveSingleTaskIdOnSuccess(String pendingTaskId) {
        try {
            com.openxt.uploadsshfile.store.UnifiedConfigStore store =
                    StoreManager.getInstance().getUnifiedConfigStore();
            String cur = store.getSingleUploadTaskId();
            String candidate = (pendingTaskId != null && !pendingTaskId.trim().isEmpty())
                    ? pendingTaskId.trim() : cur;
            if (candidate == null) {
                for (int i = 0; i < 5; i++) {
                    candidate = com.openxt.uploadsshfile.model.TaskIdGenerator.nextId();
                    if (!store.isTaskIdTaken(candidate, null)) {
                        break;
                    }
                    if (i == 4) {
                        Logger.debug("UploadAction", "task id generation kept colliding, skip");
                        return;
                    }
                }
            }
            if (candidate.equals(cur)) {
                return;
            }
            if (store.isTaskIdTaken(candidate, null)) {
                Logger.debug("UploadAction", "task id conflict with namespace, keep existing: " + cur);
                return;
            }
            store.setSingleUploadTaskId(candidate);
        } catch (Exception e) {
            Logger.debug("UploadAction", "Failed to save single task id: " + e.getMessage());
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
                    protected com.openxt.uploadsshfile.ssh.TimeoutPrompter timeoutPrompter() {
                        // D-17（清单⑩）：上传后自动执行命令路径启用递进超时询问；
                        // 桥接到既有 ExecutionProgressDialog.promptContinueWait（此前无人调用）
                        if (executionDialog == null) {
                            return null; // 无进度窗场景保持老行为（180s 硬停不询问）
                        }
                        return this::promptContinueWait;
                    }

                    @Override
                    protected boolean promptContinueWait(String command, long elapsedMs) {
                        return executionDialog != null
                                && executionDialog.promptContinueWait(command, elapsedMs);
                    }

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
