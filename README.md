# UploadSSHFile - IntelliJ IDEA Plugin Marketplace Introduction

---

## Plugin Overview

**Plugin Name**: UploadSSHFile  
**Plugin ID**: `com.openxt.uploadsshfile`  
**Version**: 0.0.1  
**Developer**: Kola  
**Category**: Utility

---

## Build from Source

### Prerequisites
- **JDK 21** (set `JAVA_HOME` or configure in `gradle.properties`)
- **IntelliJ IDEA 2025.3.x** (Build 253+)

### Steps
1. Clone the repository
2. Open with IntelliJ IDEA
3. IDEA will auto-import the Gradle project and download Gradle 9.4.1
4. Run `./gradlew build` to compile

> **Note**: If you need to customize Gradle home or JDK path, edit `gradle.properties`.

---

## Description

UploadSSHFile is an IntelliJ IDEA plugin designed for developers. It integrates SFTP file upload, SSH command execution, AI-powered validation, and multi-layer security protection — enabling a streamlined "right-click upload → auto-validate → auto-deploy" workflow, eliminating tedious terminal operations.

---

## Core Features

### 1. SFTP File Upload

- **Right-click one-click upload**: Right-click any file or directory in the IDEA project view to upload
- **Multi-file/directory support**: Supports uploading single files, multiple files, or entire directories
- **MD5 integrity check**: Automatically verifies local vs. remote file MD5 after upload to ensure reliable transfer; prompts user when verification fails
- **Real-time progress display**: Shows upload progress, transfer speed, completed file count, and other details
- **Secure password storage**: Passwords are encrypted and stored separately in a local encrypted file; the config file itself does not contain plaintext passwords

### 2. SSH Command Execution

- **Command group management**: Create multiple command groups, each associated with a specific server and upload path
- **Auto/manual execution**: Commands can be executed automatically after upload or triggered manually by the user
- **Standalone command execution**: Execute command groups without uploading files
- **Placeholder substitution**: Supports `{localFile}` and `{remoteFile}` placeholders in commands
- **Command timeout handling**: Multi-level timeout reminders (30s → 1min → 2min → every 5min); supports continue-waiting or abort
- **Failure handling**: On command failure, prompts the user whether to continue, avoiding batch operation interruption
- **Execution log**: Real-time command output display with one-click copy-to-clipboard

### 3. Multi-Layer Security

#### 3.1 Blacklist

- **Dangerous command blacklist**: Built-in dangerous command interception for Linux/Windows/macOS (e.g., `rm -rf /`, `mkfs`, `dd if=`, etc.)
- **Dangerous path blacklist**: Built-in protection for critical system directories (e.g., `/etc`, `/boot`, `C:\Windows`, etc.)
- **OS-specific blacklists**: Blacklists managed independently per operating system
- **Customizable**: Add/remove custom blacklist entries
- **One-click restore defaults**: Reset to system-preset default blacklist

#### 3.2 Keyword Rules

- **Failure keyword matching**: Even when exitcode=0, if the output contains failure keywords, the command is marked as failed
- **OS-classified**: Linux and Windows each have default failure keywords, with custom keyword support

#### 3.3 Semantic Pattern Matching

Pattern-matches commands using built-in regex rules to identify common risky operations (e.g., recursive deletion, pipe injection, privilege escalation, etc.)

### 4. AI-Powered Validation

> Requires enabling and configuring a model in AI Settings before taking effect.

- **Pre-execution risk analysis**: AI analyzes command risk level before execution
- **Post-execution result analysis**: AI analyzes command output to identify potential errors
- **AI blacklist check**: Uses AI to determine if a command is a dangerous operation
- **Multi-model support**: 8 AI models — OpenAI, Gemini, Claude, Ollama (local), Qwen, DeepSeek, Doubao, Moonshot
- **Independent model configuration**: Each model saves its own API Key, Base URL, and Model Name
- **Three-tier risk classification**: AI analysis results are graded as Risk, Warning, or Caution
- **Advisory only**: AI validation is advisory; the final decision to proceed always rests with the user

### 5. Multi-Server Configuration

