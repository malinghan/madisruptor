package com.madisruptor.demo03_multi_producer_consumer;

/**
 * 交易事件
 */
public class TradeEvent {

    private long tradeId;
    private String symbol;
    private double price;
    private int quantity;
    private String producerName;

    public long getTradeId() { return tradeId; }
    public void setTradeId(long tradeId) { this.tradeId = tradeId; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getProducerName() { return producerName; }
    public void setProducerName(String producerName) { this.producerName = producerName; }

    public void clear() {
        this.tradeId = 0;
        this.symbol = null;
        this.price = 0;
        this.quantity = 0;
        this.producerName = null;
    }

    @Override
    public String toString() {
        return String.format("Trade{id=%d, symbol=%s, price=%.2f, qty=%d, from=%s}",
                tradeId, symbol, price, quantity, producerName);
    }
}
