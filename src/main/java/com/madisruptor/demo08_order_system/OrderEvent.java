package com.madisruptor.demo08_order_system;

/**
 * 订单事件 - 贯穿整个订单处理流水线的事件对象
 */
public class OrderEvent {

    private long orderId;
    private String userId;
    private String productId;
    private double price;
    private int quantity;
    private double totalAmount;
    private double discount;
    private double finalAmount;

    // 处理状态
    private OrderStatus status;
    private String riskResult;
    private String inventoryResult;
    private long createTime;
    private long completeTime;

    public enum OrderStatus {
        CREATED, VALIDATED, RISK_CHECKED, INVENTORY_LOCKED, PRICED, COMPLETED, FAILED
    }

    // Getters and Setters
    public long getOrderId() { return orderId; }
    public void setOrderId(long orderId) { this.orderId = orderId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public double getDiscount() { return discount; }
    public void setDiscount(double discount) { this.discount = discount; }
    public double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(double finalAmount) { this.finalAmount = finalAmount; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getRiskResult() { return riskResult; }
    public void setRiskResult(String riskResult) { this.riskResult = riskResult; }
    public String getInventoryResult() { return inventoryResult; }
    public void setInventoryResult(String inventoryResult) { this.inventoryResult = inventoryResult; }
    public long getCreateTime() { return createTime; }
    public void setCreateTime(long createTime) { this.createTime = createTime; }
    public long getCompleteTime() { return completeTime; }
    public void setCompleteTime(long completeTime) { this.completeTime = completeTime; }

    public void clear() {
        orderId = 0; userId = null; productId = null;
        price = 0; quantity = 0; totalAmount = 0;
        discount = 0; finalAmount = 0;
        status = null; riskResult = null; inventoryResult = null;
        createTime = 0; completeTime = 0;
    }

    @Override
    public String toString() {
        return String.format("Order{id=%d, user=%s, product=%s, qty=%d, final=%.2f, status=%s}",
                orderId, userId, productId, quantity, finalAmount, status);
    }
}
