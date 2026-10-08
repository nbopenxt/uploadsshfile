package com.openxt.uploadsshfile.cliapi;

import com.openxt.uploadsshfile.ExitCodes;
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
        // D-39：java 定位三段探测（txt→JAVA_HOME→PATH），模板零实值，全机器字节一致
        // D-43：三段一律交 :check 子程序裁决，模板内不再有直接 set "JAVA=…" 的赋值
        String bat = BatScriptTemplate.content();
        assertFalse("禁止回潮：烘焙 java.exe 实值", bat.contains("set \"JAVA=D"));
        assertTrue(bat.contains("set /p JHB=<\"%~dp0java-home.txt\""));
        assertTrue("①段候选须交闸门", bat.contains("call :check \"%JHB%\""));
        assertTrue("②段候选须交闸门", bat.contains(
                "if not defined JAVA if defined JAVA_HOME if exist \"%JAVA_HOME%\\bin\\java.exe\" "
                        + "call :check \"%JAVA_HOME%\\bin\\java.exe\""));
        assertTrue("③段候选须交闸门", bat.contains("if not defined JAVA call :check java"));
        assertFalse("禁止回潮：未经版本裁决直接用 JAVA_HOME",
                bat.contains("set \"JAVA=%JAVA_HOME%"));
        assertFalse("禁止回潮：未经版本裁决直接用 PATH java",
                bat.contains("set \"JAVA=java\""));
    }

    @Test
    public void javaCandidatesPassVersionGateD43() {
        // D-43（用户实发 JNI 错 UnsupportedClassVersionError class 65.0/52.0）：
        // 每个候选必须经 java.specification.version 判 21+，不合格跳过而非使用；
        // 全部不合格给可读提示并以独立退出码结束（不再让使用者看 JVM 版本堆栈）
        String bat = BatScriptTemplate.content();
        assertTrue(":check 须先验候选文件存在（IDEA 搬家后 java-home.txt 失效要能继续回落）",
                bat.contains("if not \"%CAND%\"==\"java\" if not exist \"%CAND%\" exit /b 0"));
        assertTrue("版本探测语句（整段交 for 逐行匹配）", bat.contains(
                "for /f \"tokens=1,3\" %%a in ('\"%CAND%\" -XshowSettings:properties -version "
                        + "2^>^&1') do if \"%%a\"==\"java.specification.version\" set \"SPEC=%%b\""));
        assertTrue("探测失败＝拒绝该候选", bat.contains("if not defined SPEC exit /b 0"));
        assertTrue("拒绝 1.x 形态（Java 8 及更早）", bat.contains("if \"%MAJ%\"==\"1\" exit /b 0"));
        assertTrue("拒绝主版本 <21", bat.contains("if %MAJ% LSS 21 exit /b 0"));
        assertTrue("只有过闸门的候选才赋 JAVA", bat.contains("set \"JAVA=%CAND%\""));
        assertTrue("全不合格须给可读提示", bat.contains("no Java 21+ runtime found"));
        assertTrue("提示须回显当前两路取值便于自查", bat.contains("java-home.txt = \"%JHB%\"")
                && bat.contains("JAVA_HOME     = \"%JAVA_HOME%\""));
        assertTrue("退出码与 ExitCodes 单点定义一致",
                bat.contains("exit /b " + ExitCodes.JAVA_RUNTIME));
    }

    @Test
    public void missingProbeFileSaysOnlyRestartIdeaD44() {
        // D-44（2026-10-08 用户三次明令后的最终口径）：缺记录时 CLI 只说"重启 IDEA 才能执行本任务"。
        // 用户逐条否掉的内容用负断言钉死，防我或后人再加回去
        String bat = BatScriptTemplate.content();
        assertTrue("缺失分支必须先于闸门触发", bat.indexOf("if defined JHB goto probehit")
                < bat.indexOf(":probehit"));
        assertTrue("唯一一句提示文案", bat.contains(
                "echo [UploadSSHFile CLI] Please restart IntelliJ IDEA to run this task.\r\n"));
        assertTrue("缺失即以码 12 收手", bat.contains("exit /b 12\r\n:probehit"));
        assertTrue("停留语义沿用 D-29c", bat.contains("if not defined NO_PAUSE pause\r\nexit /b 12"));
        // 空文件与缺失同路（set /p 读不到内容 ⇒ JHB 未定义 ⇒ 走缺失支）
        assertTrue("缺失判定以 JHB 是否取到值为准", bat.contains(
                "if exist \"%~dp0java-home.txt\" set /p JHB=<\"%~dp0java-home.txt\"\r\n"
                        + "if defined JHB goto probehit"));
        String branch = bat.substring(bat.indexOf("if defined JHB goto probehit"), bat.indexOf(":probehit"));
        assertFalse("禁止提文件名（会被当成系统故障）：" + branch, branch.contains("java-home.txt"));
        assertFalse("禁止写插件菜单替代路径：" + branch, branch.toLowerCase().contains("context menu"));
        assertFalse("禁止加\"再执行一次\"尾巴：" + branch, branch.toLowerCase().contains("again"));
        assertFalse("禁止回潮：缺记录仍继续回落", bat.contains("falling back to JAVA_HOME/PATH"));
        assertFalse("禁止回潮：缺记录也打 Fix A/Fix B 长文", bat.indexOf("Fix A:")
                < bat.indexOf(":probehit"));
    }

    @Test
    public void missingProbeFileExitsTwelveWithoutReachingJvmD44() throws Exception {
        // 端到端（真跑 cmd）：无 java-home.txt，即使 JAVA_HOME＝当前测试 JVM（21+）也必须
        // 一行提示＋码 12 收手，不得启动 JVM、不得打 D-43 的三候选拦截长文
        assumeWindowsHasCmd();
        java.nio.file.Path dir = Files.createTempDirectory("uploadsshfile-noprobeset-");
        java.nio.file.Path batFile = dir.resolve(BatScriptTemplate.FILE_NAME);
        Files.write(batFile, BatScriptTemplate.content().getBytes(StandardCharsets.UTF_8));
        assertFalse(Files.exists(dir.resolve(BatScriptTemplate.JAVA_HOME_FILE_NAME)));
        String out = runCmd(batFile,
                "set \"JAVA_HOME=" + System.getProperty("java.home") + "\"",
                "set \"PATH=C:\\Windows\\system32\"");
        assertTrue("应只有那一句提示：" + out, out.contains("Please restart IntelliJ IDEA to run this task."));
        assertTrue("应返回码 12：" + out, out.contains("__EXIT__=12"));
        assertTrue("输出不得出现文件名：" + out, !out.contains("java-home.txt"));
        assertTrue("不得启动 JVM：" + out, !out.contains(BatScriptTemplate.MAIN_CLASS));
        assertTrue("不得叠 D-43 拦截长文：" + out, !out.contains("no Java 21+ runtime found"));
        assertTrue("不得出现 Fix A/Fix B 段：" + out, !out.contains("Fix A:"));
    }

    @Test
    public void emptyProbeFileTreatedAsMissingD44() throws Exception {
        // 空 java-home.txt（set /p 读不到内容）须与缺失同路，否则会掉进"候选全废"的长文分支
        assumeWindowsHasCmd();
        java.nio.file.Path dir = Files.createTempDirectory("uploadsshfile-emptyprobe-");
        java.nio.file.Path batFile = dir.resolve(BatScriptTemplate.FILE_NAME);
        Files.write(batFile, BatScriptTemplate.content().getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve(BatScriptTemplate.JAVA_HOME_FILE_NAME), new byte[0]);
        String out = runCmd(batFile,
                "set \"JAVA_HOME=" + System.getProperty("java.home") + "\"",
                "set \"PATH=C:\\Windows\\system32\"");
        assertTrue("空文件应按缺失处理：" + out, out.contains("Please restart IntelliJ IDEA to run this task."));
        assertTrue("应返回码 12：" + out, out.contains("__EXIT__=12"));
    }

    @Test
    public void forfCommandSetMustNotContainPipe() {
        // 本机 cmd 实测（2026-10-08）：for /f 命令集内嵌 "… 2^>^&1 ^| findstr …" 形态
        // 一律失败（子 cmd 报"系统找不到指定的路径"、SPEC 恒空＝所有候选被误拒、CLI 永远不可用）；
        // 故禁管道写法回潮——管道只允许出现在 bat 顶层的 echo %* | findstr（--keep-open 探测）
        String bat = BatScriptTemplate.content();
        for (String line : bat.split("\r\n")) {
            if (line.trim().startsWith("for /f")) {
                assertFalse("for /f 命令集内不得有脱字符管道：" + line, line.contains("^|"));
            }
        }
        assertTrue("顶层 --keep-open 探测管道保留",
                bat.contains("echo %* | findstr /c:\"--keep-open\""));
    }

    @Test
    public void gateAcceptsCurrentJvmWhenJavaHomePointsAtIt() throws Exception {
        // 端到端最低判据（D-40 教训同族：生成物必须被目标工具真解析/真执行）：
        // 用当前测试 JVM 的 java.home 充当 JAVA_HOME、并留一个失效的 java-home.txt，
        // 期望 bat ①段跳过、②段过闸门并真把命令行递交给 JVM（lib 不存在故必然报找不到主类，
        // 但绝不能落在"no Java 21+"拦截上）
        assumeWindowsHasCmd();
        java.nio.file.Path dir = Files.createTempDirectory("uploadsshfile-gate-");
        java.nio.file.Path batFile = dir.resolve(BatScriptTemplate.FILE_NAME);
        Files.write(batFile, BatScriptTemplate.content().getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve(BatScriptTemplate.JAVA_HOME_FILE_NAME),
                ("D:\\definitely\\not\\here\\jbr\\bin\\java.exe\r\n").getBytes(StandardCharsets.US_ASCII));
        String javaHome = System.getProperty("java.home");
        String javaExe = javaHome.endsWith("bin")
                ? javaHome + java.io.File.separator + "java.exe"
                : Paths.get(javaHome, "bin", "java.exe").toString();
        String out = runCmd(batFile, "set \"JAVA_HOME=" + javaHome + "\"",
                "set \"PATH=C:\\Windows\\system32\"", "set NO_PAUSE=1");
        assertFalse("候选 java 不应被闸门误拒：" + out, out.contains("no Java 21+ runtime found"));
        assertTrue("应真正启动 JVM 并带上主类名（lib 缺失属预期）：" + out,
                out.contains(BatScriptTemplate.MAIN_CLASS) || out.contains(javaExe));
    }

    @Test
    public void gateRejectsAllCandidatesAndExitsTwelve() throws Exception {
        // 三段候选全不可用时必须走拦截分支（退出码 12＋可读提示），不得抛 JVM 版本错误
        assumeWindowsHasCmd();
        java.nio.file.Path dir = Files.createTempDirectory("uploadsshfile-nojavapath-");
        java.nio.file.Path batFile = dir.resolve(BatScriptTemplate.FILE_NAME);
        Files.write(batFile, BatScriptTemplate.content().getBytes(StandardCharsets.UTF_8));
        Files.write(dir.resolve(BatScriptTemplate.JAVA_HOME_FILE_NAME),
                ("D:\\definitely\\not\\here\\jbr\\bin\\java.exe\r\n").getBytes(StandardCharsets.US_ASCII));
        String out = runCmd(batFile, "set \"JAVA=\"", "set \"JAVA_HOME=\"",
                "set \"PATH=C:\\Windows\\system32\"", "set NO_PAUSE=1");
        assertTrue("应给可读拦截提示：" + out, out.contains("no Java 21+ runtime found"));
        assertFalse("不得出现 JVM 版本堆栈：" + out, out.contains("UnsupportedClassVersionError"));
    }

    /** 在 cmd 下执行 bat（bat 会自身退出码回显到输出中），返回合并输出文本 */
    private static String runCmd(java.nio.file.Path batFile, String... sets) throws Exception {
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("@echo off");
        lines.add("setlocal");
        lines.add("set NO_PAUSE=1");
        for (String s : sets) {
            lines.add(s);
        }
        lines.add("call \"" + batFile + "\" 1>&2");
        lines.add("echo __EXIT__=%ERRORLEVEL%");
        lines.add("endlocal");
        java.nio.file.Path runner = batFile.getParent().resolve("runner.cmd");
        Files.write(runner, String.join("\r\n", lines).getBytes(StandardCharsets.US_ASCII));
        Process p = new ProcessBuilder("cmd", "/c", runner.toString())
                .redirectErrorStream(true).start();
        byte[] bytes = p.getInputStream().readAllBytes();
        assertTrue("cmd 应在 60 秒内返回", p.waitFor(60, java.util.concurrent.TimeUnit.SECONDS));
        return new String(bytes, java.nio.charset.Charset.forName("GBK"));
    }

    private static void assumeWindowsHasCmd() {
        org.junit.Assume.assumeTrue("bat 路线仅 Windows 可实测",
                System.getProperty("os.name", "").toLowerCase().contains("windows"));
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
