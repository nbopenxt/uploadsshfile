package com.openxt.uploadsshfile.sync;

/**
 * 锁元数据（1.0.8 / D-03，设计文档 §4.3）——{@code locks/<serverId>.info} 的读写模型。
 *
 * <p>正确性由 OS 文件锁保证（进程死亡自动释放）；本信息仅用于**回显与陈旧提示**
 * （PID + 进程启动时间双条件判活，U-05——两条件同时吻合才认定"仍存活"，
 * 不一致提示"上一持锁进程已不存在"，不作为互斥依据，D-03 裁定）。
 */
public final class LockInfo {

    public final String serverId;
    public final String taskId;
    public final long pid;
    public final long processStartTs;  // 持锁进程启动时刻（epoch ms，0＝取不到）
    public final long lockStartTs;     // 抢锁成功时刻（epoch ms）

    public LockInfo(String serverId, String taskId, long pid, long processStartTs, long lockStartTs) {
        this.serverId = serverId;
        this.taskId = taskId;
        this.pid = pid;
        this.processStartTs = processStartTs;
        this.lockStartTs = lockStartTs;
    }

    /** 序列化为 key=value 行（与 secure.dat 同风格的极简文本，UTF-8） */
    public String toText() {
        return "taskId=" + (taskId == null ? "" : taskId) + "\n"
                + "pid=" + pid + "\n"
                + "processStartTs=" + processStartTs + "\n"
                + "lockStartTs=" + lockStartTs + "\n";
    }

    public static LockInfo parse(String serverId, String text) {
        String taskId = "";
        long pid = 0, psts = 0, lsts = 0;
        if (text != null) {
            for (String line : text.split("\\r?\\n")) {
                int eq = line.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                String k = line.substring(0, eq).trim();
                String v = line.substring(eq + 1).trim();
                try {
                    switch (k) {
                        case "taskId": taskId = v; break;
                        case "pid": pid = Long.parseLong(v); break;
                        case "processStartTs": psts = Long.parseLong(v); break;
                        case "lockStartTs": lsts = Long.parseLong(v); break;
                        default: break;
                    }
                } catch (NumberFormatException ignore) {
                }
            }
        }
        return new LockInfo(serverId, taskId, pid, psts, lsts);
    }

    /** 已持锁秒数（回显"running/waited Ns"） */
    public long heldSeconds() {
        return Math.max(0, (System.currentTimeMillis() - lockStartTs) / 1000);
    }

    /**
     * 双条件判活（U-05）：PID 存活 且 该 PID 进程启动时间与记录一致。
     * 崩溃被 OS 释放锁后本信息仅用于提示——返回值只影响文案，不影响互斥正确性（D-03）。
     */
    public boolean holderLooksAlive() {
        try {
            java.util.Optional<ProcessHandle> ph = ProcessHandle.of(pid);
            if (!ph.isPresent()) {
                return false;
            }
            if (processStartTs > 0) {
                java.util.Optional<java.time.Instant> st = ph.get().info().startInstant();
                if (st.isPresent() && Math.abs(st.get().toEpochMilli() - processStartTs) > 5_000L) {
                    return false; // PID 被复用，进程启动时间对不上
                }
            }
            return ph.get().isAlive();
        } catch (Throwable t) {
            return true; // 判活探测失败按"可能存活"处理（保守展示，互斥仍由 OS 锁保证）
        }
    }
}
