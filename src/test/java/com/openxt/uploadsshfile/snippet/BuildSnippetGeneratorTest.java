package com.openxt.uploadsshfile.snippet;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import com.openxt.uploadsshfile.snippet.BuildSnippetGenerator.Tool;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * 片段模板回归（D-27 标题引号 / D-33 --yes / D-38 反转 D-36：两类形态一律不含 --file，
 * 单任务文件＝GUI 关窗快照按任务 ID 读取 / D-42（2026-10-08 用户裁定）：批形态与单任务
 * 形态同构（守卫齐备＋惰性挂载行），且模板注释改走 snippet.comment.* bundle、
 * 按复制当下界面语言呈现——原"批形态保持历史原样"保护口径由用户明令作废）。
 * 硬约束逐条钉死：见类注释与 SRS FR-08/AC-23。
 *
 * 语言态惯例：@Before 一律 forceLanguageNoPersist("en")（仅内存、幂等、与套件顺序无关，
 * 同 KeywordMatcherTest/TaskIdPanelTest 在案范式）；本地化用例内部再自行切 zh。
 */
public class BuildSnippetGeneratorTest {

    private static final String BAT = "C:\\Program Files\\JetBrains\\config\\plugins\\uploadsshfile\\uploadsshfile-cli.bat";
    private static final String ID = "7439182763928576001";

    @Before
    public void forceEnglish() {
        LanguageManager.getInstance().forceLanguageNoPersist("en");
    }

    // ---------- D-27：start 标题必须带引号（裸词会被 start 当文件执行） ----------

    @Test
    public void gradleGroovyStartTitleCarriesQuotes() {
        String single = BuildSnippetGenerator.generate(Tool.GRADLE_GROOVY, BAT, ID, false);
        String batch = BuildSnippetGenerator.generate(Tool.GRADLE_GROOVY, BAT, ID, true);
        assertTrue(single.contains("'\"UploadSSH\"'"));
        assertTrue(batch.contains("'\"UploadSSH\"'"));
        assertFalse("裸标题禁止回退", single.contains("'/c', 'start', '/wait', 'UploadSSH'"));
    }

    @Test
    public void gradleKotlinStartTitleCarriesQuotes() {
        assertTrue(BuildSnippetGenerator.generate(Tool.GRADLE_KOTLIN, BAT, ID, false).contains("\"\\\"UploadSSH\\\"\""));
        assertTrue(BuildSnippetGenerator.generate(Tool.GRADLE_KOTLIN, BAT, ID, true).contains("\"\\\"UploadSSH\\\"\""));
    }

    @Test
    public void mavenStartTitleCarriesQuotes() {
        assertTrue(BuildSnippetGenerator.generate(Tool.MAVEN, BAT, ID, false).contains("<argument>\"UploadSSH\"</argument>"));
    }

    @Test
    public void antQuotedTitleAndOsFamilyWindows() {
        String s = BuildSnippetGenerator.generate(Tool.ANT, BAT, ID, false);
        assertTrue(s.contains("<arg value=\"&quot;UploadSSH&quot;\"/>"));
        assertTrue(s.contains("osfamily=\"Windows\""));
        assertFalse(s.contains("os=\"Windows 10\""));
    }

    // ---------- D-33：--yes 四形态齐备 ----------

    @Test
    public void allToolsCarryYesFlag() {
        for (Tool t : Tool.values()) {
            String s = BuildSnippetGenerator.generate(t, BAT, ID, false);
            assertTrue(t + " 缺 --yes", s.contains("--yes"));
            String b = BuildSnippetGenerator.generate(t, BAT, ID, true);
            assertTrue(t + " 批形态缺 --yes", b.contains("--yes"));
        }
    }

    // ---------- D-38：两类形态一律不含 --file ----------

    @Test
    public void noSnippetFormCarriesFileFlag() {
        for (Tool t : Tool.values()) {
            String single = BuildSnippetGenerator.generate(t, BAT, ID, false);
            String batch = BuildSnippetGenerator.generate(t, BAT, ID, true);
            assertFalse(t + " 单任务片段混入 --file（D-38 作废）", single.contains("--file"));
            assertFalse(t + " 批处理片段混入 --file", batch.contains("--file"));
            assertTrue(t + " 缺任务 ID", single.contains(ID) && batch.contains(ID));
        }
    }

    // ---------- D-42：gradle 单/批同构（守卫齐备），注释按 bundle（en 基准） ----------

    private static final String EN_FILES_SINGLE =
            "D-38: upload list = the GUI snapshot saved for this taskId";
    private static final String EN_FILES_BATCH =
            "Upload list = the per-sub-task file lists saved in this batch task";

