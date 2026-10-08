package com.openxt.uploadsshfile.i18n;

import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * D-41（2026-10-08）：8 语言包完整性门禁——把 SRS R46"8 语言齐全"从文档口径变成构建门禁。
 *
 * <p>背景：D-41 前 de/fr/es/ja/ko/ar 六个包各缺 151 个键（运行期静默回落英文），
 * 且这类缺口此前既无断言也无人发现，正是 D-40 那条教训（"没有断言的约束等于没有约束"）的
 * 同族。此处用<b>与 {@code LanguageManager} 完全相同的加载路径</b>（classpath +
 * UTF-8 {@code InputStreamReader}）解析，故断言的是"产品真正读到的值"，而非原始文本
 * ——BOM、{@code \n} 转义、续行等解析细节一并被覆盖。
 *
 * <p>三条硬约束：① 各包键集与基准包<b>双向相等</b>（缺键＝回落英文；多键＝死键）；
 * ② 占位符 {@code {n}} 多重集逐键一致（防漏译/多译占位符导致 {@code MessageFormat} 抛错）；
 * ③ 任何键都不得为空值（含无 {@code =} 的散行被解析成"空值垃圾键"——如带 BOM 的首行注释）。
 */
public class MessageBundleCompletenessTest {

    private static final String BASE = "/messages/messages.properties";
    private static final String[] BUNDLES = {
            "/messages/messages_zh.properties",
            "/messages/messages_de.properties",
            "/messages/messages_fr.properties",
            "/messages/messages_es.properties",
            "/messages/messages_ja.properties",
            "/messages/messages_ko.properties",
            "/messages/messages_ar.properties",
    };

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[0-9]}");

    private static Properties load(String path) throws Exception {
        Properties p = new Properties();
        try (InputStream is = MessageBundleCompletenessTest.class.getResourceAsStream(path)) {
            assertNotNull("找不到语言包资源：" + path, is);
            p.load(new InputStreamReader(is, StandardCharsets.UTF_8));
        }
        return p;
    }

    /** ① + ②：键集双向相等 ＋ 占位符逐键一致。 */
    @Test
    public void allBundlesHaveIdenticalKeySetAndPlaceholders() throws Exception {
        Properties base = load(BASE);
        TreeSet<String> baseKeys = new TreeSet<>(base.stringPropertyNames());
        List<String> problems = new ArrayList<>();

        for (String path : BUNDLES) {
            Properties p = load(path);
            TreeSet<String> keys = new TreeSet<>(p.stringPropertyNames());

            for (String k : baseKeys) {
                if (!keys.contains(k)) {
                    problems.add(path + " 缺键（运行期会回落英文）：" + k);
                } else if (!placeholders(base.getProperty(k)).equals(placeholders(p.getProperty(k)))) {
                    problems.add(path + " 占位符不符 " + k + "：基准 " + placeholders(base.getProperty(k))
                            + " ≠ 实际 " + placeholders(p.getProperty(k)));
                }
            }
            TreeSet<String> extra = new TreeSet<>(keys);
            extra.removeAll(baseKeys);
            if (!extra.isEmpty()) {
                problems.add(path + " 存在基准包没有的键（疑似死键）：" + extra);
            }
        }
        assertTrue(describe(problems), problems.isEmpty());
    }

    /** ③：任何键都不得为空值。 */
    @Test
    public void noEmptyValuesInAnyBundle() throws Exception {
        List<String> problems = new ArrayList<>();
        for (String path : all()) {
            Properties p = load(path);
            for (String k : p.stringPropertyNames()) {
                String v = p.getProperty(k);
                if (v == null || v.trim().isEmpty()) {
                    problems.add(path + " 空值键：" + k);
                }
            }
        }
        assertTrue(describe(problems), problems.isEmpty());
    }

    private static List<String> all() {
        List<String> out = new ArrayList<>();
        out.add(BASE);
        Collections.addAll(out, BUNDLES);
        return out;
    }

    private static List<String> placeholders(String value) {
        List<String> out = new ArrayList<>();
        if (value == null) {
            return out;
        }
        Matcher m = PLACEHOLDER.matcher(value);
        while (m.find()) {
            out.add(m.group());
        }
        Collections.sort(out);
        return out;
    }

    private static String describe(List<String> problems) {
        int shown = Math.min(20, problems.size());
        return problems.size() + " 处语言包不完整（最多列前 " + shown + " 项）：\n"
                + String.join("\n", problems.subList(0, shown));
    }
}
