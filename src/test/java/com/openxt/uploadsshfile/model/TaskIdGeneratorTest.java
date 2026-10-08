package com.openxt.uploadsshfile.model;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * 任务 ID 生成器与 D-25 新值校验单测（FR-04/FR-05 补注）。
 * 口径：生成侧输出恒为纯数字（无 '-'）；isValidNewId 仅用于"新值"，存量不透明放行不在此校验。
 */
public class TaskIdGeneratorTest {

    @Test
    public void nextIdIsPureDigitsAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            String id = TaskIdGenerator.nextId();
            assertTrue("ID 必须为纯数字（不含 '-' 等符号）: " + id, id.matches("\\d+"));
            assertTrue("同进程连续生成必须唯一，重复: " + id, seen.add(id));
        }
    }

    @Test
    public void nextIdIsTimeMonotonic() {
        String first = TaskIdGenerator.nextId();
        String later = TaskIdGenerator.nextId();
        assertTrue("雪花输出应随时间单调不减（首=" + first + "，次=" + later + "）",
                Long.compareUnsigned(Long.parseLong(first), Long.parseLong(later)) <= 0);
    }

    @Test
    public void isValidNewIdAcceptsAlphanumeric() {
        assertTrue(TaskIdGenerator.isValidNewId("1234567890123456789")); // 雪花形态
        assertTrue(TaskIdGenerator.isValidNewId("build20261003"));
        assertTrue(TaskIdGenerator.isValidNewId("ABCdef123"));
    }

    @Test
    public void isValidNewIdRejectsDashAndOtherSymbols() {
        assertFalse("含 '-'（旧 UUID 形态）必须拒绝",
                TaskIdGenerator.isValidNewId("0d185c1e-7245-4b1f-90fc-79e9054e5c20"));
        assertFalse(TaskIdGenerator.isValidNewId("task 1"));   // 空格
        assertFalse(TaskIdGenerator.isValidNewId("task_1"));   // 下划线
        assertFalse(TaskIdGenerator.isValidNewId("任务1"));     // 非 ASCII 字母数字
        assertFalse(TaskIdGenerator.isValidNewId(""));
        assertFalse(TaskIdGenerator.isValidNewId(null));
    }
}
