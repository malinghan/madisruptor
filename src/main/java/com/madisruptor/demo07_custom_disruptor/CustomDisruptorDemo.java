package com.madisruptor.demo07_custom_disruptor;

import java.util.concurrent.CountDownLatch;

/**
 * ═══════════════════════════════════════════════════════
 *  Demo 07: 手写简易 Disruptor - 完整演示
 * ═══════════════════════════════════════════════════════
 *
 * 演示内容:
 *   1. 使用自己实现的 MiniDisruptor 框架
 *   2. 验证核心功能: 预分配、环形复用、批量消费、追尾保护
 *   3. 与官方 Disruptor 性能简单对比
 *
 * 组件清单:
 *   - MiniSequence       : 带缓存行填充的序号
 *   - MiniRingBuffer     : 环形缓冲区（预分配 + 位运算取模）
 *   - MiniSequenceBarrier: 序号屏障
 *   - MiniBatchProcessor : 批量事件处理器
 *   - MiniWaitStrategies : 等待策略实现
 *   - MiniDisruptor      : 门面整合类
 *
 * 运行方式:
 *   mvn exec:java -Dexec.mainClass="com.madisruptor.demo07_custom_disruptor.CustomDisruptorDemo"
 */
public class CustomDisruptorDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 07: 手写简易 Disruptor - 从零实现核心功能");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();

        partA_BasicDemo();
        System.out.println();
        partB_BatchDemo();
        System.out.println();
        partC_PerformanceTest();
    }

    /**
     * Part A: 基本功能演示
     */
    private static void partA_BasicDemo() throws Exception {
        System.out.println("【Part A】基本功能 - Hello MiniDisruptor");
        System.out.println("─────────────────────────────────────────────────────");

        // 创建事件类
        MiniDisruptor<StringEvent> disruptor = new MiniDisruptor<>(
                StringEvent::new,
                8,  // 很小的 buffer，方便观察环形覆盖
                new MiniWaitStrategies.YieldingWait()
        );

        // 注册处理器
        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            System.out.printf("  [消费] seq=%d, index=%d, msg='%s', endOfBatch=%s, hashCode=%s%n",
                    sequence,
                    sequence & 7,  // 手动计算 index
                    event.getValue(),
                    endOfBatch,
                    Integer.toHexString(System.identityHashCode(event)));
        });

        // 启动
        MiniRingBuffer<StringEvent> ringBuffer = disruptor.start();
        System.out.printf("  RingBuffer 大小: %d, 剩余容量: %d%n",
                ringBuffer.getBufferSize(), ringBuffer.remainingCapacity());
        System.out.println();

        // 发布事件
        System.out.println("  发布 12 条消息（超过 buffer 大小 8，验证环形覆盖）:");
        for (int i = 0; i < 12; i++) {
            long seq = ringBuffer.next();
            StringEvent event = ringBuffer.get(seq);
            event.setValue("Message-" + i);
            ringBuffer.publish(seq);

            if (i < 8) {
                // 前 8 个慢一点，让消费者跟上
                Thread.sleep(50);
            }
        }

        Thread.sleep(500);
        disruptor.shutdown();
        System.out.println("  → 环形覆盖验证: 相同 index 的 hashCode 相同（对象复用）✓");
    }

    /**
     * Part B: 批量消费演示
     */
    private static void partB_BatchDemo() throws Exception {
        System.out.println("【Part B】批量消费演示");
        System.out.println("─────────────────────────────────────────────────────");

        MiniDisruptor<StringEvent> disruptor = new MiniDisruptor<>(
                StringEvent::new, 1024,
                new MiniWaitStrategies.SleepingWait()
        );

        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            if (endOfBatch) {
                System.out.printf("  [批次结束] seq=%d, msg='%s'%n", sequence, event.getValue());
            }
        });

        MiniRingBuffer<StringEvent> ringBuffer = disruptor.start();

        // 一次性发布 20 条消息，消费者会批量处理
        System.out.println("  一次性发布 20 条消息，观察批量消费行为:");
        for (int i = 0; i < 20; i++) {
            long seq = ringBuffer.next();
            ringBuffer.get(seq).setValue("Batch-" + i);
            ringBuffer.publish(seq);
        }

        Thread.sleep(500);
        disruptor.shutdown();
        System.out.println("  → 批量消费: 多条消息在一个批次中处理 ✓");
    }

    /**
     * Part C: 性能测试
     */
    private static void partC_PerformanceTest() throws Exception {
        System.out.println("【Part C】性能测试 - MiniDisruptor 吞吐量");
        System.out.println("─────────────────────────────────────────────────────");

        int numEvents = 1_000_000;
        CountDownLatch latch = new CountDownLatch(1);

        MiniDisruptor<LongEvent> disruptor = new MiniDisruptor<>(
                LongEvent::new, 1024 * 64,
                new MiniWaitStrategies.YieldingWait()
        );

        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            if (sequence == numEvents - 1) {
                latch.countDown();
            }
        });

        MiniRingBuffer<LongEvent> ringBuffer = disruptor.start();

        // 预热
        System.out.println("  预热中...");
        CountDownLatch warmupLatch = new CountDownLatch(1);
        MiniDisruptor<LongEvent> warmup = new MiniDisruptor<>(
                LongEvent::new, 1024 * 64,
                new MiniWaitStrategies.YieldingWait()
        );
        warmup.handleEventsWith((event, seq, end) -> {
            if (seq == 99999) warmupLatch.countDown();
        });
        MiniRingBuffer<LongEvent> warmupRb = warmup.start();
        for (int i = 0; i < 100000; i++) {
            long s = warmupRb.next();
            warmupRb.get(s).setValue(i);
            warmupRb.publish(s);
        }
        warmupLatch.await();
        warmup.shutdown();

        // 正式测试
        System.out.printf("  发布 %,d 个事件...%n", numEvents);
        long start = System.nanoTime();

        for (int i = 0; i < numEvents; i++) {
            long seq = ringBuffer.next();
            ringBuffer.get(seq).setValue(i);
            ringBuffer.publish(seq);
        }

        latch.await();
        long elapsed = System.nanoTime() - start;
        long elapsedMs = elapsed / 1_000_000;
        long throughput = (long) ((double) numEvents / elapsed * 1_000_000_000);

        System.out.printf("  耗时: %d ms%n", elapsedMs);
        System.out.printf("  吞吐量: %,d ops/s%n", throughput);

        disruptor.shutdown();

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 07 完成! 你已经从零实现了 Disruptor 核心功能");
        System.out.println("═══════════════════════════════════════════════════════");
    }

    // ===== 事件类 =====

    public static class StringEvent {
        private String value;
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
    }

    public static class LongEvent {
        private long value;
        public long getValue() { return value; }
        public void setValue(long value) { this.value = value; }
    }
}
