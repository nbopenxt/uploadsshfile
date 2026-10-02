package com.openxt.uploadsshfile.snippet;

/**
 * 构建脚本片段生成器（1.0.8 / FR-08，D-15/D-18；设计文档 §6.2 四份模板的落地实现）。
 *
 * core 层纯字符串逻辑：零 IDE 依赖、零剪贴板依赖（写剪贴板与气泡归 plugin UI）。
 * 硬约束（全部内建于模板，缺一项即违反 AC-23）：
 *  - 产物绝对路径一律用构建工具自身变量取（禁手填占位、禁写死文件名）；
 *  - gradle 两形态含 onlyIf { …state.failure == null }（构建失败绝不上传，R31/P-06）；
 *  - 含 ignoreExitValue/isIgnoreExitValue（上传失败不回改构建结论，R33）；
 *  - cmd /c start /wait + 参数数组写法（新控制台窗口，R10/R12/FR-19）；
 *  - 批处理片段不含 --file（R26）；
 *  - Maven/Ant 头部 NOT VERIFIED 注释（RISK-12/D-19）。
 */
public final class BuildSnippetGenerator {

    public enum Tool { GRADLE_GROOVY, GRADLE_KOTLIN, MAVEN, ANT }

    private BuildSnippetGenerator() {
    }

    /**
     * @param cliBatPath 插件 bat 绝对路径（GUI 以 PathManager 实值传入，RISK-08；含空格/中文原样嵌入）
     * @param taskId     任务 ID（调用方保证已落盘——D-15 先落盘再复制）
     * @param isBatch    true＝批处理任务（片段不含 --file）；false＝单任务（war 产物 + --file）
     */
    public static String generate(Tool tool, String cliBatPath, String taskId, boolean isBatch) {
        switch (tool) {
            case GRADLE_GROOVY:
                return gradleGroovy(cliBatPath, taskId, isBatch);
            case GRADLE_KOTLIN:
                return gradleKotlin(cliBatPath, taskId, isBatch);
            case MAVEN:
                return maven(cliBatPath, taskId, isBatch);
            case ANT:
                return ant(cliBatPath, taskId, isBatch);
            default:
                throw new IllegalArgumentException("unknown tool: " + tool);
        }
    }

    private static String gradleGroovy(String cliBat, String taskId, boolean isBatch) {
        StringBuilder sb = new StringBuilder();
        sb.append("// UploadSSHFile auto-upload hook (taskId=").append(taskId).append(")\n");
        sb.append("def cliBat = '").append(escGroovySingle(cliBat)).append("'\n");
        sb.append("tasks.register('uploadSsh', Exec) {\n");
        sb.append("    group = 'uploadsshfile'\n");
        if (isBatch) {
            sb.append("    commandLine 'cmd', '/c', 'start', '/wait', 'UploadSSH', 'cmd', '/c',\n");
            sb.append("                \"\\\"${cliBat}\\\"\", 'run', '").append(taskId).append("'\n");
            sb.append("}\n");
            sb.append("build.finalizedBy 'uploadSsh'                        // 不想要自动上传就删这行\n");
            return sb.toString();
        }
        sb.append("    commandLine 'cmd', '/c', 'start', '/wait', 'UploadSSH', 'cmd', '/c',\n");
        sb.append("                \"\\\"${cliBat}\\\"\", 'run', '").append(taskId).append("'\n");
        sb.append("    doFirst {\n");
        sb.append("        // war 版；jar 工程把下面两处 war 改为 jar\n");
        sb.append("        def artifact = tasks.named('war').get().archiveFile.get().asFile.absolutePath\n");
        sb.append("        commandLine commandLine + ['--file', artifact]\n");
        sb.append("    }\n");
        sb.append("    onlyIf { tasks.named('war').get().state.failure == null }  // 构建失败绝不上传\n");
        sb.append("    ignoreExitValue = true                                     // 上传失败不回改构建结论\n");
        sb.append("}\n");
        sb.append("tasks.named('war') { finalizedBy 'uploadSsh' }                 // 不想要自动上传就删这行\n");
        return sb.toString();
    }

