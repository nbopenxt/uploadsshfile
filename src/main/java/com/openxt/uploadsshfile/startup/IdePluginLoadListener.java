package com.openxt.uploadsshfile.startup;

import com.intellij.ide.plugins.DynamicPluginListener;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import org.jetbrains.annotations.NotNull;

/**
 * D-46（SRS V3.17/架构·设计 V1.22，2026-10-09 用户裁定「按 D-46 实施并并入 1.0.10」）：
 * 幂等触发面新增**插件装载事件**——本插件热载完成（安装/升级/卸载后重装）即预热，
 * java-home.txt 与 bat 自愈不再需要"重启 IDEA 或点开一次菜单"（D-31 半生态/D-44 窗口的收窄项）。
 *
 * 载体选择实核（本地 253 字节码）：{@link DynamicPluginListener#pluginLoaded(IdeaPluginDescriptor)}
 * 方法零注解＝公开稳定（同接口仅 beforePluginsLoaded/pluginsLoaded 为 @ApiStatus.Experimental、
 * checkUnloadPlugin 为 Kotlin-Deprecated，本类一律不重写，防止重蹈 D-45"审核判禁"同族）；
 * 事件语义＝按插件逐个在"该插件加载完毕后"派发，descriptor 可精确识别本插件 ID。
 *
 * 边界（如实）：需重启才生效的安装当时不发本事件（由下次启动 welcome/activity 承接）；
 * 装后从未启动 IDEA 亦无从触发（仍走 D-44 一句话提示）。本触发点与既有四道并存、
 * ensureReady() 同值幂等合并，不构成第二控制点；对平台热载时序零对抗。
 */
public class IdePluginLoadListener implements DynamicPluginListener {

    /** 与本插件 plugin.xml &lt;id&gt; 一致；仅作事件过滤，不新增任何配置面 */
    private static final String OUR_PLUGIN_ID = "com.openxt.uploadsshfile";

    @Override
    public void pluginLoaded(@NotNull IdeaPluginDescriptor plugin) {
        if (!OUR_PLUGIN_ID.equals(plugin.getPluginId().getIdString())) {
            return; // 其他插件的热载与本插件预热无关
        }
        // 注入＋bat 自愈＋java-home.txt（ensureReady 内部吞 Throwable，不给装载事件新增失败面）
        IdeBootstrap.ensureReady();
    }
}
