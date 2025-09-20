package com.example.latencytrader;

/**
 * Entry point for the trading simulator.  Creates the matching engine and
 * supporting components and starts a simple command-line interface.  Orders
 * can be entered manually via standard input.
 *
 * <p>To run the simulator after building with Maven:</p>
 *
 * <pre>
 * mvn package
 * java -jar target/low-latency-trading-simulator-0.1.0.jar
 * </pre>
 */
public final class Main {
    public static void main(String[] args) {
        // Configure risk manager: max order size 1000, max position 5000 shares
        RiskManager riskManager = new RiskManager(1000, 5000);
        Publisher publisher = new Publisher();
        MatchingEngine engine = new MatchingEngine(riskManager, publisher);
        // Create a sequencer with capacity 65536 entries
        Sequencer sequencer = new Sequencer(engine, 65536);

        // Start sequencer thread
        Thread seqThread = new Thread(sequencer, "SequencerThread");
        seqThread.start();

        // Start a market data feeder for instrument XYZ on its own thread
        MarketDataFeeder feeder = new MarketDataFeeder(sequencer, "XYZ", 1000);
        Thread mdThread = new Thread(feeder, "MarketDataFeeder");
        mdThread.setDaemon(true);
        mdThread.start();

        // Start a network order gateway on port 9000
        NetworkOrderGateway netGateway = new NetworkOrderGateway(sequencer, 9000);
        Thread netThread = new Thread(netGateway, "NetworkOrderGateway");
        netThread.setDaemon(true);
        netThread.start();

        // Start a console order gateway on its own thread for local input
        OrderGateway consoleGateway = new OrderGateway(sequencer, "XYZ");
        Thread consoleThread = new Thread(consoleGateway, "ConsoleOrderGateway");
        consoleThread.setDaemon(true);
        consoleThread.start();

        // Start a periodic metrics logger thread that writes metrics to CSV every 5 seconds
        Thread metricsThread = new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(5000);
                    publisher.writeMetricsCsv("metrics.csv");
                }
            } catch (InterruptedException ignored) {
            }
        }, "MetricsLogger");
        metricsThread.setDaemon(true);
        metricsThread.start();

        // Wait for the console gateway to finish (Ctrl+D) then shut down
        try {
            consoleThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Stop other services gracefully
        feeder.stop();
        netGateway.stop();
        sequencer.stop();
        // Wait for sequencer to drain
        try {
            seqThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        // Write final metrics
        publisher.writeMetricsCsv("metrics.csv");
    }
}
