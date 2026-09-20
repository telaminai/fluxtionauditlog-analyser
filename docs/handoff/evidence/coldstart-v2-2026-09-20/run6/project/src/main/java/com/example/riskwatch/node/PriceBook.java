package com.example.riskwatch.node;

import com.example.riskwatch.event.MarketTick;
import com.fluxtion.runtime.annotations.OnEventHandler;

import java.util.HashMap;
import java.util.Map;

/**
 * Latest mid price per symbol.
 *
 * <p>The handler returns false when a tick repeats the price we already hold, which stops the rest
 * of the graph being recalculated for a no-op update.
 */
public class PriceBook {

    private final Map<String, Double> lastPrice = new HashMap<>();

    @OnEventHandler
    public boolean onTick(MarketTick tick) {
        Double previous = lastPrice.put(tick.symbol(), tick.price());
        return previous == null || previous != tick.price();
    }

    public double priceOf(String symbol) {
        return lastPrice.getOrDefault(symbol, Double.NaN);
    }

    public boolean hasPrice(String symbol) {
        return lastPrice.containsKey(symbol);
    }
}
