package com.example.latencytrader;

import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.NavigableMap;

/**
 * Core matching engine responsible for maintaining the order book, matching incoming
 * orders, and emitting acknowledgements and fills.  The engine is single
 * threaded: all calls to its public methods must be serialized on the same
 * thread in order to guarantee deterministic behaviour and avoid locks.
 */
public final class MatchingEngine {
    // Order books keyed by instrument.  Each instrument maintains its own bid/ask book.
    private final java.util.Map<String, OrderBook> books = new java.util.HashMap<>();
    private final RiskManager riskManager;
    private final MatchListener listener;

    // Track side per client order ID to support replace requests.  Real systems
    // would use an order management component.
    private final java.util.Map<Long, Side> sideMap = new java.util.HashMap<>();

    // Track account per client order ID to support replace requests.
    private final java.util.Map<Long, String> accountMap = new java.util.HashMap<>();

    // Track instrument per client order ID.
    private final java.util.Map<Long, String> instrumentMap = new java.util.HashMap<>();

    // Map client order ID to server-assigned order ID.  Used for robust cancel/replace.
    private final java.util.Map<Long, Long> clientToServerIdMap = new java.util.HashMap<>();

    // simple order ID generator for accepted orders
    private long nextOrderId = 1;
    private long nextTradeId = 1;

    // Latest observed market prices per instrument.  For this MVP we track a
    // single instrument and simply store the best bid/ask.
    private volatile long lastBidPrice = 0;
    private volatile long lastAskPrice = 0;

    public MatchingEngine(RiskManager riskManager, MatchListener listener) {
        this.riskManager = riskManager;
        this.listener = listener;
    }

    /**
     * Returns the order book for inspection (read-only).  This method is
     * primarily intended for use by components such as the market data feeder.
     */
    /**
     * Returns the order book for the given instrument, creating it lazily if
     * necessary.
     */
    public OrderBook getOrderBook(String instrument) {
        return books.computeIfAbsent(instrument, k -> new OrderBook());
    }
    /**
     * Convenience method returning the default order book (used by tests).
     */
    public OrderBook getOrderBook() {
        return getOrderBook("DEFAULT");
    }

    /**
     * Processes an incoming market data event.  Updates the internal best bid
     * and ask prices and publishes the update to the listener.  In a real
     * implementation this method might trigger order book recalculations,
     * strategy evaluations or risk checks.
     */
    public void onMarketData(MarketDataEvent event) {
        // Update last seen market prices
        this.lastBidPrice = event.bidPrice();
        this.lastAskPrice = event.askPrice();
        // Notify listener of the update
        listener.onMarketData(event.instrument(), event.bidPrice(), event.askPrice());
    }

    /**
     * Processes a new order event.  Performs risk checks, attempts to match
     * immediately against the opposite side of the book, and posts any
     * remaining quantity to the book as a resting order.
     */
    public void onNewOrder(OrderEvent event) {
        // Pre-trade risk check
        if (!riskManager.accept(event)) {
            listener.onAck(event.clientOrderId(), -1, "REJECTED_RISK");
            return;
        }

        int qtyRemaining = event.quantity();
        final boolean isBuy = event.side() == Side.BUY;
        long price = event.price();

        // Determine the book for this instrument
        OrderBook book = getOrderBook(event.instrument());
        // Match against resting orders on the opposite side
        List<Fill> fills = new ArrayList<>();
        if (isBuy) {
            // buy order matches against lowest asks
            NavigableMap<Long, Deque<Order>> asks = book.asks();
            while (qtyRemaining > 0 && !asks.isEmpty()) {
                Long bestAskPrice = asks.firstKey();
                // For a market order (price == 0) or limit order with price >= best ask
                if (price == 0 || price >= bestAskPrice) {
                    Deque<Order> level = asks.get(bestAskPrice);
                    Order resting = level.peekFirst();
                    if (resting == null) {
                        asks.pollFirstEntry();
                        continue;
                    }
                    int matched = Math.min(qtyRemaining, resting.getQuantity());
                    qtyRemaining -= matched;
                    resting.setQuantity(resting.getQuantity() - matched);
                    fills.add(new Fill(nextTradeId++, event.clientOrderId(), resting.getOrderId(), matched, bestAskPrice, event.account(), event.tsIn()));
                    if (resting.getQuantity() == 0) {
                        level.removeFirst();
                        if (level.isEmpty()) {
                            asks.remove(bestAskPrice);
                        }
                    }
                } else {
                    break;
                }
            }
        } else {
            // sell order matches against highest bids
            NavigableMap<Long, Deque<Order>> bids = book.bids();
            while (qtyRemaining > 0 && !bids.isEmpty()) {
                Long bestBidPrice = bids.firstKey();
                // For a market order (price == 0) or limit order with price <= best bid
                if (price == 0 || price <= bestBidPrice) {
                    Deque<Order> level = bids.get(bestBidPrice);
                    Order resting = level.peekFirst();
                    if (resting == null) {
                        bids.pollFirstEntry();
                        continue;
                    }
                    int matched = Math.min(qtyRemaining, resting.getQuantity());
                    qtyRemaining -= matched;
                    resting.setQuantity(resting.getQuantity() - matched);
                    fills.add(new Fill(nextTradeId++, event.clientOrderId(), resting.getOrderId(), matched, bestBidPrice, event.account(), event.tsIn()));
                    if (resting.getQuantity() == 0) {
                        level.removeFirst();
                        if (level.isEmpty()) {
                            bids.remove(bestBidPrice);
                        }
                    }
                } else {
                    break;
                }
            }
        }

        // Emit fills
        for (Fill f : fills) {
            // Update positions for the account.  The side multiplier is positive for buys (long) and
            // negative for sells (short).
            riskManager.onFill(f.account, f.quantity, isBuy ? 1 : -1);
            listener.onFill(f.clientOrderId, f.restingOrderId, f.tradeId, f.quantity, f.price, f.tsIn);
        }

        // If quantity remains, add to book as a new resting order
        if (qtyRemaining > 0) {
            long assignedId = nextOrderId++;
            Order resting = new Order(assignedId, event.clientOrderId(), event.side(), qtyRemaining, price, event.tsIn());
            book.add(resting);
            // Track the side, account and instrument of the order for future replaces
            sideMap.put(event.clientOrderId(), event.side());
            accountMap.put(event.clientOrderId(), event.account());
            instrumentMap.put(event.clientOrderId(), event.instrument());
            clientToServerIdMap.put(event.clientOrderId(), assignedId);
            listener.onAck(event.clientOrderId(), assignedId, fills.isEmpty() ? "NEW_ACCEPTED" : "PARTIALLY_FILLED", event.tsIn());
        } else {
            // fully filled
            listener.onAck(event.clientOrderId(), -1, fills.isEmpty() ? "REJECTED" : "FILLED", event.tsIn());
        }
    }

