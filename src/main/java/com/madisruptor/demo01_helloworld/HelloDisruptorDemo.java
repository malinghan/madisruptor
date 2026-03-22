package com.madisruptor.demo01_helloworld;

import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.YieldingWaitStrategy;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * ═══════════════════════════════════════════════════════
 *  Demo 01: Hello Disruptor - 最简单的入门示例
 * ═══════════════════════════════════════════════════════
 *
 * 演示内容:
 *   1. 创建 Disruptor 实例
 *   2. 注册事件处理器（消费者）
 *   3. 启动 Disruptor
 *   4. 发布事件（生产者）
 *   5. 关闭 Disruptor
 *
 * 流程图:
 *   [Producer] --publish--> [RingBuffer] --consume--> [MessageEventHandler]
 *
 * 运行方式:
 *   mvn exec:java -Dexec.mainClass="com.madisruptor.demo01_helloworld.HelloDisruptorDemo"
 */
public class HelloDisruptorDemo {

    public static void main(String[] args) throws InterruptedException {
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 01: Hello Disruptor - 入门示例");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();

        // ================ Step 1: 创建 Disruptor ================
        // 参数说明:
        //   EventFactory  - 事件工厂，用于预创建事件对象
        //   bufferSize    - RingBuffer 大小，必须是 2 的幂
        //   ThreadFactory - 消费者线程工厂
        //   ProducerType  - 单生产者(SINGLE) 或 多生产者(MULTI)
        //   WaitStrategy  - 消费者等待策略

        int bufferSize = 1024;  // 2^10 = 1024

        ThreadFactory threadFactory = new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger(1);
            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r, "disruptor-consumer-" + counter.getAndIncrement());
                t.setDaemon(true);
                return t;
            }
        };

        Disruptor<MessageEvent> disruptor = new Disruptor<>(
                new MessageEventFactory(),   // 事件工厂
                bufferSize,                  // RingBuffer 大小
                threadFactory,               // 线程工厂
                ProducerType.SINGLE,         // 单生产者
                new YieldingWaitStrategy()   // Yielding 等待策略
        );

        System.out.println("[初始化] RingBuffer 大小: " + bufferSize);
        System.out.println("[初始化] 生产者类型: SINGLE");
        System.out.println("[初始化] 等待策略: YieldingWaitStrategy");
        System.out.println();

        // ================ Step 2: 注册事件处理器 ================
        disruptor.handleEventsWith(new MessageEventHandler());
        System.out.println("[初始化] 注册消费者: MessageEventHandler");
        System.out.println();

        // ================ Step 3: 启动 Disruptor ================
        disruptor.start();
        System.out.println("[启动] Disruptor 已启动！");
        System.out.println();

        // ================ Step 4: 发布事件 ================
        RingBuffer<MessageEvent> ringBuffer = disruptor.getRingBuffer();

        System.out.println("[生产者] 开始发布 10 条消息...");
        System.out.println("─────────────────────────────────────────────────────");

        for (int i = 0; i < 10; i++) {
            final String message = "Hello Disruptor #" + i;

            // 方式一: 使用 EventTranslator（推荐，更安全）
            ringBuffer.publishEvent((event, sequence) -> {
                event.setMessage(message);
            });
        }

        // 等待消费者处理完成
        Thread.sleep(1000);

        System.out.println("─────────────────────────────────────────────────────");
        System.out.println();

        // ================ Step 5: 演示传统的两步发布方式 ================
        System.out.println("[生产者] 使用传统两步方式发布 5 条消息...");
        System.out.println("─────────────────────────────────────────────────────");

        for (int i = 10; i < 15; i++) {
            // 方式二: 传统的 next/get/publish 方式
            long sequence = ringBuffer.next();  // 申请序号
            try {
                MessageEvent event = ringBuffer.get(sequence);  // 获取事件对象
                event.setMessage("Traditional publish #" + i);  // 写入数据
            } finally {
                ringBuffer.publish(sequence);  // 必须在 finally 中发布！
            }
        }

        Thread.sleep(1000);

        System.out.println("─────────────────────────────────────────────────────");
        System.out.println();

        // ================ Step 6: 关闭 Disruptor ================
        disruptor.shutdown();
        System.out.println("[关闭] Disruptor 已安全关闭");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 01 完成! 你已经学会了 Disruptor 的基本使用");
        System.out.println("═══════════════════════════════════════════════════════");
    }
}
