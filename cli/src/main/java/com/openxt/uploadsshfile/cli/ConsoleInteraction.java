package com.openxt.uploadsshfile.cli;

import com.openxt.uploadsshfile.cliapi.ConsoleCaps;
import com.openxt.uploadsshfile.cliapi.Prompter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * cmd 英文交互（1.0.8 / D-16，设计文档 §3.2/§3.3；Prompter 的 cli 侧实现）。
 *
 * <p>提示全英文（调用方保证），UTF-8 读 System.in；遇 EOF/无 stdin 返回 false＝默认中止
 * （R36"宁可不传也不误执行"→ 上层映射 USER_ABORT(10)/TIMEOUT_OR_NO_STDIN(11)）。
 * 在调用线程就地阻塞读键盘（与 GUI 匿名子类弹 Swing 询问同构）。
 */
public class ConsoleInteraction implements Prompter {

    private final PrintStream out;
    private final BufferedReader in =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    public ConsoleInteraction(PrintStream out) {
        this.out = out;
    }

    @Override
    public boolean askYesNo(String question) {
        if (!ConsoleCaps.hasConsole()) {
            out.println(question + " [no - non-interactive default ABORT]");
            return false;
        }
        out.print(question);
        out.flush();
        try {
            String line = in.readLine();
            if (line == null) {
                return false; // EOF
            }
            String t = line.trim().toLowerCase();
            return t.equals("y") || t.equals("yes");
        } catch (IOException e) {
            return false;
        }
    }
}
