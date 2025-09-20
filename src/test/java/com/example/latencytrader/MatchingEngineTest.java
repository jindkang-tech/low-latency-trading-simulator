package com.example.latencytrader;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple unit tests for the matching engine.  These tests cover basic
 * scenarios such as new order insertion, matching, cancel and replace.
 */
public class MatchingEngineTest {
    static class NoopListener implements MatchingEngine.MatchListener {
        @Override
        public void onAck(long clientOrderId, long orderId, String status, long tsIn) {}
        @Override
        public void onFill(long clientOrderId, long restingOrderId, long tradeId, int quantity, long price, long tsIn) {}
        @Override
        public void onMarketData(String instrument, long bidPrice, long askPrice) {}
    }

    @Test
    public void testAddAndCancel() {
        RiskManager rm = new RiskManager(1000, 1000);
        MatchingEngine engine = new MatchingEngine(rm, new NoopListener());
        long seq = 1;
        OrderEvent order = new OrderEvent(seq, seq, 1L, Side.BUY, 100, 10000L, "A", "XYZ");
        engine.onNewOrder(order);
        assertNotNull(engine.getOrderBook("XYZ").bestBid());
        // cancel
        CancelEvent cancel = new CancelEvent(seq+1, seq+1, 1L);
        engine.onCancel(cancel);
        assertNull(engine.getOrderBook("XYZ").bestBid());
    }

    @Test
    public void testMatching() {
        RiskManager rm = new RiskManager(1000, 1000);
        MatchingEngine engine = new MatchingEngine(rm, new NoopListener());
        long seq = 1;
        // Add sell order
        OrderEvent sell = new OrderEvent(seq, seq, 1L, Side.SELL, 100, 10000L, "A", "XYZ");
        engine.onNewOrder(sell);
        // Add buy order that matches
        OrderEvent buy = new OrderEvent(seq+1, seq+1, 2L, Side.BUY, 100, 10000L, "B", "XYZ");
        engine.onNewOrder(buy);
        // Book should be empty after full match
        assertTrue(engine.getOrderBook("XYZ").isEmpty());
    }
}