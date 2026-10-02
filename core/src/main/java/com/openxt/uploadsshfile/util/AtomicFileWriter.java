package com.openxt.uploadsshfile.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * D-22（SRS V2.6 / 系统设计清单⑪，思想实验 #3）：受管配置文件原子写。
 *
 * 背景：1.0.7 时代 plugin-config.json / secure.dat 只有 IDEA 进程自己读写，
 * Files.writeString 直接覆写无碍；1.0.8 引入 CLI 进程外读者（每次构建启动读配置），
 * GUI 写盘瞬间 CLI 可能读到半截文件 → Gson 解析崩、偶发退出码 3 且难复现。
 *
 * 做法：同目录唯一临时文件写完 → ATOMIC_MOVE 覆盖目标。
 * NTFS 上 rename 原子，读者任何时刻看到的都是完整旧版或完整新版。
 * 写入语义与数据格式零变化；本类不含任何 IDE 依赖（core 层）。
 */
public final class AtomicFileWriter {

    private AtomicFileWriter() {
    }

    /**
     * 以 UTF-8 原子写入文本文件。
     * @param target 目标文件（父目录不存在时自动创建）
     * @param content 完整内容
     */
    public static void writeString(Path target, String content) throws IOException {
        Path dir = target.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        // 唯一临时名：pid+纳秒+序号，防同秒并发覆盖临时文件
        Path tmp = dir.resolve(target.getFileName().toString()
                + ".tmp" + ProcessHandle.current().pid()
                + "x" + System.nanoTime());
        try {
            Files.writeString(tmp, content, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                // 极端文件系统（如某些网络盘）不支持原子移动：退化为普通覆盖替换，
                // 仍优于直接覆写（临时文件已完整落盘，普通 move 的失败面仅剩极窄窗口）
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(tmp); // move 成功后本行无副作用；异常路径清残留临时文件
        }
    }
}
