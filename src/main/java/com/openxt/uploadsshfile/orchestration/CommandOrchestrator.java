package com.openxt.uploadsshfile.orchestration;

import com.openxt.uploadsshfile.ai.AICommandChecker;
import com.openxt.uploadsshfile.ai.AIResultChecker;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.logging.DailyLogService;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.CommandItem;
import com.openxt.uploadsshfile.model.CommandResult;
import com.openxt.uploadsshfile.model.EvaluationResult;
import com.openxt.uploadsshfile.model.ExecutionSummary;
import com.openxt.uploadsshfile.model.SshConnection;
import com.openxt.uploadsshfile.model.ValidationResult;
import com.openxt.uploadsshfile.ssh.SshCommandService;
import com.openxt.uploadsshfile.validation.BlacklistValidator;
import com.openxt.uploadsshfile.validation.KeywordMatcher;
import com.openxt.uploadsshfile.validation.SemanticBlacklistChecker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 命令执行编排器
 * 负责命令队列的执行编排和流程控制
 * 
 * <b>执行模式</b>：
 * <ul>
 *   <li><b>自动执行模式</b>：上传结束后自动触发执行</li>
 *   <li><b>手动执行模式</b>：用户点击命令执行按钮触发</li>
 * </ul>
 * 
 * <b>核心原则</b>：无论哪种模式，命令组内均为顺序执行，任意命令执行失败都会停下来询问用户是否继续执行下一条。
 */
public class CommandOrchestrator {
    
    private final LanguageManager lang = LanguageManager.getInstance();
    private final SshCommandService sshService;
    private final BlacklistValidator blacklistValidator;
    private final KeywordMatcher keywordMatcher;
    private final SemanticBlacklistChecker semanticBlacklistChecker;
    private final AICommandChecker aiCommandChecker;
    private final AIResultChecker aiResultChecker;
    private final DailyLogService logService;
    
    private volatile boolean cancelled = false;
    private ExecutionListener currentListener;
    
    public CommandOrchestrator(
            @NotNull SshCommandService sshService,
            @NotNull BlacklistValidator blacklistValidator,
            @NotNull KeywordMatcher keywordMatcher) {
        this(sshService, blacklistValidator, keywordMatcher, null, null, null);
    }
    
    public CommandOrchestrator(
            @NotNull SshCommandService sshService,
            @NotNull BlacklistValidator blacklistValidator,
            @NotNull KeywordMatcher keywordMatcher,
            AICommandChecker aiCommandChecker,
            AIResultChecker aiResultChecker) {
        this(sshService, blacklistValidator, keywordMatcher, aiCommandChecker, aiResultChecker, null);
    }
    
    public CommandOrchestrator(
            @NotNull SshCommandService sshService,
            @NotNull BlacklistValidator blacklistValidator,
            @NotNull KeywordMatcher keywordMatcher,
            AICommandChecker aiCommandChecker,
            AIResultChecker aiResultChecker,
            DailyLogService logService) {
        this(sshService, blacklistValidator, keywordMatcher, 
             new SemanticBlacklistChecker(), aiCommandChecker, aiResultChecker, logService);
    }
    
    /**
     * 全参数构造函数
     */
    public CommandOrchestrator(
            @NotNull SshCommandService sshService,
            @NotNull BlacklistValidator blacklistValidator,
            @NotNull KeywordMatcher keywordMatcher,
            @NotNull SemanticBlacklistChecker semanticBlacklistChecker,
            AICommandChecker aiCommandChecker,
            AIResultChecker aiResultChecker,
            DailyLogService logService) {
        this.sshService = sshService;
        this.blacklistValidator = blacklistValidator;
        this.keywordMatcher = keywordMatcher;
        this.semanticBlacklistChecker = semanticBlacklistChecker;
        this.aiCommandChecker = aiCommandChecker;
        this.aiResultChecker = aiResultChecker;
        this.logService = logService;
    }
    
