package com.example.latencytrader;

/**
 * Immutable representation of a cancel request.  References the client order ID
 * associated with a previously accepted order.
 */
public record CancelEvent(long seq, long tsIn, long clientOrderId) implements Event {
    @Override
    public long seq() {
        return seq;
    }

    @Override
    public long tsIn() {
        return tsIn;
    }
}
