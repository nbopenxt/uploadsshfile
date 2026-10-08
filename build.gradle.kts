import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
    java
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "com.openxt"
version = "1.0.8"

repositories {
    mavenCentral()
    // 本地 libs 目录作为 flat 仓库（历史保留；当前依赖全部走 files(...) 显式路径）
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

// ============================================================================
// 根项目＝plugin 模块（1.0.8/M1，FR-01）：IDE 层（action/ ui/ startup/ plugin.xml）。
// 业务逻辑全部位于 :core（零 IDE 依赖，checkCoreNoIde 双校门禁）。
// M1 段③收缩完成：13 个业务包与 messages 文案表已物理迁入 core/src/main，
// 本模块仅存 action/ ui/ startup/ 与 META-INF/plugin.xml；段①的对称
// include/exclude 圈定（D-14）与单源清单属性随之删除。
// ============================================================================
dependencies {
    intellijPlatform {
        local(file("<YOUR_IDEA_INSTALL_PATH>"))
        bundledPlugin("com.intellij.java")
    }

    // 业务层（jsch/gson/langchain4j 经其 api(files) 传递可见；打包随插件 lib/）
    implementation(project(":core"))
    // 1.0.8 R23：CLI 只随插件包发布——plugin 代码不引用 cli 类（编译期零耦合），
    // 仅以 runtimeOnly 把 cli.jar 打入标准 zip 的 lib/，供 bat 运行期 classpath 使用
    runtimeOnly(project(":cli"))
    // 注意：原 implementation 的 java-compiler-ant-tasks 已删除（2026-09-22）——
    // 它把 IDE 私有包 com.intellij.ant 打进分发包，触发 Marketplace 校验警告；
    // instrumentCode 已禁用、源码零引用，该 jar 编译期与运行期均不需要。

    // 使用 compileOnly 强制将本地 JAR 注入编译路径
    compileOnly(fileTree("<YOUR_IDEA_INSTALL_PATH>/lib") { include("*.jar") })
    compileOnly(fileTree("<YOUR_IDEA_INSTALL_PATH>/modules") { include("**/*.jar") })

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
//
// 产物分两套，各走各的用途：
// - distributions/<name>-<ver>.zip         标准 buildPlugin 产物（Gradle Zip、deflate、
//   无自定义 extra field）——上传 JetBrains Marketplace 审核用。
//   注意：手写 7z 包（store 模式 + NTFS extra + DOS made-by）在 2026-09 起的
//   新校验管线（IDEA 2026.3 EAP / 263）会被拒：“The plugin archive file
//   cannot be extracted”。市场一律交标准包。
// - distributions/<name>-<ver>-store.zip   7z 存储模式包（-mx0），仅内网离线
//   分发用（与 7z GUI 配置兼容），不得上传市场。
// ============================================

tasks.register("customPackagePlugin") {
    doLast {
        val buildDir = layout.buildDirectory.get().asFile
        val distributionsDir = buildDir.resolve("distributions")   // 正式 zip 在此，禁止清空本目录
        val stagingDir = buildDir.resolve("package-store")
        val pluginDir = stagingDir.resolve(project.name)  // 插件根目录（与插件名同名）
        val libDir = pluginDir.resolve("lib")
        val sourceJar = buildDir.resolve("libs/${project.name}-${version}.jar")
        val sourceLibsDir = file("libs")

        // 1. 创建目标目录（只清自己的暂存区，不碰 distributions）
        stagingDir.deleteRecursively()
        distributionsDir.mkdirs()
        libDir.mkdirs()
        
        // 2. 复制插件主 JAR 到 lib 目录
        if (sourceJar.exists()) {
            Files.copy(sourceJar.toPath(), libDir.resolve(sourceJar.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
            println("Copied: ${project.name}/lib/${sourceJar.name}")
        } else {
            println("Warning: Plugin JAR not found: $sourceJar")
        }

        // 2b. 1.0.8 多模块：core/cli 的 jar 一并入 lib（与标准 zip 同源；
        //     依赖构建顺序由 dependsOn 保证——见本任务尾部）
        listOf(project(":core"), project(":cli")).forEach { sub ->
            val subJarDir = sub.layout.buildDirectory.dir("libs").get().asFile
            subJarDir.listFiles { f -> f.extension == "jar" }?.forEach { jar ->
                Files.copy(jar.toPath(), libDir.resolve(jar.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
                println("Copied: ${project.name}/lib/${jar.name}")
            }
        }
        
        // 3. 复制第三方 JAR 到 lib 目录（排除 java-compiler-ant-tasks，它不需要打包）
        sourceLibsDir.listFiles()
            ?.filter { it.extension == "jar" && !it.name.startsWith("java-compiler-ant-tasks") }
            ?.forEach { jar ->
                Files.copy(jar.toPath(), libDir.resolve(jar.name).toPath(), StandardCopyOption.REPLACE_EXISTING)
                println("Copied: ${project.name}/lib/${jar.name}")
            }

        // 3b. D-39（2026-10-05 反转 D-23）：CLI 启动器 bat 随包——插件根目录静态文件，
        //     字节必须与 core 内存模板 BatScriptTemplate.content() 一致（测试钉死）；
        //     装/升级（含热载）后 CLI 直接可用，无需先点开菜单生成。
        val distBat = file("dist/uploadsshfile-cli.bat")
        if (distBat.exists()) {
            Files.copy(distBat.toPath(), pluginDir.resolve("uploadsshfile-cli.bat").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
            println("Copied: ${project.name}/uploadsshfile-cli.bat (D-39 packaged launcher)")
        } else {
            println("Warning: dist/uploadsshfile-cli.bat not found — store zip will lack the launcher (self-heal still writes it on IDE start)")
        }
        
        // 4. 执行 zip 打包（使用 7z 存储模式，确保与 7z GUI 配置兼容）
        // 命名为 -store.zip，避免覆盖标准 buildPlugin 产物（市场上传件）
        val zipFile = distributionsDir.resolve("${project.name}-${version}-store.zip")
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
            // 工作目录取暂存区父目录、只传相对目录名，保证包内条目为 uploadsshfile/...
            val cmd = if (sevenZip.contains(" ")) "\"$sevenZip\"" else sevenZip
            val process = Runtime.getRuntime().exec(
                arrayOf("cmd", "/c", "$cmd a -tzip -mx0 \"${zipFile}\" \"${project.name}\""),
                null, stagingDir
            )
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                val error = process.errorStream.bufferedReader().readText()
                println("7z warning/error: $error")
            }
        } else {
            // Fallback: 使用 PowerShell Compress-Archive (不压缩模式)
            println("7z not found, using PowerShell Compress-Archive as fallback...")
            val pb = ProcessBuilder("powershell", "-Command",
                "Compress-Archive", "-Path", "${project.name}", "-DestinationPath", "${zipFile}", "-CompressionLevel", "NoCompression", "-Force")
            pb.directory(stagingDir)
            pb.start().waitFor()
        }
        
        println("Package created: ${zipFile.name}")
        println("Size: ${zipFile.length()} bytes")
        
        // 5. 清理中间产物（-base.jar / -instrumented.jar）
        val libsDir = buildDir.resolve("libs")
        libsDir.listFiles()
            ?.filter { it.name.endsWith("-base.jar") || it.name.endsWith("-instrumented.jar") }
            ?.forEach { jar ->
                jar.delete()
                println("Cleaned: ${jar.name}")
            }
    }
}

// 替换 buildPlugin 任务
tasks.named("buildPlugin") {
    dependsOn("jar")
    finalizedBy("customPackagePlugin")
}

// 1.0.8 多模块：store zip 组装前必须已产出 core/cli 的 jar（customPackagePlugin 直读其 build/libs）
tasks.named("customPackagePlugin") {
    dependsOn(":core:jar", ":cli:jar")
}

// ============================================
// D-39（2026-10-05，反转 D-23）：标准 buildPlugin 包也随带 CLI 启动器 bat。
// buildPlugin 的输入＝prepareSandbox 产物目录——2.18.1 实测落点＝项目根
// .intellijPlatform/sandbox/<插件名>/<IDE 运行目录>/plugins/<插件名>/（build/idea-plugin
// 假设系首跑炸出的臆测，按目录特征查找防 IDE 版本号漂移）；
// 本任务依赖 prepareSandbox 完成后把 dist 静态 bat 复制进插件根目录，
// buildPlugin 再依赖本任务——次序确定：组装→补 bat→zip。
// bat 字节与 core 内存模板一致性由 BatScriptTemplateTest 钉死；IDE 侧自愈兜底
// （IdeBootstrap.ensureCliBat）只在缺失/篡改时重写同款字节，正常安装零写盘。
// Marketplace 结构风险备案：zip 内出现 lib/ 之外的根级条目属非惯例（历史 7z 包
// 被拒先例是压缩格式而非多条目）；若市场校验拒收，处置＝bat 仅保留 -store.zip、
// 标准包回退纯 lib 结构（IDE 首启生成旧路），届时另行裁定。
// ============================================
val packageCliBat = tasks.register("packageCliBat") {
    dependsOn("prepareSandbox")
    doLast {
        val distBat = file("dist/uploadsshfile-cli.bat")
        if (!distBat.exists()) {
            throw GradleException("D-39: dist/uploadsshfile-cli.bat missing — refuse to ship package without launcher")
        }
        // 装配目录存在多个同构候选（plugins/、config-test/plugins/、plugins-test/…）——
        // 逐一补 bat，无论 buildPlugin 取哪个都已覆盖（zip 内容以 unzip -l 实测复核）
        val sandboxRoot = layout.projectDirectory.dir(".intellijPlatform/sandbox").asFile
        val pluginDirs = sandboxRoot.walkTopDown().filter {
            it.isDirectory && it.name == project.name && it.parentFile?.name == "plugins"
        }.toList()
        if (pluginDirs.isEmpty()) {
            throw GradleException("D-39: no sandbox plugin dir under $sandboxRoot (prepareSandbox output missing?)")
        }
        pluginDirs.forEach { dir ->
            Files.copy(distBat.toPath(), dir.resolve("uploadsshfile-cli.bat").toPath(),
                StandardCopyOption.REPLACE_EXISTING)
            println("D-39 packaged launcher: ${dir.relativeTo(projectDir)}/uploadsshfile-cli.bat (${distBat.length()} bytes)")
        }
    }
}
tasks.named("buildPlugin") {
    dependsOn(packageCliBat)
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