    /**
     * 执行命令队列
     */
    public void executeQueue(
            @NotNull CommandConfig config,
            @NotNull SshConnection connection,
            @NotNull ExecutionListener listener) {
        
        this.cancelled = false;
        this.currentListener = listener;
        
        // 过滤启用的命令并按顺序排列
        List<CommandItem> enabledCommands = config.getCommands().stream()
            .filter(CommandItem::isEnabled)
            .sorted((a, b) -> Integer.compare(a.getOrder(), b.getOrder()))
            .collect(Collectors.toList());
        
        if (enabledCommands.isEmpty()) {
            listener.onError(lang.get("execution.noCommands"));
            return;
        }
        
        int total = enabledCommands.size();
        listener.onStart(total);
        logInfo("Execution started - Total commands: " + total);
        
        // 执行统计
        int successCount = 0;
        int failedCount = 0;
        int blockedCount = 0;
        
        for (int i = 0; i < enabledCommands.size(); i++) {
            if (cancelled) {
                break;
            }
            
            CommandItem item = enabledCommands.get(i);
            String command = item.getCommand();
            int index = i + 1;
            
            listener.onCommandStart(index, total, command);
            logInfo("Executing command " + index + "/" + total + ": " + command);
            
            // 步骤1: 黑名单检查（包含 OS 特定黑名单）
            ValidationResult validationResult = blacklistValidator.validate(
                command, 
                connection.getServerId(),
                connection.getOsType()
            );
            if (validationResult.isBlocked()) {
                blockedCount++;
                listener.onBlocked(index, total, validationResult.getReason());
                logWarn("Command blocked: " + validationResult.getReason());
                continue;
            }
            
            // 步骤2: 语义分析/AI 黑名单检查
            // - 如果启用了 AI 黑名单检查：使用 AI 模型判断
            // - 如果未启用 AI：使用语义分析
            ValidationResult semanticResult = checkWithSemanticOrAI(command, connection.getOsType());
            if (semanticResult.isBlocked()) {
                blockedCount++;
                listener.onBlocked(index, total, semanticResult.getReason());
                logWarn("Command blocked by semantic/AI check: " + semanticResult.getReason());
                continue;
            }
            if (semanticResult.isCaution()) {
                // 需要用户确认
                boolean shouldExecute = askUserCautionContinue(command, semanticResult.getReason());
                if (!shouldExecute) {
                    blockedCount++;
                    listener.onBlocked(index, total, lang.get("execution.cancelled.byUser"));
                    logInfo("User cancelled command due to caution warning");
                    continue;
                }
            }
            
            // 步骤3: AI 执行校验（可选）
            if (aiCommandChecker != null) {
                String aiCheckResult = aiCommandChecker.checkCommand(command);
                if (!aiCheckResult.isEmpty()) {
                    // AI 返回风险提示，由用户决定是否继续
                    logWarn("AI risk warning: " + aiCheckResult);
                    boolean shouldExecute = askUserRiskContinue(command, aiCheckResult);
                    if (!shouldExecute) {
                        blockedCount++;
                        listener.onBlocked(index, total, lang.get("ai.cancelled"));
                        logInfo("User cancelled command due to AI warning");
                        continue;
                    }
                }
            }
            
            // 步骤4: 执行命令（使用 Shell Channel 保持会话状态）
            CommandResult result;
            try {
                result = sshService.executeWithShell(connection, command);
            } catch (SshCommandService.ConnectionException e) {
                listener.onError(lang.get("execution.connection.error", e.getMessage()));
                logError("Connection error: " + e.getMessage());
                return;
            } catch (Exception e) {
                listener.onError(lang.get("execution.ssh.error", e.getMessage()));
                logError("SSH error: " + e.getMessage());
                return;
            }
            
            // 步骤5: 判断命令是否成功，并调整 stdout/stderr/exitCode
            // 
            // stdout/stderr 分配规则：
            // 1. exitCode != 0 → stdout="", stderr=输出（已在 SshCommandService 中处理）
            // 2. exitCode == 0 + 失败关键词 → stdout="", stderr=输出, exitCode=-2
            // 3. exitCode == 0 + AI判定错误 → stdout="", stderr=AI消息, exitCode=-2
            // 4. exitCode == 0 + 其他 → stdout=输出, stderr=""（已在 SshCommandService 中处理）
            //
            // exitCode 含义：
            // - -2: 服务器执行成功(exitcode=0)，但被关键词或AI判定为错误
            // - -1: 本地错误（无法获取退出码）
            // - 0: 成功
            // - >0: 服务器命令失败
            boolean commandSuccess;
            String failureReason = null;
            
            int exitCode = result.getExitCode();
            commandSuccess = (exitCode == 0);
            if (!commandSuccess) {
                failureReason = "Exit code: " + exitCode;
            }
            if (exitCode == 0) {
                // 成功退出码，使用关键词规则评估
                EvaluationResult evaluation = keywordMatcher.evaluate(result);
                commandSuccess = evaluation.isSuccess();
                if (!commandSuccess) {
                    // 关键词判定为失败
                    failureReason = evaluation.getReason();
                    // 修改 result：将输出移到 stderr，exitCode 设为 -2
                    String output = result.getStdout();
                    result.setExitCode(-2);
                    result.setStdout("");
                    result.setStderr(output.isEmpty() ? failureReason : output);
                } else {
                    // 步骤6: AI 结果校验（可选，仅在关键词判定为成功时执行）
                    if (aiResultChecker != null) {
                        String aiCheckResult = aiResultChecker.checkResult(command, result);
                        if (!aiCheckResult.isEmpty()) {
                            // AI 判定为失败
                            logWarn("AI result warning: " + aiCheckResult);
                            failureReason = aiCheckResult;
                            commandSuccess = false;
                            // 修改 result：exitCode 设为 -2，stderr 包含命令输出和AI消息
                            result.setExitCode(-2);
                            String output = result.getStdout();
                            result.setStdout("");
                            if (output.isEmpty()) {
                                result.setStderr(aiCheckResult);
                            } else {
                                result.setStderr(output + "\n[AI判断] " + aiCheckResult);
                            }
                        }
                    }
                }
            }
            
            if (commandSuccess) {
                successCount++;
                listener.onCommandSuccess(index, total, command, result);
                logInfo("Command " + index + " succeeded - Exit code: " + exitCode);
            } else {
                failedCount++;
                listener.onCommandFailed(index, total, command, result);
                logWarn("Command " + index + " failed: " + failureReason);
                
                // 核心原则：失败时强制询问用户
                boolean shouldContinue = askUserContinue(
                    lang.get("execution.continue.prompt", failureReason)
                );
                
                if (!shouldContinue) {
                    cancelled = true;
                    break;
                }
            }
        }
        
        // 执行完成
        ExecutionSummary summary = new ExecutionSummary(total, successCount, failedCount, blockedCount);
        summary.setStartTime(System.currentTimeMillis() - (successCount + failedCount + blockedCount) * 100);
        summary.setEndTime(System.currentTimeMillis());
        listener.onComplete(summary);
        logInfo("Execution completed - " + summary);
    }
    