- **Server CRUD**: Add, edit, save, and delete multiple server configurations
- **Path CRUD**: Each server supports multiple upload paths, with OS association
- **OS tagging**: 9 operating systems — Linux, Ubuntu, Debian, CentOS, Rocky, Alpine, RHEL, Windows, macOS
- **Connection testing**: Test SFTP connection instantly during configuration
- **Cascading deletion**: Deleting a server automatically cleans up associated paths and command groups
- **Form validation**: Unique name validation and required field checks

### 6. Last Selection Memory

- **Smart memory**: Automatically remembers the last successful server and command group after each upload or command execution
- **Auto-restore**: Next time the upload dialog opens, the last successful server and command group are auto-selected
- **Graceful fallback**: If the previously remembered config has been deleted, falls back to the first available option

### 7. Internationalization

- **8 languages**: English, 中文, Deutsch, Français, Español, 日本語, 한국어, العربية
- **Parameterized text**: Supports `{0}`, `{1}` placeholder substitution
- **Persistent language setting**: Language preference saved in config file, takes effect after restart

### 8. Unified Configuration

- **Single config file**: All settings (servers, paths, command groups, blacklists, AI, language, etc.) stored in a single `plugin-config.json`
- **Uninstall-safe**: Config directory is independent of the plugin directory; settings are preserved after uninstall
- **Auto-repair**: Automatically fills in any missing default configuration items on startup

---

## System Requirements

| Item | Requirement |
|:---|:---|
| IDE | IntelliJ IDEA 2025.3.x (Build 253+) |
| JDK | OpenJDK 17 |
| OS | Windows 10/11, macOS, Linux |

---

## Use Cases

### Scenario 1: Web Project Deployment
```
Development done → Right-click upload → AI analyzes command risk → Auto-execute deploy script → Deployed
```

### Scenario 2: Config File Update
```
Modify config → One-click upload → MD5 verification → Sync complete
```

### Scenario 3: Batch File Sync
```
Select multiple files/directories → One-click upload → View transfer log → Sync complete
```

### Scenario 4: Remote Command Execution
```
Select command group → Independent execution → Real-time output → Operation complete
```

---

## Installation

Search for **"UploadSSHFile"** in the IntelliJ IDEA Plugin Marketplace and click Install.

---

## Quick Start

1. Right-click a file → **Upload SSH File**
2. Fill in server info (host, port, username, password) on first use
3. Click **Test Connection** to verify
4. Add upload target paths for the server
5. Select server and path, then click **Upload**
6. *(Optional)* Create command groups via **SSH Command Config**
7. *(Optional)* Configure AI models via **AI Settings** for smart validation

---

## Context Menu Structure

Right-click a project file or directory to see the **Upload SSH File** menu with the following sub-items:

| Menu Item | Function |
|:---|:---|
| Upload File | Open the upload dialog; select server/path/command group and upload |
| Manage Server Config... | Manage server and upload path configurations |
| SSH Command Config... | Manage command groups, blacklists, and keyword rules |
| AI Settings... | Configure AI model parameters (API Key, model selection, etc.) |

---

## Privacy & Security

- **Password storage**: Server passwords are encrypted and stored separately in a local encrypted file; the config file does not contain plaintext passwords
- **Data security**: All configuration data is stored locally; nothing is transmitted to any third-party server
- **Network communication**: Only establishes SFTP/SSH connections to user-configured remote servers

---

## Disclaimer

**Important**: Please read the following disclaimer carefully before using this plugin:

1. **Free of charge**: This plugin is completely free to use. No fees are charged.
2. **As-is provision**: This plugin is provided "as is", without any express or implied warranties, including but not limited to warranties of merchantability, fitness for a particular purpose, or non-infringement.
3. **Use at your own risk**: Any operations performed using this plugin (including but not limited to file uploads, command execution, server configuration changes) are at the user's own risk.
4. **Data loss**: Under no circumstances shall the plugin developer be liable for any damages or data loss caused by use or inability to use this plugin, including but not limited to: upload failures, data loss or corruption, unexpected command execution results, any damage or data loss to remote servers, transmission interruptions due to network issues.
5. **Third-party services**: This plugin only provides a tool for communicating with remote servers and is not responsible for the security, availability, or reliability of remote servers. Users should ensure they only connect to trusted servers.
6. **User responsibility**: Ensure correct server info (IP, port, username, password); carefully review commands before execution; understand and accept the potential risks of remote command execution; verify in a test environment before using in production.
7. **Disclaimer updates**: The plugin developer reserves the right to update this disclaimer at any time without prior notice.

