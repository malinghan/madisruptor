package com.madisruptor.demo03_multi_producer_consumer;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.*;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ═══════════════════════════════════════════════════════
 *  Demo 03: 多生产者 / 多消费者模式
 * ═══════════════════════════════════════════════════════
 *
 * 演示内容:
 *   Part A: 多生产者 + 广播消费（每个消费者处理所有事件）
 *   Part B: 多生产者 + 竞争消费（WorkerPool，每个事件只处理一次）
 *
 * 运行方式:
 *   mvn exec:java -Dexec.mainClass="com.madisruptor.demo03_multi_producer_consumer.MultiProducerConsumerDemo"
 */
public class MultiProducerConsumerDemo {

    private static final int BUFFER_SIZE = 1024;
    private static final int NUM_PRODUCERS = 3;
    private static final int EVENTS_PER_PRODUCER = 5;

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 03: 多生产者 / 多消费者模式");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();

        partA_BroadcastConsumers();
        System.out.println();
        partB_WorkerPoolConsumers();
    }

    /**
     * Part A: 多生产者 + 广播消费
     * 每个 EventHandler 都会收到所有事件的副本
     */
    @SuppressWarnings("unchecked")
    private static void partA_BroadcastConsumers() throws Exception {
        System.out.println("【Part A】多生产者 + 广播消费 (每个消费者处理所有事件)");
        System.out.println("─────────────────────────────────────────────────────");

        AtomicInteger threadCounter = new AtomicInteger(1);
        ThreadFactory tf1 = r -> new Thread(r, "broadcast-consumer-" + threadCounter.getAndIncrement());
        Disruptor<TradeEvent> disruptor = new Disruptor<>(
                TradeEvent::new,
                BUFFER_SIZE,
                tf1,
                ProducerType.MULTI,  // 多生产者模式
                new YieldingWaitStrategy()
        );

        AtomicLong handler1Count = new AtomicLong();
        AtomicLong handler2Count = new AtomicLong();

        // 注册两个广播消费者 - 每个都会处理所有事件
        EventHandler<TradeEvent> handler1 = (event, seq, endOfBatch) -> {
            handler1Count.incrementAndGet();
            System.out.printf("  [Handler-1] seq=%d %s%n", seq, event);
        };
        EventHandler<TradeEvent> handler2 = (event, seq, endOfBatch) -> {
            handler2Count.incrementAndGet();
            System.out.printf("  [Handler-2] seq=%d %s%n", seq, event);
        };

        disruptor.handleEventsWith(handler1, handler2);
        disruptor.start();

        RingBuffer<TradeEvent> ringBuffer = disruptor.getRingBuffer();

        // 启动多个生产者线程
        ExecutorService producers = Executors.newFixedThreadPool(NUM_PRODUCERS);
        CountDownLatch latch = new CountDownLatch(NUM_PRODUCERS);

        String[] symbols = {"AAPL", "GOOGL", "MSFT"};

        for (int p = 0; p < NUM_PRODUCERS; p++) {
            final int producerId = p;
            producers.submit(() -> {
                try {
                    for (int i = 0; i < EVENTS_PER_PRODUCER; i++) {
                        final long tradeId = producerId * 100L + i;
                        final String symbol = symbols[producerId];
                        final double price = 100.0 + producerId * 50 + i;

                        ringBuffer.publishEvent((event, seq) -> {
                            event.setTradeId(tradeId);
                            event.setSymbol(symbol);
                            event.setPrice(price);
                            event.setQuantity(100);
                            event.setProducerName("Producer-" + producerId);
                        });
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        Thread.sleep(1000);
        disruptor.shutdown();
        producers.shutdown();

        int totalEvents = NUM_PRODUCERS * EVENTS_PER_PRODUCER;
        System.out.println();
        System.out.printf("  总事件数: %d | Handler-1 处理: %d | Handler-2 处理: %d%n",
                totalEvents, handler1Count.get(), handler2Count.get());
        System.out.println("  → 广播模式: 每个 Handler 都处理了所有事件 ✓");
        System.out.println();
    }

    /**
     * Part B: 多生产者 + WorkerPool 竞争消费
     * 每个事件只被一个 Worker 处理
     */
    private static void partB_WorkerPoolConsumers() throws Exception {
        System.out.println("【Part B】多生产者 + WorkerPool 竞争消费 (每个事件只处理一次)");
        System.out.println("─────────────────────────────────────────────────────");

        AtomicInteger threadCounter2 = new AtomicInteger(1);
        ThreadFactory tf2 = r -> new Thread(r, "worker-" + threadCounter2.getAndIncrement());
        Disruptor<TradeEvent> disruptor = new Disruptor<>(
                TradeEvent::new,
                BUFFER_SIZE,
                tf2,
                ProducerType.MULTI,
                new YieldingWaitStrategy()
        );

        AtomicLong[] workerCounts = new AtomicLong[3];
        for (int i = 0; i < 3; i++) {
            workerCounts[i] = new AtomicLong();
        }

        // 注册 WorkerPool - 3 个 Worker 竞争消费
        @SuppressWarnings("unchecked")
        WorkHandler<TradeEvent>[] workers = new WorkHandler[3];
        for (int w = 0; w < 3; w++) {
            final int workerId = w;
            workers[w] = (event) -> {
                workerCounts[workerId].incrementAndGet();
                System.out.printf("  [Worker-%d] %s (线程: %s)%n",
                        workerId, event, Thread.currentThread().getName());
            };
        }

        disruptor.handleEventsWithWorkerPool(workers);
        disruptor.start();

        RingBuffer<TradeEvent> ringBuffer = disruptor.getRingBuffer();

        // 多生产者发布
        ExecutorService producers = Executors.newFixedThreadPool(NUM_PRODUCERS);
        CountDownLatch latch = new CountDownLatch(NUM_PRODUCERS);
        String[] symbols = {"AAPL", "GOOGL", "MSFT"};

        for (int p = 0; p < NUM_PRODUCERS; p++) {
            final int producerId = p;
            producers.submit(() -> {
                try {
                    for (int i = 0; i < EVENTS_PER_PRODUCER; i++) {
                        final long tradeId = producerId * 100L + i;
                        final double price = 100.0 + producerId * 50 + i;
                        ringBuffer.publishEvent((event, seq) -> {
                            event.setTradeId(tradeId);
                            event.setSymbol(symbols[producerId]);
                            event.setPrice(price);
                            event.setQuantity(200);
                            event.setProducerName("Producer-" + producerId);
                        });
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        Thread.sleep(1000);
        disruptor.shutdown();
        producers.shutdown();

        long totalProcessed = 0;
        for (int i = 0; i < 3; i++) {
            System.out.printf("  Worker-%d 处理了 %d 个事件%n", i, workerCounts[i].get());
            totalProcessed += workerCounts[i].get();
        }
        System.out.printf("  总计处理: %d (预期: %d)%n", totalProcessed, NUM_PRODUCERS * EVENTS_PER_PRODUCER);
        System.out.println("  → 竞争模式: 每个事件只被一个 Worker 处理 ✓");

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 03 完成! 你已经掌握了多生产者/多消费者模式");
        System.out.println("═══════════════════════════════════════════════════════");
    }
}
