package com.openxt.uploadsshfile.model;

import java.io.Serializable;
import java.net.InetAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 任务 ID 生成器（1.0.8 / FR-04，设计文档 §1/§3.2）。
 *
 * 来源：复制改造自 NBTLBase/KolaUtil com.kola.util.KolaSnowFlake（2026-09-15 三级派生版），
 * 不引入整个 jar（用户裁定）。JDK21 化：PID 取 ProcessHandle.current().pid()；
 * 去 JVM 参数/DB 派号通道（插件场景单机自用，-D 属性与 assignSlot 不搬）。
 *
 * 位结构（与上游一致）：符号 1 | 毫秒时间戳 41（twepoch=1288834974657） | datacenter 5 | worker 5 | 序列 12。
 * 唯一性：本算法保证同机单调；跨机由 hostname+IPv4+PID 派生槽位压低碰撞（评审共识：概率性非数学必然，
 * 本插件为单机自用场景可接受）；GUI 落盘前另有命名空间查重兜底（流程 E/#13）。
 * 兼容性契约：workerId/datacenterId 保持 public static 可写（对齐上游语义，单测可固定复现）。
 * ID 输出为不透明字符串与存量 UUID 共存（R16），任何消费方不得解析其位结构。
 */
public class TaskIdGenerator implements Serializable {

    public static long workerId = derive(true);
    public static long datacenterId = derive(false);

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = -1L ^ (-1L << SEQUENCE_BITS);
    private static final long TWEPOCH = 1288834974657L;

    private long sequence = 0L;
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);

    private static volatile TaskIdGenerator instance;

    public static synchronized TaskIdGenerator getInstance() {
        if (instance == null) {
            instance = new TaskIdGenerator();
        }
        return instance;
    }

    /** 生成下一个任务 ID（字符串形态，不透明；输出恒为纯数字，天然不含 '-'） */
    public static String nextId() {
        return String.valueOf(getInstance().nextIdLong());
    }

    /**
     * 新值字符集校验（D-25，SRS FR-04/05 补注）：用户新建/手改/复制落盘的任务 ID 仅允许
     * 字母与数字，拒绝 '-'、空格及其他符号——生成侧（{@link #nextId()}）天然满足。
     * 仅用于"新值"；存量 UUID（含 '-'）按不透明字符串共存（FR-05），不得以本方法校验
     * 存量匹配/放行，否则 1.0.8 前的数据一保存即自杀。
     */
    public static boolean isValidNewId(String id) {
        if (id == null || id.isEmpty()) {
            return false;
        }
        for (int i = 0; i < id.length(); i++) {
            char c = id.charAt(i);
            boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /**
     * 槽位派生：hostname+真实IPv4+PID 三路混合（同机多进程靠 PID 分叉，跨机靠 hostname/IP 分叉）；
     * 派生异常兜底 1/1（与上游 KolaSnowFlake 行为一致，绝不因生成器自身失败而中断业务）。
     */
    private static long derive(boolean isWorker) {
        try {
            String host;
            try {
                host = InetAddress.getLocalHost().getHostName();
            } catch (Throwable ignore) {
                host = "";
            }
            long base = ((host == null ? 0L : host.hashCode() & 0xFFFFFFFFL) * 1000003L)
                    ^ (realIpMix() * 2654435761L)
                    ^ (ProcessHandle.current().pid() * 0x9E3779B97F4A7C15L);
            return isWorker ? Math.floorMod(base >> 5, 32L) : Math.floorMod(base, 32L);
        } catch (Throwable t) {
            return 1L;
        }
    }

    /** 枚举网卡取真实 IPv4（跳过 down/回环/链路本地/0.；优先 site-local；字典序定权保证重启稳定——与上游同法） */
    private static long realIpMix() {
        long best = 0L;
        boolean bestSite = false;
        try {
            java.util.List<String> keys = new java.util.ArrayList<>();
            java.util.Map<String, long[]> cand = new java.util.HashMap<>();
            java.util.Enumeration<java.net.NetworkInterface> nifs = java.net.NetworkInterface.getNetworkInterfaces();
            while (nifs != null && nifs.hasMoreElements()) {
                java.net.NetworkInterface nif = nifs.nextElement();
                try {
                    if (!nif.isUp() || nif.isLoopback()) continue;
                } catch (Throwable ignore) {
                    continue;
                }
                java.util.Enumeration<InetAddress> addrs = nif.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress ia = addrs.nextElement();
                    byte[] b = ia.getAddress();
                    if (b == null || b.length != 4) continue;
                    if (ia.isLoopbackAddress() || ia.isLinkLocalAddress()) continue;
                    if (b[0] == 0) continue;
                    long v = 0L;
                    for (byte x : b) v = (v << 8) | (x & 0xFF);
                    boolean site = ia.isSiteLocalAddress();
                    String key = nif.getName() + "/" + ia.getHostAddress();
                    keys.add(key);
                    cand.put(key, new long[]{v, site ? 1 : 0});
                }
            }
            java.util.Collections.sort(keys);
            for (String k : keys) {
                long[] cv = cand.get(k);
                if (!bestSite && cv[1] == 1L) { best = cv[0]; bestSite = true; }
                else if (best == 0L && !bestSite) best = cv[0];
            }
        } catch (Throwable ignore) {
        }
        return best;
    }

    /** 雪花主流程（synchronized 防同毫秒多线程撞序列） */
    public synchronized long nextIdLong() {
        long timestamp = System.currentTimeMillis();

        if (timestamp < lastTimestamp.get()) {
            throw new RuntimeException(String.format(
                    "Clock moved backwards. Refusing to generate id for %d milliseconds",
                    lastTimestamp.get() - timestamp));
        }

        if (lastTimestamp.get() == timestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                while (timestamp <= lastTimestamp.get()) {
                    timestamp = System.currentTimeMillis();
                }
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp.set(timestamp);

        return ((timestamp - TWEPOCH) << TIMESTAMP_LEFT_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }
}
