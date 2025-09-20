package com.example.latencytrader;

/**
 * Represents a top-of-book market data update.  This event carries the best
 * bid and ask prices (in integer ticks) for a given instrument.  Simulated
 * ticks are injected into the matching engine via the ring buffer to update
 * the internal market view and to allow strategies to react to price changes.
 */
public record MarketDataEvent(long seq,
                              long tsIn,
                              String instrument,
                              long bidPrice,
                              long askPrice)
        implements Event {
    @Override
    public long seq() {
        return seq;
    }

    @Override
    public long tsIn() {
        return tsIn;
    }
}
