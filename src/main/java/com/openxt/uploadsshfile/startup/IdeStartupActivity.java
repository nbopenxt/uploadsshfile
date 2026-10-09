package com.openxt.uploadsshfile.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.annotations.NotNull;

/**
 * D-21（SRS V2.6 / 设计文档流程 H，思想实验 #1/#2）：IDE→core 路径注入入口之一。
 *
 * D-45（2026-10-09 Marketplace 审核整改）：实现载体由 {@code ProjectManagerListener.projectOpened}
 * （平台已标注 {@code @Deprecated}＋{@code @ScheduledForRemoval}，verifier 报"scheduled for
 * removal API usage"）更换为官方替代 {@link ProjectActivity}，经
 * {@code com.intellij.postStartupActivity} EP 注册。D-21 当年否决 ProjectActivity 的理由是
 * "253 平台为 Kotlin suspend 接口、纯 Java 不可写"——本地 253 字节码实核其 JVM 签名＝
 * {@code Object execute(Project, Continuation<? super Unit>)}，Java 直接返回
 * {@code Unit.INSTANCE} 即以同步方式完成协程（不消费 continuation、无悬挂点），否决理由消失。
 *
 * 语义＝用户裁定的"startup activity 集中注入"。
 *
 * D-26（SRS V2.8，2026-10-04 人工验收竞态发现）：菜单装配期实例化 action 可早于本回调，
 * 注入职责已收口至 {@link IdeBootstrap}（启动预热 + action 构造体首行 ensurePaths 先行），
 * 本类保持为**幂等二道保险**——实际工作全部委托 IdeBootstrap，与其他触发点同值合并、
 * 不产生第二控制点（全局唯一控制点规则）。
 */
public final class IdeStartupActivity implements ProjectActivity {

    @Override
    public Object execute(@NotNull Project project,
                          @NotNull Continuation<? super Unit> continuation) {
        // D-21：路径注入（幂等）；D-23：bat 完整性自检与生成（MD5 一致则零写盘）
        IdeBootstrap.ensurePaths();
        IdeBootstrap.ensureCliBat();
        return Unit.INSTANCE;
    }
}
