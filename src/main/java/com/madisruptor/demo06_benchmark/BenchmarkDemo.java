package com.madisruptor.demo06_benchmark;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ═══════════════════════════════════════════════════════
 *  Demo 06: 性能基准测试 - Disruptor vs BlockingQueue
 * ═══════════════════════════════════════════════════════
 *
 * 演示内容:
 *   对比 Disruptor 和 ArrayBlockingQueue 在不同场景下的性能:
 *   1. 单生产者-单消费者 (1P-1C)
 *   2. 多生产者-单消费者 (3P-1C)
 *
 * 运行方式:
 *   mvn exec:java -Dexec.mainClass="com.madisruptor.demo06_benchmark.BenchmarkDemo"
 */
public class BenchmarkDemo {

    private static final int BUFFER_SIZE = 1024 * 64;  // 64K
    private static final int NUM_EVENTS = 1_000_000;   // 100万事件

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 06: 性能基准测试 - Disruptor vs BlockingQueue");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();
        System.out.printf("测试参数: bufferSize=%d, events=%,d%n", BUFFER_SIZE, NUM_EVENTS);
        System.out.println();

        // 预热
        System.out.println("预热 JVM (3轮)...");
        for (int i = 0; i < 3; i++) {
            benchmarkDisruptor1P1C();
            benchmarkBlockingQueue1P1C();
        }
        System.out.println("预热完成！");
        System.out.println();

        // ================ 测试 1: 1P-1C ================
        System.out.println("【测试 1】单生产者 - 单消费者 (1P-1C)");
        System.out.println("─────────────────────────────────────────────────────");

        long disruptorTime1P1C = 0;
        long queueTime1P1C = 0;
        int rounds = 5;

        for (int i = 0; i < rounds; i++) {
            disruptorTime1P1C += benchmarkDisruptor1P1C();
            queueTime1P1C += benchmarkBlockingQueue1P1C();
        }

        disruptorTime1P1C /= rounds;
        queueTime1P1C /= rounds;

        printResult("1P-1C", disruptorTime1P1C, queueTime1P1C);
        System.out.println();

        // ================ 测试 2: 3P-1C ================
        System.out.println("【测试 2】多生产者 - 单消费者 (3P-1C)");
        System.out.println("─────────────────────────────────────────────────────");

        long disruptorTime3P1C = 0;
        long queueTime3P1C = 0;

        for (int i = 0; i < rounds; i++) {
            disruptorTime3P1C += benchmarkDisruptor3P1C();
            queueTime3P1C += benchmarkBlockingQueue3P1C();
        }

        disruptorTime3P1C /= rounds;
        queueTime3P1C /= rounds;

