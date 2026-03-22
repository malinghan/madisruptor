package com.madisruptor.demo02_ringbuffer;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.YieldingWaitStrategy;

import java.util.concurrent.Executors;

/**
 * ═══════════════════════════════════════════════════════
 *  Demo 02: RingBuffer 深入理解
 * ═══════════════════════════════════════════════════════
 *
 * 演示内容:
 *   1. RingBuffer 的大小必须是 2 的幂（位运算取模原理）
 *   2. 序号(sequence)与索引(index)的映射关系
 *   3. RingBuffer 容量监控
 *   4. 事件对象的预分配和复用
 *   5. 生产者追赶消费者时的阻塞行为
 *
 * 运行方式:
 *   mvn exec:java -Dexec.mainClass="com.madisruptor.demo02_ringbuffer.RingBufferDemo"
 */
public class RingBufferDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 02: RingBuffer 深入理解");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();

        // ================ Part 1: 位运算取模原理 ================
        System.out.println("【Part 1】位运算取模原理");
        System.out.println("─────────────────────────────────────────────────────");

        int bufferSize = 8;  // 2^3
        int indexMask = bufferSize - 1;  // 7 = 0b0111

        System.out.printf("bufferSize = %d (二进制: %s)%n", bufferSize, Integer.toBinaryString(bufferSize));
        System.out.printf("indexMask  = %d (二进制: %s)%n", indexMask, Integer.toBinaryString(indexMask));
        System.out.println();

        for (long seq = 0; seq < 20; seq++) {
            int indexBitwise = (int) (seq & indexMask);    // 位运算
            int indexModulo = (int) (seq % bufferSize);    // 取模运算
            System.out.printf("sequence=%2d → index(位运算)=%d, index(取模)=%d, 二进制: %s & %s = %s %s%n",
                    seq, indexBitwise, indexModulo,
                    String.format("%5s", Long.toBinaryString(seq)),
                    Integer.toBinaryString(indexMask),
                    String.format("%3s", Integer.toBinaryString(indexBitwise)),
                    indexBitwise == indexModulo ? "✓" : "✗");
        }
        System.out.println();

        // ================ Part 2: 事件预分配验证 ================
        System.out.println("【Part 2】事件预分配验证");
        System.out.println("─────────────────────────────────────────────────────");

        Disruptor<LongEvent> disruptor = new Disruptor<>(
                LongEvent::new, bufferSize,
                Executors.defaultThreadFactory(),
                ProducerType.SINGLE,
                new YieldingWaitStrategy());

        // 使用慢消费者来观察 RingBuffer 行为
        EventHandler<LongEvent> slowHandler = (event, sequence, endOfBatch) -> {
            System.out.printf("  [消费] seq=%d, value=%d, hashCode=%s%n",
                    sequence, event.getValue(),
                    Integer.toHexString(System.identityHashCode(event)));
            if (sequence == 2) {
                Thread.sleep(100);  // 模拟慢消费
            }
        };

        disruptor.handleEventsWith(slowHandler);
        disruptor.start();

        RingBuffer<LongEvent> ringBuffer = disruptor.getRingBuffer();

        // 验证预分配: 获取同一个 index 位置的 event 对象
        System.out.println("验证对象复用 (index 0 的对象在不同 sequence 中):");
        LongEvent event0 = ringBuffer.get(0);
        System.out.printf("  seq=0 → hashCode=%s%n",
                Integer.toHexString(System.identityHashCode(event0)));
        System.out.println();

        // ================ Part 3: 容量监控 ================
        System.out.println("【Part 3】容量监控");
        System.out.println("─────────────────────────────────────────────────────");

        System.out.printf("初始状态: cursor=%d, remainingCapacity=%d%n",
                ringBuffer.getCursor(), ringBuffer.remainingCapacity());

        // 发布事件并观察容量变化
        for (int i = 0; i < 5; i++) {
            final long value = i * 100;
            ringBuffer.publishEvent((e, seq) -> e.setValue(value));
            System.out.printf("发布 seq=%d 后: cursor=%d, remainingCapacity=%d%n",
                    i, ringBuffer.getCursor(), ringBuffer.remainingCapacity());
        }

        Thread.sleep(500);
        System.out.println();

        // ================ Part 4: 验证 sequence 到 index 的映射 ================
        System.out.println("【Part 4】循环写入验证 (写入超过 bufferSize 的数据)");
        System.out.println("─────────────────────────────────────────────────────");

        // 继续写入更多数据，验证环形覆盖
        for (int i = 5; i < 12; i++) {
            final long value = i * 100;
            ringBuffer.publishEvent((e, seq) -> e.setValue(value));
            long cursor = ringBuffer.getCursor();
            int index = (int) (cursor & (bufferSize - 1));
            System.out.printf("发布: cursor=%d → index=%d (cursor & %d = %d), remainingCapacity=%d%n",
                    cursor, index, indexMask, index, ringBuffer.remainingCapacity());
        }

        Thread.sleep(1000);

        disruptor.shutdown();

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 02 完成! 你已经理解了 RingBuffer 的工作原理");
        System.out.println("═══════════════════════════════════════════════════════");
    }
}
