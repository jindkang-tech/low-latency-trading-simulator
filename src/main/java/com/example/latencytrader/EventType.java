package com.example.latencytrader;

/**
 * Enumeration of the various event types that flow through the matching engine.
 */
public enum EventType {
    /** Market data updates such as top-of-book snapshots or trades. */
    MD_TICK,
    /** New client order (limit or market). */
    NEW,
    /** Request to cancel a previously accepted order. */
    CANCEL,
    /** Request to replace a previously accepted order (quantity or price). */
    REPLACE;
}
