package com.madisruptor.demo04_waitstrategy;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ═══════════════════════════════════════════════════════
 *  Demo 04: WaitStrategy 等待策略对比
 * ═══════════════════════════════════════════════════════
 *
 * 演示内容:
 *   对比不同等待策略的延迟和吞吐量表现
 *   - BlockingWaitStrategy    (锁 + Condition)
 *   - SleepingWaitStrategy    (自旋 + yield + sleep)
 *   - YieldingWaitStrategy    (自旋 + yield)
 *   - BusySpinWaitStrategy    (纯自旋)
 *
 * 运行方式:
 *   mvn exec:java -Dexec.mainClass="com.madisruptor.demo04_waitstrategy.WaitStrategyDemo"
 */
public class WaitStrategyDemo {

    private static final int BUFFER_SIZE = 1024;
    private static final int NUM_EVENTS = 100_000;

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 04: WaitStrategy 等待策略对比");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();
        System.out.printf("测试参数: bufferSize=%d, events=%d%n", BUFFER_SIZE, NUM_EVENTS);
        System.out.println();

        // 预热 JVM
        System.out.println("预热 JVM...");
        benchmark("warmup", new YieldingWaitStrategy(), false);
        System.out.println("预热完成！");
        System.out.println();

        System.out.println("┌────────────────────────────────┬──────────────┬──────────────┬──────────────┐");
        System.out.println("│         等待策略                │  耗时(ms)    │  吞吐量(ops/s)│  说明         │");
        System.out.println("├────────────────────────────────┼──────────────┼──────────────┼──────────────┤");

        // 1. BlockingWaitStrategy
        long blockingTime = benchmark("BlockingWaitStrategy", new BlockingWaitStrategy(), true);
        printRow("BlockingWaitStrategy", blockingTime, "锁+Condition");

        // 2. SleepingWaitStrategy
        long sleepingTime = benchmark("SleepingWaitStrategy", new SleepingWaitStrategy(), true);
        printRow("SleepingWaitStrategy", sleepingTime, "自旋+sleep");

        // 3. YieldingWaitStrategy
        long yieldingTime = benchmark("YieldingWaitStrategy", new YieldingWaitStrategy(), true);
        printRow("YieldingWaitStrategy", yieldingTime, "自旋+yield");

        // 4. BusySpinWaitStrategy
        long busySpinTime = benchmark("BusySpinWaitStrategy", new BusySpinWaitStrategy(), true);
        printRow("BusySpinWaitStrategy", busySpinTime, "纯自旋");

        System.out.println("└────────────────────────────────┴──────────────┴──────────────┴──────────────┘");
        System.out.println();

        System.out.println("策略选型建议:");
        System.out.println("  BusySpinWaitStrategy  → 超低延迟，CPU 核心充足");
        System.out.println("  YieldingWaitStrategy  → 低延迟首选，平衡 CPU 和延迟");
        System.out.println("  SleepingWaitStrategy  → 后台任务（如日志），节省 CPU");
        System.out.println("  BlockingWaitStrategy  → CPU 资源受限，吞吐量优先");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 04 完成! 你已经理解了各种等待策略的特点");
        System.out.println("═══════════════════════════════════════════════════════");
    }

    private static void printRow(String strategy, long timeMs, String desc) {
        long throughput = (long) ((double) NUM_EVENTS / timeMs * 1000);
        System.out.printf("│ %-30s │ %10d   │ %,12d │ %-12s │%n",
                strategy, timeMs, throughput, desc);
    }

    /**
     * 基准测试：使用指定的 WaitStrategy 发布和消费 N 个事件
     */
    private static long benchmark(String name, WaitStrategy waitStrategy, boolean print) throws Exception {
        Disruptor<ValueEvent> disruptor = new Disruptor<>(
                ValueEvent::new,
                BUFFER_SIZE,
                Executors.defaultThreadFactory(),
                ProducerType.SINGLE,
                waitStrategy
        );

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong sum = new AtomicLong(0);

        disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
            sum.addAndGet(event.getValue());
            if (sequence == NUM_EVENTS - 1) {
                latch.countDown();
            }
        });

        disruptor.start();
        RingBuffer<ValueEvent> ringBuffer = disruptor.getRingBuffer();

        long start = System.nanoTime();

        // 发布事件
        for (int i = 0; i < NUM_EVENTS; i++) {
            final long value = i;
            ringBuffer.publishEvent((event, seq) -> event.setValue(value));
        }

        // 等待消费完成
        latch.await();
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        disruptor.shutdown();
        return elapsed;
    }

    /**
     * 简单的值事件
     */
    public static class ValueEvent {
        private long value;

        public long getValue() { return value; }
        public void setValue(long value) { this.value = value; }
    }
}
