package com.openxt.uploadsshfile.cliapi;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * bat 模板回归（D-39 反转 D-23：静态内容、随包发布、字节与 dist 基准文件钉死一致；
 * D-29c 语义保留：默认成功不 pause、失败 pause、--keep-open 交 Main 停留、NO_PAUSE 绝对覆盖；
 * 兼 R23 纯 ASCII 红线与 MD5 期望值联动验证——模板改动期望 MD5 自动跟随＝自愈重生成
 * 与 dist 随包一致性校验的共同前提）。
 */
public class BatScriptTemplateTest {

    @Test
    public void pauseSemanticsD29c() {
        String bat = BatScriptTemplate.content();
        // 旧语义"无条件 pause"必须消失
        assertTrue("必须按 --keep-open 探测分流",
                bat.contains("findstr /c:\"--keep-open\""));
        assertTrue("成功(EC=0)不 pause、失败 pause、KEEP 交 Main",
                bat.contains("if not defined NO_PAUSE if not \"%EC%\"==\"0\" if not defined KEEP pause"));
    }

    @Test
    public void templateIsPureAscii() {
        // R23/AC-21＋D-39 用户红线"全英文数字编码"：随包静态模板必须整体纯 ASCII
        String bat = BatScriptTemplate.content();
        for (char c : bat.toCharArray()) {
            assertTrue("bat 模板必须纯 ASCII，遇 U+" + Integer.toHexString(c), c < 128);
        }
    }

    @Test
    public void staticTemplateCarriesNoBakedJavaHome() {
        // D-39：java 定位改三段探测（txt→JAVA_HOME→PATH），模板零实值，全机器字节一致
        String bat = BatScriptTemplate.content();
        assertFalse("禁止回潮：烘焙 java.exe 实值", bat.contains("set \"JAVA=D"));
        assertTrue(bat.contains("set /p JAVA=<\"%~dp0java-home.txt\""));
        assertTrue(bat.contains("if not defined JAVA if defined JAVA_HOME if exist \"%JAVA_HOME%\\bin\\java.exe\" set \"JAVA=%JAVA_HOME%\\bin\\java.exe\""));
        assertTrue(bat.contains("if not defined JAVA set \"JAVA=java\""));
    }

    @Test
    public void distPackagedFileIsByteIdenticalToTemplate() throws Exception {
        // D-39 单点定义：zip 随包 bat（dist/）与 core 内存模板（自愈写盘源）字节恒等——
        // 任何一侧改动忘记同步即在此炸红，防"安装包版≠自愈版"的 MD5 乒乓
        String baseDir = System.getProperty("user.dir");
        java.nio.file.Path dist = Paths.get(baseDir, "dist", "uploadsshfile-cli.bat");
        assertTrue("dist 基准文件缺失: " + dist, Files.exists(dist));
        byte[] expected = BatScriptTemplate.content().getBytes(StandardCharsets.UTF_8);
        byte[] actual = Files.readAllBytes(dist);
        assertTrue("dist 与模板字节不一致（含 CRLF 行尾/内容漂移）", Arrays.equals(expected, actual));
        assertEquals(BatScriptTemplate.expectedMd5(),
                BatScriptTemplate.actualMd5(dist));
    }

    @Test
    public void expectedMd5IsStableHexAndFollowsContent() {
        String a = BatScriptTemplate.expectedMd5();
        String b = BatScriptTemplate.expectedMd5();
        assertNotNull(a);
        assertEquals("静态模板期望 MD5 必须恒定（随包字节判据）", a, b);
        assertTrue(a.matches("[0-9A-F]{32}"));
    }

    @Test
    public void actualMd5NullForMissingFile() {
        assertNull(BatScriptTemplate.actualMd5(null));
        assertNull(BatScriptTemplate.actualMd5(
                java.nio.file.Paths.get("Z:/definitely/not/here-" + System.nanoTime() + ".bat")));
    }

    @Test
    public void javaHomeProbeFileHelpers() {
        // D-39：java-home.txt 单行 CRLF＋纯 ASCII 门槛（非 ASCII 路径＝回落链兜底，不写坏值）
        String line = BatScriptTemplate.javaHomeFileContent("C:\\IDE\\jbr\\bin\\java.exe");
        assertEquals("C:\\IDE\\jbr\\bin\\java.exe\r\n", line);
        assertTrue(BatScriptTemplate.isPureAscii("C:\\Program Files\\JetBrains\\jbr\\bin\\java.exe"));
        assertFalse(BatScriptTemplate.isPureAscii("C:\\日本語\\jbr\\bin\\java.exe"));
        assertFalse(BatScriptTemplate.isPureAscii(null));
        assertTrue(BatScriptTemplate.JAVA_HOME_FILE_NAME.equals("java-home.txt"));
    }

    @Test
    public void configDirDerivationLinePresent() {
        // §5.1/R34：bat 自身位置反推配置目录并以 --config-dir 显式注入（D-28 文法依赖的 bat 侧事实）。
        // 注：本注释不可书写反斜杠+u 序列——javac 对 Unicode 转义的处理先于词法分析、注释内也生效
        String bat = BatScriptTemplate.content();
        assertTrue(bat.contains("set \"CFG=%~dp0..\\..\\uploadsshfile\""));
        assertTrue(bat.contains("--config-dir \"%CFG%\" %*"));
    }

    @Test
    public void fileNameConstants() {
        assertEquals("uploadsshfile-cli.bat", BatScriptTemplate.FILE_NAME);
        assertEquals("java-home.txt", BatScriptTemplate.JAVA_HOME_FILE_NAME);
    }
}
