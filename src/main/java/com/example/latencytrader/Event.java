package com.example.latencytrader;

/**
 * Marker interface for events flowing into and out of the matching engine.
 *
 * <p>Each event carries a sequence number and an ingress timestamp.  The sequence number
 * allows events to be ordered deterministically across threads, while the timestamp
 * captures when the event entered the system.  Additional metadata may be defined
 * on specific event implementations.</p>
 */
public interface Event {
    /**
     * Returns the sequence number assigned to this event by the ingress sequencer.
     */
    long seq();

    /**
     * Returns the timestamp (in nanoseconds) when the event entered the system.
     */
    long tsIn();
}
