pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven {
            url = uri("https://cache-redirector.jetbrains.com/intellij-dependencies")
        }
    }
}

rootProject.name = "uploadsshfile"

// 1.0.8（FR-01/D-01，M1 段①）：core=纯业务（零 IDE 依赖，AC-24 门禁），
// cli=命令行入口（仅依赖 core，M3 填 Main），根项目＝plugin（IDE 层）。
include(":core")
include(":cli")
