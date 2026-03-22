package com.madisruptor.demo07_custom_disruptor;

/**
 * ══════════════════════════════════════════════════════════
 *  MiniSequenceBarrier - 序号屏障
 * ══════════════════════════════════════════════════════════
 *
 * 协调消费者与生产者之间的关系:
 *   消费者调用 waitFor() 等待新事件
 *   Barrier 内部委托给 WaitStrategy 来决定等待方式
 */
public class MiniSequenceBarrier {

    private final MiniSequence cursorSequence;  // 生产者的游标
    private final MiniWaitStrategy waitStrategy;

    public MiniSequenceBarrier(MiniSequence cursorSequence, MiniWaitStrategy waitStrategy) {
        this.cursorSequence = cursorSequence;
        this.waitStrategy = waitStrategy;
    }

    /**
     * 等待直到指定序号可用
     *
     * @param sequence 期望消费的序号
     * @return 实际可用的最大序号（可能大于 sequence，支持批量消费）
     */
    public long waitFor(long sequence) throws InterruptedException {
        return waitStrategy.waitFor(sequence, cursorSequence);
    }

    /**
     * 获取当前生产者游标位置
     */
    public long getCursor() {
        return cursorSequence.get();
    }
}
