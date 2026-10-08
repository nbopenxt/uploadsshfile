package com.openxt.uploadsshfile.snippet;

import com.openxt.uploadsshfile.i18n.LanguageManager;

/**
 * 构建脚本片段生成器（1.0.8 / FR-08，D-15/D-18；设计文档 §6.2 四份模板的落地实现）。
 *
 * core 层纯字符串逻辑：零 IDE 依赖、零剪贴板依赖（写剪贴板与气泡归 plugin UI）。
 * 硬约束（全部内建于模板，缺一项即违反 AC-23）：
 *  - **D-38（2026-10-05 用户裁定）单任务片段＝仅带任务 ID，不含 --file**——CLI 按任务 ID
 *    读 UploadDialog 关窗快照（D-37）里的文件清单；推翻 D-36"内嵌复制时选中文件"与更早
 *    FR-08"产物取构建工具变量"两代假设。换文件＝GUI 里重新右键开窗关窗一次，片段无需重新复制；
 *  - **D-42（2026-10-08 用户裁定）批形态与单任务形态对齐**——批 Groovy 形态原"finalizedBy
 *    直挂、无守卫、历史原样"作废，改为与单任务同构：onlyIf 构建失败绝不上传＋
 *    ignoreExitValue 上传失败不回改构建结论＋清单来源说明行＋挂载语义说明行＋
 *    {@code tasks.named('build') { finalizedBy 'uploadSsh' }} 惰性挂载行（Kotlin 同理）。
 *    两形态唯一差异＝任务 ID 与"上传清单来源"注释行（单任务＝关窗快照；批＝子任务已存清单）；
 *  - **D-42 注释本地化**——模板内嵌的说明注释一律取 i18n 键 {@code snippet.comment.*}（13 键，
 *    8 语齐全，由 MessageBundleCompletenessTest 门禁锁定），按复制当下界面语言（LanguageManager
 *    单例）呈现；代码部分不受语言影响。仅 header 键走 MessageFormat（含 {0}＝taskId，
 *    译文禁单引号防吞）；其余键 {@code get(key)} 原文取，无格式化风险；
 *  - 含 ignoreExitValue/isIgnoreExitValue（上传失败不回改构建结论，R33）；
 *  - cmd /c start /wait + 参数数组写法（新控制台窗口，R10/R12/FR-19），标题参数自带
 *    双引号（D-27：裸词会被 start 当作要启动的文件）；
 *  - **D-40 转义陷阱**：Gradle 两形态的 bat 路径参数只能写成 `"\"…\""`（引号转义内嵌）；
 *    误写成 `"\\"…\""` 会让字符串在第二个引号处提前闭合——Gradle 实测报
 *    `Unexpected character: '\'`（Groovy）/ `Expecting ')'`（Kotlin）。原实现即此病，
 *    自 HEAD 起长期潜伏（片段真机跑前无人命中的原因见 D-40）；
 *  - 批处理片段不含 --file（R26；D-38 起两类片段一律不含）；
 *  - D-33：run 参数带 --yes＋责任注释（复制动作发生于 GUI 核对目标语境＝授权链完整）；
 *  - Maven/Ant 头部 NOT VERIFIED 注释（RISK-12/D-19）。
 */
public final class BuildSnippetGenerator {

    public enum Tool { GRADLE_GROOVY, GRADLE_KOTLIN, MAVEN, ANT }

    private BuildSnippetGenerator() {
    }

