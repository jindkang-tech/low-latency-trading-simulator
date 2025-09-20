package com.example.latencytrader;

/**
 * Immutable representation of an order event submitted by a client.  The order may be
 * a new order (limit or market) or a replacement of an existing order.  Cancellations
 * are represented using {@link CancelEvent} which references the original order ID.
 *
 * <p>The price is represented in integer ticks (e.g. cents) to avoid floating point
 * rounding issues.  A price of zero denotes a market order.  Quantity is assumed to
 * be a positive integer.  Time in force (TIF) fields are omitted in this basic
 * implementation but can be added later.</p>
 */
public record OrderEvent(
        long seq,
        long tsIn,
        long clientOrderId,
        Side side,
        int quantity,
        long price,
        String account,
        String instrument)
        implements Event {
    @Override
    public long seq() {
        return seq;
    }

    @Override
    public long tsIn() {
        return tsIn;
    }

    /**
     * Returns the account identifier associated with this order.  Accounts are used
     * by the {@link RiskManager} to enforce position limits.
     */
    public String account() {
        return account;
    }

    /**
     * Returns the instrument symbol for this order.
     */
    public String instrument() {
        return instrument;
    }
}
