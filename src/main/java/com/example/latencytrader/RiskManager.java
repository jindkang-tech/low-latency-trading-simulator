package com.example.latencytrader;

import java.util.HashMap;
import java.util.Map;

/**
 * Simple risk manager enforcing per-account order and position limits.  This MVP
 * implementation checks that incoming orders do not exceed a maximum order size
 * and that the resulting positions stay within defined bounds.  It maintains
 * running positions per account and updates them upon fills.
 */
public final class RiskManager {
    /** Maximum quantity permitted for any single order. */
    private final int maxOrderSize;
    /** Maximum absolute position permitted per account. */
    private final int maxPosition;
    /** Running position per account.  Positive for net long, negative for net short. */
    private final Map<String, Integer> positions = new HashMap<>();

    public RiskManager(int maxOrderSize, int maxPosition) {
        this.maxOrderSize = maxOrderSize;
        this.maxPosition = maxPosition;
    }

    /**
     * Returns whether the order passes pre-trade risk checks.  If the quantity
     * exceeds the maximum order size, the order is rejected.  This method does
     * not check positions because position impact depends on fills; positions
     * are updated via {@link #onFill(long, int, int)}.
     */
    public boolean accept(OrderEvent event) {
        // Basic size check
        if (event.quantity() <= 0 || event.quantity() > maxOrderSize) {
            return false;
        }
        // Position check: approximate new position if this order fully executes
        String account = event.account();
        int currentPos = positions.getOrDefault(account, 0);
        int sideMultiplier = event.side() == Side.BUY ? 1 : -1;
        long predicted = (long) currentPos + sideMultiplier * event.quantity();
        return Math.abs(predicted) <= maxPosition;
    }

    /**
     * Updates the position for an account upon receiving a fill.  The side is
     * indicated by +1 for buys and -1 for sells.  This method is called from
     * within the matching engine and should not be invoked concurrently.
     */
    public void onFill(long clientOrderId, int quantity, int sideMultiplier) {
        // Determine account from clientOrderId mapping.  In this simplified
        // implementation, the account is not deduced from clientOrderId; instead,
        // callers should provide the account separately.  See overloaded method.
    }

    /**
     * Updates the position for the given account.  Used by the matching engine
     * to record filled quantities.  Positive sideMultiplier indicates a buy
     * (long position), negative indicates a sell (short position).  If the new
     * position would exceed configured limits, an {@link IllegalStateException}
     * is thrown.  In a real system, positions would be updated atomically and
     * violations would cause trading to halt.
     */
    public void onFill(String account, int quantity, int sideMultiplier) {
        positions.putIfAbsent(account, 0);
        int current = positions.get(account);
        long newPos = (long) current + sideMultiplier * quantity;
        if (Math.abs(newPos) > maxPosition) {
            throw new IllegalStateException("Position limit exceeded for account " + account);
        }
        positions.put(account, (int) newPos);
    }

    /**
     * Returns the current position for the given account.
     */
    public int position(String account) {
        return positions.getOrDefault(account, 0);
    }
}