    /**
     * 中断执行
     */
    public void cancel() {
        this.cancelled = true;
        logInfo("Execution cancelled by user");
    }
    
    /**
     * 询问用户是否继续
     */
    protected boolean askUserContinue(String message) {
        // 由调用方（UI层）实现
        throw new UnsupportedOperationException(
            "askUserContinue must be implemented by UI layer"
        );
    }
    
    /**
     * AI 执行校验警告 - 询问用户是否继续执行
     * @param command 执行的命令
     * @param riskInfo AI 返回的风险提示信息
     * @return true=用户选择执行，false=用户选择取消
     */
    protected boolean askUserRiskContinue(String command, String riskInfo) {
        // 由调用方（UI层）实现
        throw new UnsupportedOperationException(
            "askUserRiskContinue must be implemented by UI layer"
        );
    }
    
    /**
     * AI 结果校验警告 - 询问用户是否继续执行后续命令
     * @param command 执行的命令
     * @param warningInfo AI 返回的警告信息（错误解释+建议措施）
     * @return true=用户选择继续，false=用户选择取消
     */
    protected boolean askUserWarningContinue(String command, String warningInfo) {
        // 由调用方（UI层）实现
        throw new UnsupportedOperationException(
            "askUserWarningContinue must be implemented by UI layer"
        );
    }
    
    /**
     * 语义/AI 检查警告 - 询问用户是否继续执行危险命令
     * @param command 执行的命令
     * @param cautionInfo 警告信息
     * @return true=用户选择继续，false=用户选择取消
     */
    protected boolean askUserCautionContinue(String command, String cautionInfo) {
        // 由调用方（UI层）实现
        throw new UnsupportedOperationException(
            "askUserCautionContinue must be implemented by UI layer"
        );
    }
    
    /**
     * 黑名单检查逻辑
     * <p>
     * 处理流程：
     * 1. 首先检查用户自定义黑名单（SemanticBlacklistChecker）→ 匹配则 BLOCKED
     * 2. 如果AI未启用 → 直接放行
     * 3. 如果AI启用 → AI判断，如果是危险操作则 CAUTION（需确认），否则继续
     * </p>
     * 
     * @param command 要检查的命令
     * @param osType 操作系统类型
     * @return 检查结果
     */
    private @NotNull ValidationResult checkWithSemanticOrAI(@NotNull String command, @Nullable String osType) {
        // 步骤1: 首先检查用户自定义黑名单
        ValidationResult blacklistResult = semanticBlacklistChecker.validate(command, osType);
        if (blacklistResult.isBlocked()) {
            // 用户黑名单匹配，直接阻止
            return blacklistResult;
        }
        
        // 步骤2: 如果AI未启用，直接放行
        if (aiCommandChecker == null || !aiCommandChecker.isAiBlacklistCheckEnabled()) {
            return ValidationResult.allowed();
        }
        
        // 步骤3: AI启用，AI增强判断
        // AI判断该命令是否为危险操作，返回 CAUTION 或 allowed
        return aiCommandChecker.checkBlacklist(command);
    }
    
    /**
     * 判断是否已取消
     */
    public boolean isCancelled() {
        return cancelled;
    }
    
    private void logInfo(String message) {
        if (logService != null) {
            logService.info("Orchestrator", message);
        }
    }
    
    private void logWarn(String message) {
        if (logService != null) {
            logService.warn("Orchestrator", message);
        }
    }
    
    private void logError(String message) {
        if (logService != null) {
            logService.error("Orchestrator", message);
        }
    }
}
