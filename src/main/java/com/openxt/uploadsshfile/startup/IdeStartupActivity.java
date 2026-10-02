package com.openxt.uploadsshfile.startup;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import com.openxt.uploadsshfile.util.PluginPathManager;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Paths;

/**
 * D-21（SRS V2.6 / 设计文档流程 H，思想实验 #1/#2）：IDE→core 路径注入唯一入口。
 *
 * 语义＝用户裁定的"startup activity 集中注入"；实现载体用 {@link ProjectManagerListener}
 * （253 平台中 ProjectActivity 为 Kotlin suspend 接口、AppLifecycleListener 无开项目回调，
 * 纯 Java 可用 EP 即此；projectOpened 每次项目打开触发、initialize 幂等，语义等价且 8 个
 * 在用 action 零改动）。未初始化窗口内点菜单由 getInstance() fail-fast 抛带指引异常兜底。
 *
 * 职责（M1 阶段）：把 PathManager 实值（本机被 idea.properties 重定向，不可写死/扫描）
 * 一次性注入 core 的 PluginPathManager。
 * 扩展（M3 阶段追加）：bat 完整性自检与生成（D-23），与本类同址。
 */
public class IdeStartupActivity implements ProjectManagerListener {

    @Override
    public void projectOpened(@NotNull Project project) {
        try {
            PluginPathManager.initialize(
                    Paths.get(PathManager.getConfigPath()),
                    Paths.get(PathManager.getLogPath()));
        } catch (Throwable t) {
            // 注入失败不得中断 IDE 打开项目；日志尽力记录，真正的失败由
            // getInstance() fail-fast 在首次使用时向用户呈现指引
            try {
                com.openxt.uploadsshfile.util.Logger.error("IdeStartup",
                        "PluginPathManager.initialize failed: " + t.getMessage());
            } catch (Throwable ignored) {
                // util.Logger 本身可能因路径未初始化而失败——静默，交给 fail-fast
            }
        }

        // D-23（M3）：bat 完整性自检与生成——磁盘 bat 全文件 MD5 ≠ md5(本机模板)
        // （缺失/篡改/滞后三态合一）即原子重生成；写失败仅记日志、不弹窗（流程 H）
        try {
            ensureCliBat();
        } catch (Throwable t) {
            try {
                com.openxt.uploadsshfile.util.Logger.error("IdeStartup",
                        "cli bat generation failed: " + t.getMessage());
            } catch (Throwable ignored) {
                // 尽力而为
            }
        }
    }

    private void ensureCliBat() throws Exception {
        String javaHome = System.getProperty("java.home");
        java.nio.file.Path batFile = Paths.get(PathManager.getPluginsDir().toString(), "uploadsshfile",
                com.openxt.uploadsshfile.cliapi.BatScriptTemplate.FILE_NAME);
        String want = com.openxt.uploadsshfile.cliapi.BatScriptTemplate.expectedMd5(javaHome);
        String have = com.openxt.uploadsshfile.cliapi.BatScriptTemplate.actualMd5(batFile);
        if (want.equals(have)) {
            return; // 合法且最新：零写盘
        }
        // 目录随插件安装天然存在；保险起见再建一次
        java.nio.file.Files.createDirectories(batFile.getParent());
        com.openxt.uploadsshfile.util.AtomicFileWriter.writeString(batFile,
                com.openxt.uploadsshfile.cliapi.BatScriptTemplate.content(javaHome));
        com.openxt.uploadsshfile.util.Logger.debug("IdeStartup",
                "uploadsshfile-cli.bat regenerated (prevMd5=" + have + ", expectedMd5=" + want
                        + ", java.home=" + javaHome + ")");
    }
}
