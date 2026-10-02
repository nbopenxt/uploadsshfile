plugins {
    java
}

// ============================================================================
// cli 模块（1.0.8 / FR-01，SRS V2.6）：命令行入口，独立进程、**不启动 IDEA**。
// 依赖方向（架构 §2）：cli → core，单向；编译期不可见 IntelliJ API。
// M1 段①＝骨架（无源码，编译通过即达意）；Main/ArgumentParser/TaskResolver/
// ConsoleInteraction 与 Main-Class 于 M3 落地（D-23：bat 不随包，由插件首启生成）。
// ============================================================================

group = "com.openxt"

dependencies {
    implementation(project(":core"))
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

// M3：cli 带 Main-Class（bat/java -jar 可起；不打 fat jar，R23）
tasks.named<Jar>("jar") {
    manifest {
        attributes["Main-Class"] = "com.openxt.uploadsshfile.cli.Main"
    }
}
