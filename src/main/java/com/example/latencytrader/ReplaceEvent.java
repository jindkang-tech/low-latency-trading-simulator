package com.example.latencytrader;

/**
 * Immutable representation of an order modification request.  A replace request
 * identifies the original client order ID and provides new values for quantity
 * and/or price.  The matching engine may partially fill the original order before
 * the replace is processed; in this MVP implementation, the replace simply
 * cancels and re-enters the order at the back of the queue.
 */
public record ReplaceEvent(
        long seq,
        long tsIn,
        long clientOrderId,
        int newQuantity,
        long newPrice,
        String account)
        implements Event {
    @Override
    public long seq() {
        return seq;
    }

    @Override
    public long tsIn() {
        return tsIn;
    }
    public String account() {
        return account;
    }
}
