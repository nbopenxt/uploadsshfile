import java.util.zip.ZipFile

plugins {
    // java-library：业务三方 jars 以 api(files) 对 plugin/cli 传递可见（编译与打包同源）
    `java-library`
}

// ============================================================================
// core 模块（1.0.8 / FR-01/FR-02，SRS V2.6）：纯业务层，**零 com.intellij 依赖**。
// 依赖门禁 checkCoreNoIde 双校（架构文档 §3.2）：
//   校 A 扫 core.jar 条目命中 com/intellij/ 即 throw；
//   校 B 扫 compileClasspath 含 IntelliJ Platform artifact 即 throw（白名单放行
//   annotations.jar——仅编译期、不打包，D-02）。
// M1 段①（D-01 划界）：文件未搬，sourceSets 从根工程 src 圈定 coreIncludes 包；
// 段② git mv 后改回本模块标准目录、删圈定配置（段③收缩）。
// ============================================================================

group = "com.openxt"

// M1 段③收缩完成（D-01）：源码与文案表已物理迁入 core/src/main（补定①），
// 段①划界用 sourceSets 圈定及其单源清单 coreIncludes 均已删除——目录即边界。

repositories {
    // files(...) 显式路径依赖，无需仓库；声明 mavenCentral 仅为未来迁移预留
}

dependencies {
    // 业务三方依赖（vendored jars，root=plugin 经 api 传递可见——action/ui 直用 gson/jsch 等）
    api(
        files(
            rootProject.file("libs/jsch-0.1.55.jar"),
            rootProject.file("libs/gson-2.13.1.jar"),
            rootProject.file("libs/langchain4j-1.13.0.jar"),
            rootProject.file("libs/langchain4j-core-1.13.0.jar"),
            rootProject.file("libs/langchain4j-open-ai-1.13.0.jar"),
            rootProject.file("libs/langchain4j-google-ai-gemini-1.13.0.jar"),
            rootProject.file("libs/langchain4j-anthropic-1.13.0.jar"),
            rootProject.file("libs/langchain4j-ollama-1.13.0.jar"),
            rootProject.file("libs/langchain4j-http-client-1.13.0.jar"),
            rootProject.file("libs/langchain4j-http-client-jdk-1.13.0.jar"),
        )
    )
    // jetbrains annotations：仅编译期（D-02，不进分发包；从本机 IDEA 安装取单 jar，
    // 不走网络——与整根 compileOnly fileTree 区隔，避免把 IDE 平台 jar 带进 core classpath）
    compileOnly(files(rootProject.file("<YOUR_IDEA_INSTALL_PATH>/lib/annotations.jar")))
    // JUnit 4（core 单测红利，设计 §8；JUnit 5 全工程排除）
    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// ===================== checkCoreNoIde 双校门禁（FR-02/AC-24） =====================

val checkCoreNoIdeJar by tasks.registering {
    description = "校A：core.jar 条目不得含 com/intellij/"
    val jarProvider = tasks.named<Jar>("jar")
    inputs.file(jarProvider.flatMap { it.archiveFile })
    doLast {
        ZipFile(inputs.files.singleFile).use { zip ->
            val hits = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.startsWith("com/intellij/") }
                .map { it.name }.toList()
            if (hits.isNotEmpty()) {
                throw GradleException("checkCoreNoIde[校A] FAILED: core.jar 含 IDE 类 -> $hits")
            }
        }
        println("checkCoreNoIde[校A] OK: core.jar 零 com/intellij/ 条目")
    }
}

val checkCoreNoIdeClasspath by tasks.registering {
    description = "校B：core compileClasspath 不得含 IntelliJ Platform artifact（白名单 annotations.jar）"
    val cpProvider = configurations.compileClasspath
    doLast {
        val bad = cpProvider.get().files.filter { f ->
            val p = f.absolutePath.replace('\\', '/').lowercase()
            (p.contains("/jetbrains/idea/") || p.contains("/intellij")) &&
                !p.endsWith("/annotations.jar")
        }
        if (bad.isNotEmpty()) {
            throw GradleException("checkCoreNoIde[校B] FAILED: core compileClasspath 混入 IDE 构件 -> ${bad.map { it.absolutePath }}")
        }
        println("checkCoreNoIde[校B] OK: compileClasspath 零 IntelliJ Platform artifact")
    }
}

tasks.named("check") {
    dependsOn(checkCoreNoIdeJar, checkCoreNoIdeClasspath)
}
