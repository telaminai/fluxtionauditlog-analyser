package com.example.riskflow.node;

import com.example.riskflow.event.PriceUpdate;
import com.fluxtion.runtime.annotations.OnEventHandler;
import com.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.fluxtion.runtime.audit.EventLogNode;

import java.util.HashMap;
import java.util.Map;

/** Latest mid price per symbol. Entry point for {@link PriceUpdate}. */
public class MarketData extends EventLogNode {

    @FluxtionIgnore
    private final Map<String, Double> midBySymbol = new HashMap<>();

    @OnEventHandler
    public boolean onPriceUpdate(PriceUpdate event) {
        double mid = event.mid();
        Double previous = midBySymbol.put(event.symbol(), mid);
        auditLog.info("symbol", event.symbol()).info("mid", mid);
        // An unchanged mid is not a market event downstream cares about.
        return previous == null || previous != mid;
    }

    /** NaN when the symbol has never been quoted. */
    public double midFor(String symbol) {
        return midBySymbol.getOrDefault(symbol, Double.NaN);
    }
}
