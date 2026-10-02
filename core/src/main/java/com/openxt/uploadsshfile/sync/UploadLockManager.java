package com.openxt.uploadsshfile.sync;

import com.openxt.uploadsshfile.util.PluginPathManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;

/**
 * 服务器粒度跨进程锁（1.0.8 / D-03/R50/U-02，设计文档 §3.2/§4.3/§5.3/§6）。
 *
 * <p>互斥单元＝{@code {config}/uploadsshfile/locks/<serverId>.lock}（OS FileLock，
 * 进程死亡由 OS 自动释放——崩溃恢复不依赖判活）；旁路 {@code <serverId>.info} 记
 * 任务 ID/PID/进程启动时间/持锁时刻，仅供回显与陈旧提示（U-05 双条件）。
 * GUI 单次上传、GUI 批处理、CLI **共用同一把锁**（R50，唯一控制点延伸）。
 *
 * <p>接口契约（§3.2）：{@link #tryLock(String, String)} 成功返回 {@link Handle}
 * （AutoCloseable，调用方 try/finally 单一出口释放，RISK-15）；失败返回 empty。
 * 等待策略归调用方（CLI 无上限每秒回显 R42；GUI 弹窗等待/取消 R50）——管理器本身不阻塞等待。
 * 不支持同 JVM 重入：调用方不得嵌套持有同一 serverId（现有 GUI/CLI/批处理均为顺序取放）。
 */
public final class UploadLockManager {

    private static final UploadLockManager INSTANCE = new UploadLockManager();

    public static UploadLockManager getInstance() {
        return INSTANCE;
    }

    private UploadLockManager() {
    }

    /** 持锁句柄：close()＝释放 FileLock + 清 .info（幂等） */
    public static final class Handle implements AutoCloseable {
        private final String serverId;
        private final FileChannel channel;
        private final FileLock lock;
        private boolean closed;

        private Handle(String serverId, FileChannel channel, FileLock lock) {
            this.serverId = serverId;
            this.channel = channel;
            this.lock = lock;
        }

        public String getServerId() {
            return serverId;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            try {
                lock.release();
            } catch (IOException ignore) {
            }
            try {
                channel.close();
            } catch (IOException ignore) {
            }
            try {
                Files.deleteIfExists(infoPath(serverId));
            } catch (IOException ignore) {
            }
        }
    }

    private Path locksDir() throws IOException {
        Path dir = PluginPathManager.getInstance().getConfigPath().resolve("locks");
        Files.createDirectories(dir); // 首建（实施注记：目录不存在时抢锁即建）
        return dir;
    }

    private Path lockPath(String serverId) throws IOException {
        return locksDir().resolve(sanitize(serverId) + ".lock");
    }

    private static Path infoPath(String serverId) throws IOException {
        return Paths.get(PluginPathManager.getInstance().getConfigPath().toString(),
                "locks", sanitize(serverId) + ".info");
    }

    /** serverId 为内部 UUID/雪花，仍做文件名兜底（防路径穿越——攻击者视角检查项） */
    private static String sanitize(String serverId) {
        return serverId == null ? "unknown" : serverId.replaceAll("[^A-Za-z0-9_.\\-]", "_");
    }

    /**
     * 非阻塞抢锁。成功＝持有并写 .info；被占＝Optional.empty（调用方决定等待策略）。
     *
     * <p><b>契约（M4 测试暴露的修正）</b>：仅"锁被占用"返回 empty；
     * 基础设施故障（路径未初始化 fail-fast、目录不可写等 IOException）**直接上抛**——
     * 吞成 empty 会让排队调用方陷入无限等待，把故障伪装成占用（语义不可混，与 M4 测试教训同族）。
     * @param taskId 展示用（回显"谁在持锁"），null 容错为空串
     */
    public synchronized Optional<Handle> tryLock(String serverId, String taskId) throws IOException {
        Path lp = lockPath(serverId);
        FileChannel ch = FileChannel.open(lp,
                StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE);
        FileLock fl;
        try {
            fl = ch.tryLock();
        } catch (java.nio.channels.OverlappingFileLockException e) {
            // 同 JVM 已持有（违反不嵌套契约的场景）：视为占用，不误释放他方锁
            ch.close();
            return Optional.empty();
        }
        if (fl == null) {
            ch.close();
            return Optional.empty();
        }
        Handle h = new Handle(serverId, ch, fl);
        long pid = ProcessHandle.current().pid();
        long pstart = ProcessHandle.current().info().startInstant()
                .map(java.time.Instant::toEpochMilli).orElse(0L);
        LockInfo info = new LockInfo(serverId, taskId, pid, pstart, System.currentTimeMillis());
        try {
            Files.write(infoPath(serverId), info.toText().getBytes(StandardCharsets.UTF_8));
        } catch (IOException infoFail) {
            // .info 仅展示用（D-03）：写失败不否定已持有的 OS 锁
        }
        return Optional.of(h);
    }

    /** 读当前持锁方信息（不存在/损坏返回 null）；供等待回显与陈旧提示 */
    public LockInfo readInfo(String serverId) {
        try {
            Path ip = infoPath(serverId);
            if (!Files.exists(ip)) {
                return null;
            }
            String text = new String(Files.readAllBytes(ip), StandardCharsets.UTF_8);
            return LockInfo.parse(serverId, text);
        } catch (IOException e) {
            return null;
        }
    }
}