    /**
     * Processes a cancel request.  Searches the order book for the given order ID and
     * removes it if found.  Emits an acknowledgement indicating success or failure.
     */
    public void onCancel(CancelEvent event) {
        Long serverOrderId = clientToServerIdMap.get(event.clientOrderId());
        boolean removed = false;
        if (serverOrderId != null) {
            String instrument = instrumentMap.get(event.clientOrderId());
            OrderBook book = getBook(instrument);
            removed = book.remove(serverOrderId);
        }
        if (removed) {
            listener.onAck(event.clientOrderId(), serverOrderId, "CANCELLED", event.tsIn());
            // Remove tracked state
            sideMap.remove(event.clientOrderId());
            accountMap.remove(event.clientOrderId());
            instrumentMap.remove(event.clientOrderId());
            clientToServerIdMap.remove(event.clientOrderId());
        } else {
            listener.onAck(event.clientOrderId(), -1, "CANCEL_REJECT", event.tsIn());
        }
    }

    /**
     * Processes a replace request.  Implemented as a cancel followed by a new order.
     */
    public void onReplace(ReplaceEvent event) {
        Long serverOrderId = clientToServerIdMap.get(event.clientOrderId());
        Side originalSide = sideMap.get(event.clientOrderId());
        String originalAccount = accountMap.get(event.clientOrderId());
        String originalInstrument = instrumentMap.get(event.clientOrderId());
        if (serverOrderId == null || originalSide == null || originalAccount == null || originalInstrument == null) {
            listener.onAck(event.clientOrderId(), -1, "REPLACE_REJECT", event.tsIn());
            return;
        }
        // Attempt in-place modification: update price and quantity on the existing order
        // Determine the book based on the original instrument
        OrderBook book = getBook(originalInstrument);
        boolean modified = book.modify(serverOrderId, event.newPrice(), event.newQuantity());
        if (modified) {
            // Send acknowledgement using existing server order ID
            listener.onAck(event.clientOrderId(), serverOrderId, "REPLACED", event.tsIn());
        } else {
            // Fall back to cancel + new if modification not possible
            boolean removed = book.remove(serverOrderId);
            if (removed) {
                sideMap.remove(event.clientOrderId());
                accountMap.remove(event.clientOrderId());
                instrumentMap.remove(event.clientOrderId());
                clientToServerIdMap.remove(event.clientOrderId());
                OrderEvent newOrder = new OrderEvent(event.seq(), event.tsIn(), event.clientOrderId(), originalSide, event.newQuantity(), event.newPrice(), originalAccount, originalInstrument);
                onNewOrder(newOrder);
                listener.onAck(event.clientOrderId(), -1, "REPLACED", event.tsIn());
            } else {
                listener.onAck(event.clientOrderId(), -1, "REPLACE_REJECT", event.tsIn());
            }
        }
    }

    /**
     * Small internal representation of a fill for convenience.
     */
    private static final class Fill {
        final long tradeId;
        final long clientOrderId;
        final long restingOrderId;
        final int quantity;
        final long price;
        final String account;
        final long tsIn;

        Fill(long tradeId, long clientOrderId, long restingOrderId, int quantity, long price, String account, long tsIn) {
            this.tradeId = tradeId;
            this.clientOrderId = clientOrderId;
            this.restingOrderId = restingOrderId;
            this.quantity = quantity;
            this.price = price;
            this.account = account;
            this.tsIn = tsIn;
        }
    }

    /**
     * Listener interface used by the matching engine to emit acknowledgements and fills.
     * The implementation may log to the console, publish to network subscribers, or
     * forward to a metrics system.
     */
    public interface MatchListener {
        void onAck(long clientOrderId, long orderId, String status, long tsIn);
        void onFill(long clientOrderId, long restingOrderId, long tradeId, int quantity, long price, long tsIn);
        /**
         * Invoked when a market data tick is processed.  The listener may use
         * this information to publish top-of-book quotes to subscribers or to
         * update UI components.
         */
        default void onMarketData(String instrument, long bidPrice, long askPrice) {}
    }
}
