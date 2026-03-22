package com.madisruptor.demo07_custom_disruptor;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * ══════════════════════════════════════════════════════════
 *  MiniSequence - 带缓存行填充的序号
 * ══════════════════════════════════════════════════════════
 *
 * 核心设计:
 *   1. 使用 volatile long 保证可见性
 *   2. 使用 VarHandle 实现 CAS 操作（替代 Unsafe）
 *   3. 前后各 7 个 long 填充，确保 value 独占一个缓存行
 *
 * 缓存行布局 (64 bytes):
 *   [padding: p1-p7 (56B)] [value (8B)] [padding: p8-p14 (56B)]
 *   value 独占一个缓存行，避免伪共享
 */
public class MiniSequence {

    // ===== 前置填充 (7 * 8 = 56 bytes) =====
    private long p1, p2, p3, p4, p5, p6, p7;

    // ===== 实际值 =====
    private volatile long value;

    // ===== 后置填充 (7 * 8 = 56 bytes) =====
    private long p8, p9, p10, p11, p12, p13, p14;

    // VarHandle 用于 CAS 操作（Java 9+ 推荐方式，替代 sun.misc.Unsafe）
    private static final VarHandle VALUE_HANDLE;

    static {
        try {
            VALUE_HANDLE = MethodHandles.lookup()
                    .findVarHandle(MiniSequence.class, "value", long.class);
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * 初始值默认为 -1，表示尚未使用
     */
    public MiniSequence() {
        this(-1);
    }

    public MiniSequence(long initialValue) {
        this.value = initialValue;
    }

    /**
     * 获取当前值（volatile 读，保证可见性）
     */
    public long get() {
        return value;
    }

    /**
     * 设置值（volatile 写，保证可见性）
     */
    public void set(long newValue) {
        this.value = newValue;
    }

    /**
     * CAS 操作: 如果当前值 == expected，则更新为 update
     *
     * @return true 如果更新成功
     */
    public boolean compareAndSet(long expected, long update) {
        return VALUE_HANDLE.compareAndSet(this, expected, update);
    }

    /**
     * 防止填充字段被 JIT 优化掉
     */
    public long preventOptimization() {
        return p1 + p2 + p3 + p4 + p5 + p6 + p7 + p8 + p9 + p10 + p11 + p12 + p13 + p14;
    }

    @Override
    public String toString() {
        return "MiniSequence{value=" + value + "}";
    }
}