*By using this plugin, you confirm that you have read, understood, and agree to the above disclaimer terms.*

---

## FAQ

### Q1: Connection failed. What should I do?

Please check:
- Confirm the server IP/hostname is correct
- Confirm the SSH port (default 22) is open
- Confirm the username and password are correct
- Check local network and firewall settings

### Q2: MD5 verification failed after upload. What should I do?

Data may have been corrupted during network transmission. Try:
- Re-upload the file
- Check server disk space
- Verify file permissions on the server side

### Q3: Command execution timed out. What should I do?

When a timeout occurs, the system shows a dialog with the current command and elapsed time. You can choose:
- **Continue waiting**: If the command is still running (e.g., compilation, download, etc.)
- **Abort**: If you suspect the command has hung

### Q4: Will AI validation block my normal commands?

AI validation is an advisory mechanism and will not forcefully block commands. Before execution, a dialog displays the risk level (Risk / Warning / Caution) to alert you of potential dangers, and you decide whether to proceed.

### Q5: Will my configuration survive a plugin reinstall?

The config directory is independent of the plugin directory. Settings are preserved after uninstalling, and all configurations are automatically restored upon reinstall.

---

## Contact

For questions or suggestions, please leave a comment on the IDEA Plugin Marketplace or submit an Issue.

---

*Last Updated: 2026-05-25*

---

---

# UploadSSHFile - IntelliJ IDEA 插件市场介绍文档

---

## 插件概览

**插件名称**：UploadSSHFile  
**插件 ID**：`com.openxt.uploadsshfile`  
**插件版本**：0.0.1  
**开发者**：Kola  
**插件类型**：实用工具 (Utility)

---

## 从源码构建

### 前置条件
- **JDK 21**（设置 `JAVA_HOME` 或在 `gradle.properties` 中配置）
- **IntelliJ IDEA 2025.3.x**（Build 253+）

### 构建步骤
1. Clone 仓库
2. 使用 IntelliJ IDEA 打开项目
3. IDEA 将自动导入 Gradle 项目并下载 Gradle 9.4.1
4. 执行 `./gradlew build` 编译

> **注意**：如需自定义 Gradle 主目录或 JDK 路径，请编辑 `gradle.properties`。

---

## 插件简介

UploadSSHFile 是一款专为开发者设计的 IntelliJ IDEA 插件，通过集成 SFTP 文件上传、SSH 命令执行、AI 智能校验、多层安全防护等功能，帮助开发者实现"右键上传 → 自动校验 → 自动部署"的一站式工作流，告别繁琐的终端操作。

---

## 核心功能

### 1. SFTP 文件上传

- **右键一键上传**：在 IDEA 项目视图中，右键点击文件或目录即可上传
- **多文件/目录支持**：支持单个文件、多个文件、整个目录的上传
- **MD5 完整性校验**：上传后自动校验本地与远程文件 MD5，确保传输可靠，校验失败时询问用户
- **实时进度显示**：显示上传进度、传输速度、已完成文件数等详细信息
- **密码安全存储**：密码经加密后独立存储于本地加密文件，配置文件本身不保存密码明文

### 2. SSH 命令执行

- **命令组管理**：支持创建多个命令组，可关联到指定服务器和上传路径
- **自动/手动执行**：上传完成后支持自动执行命令组，或由用户手动选择执行
- **独立命令执行**：支持不传文件、仅执行命令组
- **占位符替换**：命令中支持 `{localFile}` 和 `{remoteFile}` 占位符
- **命令超时处理**：多级超时提醒（30s → 1min → 2min → 每 5min），支持继续等待或终止
- **失败处理**：命令执行失败时弹窗询问用户是否继续，避免批量操作中断
- **执行日志**：实时显示命令执行输出，支持复制日志到剪贴板

