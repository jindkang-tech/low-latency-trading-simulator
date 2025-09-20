package com.example.latencytrader;

import org.HdrHistogram.Histogram;

/**
 * Publishes acknowledgements and fills to standard output and records basic
 * latency statistics using HdrHistogram.  In a production system this class
 * would broadcast messages to subscribed clients or downstream services.
 */
public final class Publisher implements MatchingEngine.MatchListener {
    private final Histogram ackLatencyHist = new Histogram(3600000000000L, 3);
    private final Histogram fillLatencyHist = new Histogram(3600000000000L, 3);
    private long ackCount = 0;
    private long fillCount = 0;
    private long mdCount = 0;

    @Override
    public void onAck(long clientOrderId, long orderId, String status, long tsIn) {
        long latency = System.nanoTime() - tsIn;
        ackLatencyHist.recordValue(latency);
        ackCount++;
        System.out.printf("ACK clOrdId=%d ordId=%d stat=%s latency=%d ns%n", clientOrderId, orderId, status, latency);
    }

    @Override
    public void onFill(long clientOrderId, long restingOrderId, long tradeId, int quantity, long price, long tsIn) {
        long latency = System.nanoTime() - tsIn;
        fillLatencyHist.recordValue(latency);
        fillCount++;
        System.out.printf("FILL clOrdId=%d restOrdId=%d tradeId=%d qty=%d px=%d latency=%d ns%n", clientOrderId, restingOrderId, tradeId, quantity, price, latency);
    }

    @Override
    public void onMarketData(String instrument, long bidPrice, long askPrice) {
        mdCount++;
        System.out.printf("MD_TICK instrument=%s bid=%d ask=%d%n", instrument, bidPrice, askPrice);
    }

    /**
     * Writes latency histograms and message counts to a CSV file.  Each call
     * appends a line with timestamp, counts and summary statistics.  After
     * writing the histograms are reset.
     */
    public synchronized void writeMetricsCsv(String fileName) {
        try (java.io.FileWriter fw = new java.io.FileWriter(fileName, true)) {
            long timestamp = System.currentTimeMillis();
            double ackMedian = ackLatencyHist.getValueAtPercentile(50.0);
            double ackP99 = ackLatencyHist.getValueAtPercentile(99.0);
            double fillMedian = fillLatencyHist.getValueAtPercentile(50.0);
            double fillP99 = fillLatencyHist.getValueAtPercentile(99.0);
            fw.write(timestamp + "," + ackCount + "," + fillCount + "," + mdCount + "," + ackMedian + "," + ackP99 + "," + fillMedian + "," + fillP99 + "\n");
            ackLatencyHist.reset();
            fillLatencyHist.reset();
            ackCount = 0;
            fillCount = 0;
            mdCount = 0;
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to write metrics CSV", e);
        }
    }
}