    @Test
    public void gradleFormsCarryBuildGuardsAndNotes() {
        for (boolean batch : new boolean[]{false, true}) {
            String g = BuildSnippetGenerator.generate(Tool.GRADLE_GROOVY, BAT, ID, batch);
            assertTrue("构建失败绝不上传（守卫代码）", g.contains("tasks.named('build').get().state.failure == null"));
            assertTrue("ignoreExitValue", g.contains("ignoreExitValue = true"));
            assertTrue("守卫注释走 bundle（en）", g.contains("never upload when the build failed"));
            assertTrue("回改注释走 bundle（en）", g.contains("upload failure never flips the build result"));
            assertFalse("旧产物形态禁止回潮（tasks.named('war')）", g.contains("named('war')"));

            String k = BuildSnippetGenerator.generate(Tool.GRADLE_KOTLIN, BAT, ID, batch);
            assertTrue(k.contains("tasks.named(\"build\").get().state.failure == null"));
            assertTrue(k.contains("isIgnoreExitValue = true"));
            assertTrue("Kotlin 注释同走 bundle（不再随代码走英文硬编码）", k.contains("never upload when the build failed"));
        }
        // 清单来源行＝唯一形态差异：单任务＝关窗快照，批＝子任务已存清单
        assertTrue(BuildSnippetGenerator.generate(Tool.GRADLE_GROOVY, BAT, ID, false).contains(EN_FILES_SINGLE));
        assertFalse(BuildSnippetGenerator.generate(Tool.GRADLE_GROOVY, BAT, ID, false).contains(EN_FILES_BATCH));
        assertTrue(BuildSnippetGenerator.generate(Tool.GRADLE_KOTLIN, BAT, ID, true).contains(EN_FILES_BATCH));
        assertFalse(BuildSnippetGenerator.generate(Tool.GRADLE_KOTLIN, BAT, ID, true).contains(EN_FILES_SINGLE));
    }

    @Test
    public void batchGradleFormsDropLegacyEagerAnchor() {
        // D-42 作废旧批形态：Groovy 不再 build.finalizedBy 直挂、不再无守卫
        String g = BuildSnippetGenerator.generate(Tool.GRADLE_GROOVY, BAT, ID, true);
        assertFalse("旧急切挂载行禁止回潮", g.contains("build.finalizedBy 'uploadSsh'"));
        assertTrue("惰性挂载行与单任务同构", g.contains("tasks.named('build') { finalizedBy 'uploadSsh' }"));
        assertTrue("删行提示走 bundle（en）", g.contains("delete this line to turn off auto-upload"));
        String k = BuildSnippetGenerator.generate(Tool.GRADLE_KOTLIN, BAT, ID, true);
        assertTrue(k.contains("tasks.named(\"build\") { finalizedBy(uploadSsh) }"));
        assertTrue(k.contains("onlyIf"));
    }

    @Test
    public void gradleFormsExplainAttachTaskTiming() {
        // 用户裁定（D-42）：注释须说明"挂在哪个任务之后就在那个任务后执行"（build/war 例举）
        for (boolean batch : new boolean[]{false, true}) {
            String g = BuildSnippetGenerator.generate(Tool.GRADLE_GROOVY, BAT, ID, batch);
            assertTrue("Groovy 缺挂载语义说明", g.contains("attached to build it runs after the whole build, attached to war it runs after the war task"));
            String k = BuildSnippetGenerator.generate(Tool.GRADLE_KOTLIN, BAT, ID, batch);
            assertTrue("Kotlin 缺挂载语义说明", k.contains("attached to build it runs after the whole build, attached to war it runs after the war task"));
        }
    }

    /**
     * D-42 结构同构实证：gradle 单/批两形态仅"清单来源注释行"不同，
     * 其余行（含守卫、挂载、说明注释）必须逐行相等——防两拷贝各自漂移。
     */
    @Test
    public void singleAndBatchDifferOnlyInFileListNote() {
        for (Tool t : new Tool[]{Tool.GRADLE_GROOVY, Tool.GRADLE_KOTLIN}) {
            String single = stripNoteLine(BuildSnippetGenerator.generate(t, BAT, ID, false), EN_FILES_SINGLE);
            String batch = stripNoteLine(BuildSnippetGenerator.generate(t, BAT, ID, true), EN_FILES_BATCH);
            assertTrue(t + " 单/批形态结构漂移：\nsingle=" + single + "\nbatch=" + batch, single.equals(batch));
        }
    }

