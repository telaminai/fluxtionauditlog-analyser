package com.example.myapp.node;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Records the net position and net cash per symbol from Trade events.
 * MarkToMarketNode revalues these downstream via {@code @OnTrigger}.
 */
public class PositionNode extends com.telamin.fluxtion.runtime.audit.EventLogNode {

    private final transient Map<String, Double> positions = new HashMap<>();
    private final transient Map<String, Double> cash = new HashMap<>();
    private final transient Map<String, Double> lastTradePrices = new HashMap<>();

    public Map<String, Double> getPositions() {
        return Collections.unmodifiableMap(positions);
    }

    public double getPosition(String symbol) {
        return positions.getOrDefault(symbol, 0.0);
    }

    /** Net cash from trading the symbol: sells add, buys subtract. */
    public double getCash(String symbol) {
        return cash.getOrDefault(symbol, 0.0);
    }

    /** Fallback mark until a PriceUpdate for the symbol arrives. */
    public Double getLastTradePrice(String symbol) {
        return lastTradePrices.get(symbol);
    }

    @com.telamin.fluxtion.runtime.annotations.OnEventHandler
@javax.annotation.processing.Generated("fluxtion-starter")
public boolean onTrade(com.example.myapp.event.Trade event) {
    positions.merge(event.symbol(), event.quantity(), Double::sum);
    cash.merge(event.symbol(), -event.quantity() * event.price(), Double::sum);
    lastTradePrices.put(event.symbol(), event.price());
    auditLog.info("symbol", event.symbol());
    auditLog.info("quantity", event.quantity());
    auditLog.info("tradePrice", event.price());
    auditLog.info("position", getPosition(event.symbol()));
    return true;
}
}
