package com.openxt.uploadsshfile.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.openxt.uploadsshfile.ai.AICommandChecker;
import com.openxt.uploadsshfile.ai.AIResultChecker;
import com.openxt.uploadsshfile.config.PathConfig;
import com.openxt.uploadsshfile.config.ServerConfig;
import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.logging.DailyLogService;
import com.openxt.uploadsshfile.model.CommandConfig;
import com.openxt.uploadsshfile.model.SshConnection;
import com.openxt.uploadsshfile.orchestration.CommandOrchestrator;
import com.openxt.uploadsshfile.persistence.SecureStorage;
import com.openxt.uploadsshfile.ssh.SshCommandService;
import com.openxt.uploadsshfile.store.StoreManager;
import com.openxt.uploadsshfile.ui.ExecutionProgressDialog;
import com.openxt.uploadsshfile.validation.BlacklistValidator;
import com.openxt.uploadsshfile.validation.KeywordMatcher;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.Frame;

/**
 * 执行命令 Action
 * 用户点击命令执行按钮时触发
 */
public class ExecuteCommandAction extends AnAction {
    
    private final LanguageManager lang = LanguageManager.getInstance();
    
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;
        
        // 获取选中的服务器配置
        ServerConfig serverConfig = getSelectedServerConfig(e);
        if (serverConfig == null) {
            showError(project, lang.get("action.select.server"));
            return;
        }
        
        // 获取选中的路径配置
        PathConfig pathConfig = getSelectedPathConfig(e);
        if (pathConfig == null) {
            showError(project, lang.get("action.select.path"));
            return;
        }
        
        // 获取命令配置
        CommandConfig commandConfig = StoreManager.getInstance()
            .getUnifiedConfigStore()
            .getCommandConfig(serverConfig.getId(), pathConfig.getId())
            .orElse(null);
        
        if (commandConfig == null || commandConfig.getCommands().isEmpty()) {
            showError(project, lang.get("action.no.config"));
            return;
        }
        
        // 获取密码
        String password = SecureStorage.getInstance().retrieve(serverConfig.getId());
        if (password == null || password.isEmpty()) {
            showError(project, lang.get("action.no.password", serverConfig.getName()));
            return;
        }
        
        // 创建 SSH 连接
        SshConnection connection = SshConnection.fromServerConfig(serverConfig, password);
        
        // 创建进度对话框
        Frame mainFrame = (Frame) SwingUtilities.getWindowAncestor(
            project.getComponent(JComponent.class));
        ExecutionProgressDialog dialog = new ExecutionProgressDialog(mainFrame);
        
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
                return dialog.askUserContinue(message);
            }
            
            @Override
            protected boolean askUserRiskContinue(String command, String riskInfo) {
                return dialog.askUserRiskContinue(command, riskInfo);
            }
            
            @Override
            protected boolean askUserWarningContinue(String command, String warningInfo) {
                return dialog.askUserWarningContinue(command, warningInfo);
            }
            
            @Override
            protected boolean askUserCautionContinue(String command, String cautionInfo) {
                return dialog.askUserCautionContinue(command, cautionInfo);
            }
        };
        
        // 显示对话框并开始执行
        dialog.setLocationRelativeTo(mainFrame);
        dialog.setVisible(true);
        
        // 在后台线程执行命令
        new Thread(() -> {
            orchestrator.executeQueue(commandConfig, connection, dialog);
            
            // 执行完成后断开连接
            sshService.disconnectAll();
        }).start();
    }
    
    @Override
    public void update(@NotNull AnActionEvent e) {
        // 检查是否有选中的服务器和路径配置
        ServerConfig serverConfig = getSelectedServerConfig(e);
        PathConfig pathConfig = getSelectedPathConfig(e);
        
        e.getPresentation().setEnabled(serverConfig != null && pathConfig != null);
    }
    
    private @Nullable ServerConfig getSelectedServerConfig(@NotNull AnActionEvent e) {
        // 尝试从选中节点获取服务器配置
        // 实际实现需要根据你的树结构来获取
        // 这里使用 UnifiedConfigStore 中的第一个配置作为示例
        var configs = StoreManager.getInstance().getUnifiedConfigStore().getAllServers();
        if (configs.isEmpty()) {
            return null;
        }
        return configs.get(0);
    }
    
    private @Nullable PathConfig getSelectedPathConfig(@NotNull AnActionEvent e) {
        // 尝试从选中节点获取路径配置
        // 获取第一个服务器的路径
        var serverConfig = getSelectedServerConfig(e);
        if (serverConfig == null) {
            return null;
        }
        var configs = StoreManager.getInstance().getUnifiedConfigStore().getPathsByServer(serverConfig.getId());
        if (configs.isEmpty()) {
            return null;
        }
        return configs.get(0);
    }
    
    private void showError(Project project, String message) {
        JOptionPane.showMessageDialog(
            project.getComponent(JComponent.class),
            message,
            lang.get("action.error.title"),
            JOptionPane.ERROR_MESSAGE
        );
    }
}
