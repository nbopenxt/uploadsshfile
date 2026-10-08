package com.openxt.uploadsshfile.startup;

import com.intellij.ide.AppLifecycleListener;

/**
 * D-26（SRS V2.8）：IDE 启动完成（appStarted）即注入 core 路径并生成 CLI bat。
 *
 * 253 平台本接口无"项目打开"回调（M1 选型时即此原因用 ProjectManagerListener 承接
 * 开项目语义），但 appStarted 早于一切项目级交互，作为 D-21 注入与 D-23 bat 首启生成
 * 的最早可靠触发点：用户安装插件后即使从未打开项目，"观察点 bat 存在"亦成立。
 * 实际工作全部委托 {@link IdeBootstrap}（幂等，与 projectOpened 双保险并存不冲突）。
 */
public class IdeAppLifecycleListener implements AppLifecycleListener {

    @Override
    public void appStarted() {
        IdeBootstrap.ensurePaths();
        IdeBootstrap.ensureCliBat();
    }
}
