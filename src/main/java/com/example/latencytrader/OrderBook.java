package com.example.latencytrader;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * Simple in-memory order book keyed by price.  Uses separate maps for bids and
 * asks so that the best price on either side can be accessed efficiently.
 *
 * <p>Bids are stored in descending order (highest price first) and asks are stored
 * in ascending order (lowest price first).  Each price level holds a FIFO queue
 * of {@link Order} instances to enforce price-time priority.</p>
 */
public final class OrderBook {
    /** Map of bid price to queue of resting orders. */
    private final NavigableMap<Long, Deque<Order>> bids = new TreeMap<>((a, b) -> Long.compare(b, a));
    /** Map of ask price to queue of resting orders. */
    private final NavigableMap<Long, Deque<Order>> asks = new TreeMap<>();

    /**
     * Adds a new resting order to the book.  The order must already have been
     * validated by pre-trade risk checks.
     */
    public void add(Order order) {
        NavigableMap<Long, Deque<Order>> map = order.getSide() == Side.BUY ? bids : asks;
        map.computeIfAbsent(order.getPrice(), p -> new ArrayDeque<>()).addLast(order);
    }

    /**
     * Removes an order from the book completely.  This method is called when
     * a cancel request is processed.  The order is searched by order ID; if not
     * found, nothing is removed.  In a real system, order ID to position mapping
     * should be used to locate orders faster.
     */
    public boolean remove(long orderId) {
        // search both sides; this is O(n) across all price levels but acceptable
        for (NavigableMap<Long, Deque<Order>> side : new NavigableMap[]{bids, asks}) {
            for (Deque<Order> deque : side.values()) {
                for (Order ord : deque) {
                    if (ord.getOrderId() == orderId) {
                        deque.remove(ord);
                        if (deque.isEmpty()) {
                            side.values().removeIf(Deque::isEmpty);
                        }
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Modifies an existing order's price and quantity.  Locates the order by
     * server-assigned ID, removes it from its current price level, applies
     * the new values and re-inserts it at the back of the new price level
     * queue.  Returns {@code true} if the order was found and modified.
     */
    public boolean modify(long orderId, long newPrice, int newQuantity) {
        // Determine the side based on where the order is found
        for (NavigableMap<Long, Deque<Order>> side : new NavigableMap[]{bids, asks}) {
            for (Long priceKey : side.keySet()) {
                Deque<Order> deque = side.get(priceKey);
                for (Order ord : deque) {
                    if (ord.getOrderId() == orderId) {
                        // Remove from current queue
                        deque.remove(ord);
                        if (deque.isEmpty()) {
                            side.remove(priceKey);
                        }
                        // Update quantity and price
                        ord.setQuantity(newQuantity);
                        ord.setPrice(newPrice);
                        // Insert into new price level
                        side.computeIfAbsent(newPrice, p -> new ArrayDeque<>()).addLast(ord);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Returns the best bid price or null if no bids are present.
     */
    public Long bestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    /**
     * Returns the best ask price or null if no asks are present.
     */
    public Long bestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    /**
     * Returns whether the book has any resting orders.
     */
    public boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }

    /**
     * Returns the entire bid map for iteration (read-only).  The returned map
     * should not be modified.
     */
    public NavigableMap<Long, Deque<Order>> bids() {
        return bids;
    }

    /**
     * Returns the entire ask map for iteration (read-only).  The returned map
     * should not be modified.
     */
    public NavigableMap<Long, Deque<Order>> asks() {
        return asks;
    }
}
