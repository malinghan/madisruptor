package com.madisruptor.demo07_custom_disruptor;

/**
 * ══════════════════════════════════════════════════════════
 *  MiniBatchProcessor - 批量事件处理器
 * ══════════════════════════════════════════════════════════
 *
 * 核心设计:
 *   1. 在独立线程中运行
 *   2. 通过 SequenceBarrier 等待新事件
 *   3. 批量获取可用事件并处理
 *   4. 更新消费进度 Sequence
 *
 * 这是官方 BatchEventProcessor 的简化版本
 */
public class MiniBatchProcessor<E> implements Runnable {

    private final MiniRingBuffer<E> ringBuffer;
    private final MiniSequenceBarrier barrier;
    private final MiniEventHandler<E> handler;

    // 当前消费进度
    private final MiniSequence sequence = new MiniSequence(-1);

    // 运行状态
    private volatile boolean running = true;

    public MiniBatchProcessor(MiniRingBuffer<E> ringBuffer,
                               MiniSequenceBarrier barrier,
                               MiniEventHandler<E> handler) {
        this.ringBuffer = ringBuffer;
        this.barrier = barrier;
        this.handler = handler;
    }

    @Override
    public void run() {
        long nextSequence = sequence.get() + 1;  // 从 0 开始消费

        while (running) {
            try {
                // 1. 等待可用的序号（可能批量返回多个）
                long availableSequence = barrier.waitFor(nextSequence);

                // 2. 批量处理: 从 nextSequence 到 availableSequence
                while (nextSequence <= availableSequence) {
                    E event = ringBuffer.get(nextSequence);
                    boolean endOfBatch = (nextSequence == availableSequence);

                    // 3. 调用用户的事件处理器
                    handler.onEvent(event, nextSequence, endOfBatch);

                    nextSequence++;
                }

                // 4. 更新消费进度
                sequence.set(availableSequence);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                // 简化的异常处理: 记录并跳过
                System.err.println("[MiniProcessor] 处理异常: " + e.getMessage());
                sequence.set(nextSequence);
                nextSequence++;
            }
        }
    }

    /**
     * 获取消费者的当前序号
     */
    public MiniSequence getSequence() {
        return sequence;
    }

    /**
     * 停止处理器
     */
    public void halt() {
        running = false;
    }
}
