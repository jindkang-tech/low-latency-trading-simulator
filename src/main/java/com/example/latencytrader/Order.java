package com.example.latencytrader;

/**
 * Represents a resting order inside the order book.  Once an order is accepted it
 * is converted into this lightweight mutable representation used by the matching
 * engine.  Mutable state allows quantity to be decremented on fills without
 * allocations.  This class is deliberately kept simple; risk checks and
 * persistence are handled elsewhere.
 */
public final class Order {
    private final long orderId;
    private final long clientOrderId;
    private final Side side;
    private int quantity;
    private long price;
    private final long tsIn;

    public Order(long orderId, long clientOrderId, Side side, int quantity, long price, long tsIn) {
        this.orderId = orderId;
        this.clientOrderId = clientOrderId;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.tsIn = tsIn;
    }

    public long getOrderId() {
        return orderId;
    }

    public long getClientOrderId() {
        return clientOrderId;
    }

    public Side getSide() {
        return side;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public long getPrice() {
        return price;
    }

    public void setPrice(long price) {
        this.price = price;
    }

    public long getTsIn() {
        return tsIn;
    }
}