    private static String gradleKotlin(String cliBat, String taskId, boolean isBatch) {
        StringBuilder sb = new StringBuilder();
        sb.append("// UploadSSHFile auto-upload hook (taskId=").append(taskId).append(")\n");
        sb.append("val cliBat = \"").append(escKotlinString(cliBat)).append("\"\n");
        if (isBatch) {
            sb.append("val uploadSsh by tasks.registering(Exec::class) {\n");
            sb.append("    group = \"uploadsshfile\"\n");
            sb.append("    commandLine(\"cmd\", \"/c\", \"start\", \"/wait\", \"UploadSSH\", \"cmd\", \"/c\",\n");
            sb.append("                \"\\\"$cliBat\\\"\", \"run\", \"").append(taskId).append("\")\n");
            sb.append("}\n");
            sb.append("tasks.named(\"build\") { finalizedBy(uploadSsh) }\n");
            return sb.toString();
        }
        sb.append("val uploadSsh by tasks.registering(Exec::class) {\n");
        sb.append("    group = \"uploadsshfile\"\n");
        sb.append("    commandLine(\"cmd\", \"/c\", \"start\", \"/wait\", \"UploadSSH\", \"cmd\", \"/c\",\n");
        sb.append("                \"\\\"$cliBat\\\"\", \"run\", \"").append(taskId).append("\")\n");
        sb.append("    doFirst {\n");
        sb.append("        val artifact = tasks.named<War>(\"war\").get().archiveFile.get().asFile.absolutePath\n");
        sb.append("        commandLine(commandLine + listOf(\"--file\", artifact))\n");
        sb.append("    }\n");
        sb.append("    onlyIf { tasks.named<War>(\"war\").get().state.failure == null }\n");
        sb.append("    isIgnoreExitValue = true\n");
        sb.append("}\n");
        sb.append("tasks.named<War>(\"war\") { finalizedBy(uploadSsh) }\n");
        return sb.toString();
    }

    private static String maven(String cliBat, String taskId, boolean isBatch) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- UploadSSHFile auto-upload hook (taskId=").append(taskId).append(") -->\n");
        sb.append("<!-- NOT VERIFIED: 该写法未经真实构建验证，请检查路径与引号转义后使用（置于 package 之后） -->\n");
        sb.append("<plugin>\n");
        sb.append("  <groupId>org.codehaus.mojo</groupId><artifactId>exec-maven-plugin</artifactId>\n");
        sb.append("  <executions><execution><id>uploadSsh</id><phase>package</phase><goals><goal>exec</goal></goals>\n");
        sb.append("    <configuration>\n");
        sb.append("      <executable>cmd</executable>\n");
        sb.append("      <arguments>\n");
        sb.append("        <argument>/c</argument><argument>start</argument><argument>/wait</argument>\n");
        sb.append("        <argument>UploadSSH</argument><argument>cmd</argument><argument>/c</argument>\n");
        sb.append("        <argument>\"").append(escXml(cliBat)).append("\"</argument>\n");
        sb.append("        <argument>run</argument><argument>").append(escXml(taskId)).append("</argument>\n");
        if (!isBatch) {
            sb.append("        <argument>--file</argument>\n");
            sb.append("        <argument>${project.build.directory}/${project.build.finalName}.war</argument>\n");
        }
        sb.append("      </arguments>\n");
        sb.append("      <successCodes><successCode>0</successCode><successCode>1</successCode><successCode>2</successCode><successCode>3</successCode><successCode>4</successCode><successCode>5</successCode><successCode>6</successCode><successCode>7</successCode><successCode>8</successCode><successCode>9</successCode><successCode>10</successCode><successCode>11</successCode></successCodes>\n");
        sb.append("    </configuration>\n");
        sb.append("  </execution></executions>\n");
        sb.append("</plugin>\n");
        return sb.toString();
    }

    private static String ant(String cliBat, String taskId, boolean isBatch) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!-- UploadSSHFile auto-upload hook (taskId=").append(taskId).append(") -->\n");
        sb.append("<!-- NOT VERIFIED: 该写法未经真实构建验证，请检查路径与引号转义后使用 -->\n");
        sb.append("<target name=\"uploadSsh\" dependsOn=\"jar\">\n");
        sb.append("  <exec executable=\"cmd\" os=\"Windows 10\" spawn=\"false\">\n");
        sb.append("    <arg value=\"/c\"/><arg value=\"start\"/><arg value=\"/wait\"/>\n");
        sb.append("    <arg value=\"UploadSSH\"/><arg value=\"cmd\"/><arg value=\"/c\"/>\n");
        sb.append("    <arg value=\"&quot;").append(escXml(cliBat)).append("&quot;\"/>\n");
        sb.append("    <arg value=\"run\"/><arg value=\"").append(escXml(taskId)).append("\"/>\n");
        if (!isBatch) {
            sb.append("    <arg value=\"--file\"/><arg value=\"${build.dir}/${ant.project.name}.jar\"/>\n");
        }
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
