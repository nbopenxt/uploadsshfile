package com.openxt.uploadsshfile;

import org.junit.Assume;

/**
 * 测试配置验证工具类 - 用于在测试方法执行前检查配置是否完整。
 * 
 * <p><b>使用方式：</b>
 * <pre>{@code
 * @Test
 * public void testSshConnection() {
 *     TestConfigAssume.assumeLinuxServerConfigured();
 *     // 测试代码...
 * }
 * }</pre>
 * 
 * <p><b>工作原理：</b>
 * 如果配置未设置（空字符串），调用 {@link Assume#assumeTrue(String, boolean)} 
 * 会跳过当前测试方法，标记为 "Skipped" 而非 "Failed"。
 */
public final class TestConfigAssume {

    private TestConfigAssume() {
        // 工具类，禁止实例化
    }

    // ================================================================
    // Linux 服务器 A 配置检查
    // ================================================================

    /**
     * 假设 Linux 服务器 A 已配置。
     * 如果未配置，跳过当前测试。
     */
    public static void assumeLinuxServerConfigured() {
        Assume.assumeTrue(
            "Linux 服务器配置未完成，请在 TestConfig.java 中设置 LINUX_HOST、LINUX_USER、LINUX_PASSWORD",
            !TestConfig.LINUX_HOST.isEmpty() 
                && !TestConfig.LINUX_USER.isEmpty() 
                && !TestConfig.LINUX_PASSWORD.isEmpty()
        );
    }

    /**
     * 假设 Linux 服务器远程路径已配置。
     */
    public static void assumeLinuxRemotePathConfigured() {
        assumeLinuxServerConfigured();
        Assume.assumeTrue(
            "Linux 远程路径未配置，请在 TestConfig.java 中设置 LINUX_REMOTE_PATH",
            !TestConfig.LINUX_REMOTE_PATH.isEmpty()
        );
    }

    // ================================================================
    // Windows 服务器 B 配置检查
    // ================================================================

    /**
     * 假设 Windows 服务器 B 已配置。
     */
    public static void assumeWindowsBServerConfigured() {
        Assume.assumeTrue(
            "Windows B 服务器配置未完成，请在 TestConfig.java 中设置 WINDOWS_B_HOST、WINDOWS_B_USER、WINDOWS_B_PASSWORD",
            !TestConfig.WINDOWS_B_HOST.isEmpty() 
                && !TestConfig.WINDOWS_B_USER.isEmpty() 
                && !TestConfig.WINDOWS_B_PASSWORD.isEmpty()
        );
    }

    /**
     * 假设 Windows 服务器 B 远程路径已配置。
     */
    public static void assumeWindowsBRemotePathConfigured() {
        assumeWindowsBServerConfigured();
        Assume.assumeTrue(
            "Windows B 远程路径未配置，请在 TestConfig.java 中设置 WINDOWS_B_REMOTE_PATH",
            !TestConfig.WINDOWS_B_REMOTE_PATH.isEmpty()
        );
    }

    // ================================================================
    // Windows 服务器 C 配置检查
    // ================================================================

    /**
     * 假设 Windows 服务器 C 已配置。
     */
    public static void assumeWindowsCServerConfigured() {
        Assume.assumeTrue(
            "Windows C 服务器配置未完成，请在 TestConfig.java 中设置 WINDOWS_C_HOST、WINDOWS_C_USER、WINDOWS_C_PASSWORD",
            !TestConfig.WINDOWS_C_HOST.isEmpty() 
                && !TestConfig.WINDOWS_C_USER.isEmpty() 
                && !TestConfig.WINDOWS_C_PASSWORD.isEmpty()
        );
    }

    /**
     * 假设 Windows 服务器 C 远程路径已配置。
     */
    public static void assumeWindowsCRemotePathConfigured() {
        assumeWindowsCServerConfigured();
        Assume.assumeTrue(
            "Windows C 远程路径未配置，请在 TestConfig.java 中设置 WINDOWS_C_REMOTE_PATH",
            !TestConfig.WINDOWS_C_REMOTE_PATH.isEmpty()
        );
    }

    // ================================================================
    // AI 服务配置检查
    // ================================================================

    /**
     * 假设千问 AI 服务已配置。
     */
    public static void assumeQwenAiConfigured() {
        Assume.assumeTrue(
            "千问 AI 配置未完成，请在 TestConfig.java 中设置 QWEN_API_KEY",
            !TestConfig.QWEN_API_KEY.isEmpty()
        );
    }

    /**
     * 假设豆包 AI 服务已配置。
     */
    public static void assumeDoubaoAiConfigured() {
        Assume.assumeTrue(
            "豆包 AI 配置未完成，请在 TestConfig.java 中设置 DOUBAO_API_KEY",
            !TestConfig.DOUBAO_API_KEY.isEmpty()
        );
    }

    /**
     * 假设任意一个 AI 服务已配置（千问或豆包）。
     */
    public static void assumeAnyAiConfigured() {
        Assume.assumeTrue(
            "至少需要配置一个 AI 服务（千问或豆包），请在 TestConfig.java 中设置 API Key",
            !TestConfig.QWEN_API_KEY.isEmpty() || !TestConfig.DOUBAO_API_KEY.isEmpty()
        );
    }

    // ================================================================
    // 通用配置检查
    // ================================================================

    /**
     * 假设给定的字符串配置不为空。
     * 
     * @param configName 配置项名称（用于错误提示）
     * @param value 配置值
     */
    public static void assumeConfigNotEmpty(String configName, String value) {
        Assume.assumeTrue(
            configName + " 未配置，请在 TestConfig.java 中设置",
            value != null && !value.isEmpty()
        );
    }

    /**
     * 假设所有必需的 SSH 服务器配置都已设置。
     * 用于需要多个服务器的集成测试。
     */
    public static void assumeAllSshServersConfigured() {
        assumeLinuxServerConfigured();
        assumeWindowsBServerConfigured();
        assumeWindowsCServerConfigured();
    }
}
