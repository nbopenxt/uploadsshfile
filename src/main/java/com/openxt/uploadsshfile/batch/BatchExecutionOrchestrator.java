package com.openxt.uploadsshfile.batch;

import com.openxt.uploadsshfile.config.ConfigManager;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.model.ExecutionSummary;
import com.openxt.uploadsshfile.model.SshConnection;
import com.openxt.uploadsshfile.orchestration.CommandOrchestrator;
import com.openxt.uploadsshfile.orchestration.ExecutionListener;
import com.openxt.uploadsshfile.persistence.SecureStorage;
import com.openxt.uploadsshfile.sftp.SftpService;
import com.openxt.uploadsshfile.store.UnifiedConfigStore;
import com.openxt.uploadsshfile.util.Logger;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 批处理任务执行编排器。
 * 使用单线程 ExecutorService 顺序执行子任务，
 * 复用 SftpService + CommandOrchestrator。
 */
public class BatchExecutionOrchestrator {

    private final ConfigManager configManager;
    private final UnifiedConfigStore configStore;
    private final SecureStorage secureStorage;
    private BatchExecutionListener listener;
    private ExecutorService executor;
    private final AtomicBoolean cancelling = new AtomicBoolean(false);

    public BatchExecutionOrchestrator() {
        this.configManager = ConfigManager.getInstance();
        this.configStore = UnifiedConfigStore.getInstance();
        this.secureStorage = SecureStorage.getInstance();
    }

    public void setListener(BatchExecutionListener listener) {
        this.listener = listener;
    }

