import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.15.0"
}

group = "com.openxt"
version = "1.0.2"

repositories {
    mavenCentral()
    // 本地 libs 目录作为 flat 仓库 - 包含下载的 java-compiler-ant-tasks
    flatDir { dirs("libs") }
    intellijPlatform {
        defaultRepositories()
    }
}

configurations {
    // 排除 JUnit 5 以避免与 IntelliJ Platform 冲突
    all {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
    }
    testImplementation {
        exclude(group = "org.junit.jupiter")
        exclude(group = "org.junit.platform")
    }
}

dependencies {
    intellijPlatform {
        local(file("D:/JetBrains/IDEA"))
        bundledPlugin("com.intellij.java")
    }
    
    // SFTP 支持 - 编译时依赖
    implementation(files("libs/jsch-0.1.55.jar"))
    // 强制使用本地 java-compiler-ant-tasks JAR
    implementation(files("libs/java-compiler-ant-tasks-253.31033.145.jar"))
    
    // 使用 compileOnly 强制将本地 JAR 注入编译路径
    compileOnly(fileTree("D:/JetBrains/IDEA/lib") { include("*.jar") })
    compileOnly(fileTree("D:/JetBrains/IDEA/modules") { include("**/*.jar") })
    
    // LangChain4j 核心（支持所有主流大模型）- 本地 jar
    implementation(files("libs/langchain4j-1.13.0.jar"))
    
    // OpenAI compatible protocol (Qwen/DeepSeek/Doubao/Moonshot all use this) - local jar
    implementation(files("libs/langchain4j-open-ai-1.13.0.jar"))
    
    // Google Gemini 原生 - 本地 jar
    implementation(files("libs/langchain4j-google-ai-gemini-1.13.0.jar"))
    
    // Anthropic Claude 原生 - 本地 jar
    implementation(files("libs/langchain4j-anthropic-1.13.0.jar"))
    
    // Ollama 本地模型 - 本地 jar
    implementation(files("libs/langchain4j-ollama-1.13.0.jar"))
    
    // LangChain4j HTTP Client（必需）- 本地 jar
    implementation(files("libs/langchain4j-http-client-1.13.0.jar"))
    
    // LangChain4j JDK HTTP Client（HTTP 实现）- 本地 jar
    implementation(files("libs/langchain4j-http-client-jdk-1.13.0.jar"))
    
    // LangChain4j Core（包含 ChatModel 等核心接口）- 本地 jar
    implementation(files("libs/langchain4j-core-1.13.0.jar"))
    
    // JUnit 4 测试依赖（与 IntelliJ Platform 兼容）
    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    projectName.set("uploadsshfile")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks {
    withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    patchPluginXml {
        sinceBuild.set("253")
        untilBuild.set("254.*")
    }
    
    // 禁用 instrumentCode - 避免访问外网下载 java-compiler-ant-tasks
    named<Task>("instrumentCode") {
        enabled = false
    }
    named<Task>("instrumentTestCode") {
        enabled = false
    }
    
    // 让 build 任务也生成 ZIP 包
    named("build") {
        dependsOn("buildPlugin")
    }
    
    // 配置 JUnit 4 测试
    test {
        // 忽略无效的 JVM 选项
        jvmArgs(
            "-XX:+IgnoreUnrecognizedVMOptions",
            "-Xmx512m",
            "-Dfile.encoding=UTF-8",
            // 测试时启用 SSH 主机白名单
            "-Dssh.host.whitelist.enabled=true"
        )
    }
}

// ============================================
// 自定义打包配置
// ============================================

tasks.register("customPackagePlugin") {
    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val distributionsDir = buildDir.resolve("distributions")
        val pluginDir = distributionsDir.resolve("${project.name}")  // 插件根目录（与插件名同名）
        val libDir = pluginDir.resolve("lib")
        val sourceJar = buildDir.resolve("libs/${project.name}-${version}.jar")
        val sourceLibsDir = file("libs")
        
        // 1. 创建目标目录
        distributionsDir.deleteRecursively()
        distributionsDir.mkdirs()
        pluginDir.mkdirs()
        libDir.mkdirs()
        
        // 2. 复制插件主 JAR 到 lib 目录
        if (sourceJar.exists()) {
            Files.copy(sourceJar.toPath(), libDir.resolve(sourceJar.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            println("Copied: ${project.name}/lib/${sourceJar.name}")
        } else {
            println("Warning: Plugin JAR not found: $sourceJar")
        }
        
        // 3. 复制第三方 JAR 到 lib 目录（排除 java-compiler-ant-tasks，它不需要打包）
        sourceLibsDir.listFiles()
            ?.filter { it.extension == "jar" && !it.name.startsWith("java-compiler-ant-tasks") }
            ?.forEach { jar ->
                Files.copy(jar.toPath(), libDir.resolve(jar.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
                println("Copied: ${project.name}/lib/${jar.name}")
            }
        
        // 4. 执行 zip 打包（使用 7z 存储模式，确保与 7z GUI 配置兼容）
        val zipFile = distributionsDir.resolve("${project.name}-${version}.zip")
        zipFile.delete()

        // 优先使用 7z，fallback 到 PowerShell Compress-Archive
        val sevenZipPaths = listOf(
            "D:/7-Zip/7z.exe",
            "C:/Program Files/7-Zip/7z.exe",
            "C:/Program Files (x86)/7-Zip/7z.exe",
            "7z"
        )
        val sevenZip = sevenZipPaths.find { file(it).exists() || it == "7z" }

        if (sevenZip != null) {
            // 使用 7z 存储模式打包 (-mx0 = 不压缩)
            val cmd = if (sevenZip.contains(" ")) "\"$sevenZip\"" else sevenZip
            val process = Runtime.getRuntime().exec(
                arrayOf("cmd", "/c", "$cmd a -tzip -mx0 \"${zipFile}\" \"${pluginDir}\""),
                null, distributionsDir
            )
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val error = process.errorStream.bufferedReader().readText()
                println("7z warning/error: $error")
            }
        } else {
            // Fallback: 使用 PowerShell Compress-Archive (不压缩模式)
            println("7z not found, using PowerShell Compress-Archive as fallback...")
            val process = Runtime.getRuntime().exec(arrayOf("powershell", "-Command",
                "Compress-Archive", "-Path", "${pluginDir}", "-DestinationPath", "${zipFile}", "-CompressionLevel", "NoCompression", "-Force"))
            process.waitFor()
        }
        
        println("Package created: ${zipFile.name}")
        println("Size: ${zipFile.length()} bytes")
    }
}

// 替换 buildPlugin 任务
tasks.named("buildPlugin") {
    dependsOn("jar")
    finalizedBy("customPackagePlugin")
}

// 运行 Shell Channel 测试任务
tasks.register<JavaExec>("runShellChannelTest") {
    group = "verification"
    description = "运行 ShellChannelTest 测试类"

    // 主类名
    mainClass.set("com.openxt.uploadsshfile.ssh.ShellChannelTest")

    // classpath
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].runtimeClasspath

    // JVM 参数
    jvmArgs(
        "-Xmx512m",
        "-Dfile.encoding=UTF-8"
    )

    // 工作目录
    workingDir = project.projectDir
}

// 运行 Shell Channel 测试任务 2 (Windows 服务器)
tasks.register<JavaExec>("runShellChannelTest2") {
    group = "verification"
    description = "运行 ShellChannelTest2 测试类 (Windows 服务器)"

    // 主类名
    mainClass.set("com.openxt.uploadsshfile.ssh.ShellChannelTest2")

    // classpath
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].runtimeClasspath

    // JVM 参数
    jvmArgs(
        "-Xmx512m",
        "-Dfile.encoding=UTF-8"
    )

    // 工作目录
    workingDir = project.projectDir
}

// 运行 Shell Channel 测试任务 3 (Linux + Windows 上传与命令执行)
tasks.register<JavaExec>("runShellChannelTest3") {
    group = "verification"
    description = "运行 ShellChannelTest3 测试类 (Linux + Windows 上传与命令执行)"

    // 主类名
    mainClass.set("com.openxt.uploadsshfile.ssh.ShellChannelTest3")

    // classpath
    classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].runtimeClasspath

    // JVM 参数
    jvmArgs(
        "-Xmx512m",
        "-Dfile.encoding=UTF-8"
    )

    // 工作目录
    workingDir = project.projectDir
}

// 运行批处理进度模拟测试
tasks.register<JavaExec>("runBatchProgressSimulationTest") {
    group = "verification"
    description = "运行批处理进度模拟测试"

    mainClass.set("com.openxt.uploadsshfile.batch.BatchProgressSimulationTestRunner")

    classpath = sourceSets["test"].runtimeClasspath + sourceSets["main"].runtimeClasspath

    jvmArgs(
        "-Xmx512m",
        "-Dfile.encoding=UTF-8"
    )

    workingDir = project.projectDir
}
