package com.openxt.uploadsshfile.startup;

import com.intellij.openapi.application.PathManager;
import com.openxt.uploadsshfile.cliapi.BatScriptTemplate;
import com.openxt.uploadsshfile.util.AtomicFileWriter;
import com.openxt.uploadsshfile.util.Logger;
import com.openxt.uploadsshfile.util.PluginPathManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * D-26（SRS V2.8，人工验收 2026-10-04 发现）：plugin 层 IDE 路径注入唯一入口。
 *
 * 背景：D-21 原实现把注入只挂在 ProjectManagerListener.projectOpened，而菜单装配期
 * （ActionUpdater→实例化/展开）可早于 projectOpened——会话恢复启动后立刻右键即命中
 * fail-fast 红气球（UploadAction.&lt;init&gt; 等 6 个 action 构造体触达存储单例）。
 *
 * 触发点（三道幂等保险，同值 initialize 天然合并，不产生第二控制点）：
 *   ① {@link IdeAppLifecycleListener#welcomeScreenDisplayed()}——启动预热（欢迎屏展示）
 *      即注入并生成 CLI bat（用户从未打开项目也满足 D-23"首启生成"；D-45 载体整改，
 *      原 appStarted 为 @ApiStatus.Internal 审核判禁）；
 *   ② 各 action 构造体/菜单展开期入口（update/isSelected）首行 ensureReady()
 *      （D-31 起＝注入＋bat 自愈）——覆盖任何先于 ① 的实例化时序，
 *      且热载半生态（无生命周期回调）下点一次菜单即补齐 bat；
 *   ③ {@link IdeStartupActivity#execute}——开项目二道保险（postStartupActivity EP；
 *      D-45 载体由 projectOpened 换为 ProjectActivity，后者已 @ScheduledForRemoval）；
 *   ④ {@link IdePluginLoadListener#pluginLoaded}——本插件热载完成即预热（D-46，
 *      DynamicPluginListener 公开回调；装/重装后免重启免点菜单生成 java-home.txt）。
 *   （另有 D-29 复制入口一道，合计触发面五道——D-31"四道并存"口径之扩展，同值幂等合并。）
 *
 * ensurePaths() 吞 Throwable 尽力记录：action 构造入口不得引入新失败面；
 * 真实失败仍由 core getInstance() fail-fast 在首次使用时向用户呈现指引。
 */
public final class IdeBootstrap {

    private IdeBootstrap() {
    }

    /**
     * D-31（2026-10-04 热载半生态二次复发，用户裁定加兜底）：菜单/构造入口统一走本方法＝
     * 注入＋bat 自愈。热载（loaded without restart）不触发启动预热回调（D-45 前＝appStarted/
     * projectOpened，现＝welcomeScreenDisplayed/postStartupActivity），
     * 而插件安装会清空目录＝bat 缺失、外部直敲 CLI 踩空——action 入口顺带补生成后，
     * 用户点过任意插件菜单 bat 即回。代价实测口径：470B 文件读＋MD5 比对亚毫秒级，
     * 仅缺失/滞后才写盘（&lt;5ms），菜单展开线程可接受（D-26"入口不写盘"决定经用户裁定于此反转）。
     */
    public static void ensureReady() {
        ensurePaths();
        ensureCliBat();
    }

    /** 一次性路径注入（幂等；core 侧同值重复调用直接通过） */
    public static synchronized void ensurePaths() {
        try {
            PluginPathManager.initialize(
                    Paths.get(PathManager.getConfigPath()),
                    Paths.get(PathManager.getLogPath()));
        } catch (Throwable t) {
            // 注入失败不得中断 action 实例化/菜单展开；日志尽力记录
            try {
                Logger.error("IdeBootstrap", "PluginPathManager.initialize failed: " + t.getMessage());
            } catch (Throwable ignored) {
                // util.Logger 本身可能因路径未初始化而失败——静默，交给 fail-fast
            }
        }
    }

    /**
     * D-39（2026-10-05 用户裁定，反转 D-23）：bat 随包发布后本方法语义降级为**兜底自愈**——
     * 磁盘 bat 全文件 MD5 ≠ md5(静态模板)（缺失/误删/篡改三态合一）即从内存模板原子重生成；
     * 正常安装（zip 已含字节一致的同款 bat）时 want==have 零写盘。写失败仅记日志、不弹窗。
     * 顺带刷新 java 探测数据文件 java-home.txt（写入失败不影响 bat 自愈，bat 回落 JAVA_HOME/PATH）。
     * D-43：回落链上每个候选都由 bat 侧 {@code :check} 验 {@code java.specification.version>=21}，
     * 故本方法只需写"IDEA 自己正在用的那个 java.exe"，无须也不能替 bat 判版本。
     */
    public static void ensureCliBat() {
        Path pluginDir = null;
        try {
            pluginDir = Paths.get(PathManager.getPluginsDir().toString(), "uploadsshfile");
            Path batFile = pluginDir.resolve(BatScriptTemplate.FILE_NAME);
            String want = BatScriptTemplate.expectedMd5();
            String have = BatScriptTemplate.actualMd5(batFile);
            if (!want.equals(have)) {
                // 目录随插件安装天然存在；保险起见再建一次
                Files.createDirectories(pluginDir);
                AtomicFileWriter.writeString(batFile, BatScriptTemplate.content());
                Logger.debug("IdeBootstrap",
                        "uploadsshfile-cli.bat restored from static template (prevMd5=" + have
                                + ", expectedMd5=" + want + ")");
            }
        } catch (Throwable t) {
            try {
                Logger.error("IdeBootstrap", "cli bat self-heal failed: " + t.getMessage());
            } catch (Throwable ignored) {
                // 尽力而为
            }
        }
        writeJavaHomeProbe(pluginDir);
    }

    /**
     * D-39：java-home.txt 单点写入——内容＝本 IDE 进程 java.home 下 java.exe 绝对路径（单行 CRLF）。
     * 仅纯 ASCII 路径可写（cmd 以系统 ANSI 码页解 bat，非 ASCII 字节不可靠——宁可回落
     * JAVA_HOME/PATH）；路径变化才重写（启动预热/菜单入口触发，成本＝一次读比对）。
     */
    private static void writeJavaHomeProbe(Path pluginDir) {
        if (pluginDir == null) {
            return;
        }
        Path probe = pluginDir.resolve(BatScriptTemplate.JAVA_HOME_FILE_NAME);
        try {
            String javaHome = System.getProperty("java.home");
            String javaExe = javaHome.endsWith("bin")
                    ? javaHome + java.io.File.separator + "java.exe"
                    : javaHome + java.io.File.separator + "bin" + java.io.File.separator + "java.exe";
            if (!BatScriptTemplate.isPureAscii(javaExe)) {
                Files.deleteIfExists(probe); // 非 ASCII＝不可用，删除残档防 bat 读到坏值
                return;
            }
            String want = BatScriptTemplate.javaHomeFileContent(javaExe);
            if (Files.exists(probe) && want.equals(new String(
                    Files.readAllBytes(probe), java.nio.charset.StandardCharsets.US_ASCII))) {
                return; // 零写盘
            }
            AtomicFileWriter.writeString(probe, want);
            Logger.debug("IdeBootstrap", "java-home.txt refreshed: " + javaExe);
        } catch (Throwable t) {
            try {
                Logger.error("IdeBootstrap", "java-home probe write failed: " + t.getMessage());
            } catch (Throwable ignored) {
                // 尽力而为——bat 回落链兜底
            }
        }
    }
}
