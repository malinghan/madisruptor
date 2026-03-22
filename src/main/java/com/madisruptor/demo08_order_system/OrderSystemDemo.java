package com.madisruptor.demo08_order_system;

import com.lmax.disruptor.*;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ═══════════════════════════════════════════════════════
 *  Demo 08: 实战 - 高性能订单处理系统
 * ═══════════════════════════════════════════════════════
 *
 * 业务场景:
 *   电商订单处理流水线，使用 Disruptor 构建高性能事件驱动架构
 *
 * 处理流程（菱形 + 串行混合）:
 *
 *   [订单接入] → [参数校验] → [风控检查] ─┐
 *                           → [库存锁定] ─┤
 *                                         ├→ [价格计算] → [订单确认] → [清理]
 *
 * 各处理器说明:
 *   1. ValidationHandler - 校验订单参数
 *   2. RiskCheckHandler  - 风控检查（并行）
 *   3. InventoryHandler  - 库存锁定（并行）
 *   4. PricingHandler    - 价格计算（等待2,3完成）
 *   5. ConfirmHandler    - 订单确认
 *   6. CleanHandler      - 清理 Event 对象
 *
 * 运行方式:
 *   mvn exec:java -Dexec.mainClass="com.madisruptor.demo08_order_system.OrderSystemDemo"
 */
public class OrderSystemDemo {

    private static final int BUFFER_SIZE = 1024;
    private static final AtomicLong ORDER_ID_GEN = new AtomicLong(10000);
    private static final AtomicLong completedOrders = new AtomicLong(0);
    private static final AtomicLong failedOrders = new AtomicLong(0);

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 08: 实战 - 高性能订单处理系统");
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println();
        System.out.println("  处理流程:");
        System.out.println("  [接入] → [校验] → [风控] ─┐");
        System.out.println("                  → [库存] ─┤");
        System.out.println("                            ├→ [计价] → [确认] → [清理]");
        System.out.println();
        System.out.println("─────────────────────────────────────────────────────");

        // ================ 构建处理流水线 ================
        Disruptor<OrderEvent> disruptor = new Disruptor<>(
                OrderEvent::new,
                BUFFER_SIZE,
                Executors.defaultThreadFactory(),
                ProducerType.MULTI,
                new YieldingWaitStrategy()
        );

        // 定义处理器
        EventHandler<OrderEvent> validationHandler = new ValidationHandler();
        EventHandler<OrderEvent> riskCheckHandler = new RiskCheckHandler();
        EventHandler<OrderEvent> inventoryHandler = new InventoryHandler();
        EventHandler<OrderEvent> pricingHandler = new PricingHandler();
        EventHandler<OrderEvent> confirmHandler = new ConfirmHandler();
        EventHandler<OrderEvent> cleanHandler = new CleanHandler();

        // 构建依赖链:
        // 1. 先校验
        // 2. 校验完成后，风控和库存并行
        // 3. 风控和库存都完成后，计价
        // 4. 计价完成后，确认
        // 5. 确认完成后，清理
        disruptor.handleEventsWith(validationHandler)
                 .then(riskCheckHandler, inventoryHandler)
                 .then(pricingHandler)
                 .then(confirmHandler)
                 .then(cleanHandler);

        // 设置异常处理
        disruptor.setDefaultExceptionHandler(new ExceptionHandler<OrderEvent>() {
            @Override
            public void handleEventException(Throwable ex, long sequence, OrderEvent event) {
                System.err.printf("  [异常] 订单 %d 处理失败: %s%n", event.getOrderId(), ex.getMessage());
                event.setStatus(OrderEvent.OrderStatus.FAILED);
                failedOrders.incrementAndGet();
            }
            @Override
            public void handleOnStartException(Throwable ex) {
                System.err.println("  [异常] 启动失败: " + ex.getMessage());
            }
            @Override
            public void handleOnShutdownException(Throwable ex) {
                System.err.println("  [异常] 关闭失败: " + ex.getMessage());
            }
        });

        disruptor.start();
        System.out.println("  [系统] 订单处理系统已启动！");
        System.out.println();

        RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();

        // ================ 模拟多个客户端提交订单 ================
        int numClients = 3;
        int ordersPerClient = 5;
        int totalOrders = numClients * ordersPerClient;

        String[] users = {"user_alice", "user_bob", "user_charlie"};
        String[] products = {"iPhone-15", "MacBook-Pro", "AirPods"};
        double[] prices = {7999.0, 14999.0, 1299.0};

        ExecutorService clientPool = Executors.newFixedThreadPool(numClients);
        CountDownLatch latch = new CountDownLatch(numClients);

        System.out.printf("  [系统] 模拟 %d 个客户端，每个提交 %d 个订单，共 %d 个订单%n",
                numClients, ordersPerClient, totalOrders);
        System.out.println("─────────────────────────────────────────────────────");
        System.out.println();