### 3. 多层安全防护

#### 3.1 黑名单机制

- **危险命令黑名单**：内置 Linux/Windows/macOS 各系统的危险命令拦截（如 `rm -rf /`、`mkfs`、`dd if=` 等）
- **危险路径黑名单**：内置系统关键目录保护（如 `/etc`、`/boot`、`C:\Windows` 等）
- **按 OS 区分**：黑名单按操作系统独立管理
- **用户自定义**：支持添加/删除自定义黑名单项
- **一键恢复默认**：支持重置为系统预设默认黑名单

#### 3.2 关键词规则

- **失败关键词匹配**：即使命令 exitcode=0，若输出包含失败关键词也判定为失败
- **按 OS 分类**：Linux 和 Windows 各有默认失败关键词，支持自定义补充

#### 3.3 语义模式匹配

基于内置正则规则对命令进行模式匹配，识别常见风险操作（如递归删除、管道注入、权限提升等）

### 4. AI 智能校验

> 需在 AI Settings 中启用并配置模型后生效。

- **执行前风险分析**：在命令执行前，由 AI 分析命令风险等级
- **执行结果分析**：命令执行后，AI 分析输出结果识别潜在错误
- **AI 黑名单检查**：使用 AI 判断命令是否属于危险操作
- **多模型支持**：支持 8 种 AI 模型 — OpenAI、Gemini、Claude、Ollama（本地）、Qwen（通义千问）、DeepSeek、Doubao（豆包）、Moonshot
- **独立模型配置**：每种模型独立保存 API Key、Base URL、Model Name
- **风险分级**：AI 分析结果分为 Risk（风险）、Warning（警告）、Caution（注意）三级
- **建议执行**：AI 校验为建议性质，由用户最终决定是否继续

### 5. 多服务器配置

- **服务器 CRUD**：添加、编辑、保存、删除多个服务器配置
- **路径 CRUD**：每个服务器可配置多个上传路径，支持关联到特定操作系统
- **操作系统标记**：支持 Linux/Ubuntu/Debian/CentOS/Rocky/Alpine/RHEL/Windows/macOS 共 9 种操作系统
- **连接测试**：配置时可即时测试 SFTP 连接
- **关联删除**：删除服务器时自动清理关联路径和命令组
- **表单校验**：名称唯一性、必填项校验

### 6. 记忆上次选择

- **智能记忆**：每次上传或命令执行成功后，自动记住当前服务器和命令组
- **自动恢复**：下次打开上传对话框时，自动选中上次成功的服务器和命令组
- **容错降级**：若上次的配置已被删除，自动回退到第一个可用选项

### 7. 多语言国际化

- **8 种语言**：English、中文、Deutsch、Français、Español、日本語、한국어、العربية
- **参数化文本**：支持 `{0}`、`{1}` 等占位符替换
- **语言持久化**：语言设置保存在配置文件中，重启后生效

### 8. 统一配置管理

- **单一配置文件**：所有配置（服务器、路径、命令组、黑名单、AI、语言等）统一存储在 `plugin-config.json`
- **卸载安全**：配置目录独立于插件目录，卸载后配置保留
- **自动修复**：启动时自动补齐可能缺失的默认配置项

---

## 系统要求

| 项目 | 要求 |
|:---|:---|
| IDE | IntelliJ IDEA 2025.3.x (Build 253+) |
| JDK | OpenJDK 17 |
| 操作系统 | Windows 10/11、macOS、Linux |

---

## 使用场景

### 场景一：Web 项目部署
```
开发完成 → 右键上传 → AI 分析命令风险 → 自动执行部署脚本 → 部署完成
```

### 场景二：配置文件更新
```
修改配置 → 一键上传 → MD5 校验确认 → 完成同步
```

### 场景三：批量文件同步
```
选择多个文件/目录 → 一键上传 → 查看传输日志 → 完成同步
```

