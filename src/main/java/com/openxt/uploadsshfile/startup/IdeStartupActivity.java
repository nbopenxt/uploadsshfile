package com.openxt.uploadsshfile.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.jetbrains.annotations.NotNull;

/**
 * D-21（SRS V2.6 / 设计文档流程 H，思想实验 #1/#2）：IDE→core 路径注入入口之一。
 *
 * 语义＝用户裁定的"startup activity 集中注入"；实现载体用 {@link ProjectManagerListener}
 * （253 平台中 ProjectActivity 为 Kotlin suspend 接口、AppLifecycleListener 无开项目回调，
 * 纯 Java 可用 EP 即此；projectOpened 每次项目打开触发、initialize 幂等）。
 *
 * D-26（SRS V2.8，2026-10-04 人工验收竞态发现）：菜单装配期实例化 action 可早于
 * projectOpened，注入职责已收口至 {@link IdeBootstrap}（appStarted + action 构造体首行
 * ensurePaths 双点先行），本类降为**幂等二道保险**——实际工作全部委托 IdeBootstrap，
 * 与 ①② 触发点同值合并、不产生第二控制点（全局唯一控制点规则）。
 */
public class IdeStartupActivity implements ProjectManagerListener {

    @Override
    public void projectOpened(@NotNull Project project) {
        // D-21：路径注入（幂等）；D-23：bat 完整性自检与生成（MD5 一致则零写盘）
        IdeBootstrap.ensurePaths();
        IdeBootstrap.ensureCliBat();
    }
}
