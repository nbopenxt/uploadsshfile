package com.openxt.uploadsshfile.ui;

import com.openxt.uploadsshfile.i18n.LanguageManager;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.swing.JTextField;
import java.lang.reflect.Field;
import java.nio.file.Paths;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * D-30C 回归：任务 ID "已改且非法"判定（hasInvalidPendingEdit）——
 * 实时红框与关窗确认共用同一谓词，判错即提示缺失/误报。
 * 语义边界＝D-25/FR-05：空值＝兜底预生成分支不算非法；回填存量原值＝未改号放行；
 * 仅"非空、不同于落盘值、字符集不过"才触发提醒。
 */
public class TaskIdPanelTest {

    @BeforeClass
    public static void injectPaths() throws Exception {
        java.nio.file.Path root = Paths.get(System.getProperty("java.io.tmpdir"), "uploadsshfile-test-inject");
        java.nio.file.Files.createDirectories(root);
        com.openxt.uploadsshfile.util.PluginPathManager.initialize(root, root);
    }

    private static final String PERSISTED = "2106573516247949312";

    private final TaskIdPanel.TaskIdHost host = new TaskIdPanel.TaskIdHost() {
        @Override public String persistedId() { return PERSISTED; }
        @Override public boolean persist(String newId) { return true; }
        @Override public boolean isBatch() { return false; }
    };

    private TaskIdPanel panel;
    private JTextField idField;

    @Before
    public void setUp() throws Exception {
        LanguageManager lm = LanguageManager.getInstance();
        lm.forceLanguageNoPersist("en");
        panel = new TaskIdPanel(host, PERSISTED, "C:\\idea\\plugins\\uploadsshfile\\uploadsshfile-cli.bat", lm);
        Field f = TaskIdPanel.class.getDeclaredField("idField");
        f.setAccessible(true);
        idField = (JTextField) f.get(panel);
    }

    @Test
    public void persistedValueUnchangedIsNotInvalid() {
        assertFalse(panel.hasInvalidPendingEdit());
    }

    @Test
    public void emptyIsNotInvalidFallbackBranch() {
        idField.setText("");
        assertFalse("空值＝D-25 预生成兜底分支，不弹非法提醒", panel.hasInvalidPendingEdit());
    }

    @Test
    public void hyphenPendingEditIsInvalid() {
        idField.setText(PERSISTED + "-a");
        assertTrue(panel.hasInvalidPendingEdit());
    }

    @Test
    public void cjkPendingEditIsInvalid() {
        idField.setText(PERSISTED + "测试");
        assertTrue("中文＝非字母数字，必须实时提醒（2026-10-04 用户场景）", panel.hasInvalidPendingEdit());
    }

    @Test
    public void validAlphanumericNewValueIsNotInvalid() {
        idField.setText("9876543210ABCdef");
        assertFalse(panel.hasInvalidPendingEdit());
    }

    @Test
    public void spaceInValueIsInvalid() {
        idField.setText("2106 573");
        assertTrue(panel.hasInvalidPendingEdit());
    }
}
