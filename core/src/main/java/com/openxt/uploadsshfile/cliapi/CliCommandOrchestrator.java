package com.openxt.uploadsshfile.cliapi;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.orchestration.CommandOrchestrator;
import com.openxt.uploadsshfile.ssh.TimeoutPrompter;
import com.openxt.uploadsshfile.ssh.SshCommandService;
import com.openxt.uploadsshfile.validation.BlacklistValidator;
import com.openxt.uploadsshfile.validation.KeywordMatcher;
import com.openxt.uploadsshfile.validation.SemanticBlacklistChecker;
import com.openxt.uploadsshfile.ai.AICommandChecker;
import com.openxt.uploadsshfile.ai.AIResultChecker;
import com.openxt.uploadsshfile.logging.DailyLogService;

/**
 * CLI 编排器（1.0.8 / D-13/D-17，设计文档 §1/§3.2 补定 #3）。
 *
 * <p>extends 既有 CommandOrchestrator——六段管线判定顺序一行不改，仅覆写询问钩子：
 * 4 个 {@code protected askUserXxx} → cmd 英文提问读键盘（经 {@link Prompter}），
 * {@code timeoutPrompter()} → 启用 D-17 递进超时询问，{@code promptContinueWait} → cmd 英文问。
 * 询问期间 EchoGuard 置位（排队心跳/\r 进度行静默，提问不被撕开）。
 *
 * <p>文案口径（AXIOM-B）：取 bundle {@code en}（CLI 启动时 LanguageManager 固定 en，P-02），
 * 输出层再净化 6 条含 {@code 【】} 的 en 文案为 {@code []}（实测 messages.properties:255-264，P-02/#8）。
 */
public class CliCommandOrchestrator extends CommandOrchestrator {

    private final Prompter prompter;
    private final EchoGuard echo;
    private final LanguageManager lang;

    public CliCommandOrchestrator(SshCommandService sshService,
                                  BlacklistValidator blacklistValidator,
                                  KeywordMatcher keywordMatcher,
                                  SemanticBlacklistChecker semanticBlacklistChecker,
                                  AICommandChecker aiCommandChecker,
                                  AIResultChecker aiResultChecker,
                                  DailyLogService logService,
                                  Prompter prompter,
                                  EchoGuard echo,
                                  LanguageManager lang) {
        super(sshService, blacklistValidator, keywordMatcher,
              semanticBlacklistChecker, aiCommandChecker, aiResultChecker, logService);
        this.prompter = prompter;
        this.echo = echo;
        this.lang = lang;
    }

    /** 英文问句 + ASCII 净化 + EchoGuard 独占窗口 */
    private boolean ask(String enKey, Object... args) {
        String q = ascii(enKey, args);
        echo.freeze();
        echo.begin();
        try {
            return prompter.askYesNo(q);
        } finally {
            echo.end();
        }
    }

    /** bundle en 取值并净化全角括号为 ASCII（CLI 输出红线 AC-21） */
    private String ascii(String key, Object... args) {
        return asciiText(lang.get(key, args));
    }

    /** 静态净化入口（CliRunner 等共用）：全角【】→[]，远端混入的中文括号亦净化 */
    public static String asciiText(String s) {
        if (s == null) {
            return "";
        }
        return s.replace('【', '[').replace('】', ']');
    }

    @Override
    protected boolean askUserContinue(String message) {
        return ask("cli.ask.continue.failed", ascii(message));
    }

    @Override
    protected boolean askUserRiskContinue(String command, String riskInfo) {
        return ask("cli.ask.continue.risk", command, riskInfo);
    }

    @Override
    protected boolean askUserWarningContinue(String command, String warningInfo) {
        return ask("cli.ask.continue.warning", command, warningInfo);
    }

    @Override
    protected boolean askUserCautionContinue(String command, String cautionInfo) {
        return ask("cli.ask.continue.caution", command, cautionInfo);
    }

    @Override
    protected TimeoutPrompter timeoutPrompter() {
        return this::promptContinueWait; // D-17：CLI 启用递进超时询问
    }

    @Override
    protected boolean promptContinueWait(String command, long elapsedMs) {
        // 与设计 §5.7 文案一致：Still running (elapsed mm:ss). Keep waiting? (y/n)
        echo.freeze();
        echo.begin();
        try {
            return prompter.askYesNo(
                    "Still running (elapsed " + formatDuration(elapsedMs) + "). Keep waiting? (y/n) ");
        } finally {
            echo.end();
        }
    }

    /** 秒数 → mm:ss（与 TimeoutManager.formatDuration 同口径，此处独立实现保持 core 无状态耦合） */
    static String formatDuration(long ms) {
        long sec = ms / 1000;
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }
}
