package com.example.latencytrader;

import java.util.Random;

/**
 * Simulates market data by generating random price ticks for a single instrument.  In a
 * real system this component would ingest data from a network feed and publish
 * snapshots or incremental updates to subscribers.  Here we simply print the
 * simulated best bid and ask to the console at regular intervals.
 */
public final class MarketDataFeeder implements Runnable {
    private final Sequencer sequencer;
    private final String instrument;
    private final long intervalMillis;
    private volatile boolean running = true;
    private final Random random = new Random();
    private long lastPrice = 10000L; // start at 100.00 in ticks

    public MarketDataFeeder(Sequencer sequencer, String instrument, long intervalMillis) {
        this.sequencer = sequencer;
        this.instrument = instrument;
        this.intervalMillis = intervalMillis;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            // simulate a random walk for bid and ask around last price
            int delta = random.nextInt(5) - 2;
            long bid = lastPrice + delta - 1; // bid slightly below mid
            long ask = lastPrice + delta + 1; // ask slightly above mid
            lastPrice = (bid + ask) / 2;
            long seq = System.nanoTime();
            long tsIn = seq;
            MarketDataEvent event = new MarketDataEvent(seq, tsIn, instrument, bid, ask);
            sequencer.publish(event);
        }
    }
}
