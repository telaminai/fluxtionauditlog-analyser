package com.example.riskwatch.node;

import com.example.riskwatch.event.Trade;
import com.fluxtion.runtime.annotations.OnEventHandler;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Net position and average cost per symbol, built from the trade stream.
 *
 * <p>Average cost is only re-based while the position is growing in the same direction; reducing a
 * position leaves the cost basis alone, which is what a desk P&amp;L wants.
 */
public class PositionBook {

    private final Map<String, Integer> quantity = new LinkedHashMap<>();
    private final Map<String, Double> averageCost = new LinkedHashMap<>();
    private int tradeCount;

    @OnEventHandler
    public boolean onTrade(Trade trade) {
        String symbol = trade.symbol();
        int existingQty = quantity.getOrDefault(symbol, 0);
        double existingCost = averageCost.getOrDefault(symbol, 0.0);
        int newQty = existingQty + trade.quantity();

        if (existingQty == 0 || Integer.signum(existingQty) == Integer.signum(trade.quantity())) {
            // opening or adding: blend the cost basis
            double blended = (existingCost * existingQty + trade.price() * trade.quantity()) / newQty;
            averageCost.put(symbol, blended);
        } else if (Integer.signum(newQty) != Integer.signum(existingQty) && newQty != 0) {
            // flipped through zero: the new side starts at this trade price
            averageCost.put(symbol, trade.price());
        }

        quantity.put(symbol, newQty);
        tradeCount++;
        return true;
    }

    public Set<String> symbols() {
        return quantity.keySet();
    }

    public int quantityOf(String symbol) {
        return quantity.getOrDefault(symbol, 0);
    }

    public double averageCostOf(String symbol) {
        return averageCost.getOrDefault(symbol, 0.0);
    }

    public int getTradeCount() {
        return tradeCount;
    }
}
