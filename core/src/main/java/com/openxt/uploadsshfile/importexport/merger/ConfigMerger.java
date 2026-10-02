package com.openxt.uploadsshfile.importexport.merger;

/**
 * 配置合并器接口。
 * 每种配置类型独立实现合并逻辑。
 *
 * @param <T> 配置类型
 */
public interface ConfigMerger<T> {

    /**
     * 将 source (导入的 A) 合并到 target (当前的 B) 中。
     * @param source 导入的配置 (A)
     * @param target 当前配置 (B)
     * @param ctx    重映射上下文（传递 ID 映射表）
     * @return 合并后的配置
     */
    T merge(T source, T target, RemapContext ctx);

    /** 获取本次合并的统计信息 */
    MergeCounts getMergeCounts();
}