        for (int c = 0; c < numClients; c++) {
            final int clientId = c;
            clientPool.submit(() -> {
                try {
                    for (int i = 0; i < ordersPerClient; i++) {
                        final long orderId = ORDER_ID_GEN.incrementAndGet();
                        final String user = users[clientId];
                        final String product = products[clientId];
                        final double price = prices[clientId];
                        final int qty = i + 1;

                        ringBuffer.publishEvent((event, seq) -> {
                            event.setOrderId(orderId);
                            event.setUserId(user);
                            event.setProductId(product);
                            event.setPrice(price);
                            event.setQuantity(qty);
                            event.setStatus(OrderEvent.OrderStatus.CREATED);
                            event.setCreateTime(System.currentTimeMillis());
                        });

                        Thread.sleep(50);  // 模拟客户端间隔
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        Thread.sleep(3000);  // 等待所有处理完成

        System.out.println();
        System.out.println("─────────────────────────────────────────────────────");
        System.out.println("  [统计]");
        System.out.printf("    总订单数:   %d%n", totalOrders);
        System.out.printf("    成功订单:   %d%n", completedOrders.get());
        System.out.printf("    失败订单:   %d%n", failedOrders.get());
        System.out.printf("    剩余容量:   %d%n", ringBuffer.remainingCapacity());

        disruptor.shutdown();
        clientPool.shutdown();

        System.out.println();
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("  Demo 08 完成! 你已经掌握了 Disruptor 的实战应用");
        System.out.println("  恭喜完成所有 Demo! Disruptor 学习之旅圆满结束！");
        System.out.println("═══════════════════════════════════════════════════════");
    }

    // ==================== 处理器实现 ====================

    /**
     * 1. 参数校验处理器
     */
    static class ValidationHandler implements EventHandler<OrderEvent> {
        @Override
        public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) throws Exception {
            if (event.getStatus() == OrderEvent.OrderStatus.FAILED) return;

            // 校验参数
            if (event.getPrice() <= 0 || event.getQuantity() <= 0) {
                event.setStatus(OrderEvent.OrderStatus.FAILED);
                System.out.printf("  [1.校验] 订单 %d ❌ 参数无效%n", event.getOrderId());
                return;
            }

            event.setStatus(OrderEvent.OrderStatus.VALIDATED);
            System.out.printf("  [1.校验] 订单 %d ✅ 参数有效 (user=%s, product=%s, qty=%d)%n",
                    event.getOrderId(), event.getUserId(), event.getProductId(), event.getQuantity());
            Thread.sleep(5);  // 模拟处理耗时
        }
    }

    /**
     * 2. 风控检查处理器（与库存并行）
     */
    static class RiskCheckHandler implements EventHandler<OrderEvent> {
        @Override
        public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) throws Exception {
            if (event.getStatus() == OrderEvent.OrderStatus.FAILED) return;

            // 模拟风控规则: 单笔金额超过 50000 为高风险
            double amount = event.getPrice() * event.getQuantity();
            if (amount > 50000) {
                event.setRiskResult("HIGH_RISK");
                System.out.printf("  [2.风控] 订单 %d ⚠️  高风险 (金额=%.0f)%n",
                        event.getOrderId(), amount);
            } else {
                event.setRiskResult("PASS");
                System.out.printf("  [2.风控] 订单 %d ✅ 通过 (金额=%.0f)%n",
                        event.getOrderId(), amount);
            }
            Thread.sleep(15);  // 风控耗时较长
        }
    }

    /**
     * 3. 库存锁定处理器（与风控并行）
     */
    static class InventoryHandler implements EventHandler<OrderEvent> {
        @Override
        public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) throws Exception {
            if (event.getStatus() == OrderEvent.OrderStatus.FAILED) return;

            // 模拟库存检查: 数量超过 4 库存不足
            if (event.getQuantity() > 4) {
                event.setInventoryResult("OUT_OF_STOCK");
                System.out.printf("  [3.库存] 订单 %d ❌ 库存不足 (需要=%d)%n",
                        event.getOrderId(), event.getQuantity());
            } else {
                event.setInventoryResult("LOCKED");
                System.out.printf("  [3.库存] 订单 %d ✅ 已锁定 (数量=%d)%n",
                        event.getOrderId(), event.getQuantity());
            }
            Thread.sleep(10);
        }
    }

    /**
     * 4. 价格计算处理器（等待风控和库存完成）
     */
    static class PricingHandler implements EventHandler<OrderEvent> {
        @Override
        public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) throws Exception {
            if (event.getStatus() == OrderEvent.OrderStatus.FAILED) return;

            // 检查前置条件
            if ("OUT_OF_STOCK".equals(event.getInventoryResult())) {
                event.setStatus(OrderEvent.OrderStatus.FAILED);
                failedOrders.incrementAndGet();
                return;
            }

            double total = event.getPrice() * event.getQuantity();
            event.setTotalAmount(total);

            // 模拟折扣: 金额 > 10000 打 95 折
            double discount = total > 10000 ? 0.95 : 1.0;
            event.setDiscount(discount);
            event.setFinalAmount(total * discount);

            System.out.printf("  [4.计价] 订单 %d ✅ 总额=%.0f, 折扣=%.0f%%, 实付=%.2f%n",
                    event.getOrderId(), total, discount * 100, event.getFinalAmount());
            Thread.sleep(5);
        }
    }

    /**
     * 5. 订单确认处理器
     */
    static class ConfirmHandler implements EventHandler<OrderEvent> {
        @Override
        public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) throws Exception {
            if (event.getStatus() == OrderEvent.OrderStatus.FAILED) return;

            event.setCompleteTime(System.currentTimeMillis());
            event.setStatus(OrderEvent.OrderStatus.COMPLETED);
            completedOrders.incrementAndGet();

            long latency = event.getCompleteTime() - event.getCreateTime();
            System.out.printf("  [5.确认] 订单 %d ✅ 完成! %s → 延迟=%dms%n",
                    event.getOrderId(), event, latency);
        }
    }

    /**
     * 6. 清理处理器（释放大对象引用）
     */
    static class CleanHandler implements EventHandler<OrderEvent> {
        @Override
        public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
            event.clear();
        }
    }
}
