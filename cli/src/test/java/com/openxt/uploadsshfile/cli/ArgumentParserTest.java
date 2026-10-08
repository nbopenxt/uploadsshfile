package com.openxt.uploadsshfile.cli;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * CLI 文法回归（D-28，2026-10-04 CLI 端到端实测发现"bat 前置注入 --config-dir 被
 * args[0] 硬检拒绝"后首次建立测试——此前 ArgumentParser 零测试，M3 缺口）。
 *
 * 文法权威＝SRS FR-10＋本类注释：run 是首个**位置参数**、旗标可任意位置
 * （bat 模板按此语义在 %* 之前注入 --config-dir）。
 */
public class ArgumentParserTest {

    @Test
    public void plainRunForm() {
        ArgumentParser.Parsed p = ArgumentParser.parse(
                new String[]{"run", "7439182763928576001"});
        assertNull(p.error);
        assertEquals("7439182763928576001", p.taskId);
    }

    /** D-28 核心场景：bat 注入形态——--config-dir 在 run 之前（D-38：bat 行不再带 --file） */
    @Test
    public void batPrefixConfigDirIsAccepted() {
        ArgumentParser.Parsed p = ArgumentParser.parse(new String[]{
                "--config-dir", "C:\\Users\\Public\\Documents\\.IntelliJIdea\\config\\uploadsshfile",
                "run", "2106573516247949312",
                "--yes"});
        assertNull(p.error);
        assertEquals("2106573516247949312", p.taskId);
        assertEquals("C:\\Users\\Public\\Documents\\.IntelliJIdea\\config\\uploadsshfile", p.configDir);
        assertTrue(p.assumeYes);
    }

    @Test
    public void flagsInAnyOrderAfterRun() {
        ArgumentParser.Parsed p = ArgumentParser.parse(new String[]{
                "run", "--verbose", "123", "--config-dir", "C:\\cfg"});
        assertNull(p.error);
        assertEquals("123", p.taskId);
        assertTrue(p.verbose);
        assertEquals("C:\\cfg", p.configDir);
    }

    @Test
    public void missingRunRejected() {
        ArgumentParser.Parsed p = ArgumentParser.parse(
                new String[]{"--config-dir", "C:\\cfg", "123"});
        assertNotNull(p.error);
        assertTrue(p.error, p.error.contains("run"));
    }

    @Test
    public void wrongFirstPositionalRejected() {
        ArgumentParser.Parsed p = ArgumentParser.parse(new String[]{"exec", "123"});
        assertNotNull(p.error);
        assertTrue(p.error, p.error.contains("'run'"));
    }

    @Test
    public void extraPositionalRejected() {
        ArgumentParser.Parsed p = ArgumentParser.parse(new String[]{"run", "1", "2"});
        assertNotNull(p.error);
        assertTrue(p.error, p.error.contains("too many positional"));
    }

    // D-38：duplicateFileRejected / fileWithoutValueRejected 两例作废——
    // --file 不再是合法旗标（统一由 fileFlagRejectedAsUnknownAfterD38 覆盖）

    @Test
    public void duplicateConfigDirRejected() {
        ArgumentParser.Parsed p = ArgumentParser.parse(new String[]{
                "--config-dir", "a", "run", "1", "--config-dir", "b"});
        assertNotNull(p.error);
        assertTrue(p.error, p.error.contains("--config-dir may appear only once"));
    }

    @Test
    public void unknownFlagRejected() {
        ArgumentParser.Parsed p = ArgumentParser.parse(new String[]{"run", "1", "--bogus"});
        assertNotNull(p.error);
        assertTrue(p.error, p.error.contains("unknown flag"));
    }

    @Test
    public void emptyArgsRejected() {
        assertNotNull(ArgumentParser.parse(new String[]{}).error);
        assertNotNull(ArgumentParser.parse(null).error);
    }

    @Test
    public void blankTaskIdRejected() {
        ArgumentParser.Parsed p = ArgumentParser.parse(new String[]{"run", "  "});
        assertNotNull(p.error);
        assertTrue(p.error, p.error.contains("task id is required"));
    }

    /** D-33：--yes 旗标（跳过目标确认） */
    @Test
    public void yesFlagParsed() {
        ArgumentParser.Parsed p = ArgumentParser.parse(new String[]{"run", "1", "--yes"});
        assertNull(p.error);
        assertTrue(p.assumeYes);
        assertFalse(ArgumentParser.parse(new String[]{"run", "1"}).assumeYes);
    }

    /** D-29c：--keep-open 旗标（无值、任意位置） */
    @Test
    public void keepOpenFlagAcceptedAnywhere() {
        ArgumentParser.Parsed p = ArgumentParser.parse(new String[]{"--keep-open", "run", "1"});
        assertNull(p.error);
        assertTrue(p.keepOpen);
        ArgumentParser.Parsed q = ArgumentParser.parse(new String[]{"run", "1", "--keep-open"});
        assertNull(q.error);
        assertTrue(q.keepOpen);
    }

    /** D-38：--file 旗标整体作废——带值/裸旗标均按未知旗标拒绝（旧 D-36 片段升级后需重新复制） */
    @Test
    public void fileFlagRejectedAsUnknownAfterD38() {
        ArgumentParser.Parsed withValue = ArgumentParser.parse(
                new String[]{"run", "1", "--file", "sub\\a.war"});
        assertNotNull(withValue.error);
        assertTrue(withValue.error, withValue.error.contains("unknown flag: --file"));
        ArgumentParser.Parsed bare = ArgumentParser.parse(new String[]{"run", "1", "--file"});
        assertNotNull(bare.error);
        assertTrue(bare.error, bare.error.contains("unknown flag: --file"));
    }
}
