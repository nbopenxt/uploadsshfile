package com.openxt.uploadsshfile.startup;

import com.intellij.ide.AppLifecycleListener;

/**
 * D-26（SRS V2.8）：IDE 启动预热（欢迎屏展示）即注入 core 路径并生成 CLI bat。
 *
 * D-45（2026-10-09 Marketplace 审核整改）：原重写方法 {@code appStarted()} 已被平台标注
 * {@code @ApiStatus.Internal}（verifier 报"internal API usage"，官方指引改用项目级
 * ProjectActivity），本类改重写同接口的**公开**回调 {@link #welcomeScreenDisplayed()}
 * （本地 253 字节码实核：本接口仅 appStarted/beforeAppWillBeClosed 为 Internal，其余公开）。
 * 覆盖分工：开项目会话由 {@link IdeStartupActivity}（postStartupActivity）承接，
 * "从未开项目只见欢迎屏"会话由本回调承接——两者合并保持 D-26"不依赖曾打开项目"
 * 的预热语义与 D-43 Fix A"启动一次 IDEA"指引继续成立。
 * 实际工作全部委托 {@link IdeBootstrap}（幂等，与开项目回调并存不冲突）。
 */
public class IdeAppLifecycleListener implements AppLifecycleListener {

    @Override
    public void welcomeScreenDisplayed() {
        IdeBootstrap.ensurePaths();
        IdeBootstrap.ensureCliBat();
    }
}