    /**
     * @param cliBatPath 插件 bat 绝对路径（GUI 以 PathManager 实值传入，RISK-08；含空格/中文原样嵌入）
     * @param taskId     任务 ID（调用方保证已落盘——D-15 先落盘再复制）
     * @param isBatch    true＝批任务模板；false＝单任务模板。D-38：两形态命令行同构
     *                   （run &lt;taskId&gt; --yes，均不含 --file）。D-42 起 gradle 两形态
     *                   结构也同构，差异仅剩清单来源注释行
     */
    public static String generate(Tool tool, String cliBatPath, String taskId, boolean isBatch) {
        if (taskId == null || taskId.isEmpty()) {
            throw new IllegalArgumentException("taskId required");
        }
        switch (tool) {
            case GRADLE_GROOVY:
                return gradleGroovy(cliBatPath, taskId, isBatch);
            case GRADLE_KOTLIN:
                return gradleKotlin(cliBatPath, taskId, isBatch);
            case MAVEN:
                return maven(cliBatPath, taskId);
            case ANT:
                return ant(cliBatPath, taskId);
            default:
                throw new IllegalArgumentException("unknown tool: " + tool);
        }
    }

    // ---------- D-42：注释文本按复制当下界面语言取 bundle（CLI 不走本类，GUI 专属路径） ----------

    private static String msg(String key) {
        return LanguageManager.getInstance().get(key);
    }

    /** header 键含 {0}=taskId，走 MessageFormat；8 语译文一律避免单引号（撇号会开启引用段吞字符，D-41 教训） */
    private static String header(String taskId) {
        return LanguageManager.getInstance().get("snippet.comment.header", taskId);
    }

    // ===================== Gradle Groovy DSL =====================

    private static String gradleGroovy(String cliBat, String taskId, boolean isBatch) {
        StringBuilder sb = new StringBuilder();
        sb.append("// ").append(header(taskId)).append("\n");
        sb.append("def cliBat = '").append(escGroovySingle(cliBat)).append("'\n");
        sb.append("tasks.register('uploadSsh', Exec) {\n");
        sb.append("    group = 'uploadsshfile'\n");
        sb.append("    commandLine 'cmd', '/c', 'start', '/wait', '\"UploadSSH\"', 'cmd', '/c',\n");
        sb.append("                \"\\\"${cliBat}\\\"\", 'run', '").append(taskId)
          .append("', '--yes'   // ").append(msg("snippet.comment.yesSkip")).append("\n");
        sb.append("    onlyIf { tasks.named('build').get().state.failure == null }  // ")
          .append(msg("snippet.comment.guardBuildFailed")).append("\n");
        sb.append("    ignoreExitValue = true                                     // ")
          .append(msg("snippet.comment.ignoreExitValue")).append("\n");
        sb.append("}\n");
        sb.append("// ").append(msg(isBatch
                ? "snippet.comment.filesBatch" : "snippet.comment.filesSingle")).append("\n");
        sb.append("// ").append(msg("snippet.comment.hookAfter")).append("\n");
        sb.append("tasks.named('build') { finalizedBy 'uploadSsh' }                 // ")
          .append(msg("snippet.comment.removeHook")).append("\n");
        return sb.toString();
    }

    // ===================== Gradle Kotlin DSL =====================

    private static String gradleKotlin(String cliBat, String taskId, boolean isBatch) {
        StringBuilder sb = new StringBuilder();
        sb.append("// ").append(header(taskId)).append("\n");
        sb.append("val cliBat = \"").append(escKotlinString(cliBat)).append("\"\n");
        sb.append("val uploadSsh by tasks.registering(Exec::class) {\n");
        sb.append("    group = \"uploadsshfile\"\n");
        sb.append("    commandLine(\"cmd\", \"/c\", \"start\", \"/wait\", \"\\\"UploadSSH\\\"\", \"cmd\", \"/c\",\n");
        sb.append("                \"\\\"$cliBat\\\"\", \"run\", \"").append(taskId)
          .append("\", \"--yes\") // ").append(msg("snippet.comment.yesSkip")).append("\n");
        sb.append("    onlyIf { tasks.named(\"build\").get().state.failure == null } // ")
          .append(msg("snippet.comment.guardBuildFailed")).append("\n");
        sb.append("    isIgnoreExitValue = true // ").append(msg("snippet.comment.ignoreExitValue")).append("\n");
        sb.append("}\n");
        sb.append("// ").append(msg(isBatch
                ? "snippet.comment.filesBatch" : "snippet.comment.filesSingle")).append("\n");
        sb.append("// ").append(msg("snippet.comment.hookAfter")).append("\n");
        sb.append("tasks.named(\"build\") { finalizedBy(uploadSsh) } // ")
          .append(msg("snippet.comment.removeHook")).append("\n");
        return sb.toString();
    }