    /**
     * 异步执行批处理任务，返回全部子任务结果。
     */
    public void execute(BatchTask task) {
        if (task == null || task.getSubTasks() == null || task.getSubTasks().isEmpty()) {
            return;
        }

        cancelling.set(false);
        List<BatchSubTask> subTasks = new ArrayList<>(task.getSubTasks());
        // 按 order 字段排序，确保按预期顺序执行
        subTasks.sort(Comparator.comparingInt(BatchSubTask::getOrder));
        List<BatchSubTaskResult> results = new ArrayList<>();

        executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            if (listener != null) {
                listener.onBatchStart(task, subTasks.size());
            }

            for (int i = 0; i < subTasks.size(); i++) {
                if (cancelling.get()) break;

                BatchSubTask subTask = subTasks.get(i);
                if (listener != null) {
                    listener.onSubTaskStart(subTask, i + 1, subTasks.size());
                }

                BatchSubTaskResult result = executeSubTask(subTask, task.getName());
                results.add(result);

                if (listener != null) {
                    listener.onSubTaskCompleted(result);
                }
            }

            if (cancelling.get() && listener != null) {
                listener.onBatchCancelled();
            } else if (listener != null) {
                listener.onBatchCompleted(results);
            }
        });
    }

    private BatchSubTaskResult executeSubTask(BatchSubTask subTask, String taskName) {
        long startTime = System.currentTimeMillis();
        Logger.debug("BatchExecutionOrchestrator", "Starting subTask: " + subTask.getId() + ", order: " + subTask.getOrder());
        BatchSubTaskResult result = new BatchSubTaskResult();
        result.setSubTaskId(subTask.getId());
        LanguageManager lm = LanguageManager.getInstance();

        SftpService sftpService = new SftpService();
        ServerConfig server = null;
        PathConfig path = null;

        try {
            // 1. Load config
            Logger.debug("BatchExecutionOrchestrator", "Loading server config for id: " + subTask.getServerId());
            server = configStore.getServer(subTask.getServerId());
            if (server == null) {
                throw new RuntimeException(lm.get("batch.error.serverNotFound", subTask.getServerId()));
            }
            Logger.debug("BatchExecutionOrchestrator", "Server loaded: " + server.getName());

            List<PathConfig> paths = configStore.getPathsByServer(subTask.getServerId());
            path = paths.stream()
                    .filter(p -> p.getId().equals(subTask.getPathId()))
                    .findFirst()
                    .orElse(null);
            if (path == null) {
                throw new RuntimeException(lm.get("batch.error.pathNotFound", subTask.getPathId()));
            }
            Logger.debug("BatchExecutionOrchestrator", "Path loaded: " + path.getRemotePath());

            String password = secureStorage.retrieve(server.getId());
            if (password == null) {
                throw new RuntimeException(lm.get("batch.error.passwordNotSet", server.getName()));
            }
            Logger.debug("BatchExecutionOrchestrator", "Password retrieved successfully");

            result.setTaskDescription(taskName + " -> " + server.getName() + ":" + path.getRemotePath());

            // 2. File validation
            if (subTask.getFilePaths() == null || subTask.getFilePaths().isEmpty()) {
                throw new RuntimeException(lm.get("batch.error.noFilesSelected"));
            }
            Logger.debug("BatchExecutionOrchestrator", "Validating " + subTask.getFilePaths().size() + " files/directories");

            // 3. SFTP connection + upload
            if (listener != null) {
                listener.onLog(lm.get("progress.connecting", server.getHost()));
            }
            Logger.debug("BatchExecutionOrchestrator", "Connecting to SFTP: " + server.getHost() + ":" + server.getPort());
            sftpService.connect(server, password);
            Logger.debug("BatchExecutionOrchestrator", "SFTP connected, isWindows: " + sftpService.isWindows());
            if (listener != null) {
                listener.onLog(lm.get("progress.connected"));
            }

            List<String> filePaths = subTask.getFilePaths();

            for (int i = 0; i < filePaths.size(); i++) {
                String filePath = filePaths.get(i);
                File file = new File(filePath);
                Logger.debug("BatchExecutionOrchestrator", "Processing file/dir: " + filePath + ", exists: " + file.exists());
                if (!file.exists()) {
                    throw new RuntimeException(lm.get("batch.error.fileNotFound", filePath));
                }

                if (listener != null) {
                    listener.onFileStart(file.getName(), i + 1, filePaths.size());
                }

                // Accumulated bytes from completed inner files (for directory uploads)
                final long[] innerCompletedBytes = {0};

                SftpService.UploadProgressCallback progressCallback = new SftpService.UploadProgressCallback() {
                    @Override
                    public void onProgress(String fileName, int percent, long uploaded, long total) {
                        if (listener != null) {
                            // For directories: accumulate inner file progress and recalculate percent
                            long accumulatedUploaded = innerCompletedBytes[0] + uploaded;
                            int adjustedPercent = (total > 0) ? (int) (accumulatedUploaded * 100.0 / total) : percent;
                            listener.onUploadProgress(fileName, Math.min(adjustedPercent, 100), accumulatedUploaded, total);
                        }
                    }
                    @Override
                    public void onFileCompleted(String fileName, long size, boolean success, String errorMessage) {
                        // Accumulate completed inner file bytes for directory progress
                        if (success) {
                            innerCompletedBytes[0] += size;
                        }
                    }
                };

                try {
                    if (file.isDirectory()) {
                        Logger.debug("BatchExecutionOrchestrator", "Uploading directory: " + file.getAbsolutePath());
                        sftpService.uploadDirectory(file, path.getRemotePath(), progressCallback);
                    } else {
                        Logger.debug("BatchExecutionOrchestrator", "Uploading file: " + file.getAbsolutePath() + " to " + path.getRemotePath());
                        sftpService.uploadFile(file, path.getRemotePath(), progressCallback);
                    }
                    // Notify listener of file/dir upload completion for progress tracking
                    if (listener != null) {
                        long completedSize = file.isDirectory() ? calculateSize(file) : file.length();
                        listener.onFileCompleted(file.getName(), completedSize, true, null);
                    }
                } catch (Exception e) {
                    Logger.error("BatchExecutionOrchestrator", "Upload failed for " + filePath + ": " + e.getMessage());
                    if (listener != null) {
                        listener.onFileCompleted(file.getName(), 0, false, e.getMessage());
                    }
                    throw e;
                }
            }

            // 4. Command group execution (optional)
            if (subTask.getCommandConfigId() != null) {
                Logger.debug("BatchExecutionOrchestrator", "Executing command config: " + subTask.getCommandConfigId());
                CommandConfig cmdConfig = configStore.getCommandConfig(subTask.getCommandConfigId())
                        .orElse(null);
                if (cmdConfig != null) {
                    if (listener != null) {
                        listener.onLog("\n" + lm.get("progress.executing.commands"));
                    }
                    SshConnection connection = SshConnection.fromServerConfig(server, password);
                    CommandOrchestrator orchestrator = new CommandOrchestrator(
                            new com.openxt.uploadsshfile.ssh.SshCommandService(),
                            new com.openxt.uploadsshfile.validation.BlacklistValidator(),
                            new com.openxt.uploadsshfile.validation.KeywordMatcher());
                    orchestrator.executeQueue(cmdConfig, connection, new ExecutionListener() {
                        @Override public void onStart(int totalCommands) {
                            if (listener != null) {
                                listener.onLog(lm.get("execution.start.total", totalCommands));
                            }
                        }
                        @Override public void onCommandStart(int index, int total, String command) {
                            if (listener != null) {
                                listener.onLog("\n" + lm.get("execution.start", index, command));
                            }
                        }
                        @Override public void onCommandSuccess(int index, int total, String command, CommandResult result) {
                            if (listener != null) {
                                listener.onLog("\n" + lm.get("result.success") + ": " + command);
                                if (result.getStdout() != null && !result.getStdout().isEmpty()) {
                                    listener.onLog("\n" + lm.get("result.output") + ":");
                                    listener.onLog("\n" + result.getStdout());
                                }
                            }
                        }
                        @Override public void onCommandFailed(int index, int total, String command, CommandResult result) {
                            if (listener != null) {
                                listener.onLog("\n" + lm.get("result.failed") + ": " + command);
                                if (result.getStderr() != null && !result.getStderr().isEmpty()) {
                                    listener.onLog("\n" + lm.get("result.error") + ":");
                                    listener.onLog("\n" + result.getStderr());
                                }
                            }
                        }
                        @Override public void onBlocked(int index, int total, String reason) {
                            if (listener != null) {
                                listener.onLog("\n" + lm.get("result.blocked") + ": " + reason);
                            }
                        }
                        @Override public void onComplete(ExecutionSummary summary) {
                            if (listener != null) {
                                listener.onLog("\n" + lm.get("summary.title"));
                                listener.onLog("\n" + lm.get("summary.total", summary.getTotal()));
                                listener.onLog(" " + lm.get("summary.success", summary.getSuccessCount()));
                                listener.onLog(" " + lm.get("summary.failed", summary.getFailedCount()));
                                listener.onLog(" " + lm.get("summary.blocked", summary.getBlockedCount()));
                            }
                        }
                        @Override public void onError(String errorMessage) {
                            if (listener != null) {
                                listener.onLog("\n" + lm.get("execution.error", errorMessage));
                            }
                        }
                    });
                }
            }

            result.setStatus(BatchSubTaskResult.Status.SUCCESS);
            Logger.debug("BatchExecutionOrchestrator", "SubTask completed successfully: " + subTask.getId());

        } catch (Exception e) {
            result.setStatus(BatchSubTaskResult.Status.FAILED);
            result.setErrorMessage(e.getMessage());
            Logger.error("BatchExecutionOrchestrator", "SubTask failed: " + subTask.getId() + ", error: " + e.getMessage());
            if (listener != null) {
                listener.onLog("\n" + lm.get("execution.error", e.getMessage()));
            }
        } finally {
            try {
                sftpService.disconnect();
                Logger.debug("BatchExecutionOrchestrator", "SFTP disconnected");
            } catch (Exception ignored) {}
            result.setDurationMs(System.currentTimeMillis() - startTime);
        }

        return result;
    }

    /** 取消执行（当前子任务完成后停止，不中断进行中的子任务） */
    public void cancel() {
        cancelling.set(true);
    }

    /** 是否正在执行 */
    public boolean isRunning() {
        return executor != null && !executor.isShutdown();
    }

    /**
     * 递归计算文件或目录的总大小。
     */
    private long calculateSize(File file) {
        if (file == null || !file.exists()) return 0;
        if (file.isFile()) return file.length();
        long size = 0;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                size += calculateSize(child);
            }
        }
        return size;
    }
}