### 场景四：远程命令执行
```
选择命令组 → 独立执行 → 实时查看输出 → 完成运维操作
```

---

## 安装指南

在 IntelliJ IDEA 插件市场搜索 **"UploadSSHFile"**，点击安装即可。

---

## 快速开始

1. 右键点击文件 → **Upload SSH File**
2. 首次使用时，按提示填写服务器信息（主机、端口、用户名、密码）
3. 点击"测试连接"确保配置正确
4. 为服务器添加上传目标路径
5. 选择服务器和路径后点击"Upload"
6. *(可选)* 通过 **SSH Command Config** 菜单创建命令组
7. *(可选)* 通过 **AI Settings** 菜单配置 AI 模型，为命令执行加持智能校验

---

## 光标菜单入口

右键项目文件或目录后，可见一级菜单 **Upload SSH File**，包含以下子菜单：

| 菜单项 | 功能 |
|:---|:---|
| Upload File | 打开上传对话框，选择服务器/路径/命令组后上传 |
| Manage Server Config... | 管理服务器和上传路径配置 |
| SSH Command Config... | 管理命令组、黑名单、关键词规则 |
| AI Settings... | 配置 AI 模型参数（API Key、模型选型等） |

---

## 隐私与安全

- **密码存储**：服务器密码经加密后独立存储于本地加密文件，配置文件中不保存密码明文
- **数据安全**：所有配置信息存储在本地，不会传输到任何第三方服务器
- **网络通信**：仅与用户配置的远程服务器建立 SFTP/SSH 连接

---

## 免责说明

**重要提示**：在使用本插件之前，请仔细阅读以下免责条款：

1. **免费使用**：本插件完全免费提供，不收取任何费用。
2. **按现状提供**：本插件按"现状"提供，不提供任何明示或暗示的保证，包括但不限于对适销性、特定用途适用性、非侵权性的保证。
3. **使用风险**：用户使用本插件进行的任何操作（包括但不限于文件上传、命令执行、服务器配置修改等）的风险由用户自行承担。
4. **数据损失**：在任何情况下，因使用或无法使用本插件（包括文件上传失败、数据丢失或损坏、命令执行结果不符合预期、对远程服务器造成的任何损害或数据损失、因网络问题导致的传输中断等），本插件开发者均不承担任何责任。
5. **第三方服务**：本插件仅提供与远程服务器通信的工具，不对远程服务器的安全性、可用性或可靠性负责。用户应确保只连接可信任的服务器。
6. **用户责任**：用户应确保输入的服务器信息正确无误；谨慎审查将要执行的命令内容；了解并承担执行远程命令的潜在风险；建议在正式环境使用前，先在测试环境验证。
7. **免责声明更新**：本插件开发者保留随时更新本免责说明的权利，恕不另行通知。

**使用本插件即表示您已阅读、理解并同意接受上述免责条款。**

---

## 常见问题

### Q：连接服务器失败怎么办？

请检查以下内容：
- 确认服务器 IP/主机名正确
- 确认 SSH 端口（默认 22）开放
- 确认用户名和密码正确
- 检查本地网络和防火墙设置

### Q：上传后 MD5 校验失败怎么办？

可能是网络传输过程中数据损坏。请尝试：
- 重新上传文件
- 检查服务器磁盘空间
- 确认服务器端文件权限

### Q：命令执行超时怎么办？

超时后系统会弹窗提示当前执行的命令和已过时长，可选择：
- **继续等待**：如果命令仍在执行中（如编译、下载等耗时操作）
- **终止命令**：如果怀疑命令已卡死

### Q：AI 校验是否会拦截我的正常命令？

AI 校验为提醒机制，不会强制拦截。执行命令前会弹窗展示风险等级（Risk / Warning / Caution），提醒用户潜在风险，由用户选择是否继续执行。

### Q：插件重新安装后配置还在吗？

配置目录独立于插件目录，卸载后配置保留。重新安装后所有配置自动恢复。

---

## 联系我们

如有问题或建议，欢迎通过 IDEA 插件市场留言或提交 Issue。

---