    // ===================== Maven =====================

    private static String maven(String cliBat, String taskId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- ").append(header(taskId)).append(" -->\n");
        sb.append("<!-- ").append(msg("snippet.comment.mavenNotVerified")).append(" -->\n");
        sb.append("<plugin>\n");
        sb.append("  <groupId>org.codehaus.mojo</groupId><artifactId>exec-maven-plugin</artifactId>\n");
        sb.append("  <executions><execution><id>uploadSsh</id><phase>package</phase><goals><goal>exec</goal></goals>\n");
        sb.append("    <configuration>\n");
        sb.append("      <executable>cmd</executable>\n");
        sb.append("      <arguments>\n");
        sb.append("        <argument>/c</argument><argument>start</argument><argument>/wait</argument>\n");
        sb.append("        <argument>\"UploadSSH\"</argument><argument>cmd</argument><argument>/c</argument>   <!-- ")
          .append(msg("snippet.comment.titleQuote")).append(" -->\n");
        sb.append("        <argument>\"").append(escXml(cliBat)).append("\"</argument>\n");
        sb.append("        <argument>run</argument><argument>").append(escXml(taskId))
          .append("</argument><argument>--yes</argument> <!-- ").append(msg("snippet.comment.xmlRunNote")).append(" -->\n");
        sb.append("      </arguments>\n");
        sb.append("      <successCodes><successCode>0</successCode><successCode>1</successCode><successCode>2</successCode><successCode>3</successCode><successCode>4</successCode><successCode>5</successCode><successCode>6</successCode><successCode>7</successCode><successCode>8</successCode><successCode>9</successCode><successCode>10</successCode><successCode>11</successCode></successCodes>\n");
        sb.append("    </configuration>\n");
        sb.append("  </execution></executions>\n");
        sb.append("</plugin>\n");
        return sb.toString();
    }

    // ===================== Ant =====================

    private static String ant(String cliBat, String taskId) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- ").append(header(taskId)).append(" -->\n");
        sb.append("<!-- ").append(msg("snippet.comment.antNotVerified")).append(" -->\n");
        sb.append("<target name=\"uploadSsh\" dependsOn=\"jar\">\n");
        sb.append("  <exec executable=\"cmd\" osfamily=\"Windows\" spawn=\"false\">   <!-- ")
          .append(msg("snippet.comment.osFamily")).append(" -->\n");
        sb.append("    <arg value=\"/c\"/><arg value=\"start\"/><arg value=\"/wait\"/>\n");
        sb.append("    <arg value=\"&quot;UploadSSH&quot;\"/><arg value=\"cmd\"/><arg value=\"/c\"/>\n");
        sb.append("    <arg value=\"&quot;").append(escXml(cliBat)).append("&quot;\"/>\n");
        sb.append("    <arg value=\"run\"/><arg value=\"").append(escXml(taskId))
          .append("\"/><arg value=\"--yes\"/> <!-- ").append(msg("snippet.comment.xmlRunNote")).append(" -->\n");
        sb.append("  </exec>\n");
        sb.append("</target>\n");
        return sb.toString();
    }

    // —— 最小转义集：bat 路径来自实值，可能含单引号/反斜杠/中文空格 —— //

    private static String escGroovySingle(String p) {
        return p.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static String escKotlinString(String p) {
        return p.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$");
    }

    private static String escXml(String p) {
        return p.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
