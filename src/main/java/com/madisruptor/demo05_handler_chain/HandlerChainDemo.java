package com.madisruptor.demo05_handler_chain;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.Executors;

/**
 * ═══════════════════════════════════════════════════════
 *  Demo 05: 事件处理器链（Pipeline / 菱形依赖）
 * ═══════════════════════════════════════════════════════
 *
 * 演示内容:
 *   Part A: 串行链 - 验证 → 计算 → 存储
 *   Part B: 菱形依赖 - 验证 → (风控 + 计费) → 最终处理
 *
 * 业务场景: 订单处理流水线
 *   1. 验证Handler  - 校验订单有效性
 *   2. 风控Handler  - 风险评估
 *   3. 计费Handler  - 计算费用
 *   4. 最终Handler  - 汇总结果
 *
 * 运行方式:
 *   mvn exec:java -Dexec.mainClass="com.madisruptor.demo05_handler_chain.HandlerChainDemo"
 */
public class HandlerChainDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 05: 事件处理器链（Pipeline / 菱形依赖）");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();

        partA_Pipeline();
        System.out.println();
        partB_Diamond();
    }

    /**
     * Part A: 串行管道
     * [Producer] → [验证] → [计算] → [存储]
     */
    @SuppressWarnings("unchecked")
    private static void partA_Pipeline() throws Exception {
        System.out.println("【Part A】串行管道模式");
        System.out.println("  [Producer] → [验证] → [计算] → [存储]");
        System.out.println("─────────────────────────────────────────────────────");

        Disruptor<OrderEvent> disruptor = new Disruptor<>(
                OrderEvent::new, 1024,
                Executors.defaultThreadFactory(),
                ProducerType.SINGLE,
                new YieldingWaitStrategy());

        // 串行链: handler1 → handler2 → handler3
        EventHandler<OrderEvent> validateHandler = (event, seq, endOfBatch) -> {
            event.setStatus("VALIDATED");
            System.out.printf("  [1.验证] seq=%d, orderId=%d → %s (线程: %s)%n",
                    seq, event.getOrderId(), event.getStatus(), Thread.currentThread().getName());
            Thread.sleep(10);  // 模拟处理耗时
        };

        EventHandler<OrderEvent> calculateHandler = (event, seq, endOfBatch) -> {
            event.setAmount(event.getPrice() * event.getQuantity());
            event.setStatus("CALCULATED");
            System.out.printf("  [2.计算] seq=%d, orderId=%d, amount=%.2f → %s (线程: %s)%n",
                    seq, event.getOrderId(), event.getAmount(), event.getStatus(), Thread.currentThread().getName());
            Thread.sleep(10);
        };

        EventHandler<OrderEvent> storeHandler = (event, seq, endOfBatch) -> {
            event.setStatus("STORED");
            System.out.printf("  [3.存储] seq=%d, orderId=%d → %s (线程: %s)%n",
                    seq, event.getOrderId(), event.getStatus(), Thread.currentThread().getName());
        };

        // 关键: then() 表示依赖关系
        disruptor.handleEventsWith(validateHandler)
                 .then(calculateHandler)
                 .then(storeHandler);

        disruptor.start();

        var ringBuffer = disruptor.getRingBuffer();
        for (int i = 1; i <= 3; i++) {
            final int orderId = i;
            ringBuffer.publishEvent((event, seq) -> {
                event.setOrderId(orderId);
                event.setPrice(100.0 * orderId);
                event.setQuantity(orderId * 10);
                event.setStatus("NEW");
            });
        }

        Thread.sleep(2000);
        disruptor.shutdown();
        System.out.println("  → 串行管道: 每个事件按顺序经过所有Handler ✓");
    }

    /**
     * Part B: 菱形依赖
     *              → [风控] ─┐
     * [Producer] →            ├→ [最终处理]
     *              → [计费] ─┘
     */
    @SuppressWarnings("unchecked")
    private static void partB_Diamond() throws Exception {
        System.out.println("【Part B】菱形依赖模式");
        System.out.println("              → [风控] ─┐");
        System.out.println("  [Producer] →           ├→ [最终处理]");
        System.out.println("              → [计费] ─┘");
        System.out.println("─────────────────────────────────────────────────────");

        Disruptor<OrderEvent> disruptor = new Disruptor<>(
                OrderEvent::new, 1024,
                Executors.defaultThreadFactory(),
                ProducerType.SINGLE,
                new YieldingWaitStrategy());

        EventHandler<OrderEvent> riskHandler = (event, seq, endOfBatch) -> {
            // 模拟风控检查
            boolean risky = event.getPrice() > 500;
            event.setRiskLevel(risky ? "HIGH" : "LOW");
            System.out.printf("  [风控] seq=%d, orderId=%d, risk=%s (线程: %s)%n",
                    seq, event.getOrderId(), event.getRiskLevel(), Thread.currentThread().getName());
            Thread.sleep(20);  // 风控耗时较长
        };

        EventHandler<OrderEvent> billingHandler = (event, seq, endOfBatch) -> {
            // 模拟计费
            event.setAmount(event.getPrice() * event.getQuantity());
            double fee = event.getAmount() * 0.01;  // 1% 手续费
            event.setFee(fee);
            System.out.printf("  [计费] seq=%d, orderId=%d, fee=%.2f (线程: %s)%n",
                    seq, event.getOrderId(), event.getFee(), Thread.currentThread().getName());
            Thread.sleep(15);  // 计费耗时
        };

        EventHandler<OrderEvent> finalHandler = (event, seq, endOfBatch) -> {
            // 汇总结果 - 等待风控和计费都完成
            System.out.printf("  [最终] seq=%d, orderId=%d, risk=%s, fee=%.2f, amount=%.2f (线程: %s)%n",
                    seq, event.getOrderId(), event.getRiskLevel(), event.getFee(),
                    event.getAmount(), Thread.currentThread().getName());
        };

        // 菱形依赖: riskHandler 和 billingHandler 并行，都完成后再 finalHandler
        disruptor.handleEventsWith(riskHandler, billingHandler)
                 .then(finalHandler);

        disruptor.start();

        var ringBuffer = disruptor.getRingBuffer();
        for (int i = 1; i <= 5; i++) {
            final int orderId = i;
            ringBuffer.publishEvent((event, seq) -> {
                event.setOrderId(orderId);
                event.setPrice(100.0 * orderId);
                event.setQuantity(orderId * 5);
                event.setStatus("NEW");
            });
        }

        Thread.sleep(3000);
        disruptor.shutdown();

        System.out.println("  → 菱形依赖: 风控和计费并行处理，最终Handler等待两者完成 ✓");
        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 05 完成! 你已经掌握了处理器链的各种模式");
        System.out.println("═══════════════════════════════════════════════════════");
    }

    /**
     * 订单事件
     */
    public static class OrderEvent {
        private long orderId;
        private double price;
        private int quantity;
        private double amount;
        private double fee;
        private String status;
        private String riskLevel;

        public long getOrderId() { return orderId; }
        public void setOrderId(long orderId) { this.orderId = orderId; }
        public double getPrice() { return price; }
        public void setPrice(double price) { this.price = price; }
        public int getQuantity() { return quantity; }
        public void setQuantity(int quantity) { this.quantity = quantity; }
        public double getAmount() { return amount; }
        public void setAmount(double amount) { this.amount = amount; }
        public double getFee() { return fee; }
        public void setFee(double fee) { this.fee = fee; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getRiskLevel() { return riskLevel; }
        public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    }
}
