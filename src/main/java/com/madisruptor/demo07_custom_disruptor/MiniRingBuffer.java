package com.madisruptor.demo07_custom_disruptor;

/**
 * ══════════════════════════════════════════════════════════
 *  MiniRingBuffer - 简化版环形缓冲区
 * ══════════════════════════════════════════════════════════
 *
 * 核心设计:
 *   1. 固定大小的数组，大小必须是 2 的幂
 *   2. 预分配所有 Event 对象，避免 GC
 *   3. 使用位运算取模: index = sequence & (bufferSize - 1)
 *   4. 单生产者模式（无锁）
 *
 * 生产者写入流程:
 *   1. next()     → 获取下一个可写序号
 *   2. get(seq)   → 获取该位置的预分配事件对象
 *   3. 写入数据    → 覆盖事件对象的字段
 *   4. publish()  → 更新游标，通知消费者
 */
public class MiniRingBuffer<E> {

    // 预分配的事件数组
    private final Object[] entries;

    // 缓冲区大小 (2的幂)
    private final int bufferSize;

    // 位运算掩码 = bufferSize - 1
    private final int indexMask;

    // 生产者游标: 指向最后一个已发布事件的序号
    private final MiniSequence cursor = new MiniSequence(-1);

    // 消费者序号数组（用于检查是否会追尾）
    private MiniSequence[] gatingSequences = new MiniSequence[0];

    // 等待策略
    private final MiniWaitStrategy waitStrategy;

    // 单生产者的下一个序号（非 volatile，仅生产者线程访问）
    private long nextValue = -1;
    // 缓存的最小消费者序号（减少 volatile 读取）
    private long cachedGatingValue = -1;

    /**
     * 构造 RingBuffer
     *
     * @param factory    事件工厂
     * @param bufferSize 缓冲区大小（必须是 2 的幂）
     * @param waitStrategy 等待策略
     */
    public MiniRingBuffer(MiniEventFactory<E> factory, int bufferSize, MiniWaitStrategy waitStrategy) {
        // 验证 bufferSize 是 2 的幂
        if (Integer.bitCount(bufferSize) != 1) {
            throw new IllegalArgumentException("bufferSize must be a power of 2, got: " + bufferSize);
        }

        this.bufferSize = bufferSize;
        this.indexMask = bufferSize - 1;
        this.entries = new Object[bufferSize];
        this.waitStrategy = waitStrategy;

        // 预分配所有事件对象
        for (int i = 0; i < bufferSize; i++) {
            entries[i] = factory.newInstance();
        }
    }

    /**
     * 申请下一个可写序号（单生产者模式）
     *
     * 如果写太快会追尾消费者，此方法会自旋等待直到有空间
     */
    public long next() {
        long nextSequence = nextValue + 1;
        long wrapPoint = nextSequence - bufferSize;  // 环绕点

        // 检查是否会追尾消费者
        if (wrapPoint > cachedGatingValue) {
            // 需要查询消费者的最小序号
            long minSequence;
            while (wrapPoint > (minSequence = getMinimumGatingSequence())) {
                Thread.yield();  // 等待消费者跟上
            }
            cachedGatingValue = minSequence;
        }

        nextValue = nextSequence;
        return nextSequence;
    }

    /**
     * 获取指定序号位置的事件对象
     * 使用位运算取模: index = sequence & (bufferSize - 1)
     */
    @SuppressWarnings("unchecked")
    public E get(long sequence) {
        return (E) entries[(int) (sequence & indexMask)];
    }

    /**
     * 发布事件：更新游标，使消费者可见
     */
    public void publish(long sequence) {
        cursor.set(sequence);
    }

    /**
     * 获取当前游标位置
     */
    public MiniSequence getCursor() {
        return cursor;
    }

    /**
     * 获取缓冲区大小
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * 创建序号屏障
     */
    public MiniSequenceBarrier newBarrier() {
        return new MiniSequenceBarrier(cursor, waitStrategy);
    }

    /**
     * 设置消费者序号（用于追尾保护）
     */
    public void addGatingSequences(MiniSequence... sequences) {
        MiniSequence[] newGating = new MiniSequence[gatingSequences.length + sequences.length];
        System.arraycopy(gatingSequences, 0, newGating, 0, gatingSequences.length);
        System.arraycopy(sequences, 0, newGating, gatingSequences.length, sequences.length);
        this.gatingSequences = newGating;
    }

    /**
     * 获取最慢消费者的序号
     */
    private long getMinimumGatingSequence() {
        long minimum = Long.MAX_VALUE;
        for (MiniSequence seq : gatingSequences) {
            long value = seq.get();
            if (value < minimum) {
                minimum = value;
            }
        }
        return minimum;
    }

    /**
     * 获取剩余容量
     */
    public long remainingCapacity() {
        long consumed = getMinimumGatingSequence();
        long produced = cursor.get();
        return bufferSize - (produced - consumed);
    }
}
