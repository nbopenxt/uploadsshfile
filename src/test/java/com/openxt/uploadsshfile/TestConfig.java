package com.openxt.uploadsshfile;

/**
 * 测试配置中心 - 统一管理所有测试服务器的连接信息。
 *
 * <p><b>使用说明：</b>
 * <ol>
 *   <li>将下方各服务器配置修改为你的实际测试环境</li>
 *   <li>提交到 GitHub 前，请确认已还原为占位符值（空字符串）</li>
 *   <li>建议执行 {@code git update-index --assume-unchanged} 避免误提交</li>
 * </ol>
 *
 * <p><b>快速恢复配置命令：</b>
 * <pre>{@code
 * git update-index --no-assume-unchanged src/test/java/com/openxt/uploadsshfile/TestConfig.java
 * }</pre>
 */
public final class TestConfig {

    private TestConfig() {
        // 工具类，禁止实例化
    }

    // ================================================================
    //  Linux 服务器 A（用于 SshIntegrationTest / ShellChannelTest / ShellChannelTest3）
    // ================================================================

    /** SSH 主机地址（如 "10.55.3.21"） */
    public static final String LINUX_HOST = "10.55.3.21";

    /** SSH 端口 */
    public static final int LINUX_PORT = 22;

    /** SSH 登录用户名（如 "root"） */
    public static final String LINUX_USER = "";

    /** SSH 登录密码 */
    public static final String LINUX_PASSWORD = "";

    /** 远程测试目录（如 "/home/temp"） */
    public static final String LINUX_REMOTE_PATH = "/home/temp";

    // ================================================================
    //  Windows 服务器 B（用于 ShellChannelTest2）
    // ================================================================

    /** SSH 主机地址（如 "192.168.1.101"） */
    public static final String WINDOWS_B_HOST = "";

    /** SSH 端口 */
    public static final int WINDOWS_B_PORT = 22;

    /** SSH 登录用户名（如 "lenovo"） */
    public static final String WINDOWS_B_USER = "";

    /** SSH 登录密码 */
    public static final String WINDOWS_B_PASSWORD = "";

    /** 远程测试目录 - Windows OpenSSH SFTP 路径格式（如 "/e:/temp"） */
    public static final String WINDOWS_B_REMOTE_PATH = "";

    // ================================================================
    //  Windows 服务器 C（用于 ShellChannelTest3）
    // ================================================================

    /** SSH 主机地址（如 "10.55.3.18"） */
    public static final String WINDOWS_C_HOST = "10.55.3.18";

    /** SSH 端口 */
    public static final int WINDOWS_C_PORT = 22;

    /** SSH 登录用户名（如 "administrator"） */
    public static final String WINDOWS_C_USER = "";

    /** SSH 登录密码 */
    public static final String WINDOWS_C_PASSWORD = "";

    /** 远程测试目录（如 "e:/temp"） */
    public static final String WINDOWS_C_REMOTE_PATH = "e:/temp";

    // ================================================================
    //  AI 服务密钥（用于 testAIBlacklistCheck / testAIResultCheck）
    // ================================================================

    /** 千问 API Base URL */
    public static final String QWEN_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1";

    /** 千问 API Key */
    public static final String QWEN_API_KEY = "";

    /** 千问模型名称 */
    public static final String QWEN_MODEL_NAME = "qwen3.6-flash";

    /** 豆包 API Base URL */
    public static final String DOUBAO_BASE_URL = "https://ark.cn-beijing.volces.com/api/v3";

    /** 豆包 API Key */
    public static final String DOUBAO_API_KEY = "";

    /** 豆包模型名称 */
    public static final String DOUBAO_MODEL_NAME = "doubao-seed-2-0-lite-260428";
}