        printResult("3P-1C", disruptorTime3P1C, queueTime3P1C);

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 06 完成! Disruptor 的性能优势一目了然");
        System.out.println("═══════════════════════════════════════════════════════");
    }

    private static void printResult(String scenario, long disruptorMs, long queueMs) {
        long dThroughput = (long) ((double) NUM_EVENTS / disruptorMs * 1000);
        long qThroughput = (long) ((double) NUM_EVENTS / queueMs * 1000);
        double ratio = (double) queueMs / disruptorMs;

        System.out.println("┌────────────────────────┬──────────────┬──────────────────┐");
        System.out.println("│         方案            │  平均耗时(ms) │   吞吐量(ops/s)   │");
        System.out.println("├────────────────────────┼──────────────┼──────────────────┤");
        System.out.printf("│ Disruptor              │ %10d   │ %,14d   │%n", disruptorMs, dThroughput);
        System.out.printf("│ ArrayBlockingQueue     │ %10d   │ %,14d   │%n", queueMs, qThroughput);
        System.out.println("├────────────────────────┼──────────────┼──────────────────┤");
        System.out.printf("│ Disruptor 快           │     %.1fx     │                  │%n", ratio);
        System.out.println("└────────────────────────┴──────────────┴──────────────────┘");
    }

    // ==================== Disruptor 1P-1C ====================
    private static long benchmarkDisruptor1P1C() throws Exception {
        Disruptor<ValueEvent> disruptor = new Disruptor<>(
                ValueEvent::new, BUFFER_SIZE,
                Executors.defaultThreadFactory(),
                ProducerType.SINGLE,
                new YieldingWaitStrategy());

        CountDownLatch latch = new CountDownLatch(1);
        disruptor.handleEventsWith((event, seq, end) -> {
            if (seq == NUM_EVENTS - 1) latch.countDown();
        });
        disruptor.start();

        RingBuffer<ValueEvent> ringBuffer = disruptor.getRingBuffer();

        long start = System.nanoTime();
        for (int i = 0; i < NUM_EVENTS; i++) {
            long seq = ringBuffer.next();
            try {
                ringBuffer.get(seq).setValue(i);
            } finally {
                ringBuffer.publish(seq);
            }
        }
        latch.await();
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        disruptor.shutdown();
        return elapsed;
    }

    // ==================== BlockingQueue 1P-1C ====================
    private static long benchmarkBlockingQueue1P1C() throws Exception {
        ArrayBlockingQueue<Long> queue = new ArrayBlockingQueue<>(BUFFER_SIZE);
        CountDownLatch latch = new CountDownLatch(1);

        // 消费者线程
        Thread consumer = new Thread(() -> {
            long count = 0;
            while (count < NUM_EVENTS) {
                try {
                    queue.take();
                    count++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            latch.countDown();
        });
        consumer.setDaemon(true);
        consumer.start();

        long start = System.nanoTime();
        for (int i = 0; i < NUM_EVENTS; i++) {
            queue.put((long) i);
        }
        latch.await();
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        return elapsed;
    }

    // ==================== Disruptor 3P-1C ====================
    private static long benchmarkDisruptor3P1C() throws Exception {
        Disruptor<ValueEvent> disruptor = new Disruptor<>(
                ValueEvent::new, BUFFER_SIZE,
                Executors.defaultThreadFactory(),
                ProducerType.MULTI,  // 多生产者
                new YieldingWaitStrategy());

        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong consumeCount = new AtomicLong(0);

        disruptor.handleEventsWith((event, seq, end) -> {
            if (consumeCount.incrementAndGet() == NUM_EVENTS) latch.countDown();
        });
        disruptor.start();

        RingBuffer<ValueEvent> ringBuffer = disruptor.getRingBuffer();
        int eventsPerProducer = NUM_EVENTS / 3;

        ExecutorService producers = Executors.newFixedThreadPool(3);
        CountDownLatch producerLatch = new CountDownLatch(3);

        long start = System.nanoTime();

        for (int p = 0; p < 3; p++) {
            final int extra = (p == 2) ? NUM_EVENTS - eventsPerProducer * 3 : 0;
            producers.submit(() -> {
                for (int i = 0; i < eventsPerProducer + extra; i++) {
                    ringBuffer.publishEvent((event, seq) -> event.setValue(seq));
                }
                producerLatch.countDown();
            });
        }

        latch.await();
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        disruptor.shutdown();
        producers.shutdown();
        return elapsed;
    }

    // ==================== BlockingQueue 3P-1C ====================
    private static long benchmarkBlockingQueue3P1C() throws Exception {
        ArrayBlockingQueue<Long> queue = new ArrayBlockingQueue<>(BUFFER_SIZE);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicLong consumeCount = new AtomicLong(0);

        Thread consumer = new Thread(() -> {
            while (consumeCount.get() < NUM_EVENTS) {
                try {
                    queue.take();
                    if (consumeCount.incrementAndGet() == NUM_EVENTS) {
                        latch.countDown();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        consumer.setDaemon(true);
        consumer.start();

        int eventsPerProducer = NUM_EVENTS / 3;
        ExecutorService producers = Executors.newFixedThreadPool(3);

        long start = System.nanoTime();

        for (int p = 0; p < 3; p++) {
            final int extra = (p == 2) ? NUM_EVENTS - eventsPerProducer * 3 : 0;
            producers.submit(() -> {
                for (int i = 0; i < eventsPerProducer + extra; i++) {
                    try {
                        queue.put((long) i);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
        }

        latch.await();
        long elapsed = (System.nanoTime() - start) / 1_000_000;

        producers.shutdown();
        return elapsed;
    }

    public static class ValueEvent {
        private long value;
        public long getValue() { return value; }
        public void setValue(long value) { this.value = value; }
    }
}
