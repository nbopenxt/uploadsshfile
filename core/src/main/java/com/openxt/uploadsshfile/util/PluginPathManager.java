package com.openxt.uploadsshfile.util;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 路径管理工具类
 * 统一管理插件配置目录和日志目录
 *
 * 注意：配置目录放在 {IDEA_CONFIG}/uploadsshfile/ 而非 {IDEA_CONFIG}/plugins/uploadsshfile/
 * 这样插件卸载时配置不会被删除，重装后可自动恢复
 *
 * D-21（SRS V2.6）：本类位于 core 模块，禁止依赖 com.intellij。
 * IDEA 配置/日志根目录的实值只能由宿主进程取得，经 initialize() 一次性注入：
 *   - GUI：plugin 模块 IdeBootstrap（D-26 起＝appStarted + 菜单入口 + projectOpened 三道
 *     幂等保险，均为 PathManager 实值）；
 *   - CLI：cli.Main 解析 --config-dir / 由 bat %~dp0 反推后调用。
 * 未初始化即 getInstance() 抛带指引的 IllegalStateException（fail-fast，M1 出口自证项）。
 */
public class PluginPathManager {

    private static final String PLUGIN_CONFIG_DIR = "uploadsshfile";

    private static PluginPathManager instance;

    private final Path ideaConfigRoot;
    private final Path ideaLogRoot;

    private PluginPathManager(Path ideaConfigRoot, Path ideaLogRoot) {
        this.ideaConfigRoot = ideaConfigRoot;
        this.ideaLogRoot = ideaLogRoot;
    }

    /**
     * 一次性初始化（幂等：同值重复调用直接通过；异值抛错防双写者分叉）。
     * @param ideaConfigRoot IDEA 配置根目录（GUI＝PathManager.getConfigPath()；CLI＝bat 反推或 --config-dir）
     * @param ideaLogRoot    IDEA 日志根目录（GUI＝PathManager.getLogPath()；CLI＝可传配置根同值，日志目录自动带 uploadsshfile 子目录）
     */
    public static synchronized void initialize(Path ideaConfigRoot, Path ideaLogRoot) {
        if (ideaConfigRoot == null || ideaLogRoot == null) {
            throw new IllegalArgumentException("initialize() requires non-null IDEA config/log root paths");
        }
        if (instance == null) {
            instance = new PluginPathManager(ideaConfigRoot.toAbsolutePath().normalize(),
                                             ideaLogRoot.toAbsolutePath().normalize());
        } else if (!instance.ideaConfigRoot.equals(ideaConfigRoot.toAbsolutePath().normalize())
                || !instance.ideaLogRoot.equals(ideaLogRoot.toAbsolutePath().normalize())) {
            throw new IllegalStateException(
                "PluginPathManager is already initialized with different paths; a second control point is forbidden (single-authority rule)");
        }
    }

    /** 测试/重载场景用：清空注入状态（仅 core 单测调用，业务代码禁用） */
    public static synchronized void resetForTests() {
        instance = null;
    }

    public static synchronized PluginPathManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                "PluginPathManager not initialized. GUI normally auto-initializes via IdeBootstrap (appStarted / menu "
              + "entries / projectOpened, D-26) — seeing this in the IDE means a regression: report idea.log near first "
              + "use. CLI: cli.Main must call initialize() after parsing --config-dir / %~dp0 before any config access.");
        }
        return instance;
    }

    /**
     * 获取插件配置目录
     */
    public Path getConfigPath() {
        return ideaConfigRoot.resolve(PLUGIN_CONFIG_DIR);
    }

    /**
     * 获取插件日志目录
     */
    public Path getLogPath() {
        return ideaLogRoot.resolve(PLUGIN_CONFIG_DIR);
    }

    /**
     * 确保目录存在
     */
    public Path ensureDirectory(Path path) {
        File dir = path.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return path;
    }
}
