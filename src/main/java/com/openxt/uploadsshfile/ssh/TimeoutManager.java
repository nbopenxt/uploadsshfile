package com.openxt.uploadsshfile.ssh;

import com.openxt.uploadsshfile.i18n.LanguageManager;

/**
 * 递进式超时管理器
 *
 * <p>根据需求设计：
 * <ul>
 *   <li>第1次: 30秒</li>
 *   <li>第2次: 1分钟</li>
 *   <li>第3次: 2分钟</li>
 *   <li>第4次及以后: 5分钟</li>
 * </ul>
 */
public class TimeoutManager {

    private static final LanguageManager lang = LanguageManager.getInstance();

    /**
     * 递进式超时时间数组（毫秒）
     */
    private static final long[] PROGRESSIVE_TIMEOUTS = {
        30_000L,    // 第1次: 30秒
        60_000L,    // 第2次: 1分钟
        120_000L,   // 第3次: 2分钟
        300_000L    // 第4次及以后: 5分钟
    };

    /**
     * 当前询问次数
     */
    private int promptCount = 0;

    /**
     * 重置计数器
     */
    public void reset() {
        this.promptCount = 0;
    }

    /**
     * 获取当前的超时时间
     *
     * @return 超时时间（毫秒）
     */
    public long getCurrentTimeout() {
        int index = Math.min(promptCount, PROGRESSIVE_TIMEOUTS.length - 1);
        return PROGRESSIVE_TIMEOUTS[index];
    }

    /**
     * 获取下一次的超时时间
     *
     * @return 超时时间（毫秒）
     */
    public long getNextTimeout() {
        int index = Math.min(promptCount + 1, PROGRESSIVE_TIMEOUTS.length - 1);
        return PROGRESSIVE_TIMEOUTS[index];
    }

    /**
     * 增加询问次数（每次弹窗询问后调用）
     */
    public void incrementPromptCount() {
        promptCount++;
    }

    /**
     * 获取当前询问次数
     *
     * @return 询问次数
     */
    public int getPromptCount() {
        return promptCount;
    }

    /**
     * 检查是否应该询问用户
     *
     * @param elapsedMs 已等待的时间（毫秒）
     * @return true 表示应该询问用户
     */
    public boolean shouldPromptUser(long elapsedMs) {
        return elapsedMs >= getCurrentTimeout();
    }

    /**
     * 获取格式化的时间字符串
     *
     * @param milliseconds 毫秒数
     * @return 格式化的时间字符串
     */
    public static String formatDuration(long milliseconds) {
        if (milliseconds < 1000) {
            return lang.get("timeout.format.milliseconds", String.valueOf(milliseconds));
        }

        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return lang.get("timeout.format.seconds", String.valueOf(seconds));
        }

        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;

        if (minutes == 1 && remainingSeconds == 0) {
            return lang.get("timeout.format.minute");
        } else if (remainingSeconds == 0) {
            return lang.get("timeout.format.minutes", String.valueOf(minutes));
        } else {
            return lang.get("timeout.format.compound", String.valueOf(minutes), String.valueOf(remainingSeconds));
        }
    }

    /**
     * 获取超时次数对应的描述
     *
     * @param count 超时次数
     * @return 描述字符串
     */
    public static String getTimeoutDescription(int count) {
        switch (count) {
            case 0:
                return lang.get("timeout.desc.first");
            case 1:
                return lang.get("timeout.desc.second");
            case 2:
                return lang.get("timeout.desc.third");
            default:
                return lang.get("timeout.desc.other", String.valueOf(count + 1));
        }
    }
}
