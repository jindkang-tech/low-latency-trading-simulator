package com.example.latencytrader;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * The Sequencer acts as an ingress queue for all events into the matching engine.
 * It serializes incoming events by placing them on a single-writer ring buffer
 * (implemented here with an {@link ArrayBlockingQueue}).  A dedicated thread
 * consumes events from the queue and dispatches them to the engine.  This
 * decouples I/O threads from the matching thread and preserves determinism.
 *
 * <p>In a production system you would likely use the LMAX Disruptor here;
 * swapping the {@code queue} with a Disruptor ring buffer is straightforward.</p>
 */
public final class Sequencer implements Runnable {
    private final BlockingQueue<Event> queue;
    private final MatchingEngine engine;
    private volatile boolean running = true;

    public Sequencer(MatchingEngine engine, int capacity) {
        this.engine = engine;
        this.queue = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * Publishes an event into the sequencer.  If the queue is full the call
     * will block until space becomes available.  In a high-performance system
     * you might prefer to drop or reject events when the buffer is full.
     */
    public void publish(Event event) {
        try {
            queue.put(event);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Stops the sequencer loop.  The thread will exit after processing all
     * currently queued events.
     */
    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        try {
            while (running || !queue.isEmpty()) {
                Event event = queue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null) {
                    dispatch(event);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void dispatch(Event event) {
        if (event instanceof OrderEvent) {
            engine.onNewOrder((OrderEvent) event);
        } else if (event instanceof CancelEvent) {
            engine.onCancel((CancelEvent) event);
        } else if (event instanceof ReplaceEvent) {
            engine.onReplace((ReplaceEvent) event);
        } else if (event instanceof MarketDataEvent) {
            engine.onMarketData((MarketDataEvent) event);
        } else {
            // unknown event type
            throw new IllegalArgumentException("Unsupported event type: " + event.getClass());
        }
    }
}
