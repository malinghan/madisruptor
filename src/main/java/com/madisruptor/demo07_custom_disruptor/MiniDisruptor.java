package com.madisruptor.demo07_custom_disruptor;

/**
 * ══════════════════════════════════════════════════════════
 *  MiniDisruptor - 门面类，整合所有组件
 * ══════════════════════════════════════════════════════════
 *
 * 简化版 Disruptor 的入口：
 *   1. 创建 RingBuffer
 *   2. 注册 EventHandler
 *   3. 启动消费者线程
 *   4. 提供发布事件的方法
 *   5. 优雅关闭
 */
public class MiniDisruptor<E> {

    private final MiniRingBuffer<E> ringBuffer;
    private MiniBatchProcessor<E> processor;
    private Thread processorThread;

    /**
     * 创建 MiniDisruptor
     *
     * @param factory      事件工厂
     * @param bufferSize   缓冲区大小（2的幂）
     * @param waitStrategy 等待策略
     */
    public MiniDisruptor(MiniEventFactory<E> factory, int bufferSize, MiniWaitStrategy waitStrategy) {
        this.ringBuffer = new MiniRingBuffer<>(factory, bufferSize, waitStrategy);
    }

    /**
     * 注册事件处理器
     */
    public MiniDisruptor<E> handleEventsWith(MiniEventHandler<E> handler) {
        MiniSequenceBarrier barrier = ringBuffer.newBarrier();
        this.processor = new MiniBatchProcessor<>(ringBuffer, barrier, handler);

        // 将消费者的序号注册到 RingBuffer，用于追尾保护
        ringBuffer.addGatingSequences(processor.getSequence());

        return this;
    }

    /**
     * 启动 Disruptor
     */
    public MiniRingBuffer<E> start() {
        if (processor == null) {
            throw new IllegalStateException("必须先调用 handleEventsWith() 注册处理器");
        }

        processorThread = new Thread(processor, "mini-disruptor-consumer");
        processorThread.setDaemon(true);
        processorThread.start();

        return ringBuffer;
    }

    /**
     * 获取 RingBuffer
     */
    public MiniRingBuffer<E> getRingBuffer() {
        return ringBuffer;
    }

    /**
     * 关闭 Disruptor
     */
    public void shutdown() {
        if (processor != null) {
            processor.halt();
        }
        if (processorThread != null) {
            processorThread.interrupt();
            try {
                processorThread.join(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
