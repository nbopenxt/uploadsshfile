package com.openxt.uploadsshfile.startup;

import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.startup.ProjectActivity;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * D-45（2026-10-09 Marketplace 审核整改）结构回归：启动注入载体与其 plugin.xml 注册必须同构
 * （D-25 教训——注册语法错误只有装回 IDE 加载才暴露，此处以静态断言前移到构建期）。
 *
 * 锁死四件事：
 *   ① 开项目载体＝ IdeStartupActivity 实现 ProjectActivity（官方替代，注册于
 *      com.intellij.postStartupActivity EP），且不再实现 ScheduledForRemoval 的
 *      ProjectManagerListener；
 *   ② 欢迎载体＝ IdeAppLifecycleListener 重写公开 welcomeScreenDisplayed、
 *      绝不重写 @ApiStatus.Internal 的 appStarted（Marketplace verifier 判禁项）；
 *   ③ 文档中无 projectListeners/ProjectManagerListener 残留注册；
 *   ④（D-46）装载事件载体＝ IdePluginLoadListener 仅重写公开 pluginLoaded（同接口
 *      Experimental 的 beforePluginsLoaded/pluginsLoaded 等一律禁重写），且
 *      applicationListeners 注册 topic＝DynamicPluginListener 齐备。
 */
public class StartupRegistrationTest {

    private static Document loadPluginXml() throws Exception {
        try (InputStream in = StartupRegistrationTest.class.getResourceAsStream("/META-INF/plugin.xml")) {
            assertNotNull("测试类路径必须能读到 META-INF/plugin.xml", in);
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
        }
    }

    @Test
    public void projectOpenCarrierMustBeProjectActivity() {
        assertTrue("IdeStartupActivity 必须实现 ProjectActivity（projectOpened 已 @ScheduledForRemoval）",
                ProjectActivity.class.isAssignableFrom(IdeStartupActivity.class));
        assertFalse("不得再实现 ProjectManagerListener（verifier 报 scheduled-for-removal 的源头）",
                ProjectManagerListener.class.isAssignableFrom(IdeStartupActivity.class));
    }

    @Test
    public void appLifecycleListenerMustNotOverrideInternalAppStarted() {
        try {
            IdeAppLifecycleListener.class.getDeclaredMethod("welcomeScreenDisplayed");
        } catch (NoSuchMethodException e) {
            fail("IdeAppLifecycleListener 必须重写公开回调 welcomeScreenDisplayed()");
        }
        for (Method m : IdeAppLifecycleListener.class.getDeclaredMethods()) {
            assertNotEquals("appStarted() 是平台 @ApiStatus.Internal 方法，禁止重写（D-45/审核 internal API 项）",
                    "appStarted", m.getName());
        }
    }

    @Test
    public void pluginXmlRegistrationMustMatchCarriers() throws Exception {
        Document doc = loadPluginXml();

        NodeList post = doc.getElementsByTagName("postStartupActivity");
        assertEquals("postStartupActivity EP 注册必须唯一", 1, post.getLength());
        assertEquals(IdeStartupActivity.class.getName(),
                ((Element) post.item(0)).getAttribute("implementation"));

        assertEquals("不得残留 <projectListeners>（D-45 已移除 ProjectManagerListener 载体）",
                0, doc.getElementsByTagName("projectListeners").getLength());

        NodeList listeners = doc.getElementsByTagName("listener");
        boolean appFound = false;
        for (int i = 0; i < listeners.getLength(); i++) {
            Element e = (Element) listeners.item(i);
            if (IdeAppLifecycleListener.class.getName().equals(e.getAttribute("class"))) {
                assertEquals("com.intellij.ide.AppLifecycleListener", e.getAttribute("topic"));
                appFound = true;
            }
            assertNotEquals("topic 不得再指向 ProjectManagerListener",
                    ProjectManagerListener.class.getName(), e.getAttribute("topic"));
        }
        assertTrue("applicationListeners 必须仍注册 IdeAppLifecycleListener", appFound);

        boolean dynFound = false;
        for (int i = 0; i < listeners.getLength(); i++) {
            Element e = (Element) listeners.item(i);
            if (IdePluginLoadListener.class.getName().equals(e.getAttribute("class"))) {
                assertEquals("com.intellij.ide.plugins.DynamicPluginListener", e.getAttribute("topic"));
                dynFound = true;
            }
        }
        assertTrue("applicationListeners 必须注册 IdePluginLoadListener（D-46 装载事件预热）", dynFound);
    }

    @Test
    public void pluginLoadListenerMustOnlyOverridePublicPluginLoaded() {
        assertTrue("IdePluginLoadListener 必须实现 DynamicPluginListener（D-46 第五道幂等触发点）",
                com.intellij.ide.plugins.DynamicPluginListener.class.isAssignableFrom(IdePluginLoadListener.class));
        try {
            IdePluginLoadListener.class.getDeclaredMethod("pluginLoaded",
                    com.intellij.ide.plugins.IdeaPluginDescriptor.class);
        } catch (NoSuchMethodException e) {
            fail("IdePluginLoadListener 必须重写公开回调 pluginLoaded(IdeaPluginDescriptor)");
        }
        for (Method m : IdePluginLoadListener.class.getDeclaredMethods()) {
            String n = m.getName();
            assertFalse("同接口非稳定公开回调一律禁重写（@Experimental 的 beforePluginsLoaded/pluginsLoaded 等＝审核警示源，D-45 教训防回潮）",
                    n.equals("beforePluginsLoaded") || n.equals("pluginsLoaded")
                            || n.equals("beforePluginLoaded") || n.equals("beforePluginUnload")
                            || n.equals("pluginUnloaded") || n.equals("checkUnloadPlugin"));
        }
    }
}