    private static String stripNoteLine(String snippet, String notePrefix) {
        StringBuilder sb = new StringBuilder();
        for (String line : snippet.split("\n", -1)) {
            if (line.contains(notePrefix)) {
                continue;
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    // ---------- D-42：注释随界面语言切换 ----------

    @Test
    public void snippetCommentsFollowUiLanguage() {
        String en = BuildSnippetGenerator.generate(Tool.GRADLE_GROOVY, BAT, ID, false);
        assertTrue("en 头行为 bundle 英文", en.contains("UploadSSHFile auto-upload hook (taskId=" + ID + ")"));
        assertFalse("en 输出不得混入中文注释", en.contains("构建失败绝不上传"));

        LanguageManager.getInstance().forceLanguageNoPersist("zh");
        String zh = BuildSnippetGenerator.generate(Tool.GRADLE_GROOVY, BAT, ID, false);
        assertTrue("zh 头行本地化（MessageFormat {0} 正常替换）", zh.contains("UploadSSHFile 自动上传钩子（taskId=" + ID + "）"));
        assertTrue("zh 守卫注释", zh.contains("构建失败绝不上传"));
        assertTrue("zh 删行提示", zh.contains("不想要自动上传就删这行"));
        assertTrue("zh 挂载语义说明", zh.contains("挂 war 则 war 打包完成后上传"));
        assertFalse("zh 输出不得混入英文守卫注释", zh.contains("never upload when the build failed"));
        // Maven/Ant 注释同样跟随语言（XML 注释体）
        String mavenZh = BuildSnippetGenerator.generate(Tool.MAVEN, BAT, ID, false);
        assertTrue(mavenZh.contains("未经真实构建验证"));
    }

    // ---------- Maven/Ant ----------

    @Test
    public void mavenAntFormsCarrySnapshotNoteAndNeverReferenceFileVar() {
        String m = BuildSnippetGenerator.generate(Tool.MAVEN, BAT, ID, false);
        assertTrue("Maven 注明 D-38 清单来源（en）", m.contains("no file flag needed"));
        assertTrue("Maven NOT VERIFIED 头（en）", m.contains("NOT VERIFIED"));
        assertFalse("Maven 旧产物变量禁止回潮", m.contains("${project.build.finalName}"));
        String a = BuildSnippetGenerator.generate(Tool.ANT, BAT, ID, false);
        assertTrue("Ant 失败不上传靠 dependsOn 链", a.contains("dependsOn=\"jar\""));
        assertTrue("Ant osfamily 说明注释（en）", a.contains("silently skips on Win11"));
        assertFalse("Ant 旧产物变量禁止回潮", a.contains("${ant.project.name}"));
        assertFalse("Ant 注释不得含 os=Windows 10 字面量（D-30 教训）", a.contains("os=\"Windows 10\""));
    }

    @Test
    public void taskIdRequiredBothForms() {
        try {
            BuildSnippetGenerator.generate(Tool.GRADLE_GROOVY, BAT, null, false);
            assertTrue("应抛 IllegalArgumentException", false);
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void batPathEscapingAndQuotedEmbed() {
        String nasty = "C:\\it's\\batt.bat";
        String g = BuildSnippetGenerator.generate(Tool.GRADLE_GROOVY, nasty, ID, false);
        assertTrue("单引号转义", g.contains("it\\'s"));
        assertTrue("反斜杠双写", g.contains("C:\\\\it"));
        String knasty = "C:\\q\"d\\x";
        String k = BuildSnippetGenerator.generate(Tool.GRADLE_KOTLIN, knasty, ID, false);
        assertTrue("Kotlin 双引号转义", k.contains("q\\\"d"));
        String m = BuildSnippetGenerator.generate(Tool.MAVEN, "E:\\a&b<c>.txt", ID, false);
        assertTrue("XML 实体转义", m.contains("a&amp;b&lt;c&gt;"));
        assertTrue(BatPathInGroovy(g));
    }

    // ---------- D-40：bat 路径参数的"引号内嵌"形态 ----------

    /**
     * D-40（2026-10-08 用户实测报错回贴）：参数必须形如 {@code "\"…\""}。原实现多写了一个
     * 反斜杠（{@code "\\"…\""}），字符串在第二个引号处提前闭合——Gradle 9.4.1 真解析实测：
     * Groovy 报 {@code Unexpected character: '\'}、Kotlin 报 {@code Expecting ')'}。
     * 此处等值钉死合法形态、并负断言封死坏形态；旧断言只用 contains（对两种形态都成立）
     * 是本 bug 长期漏网的原因。
     */
    @Test
    public void gradleFormsEmbedQuotedBatPathArgument() {
        for (Tool t : new Tool[]{Tool.GRADLE_GROOVY, Tool.GRADLE_KOTLIN}) {
            String var = t == Tool.GRADLE_GROOVY ? "${cliBat}" : "$cliBat";
            String expected = "\"\\\"" + var + "\\\"\"";
            String broken = "\"\\\\\"";
            for (boolean batch : new boolean[]{false, true}) {
                String s = BuildSnippetGenerator.generate(t, BAT, ID, batch);
                assertTrue(t + "(batch=" + batch + ") 缺少引号内嵌形态 " + expected, s.contains(expected));
                assertFalse(t + "(batch=" + batch + ") 出现会提前闭合的 " + broken, s.contains(broken));
            }
        }
    }

    private static boolean BatPathInGroovy(String g) {
        return g.contains("\"\\\"${cliBat}\\\"\"");
    }
}
