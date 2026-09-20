package com.example.riskflow.node;

import com.fluxtion.runtime.annotations.OnTrigger;
import com.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.fluxtion.runtime.audit.EventLogNode;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mark-to-market exposure: |net position| × mid, per symbol and in total.
 * Both parents are trigger parents — a new price revalues the book exactly as a new order does.
 */
public class ExposureCalculator extends EventLogNode {

    private final PositionBook positionBook;
    private final MarketData marketData;

    @FluxtionIgnore
    private final Map<String, Double> exposureBySymbol = new LinkedHashMap<>();
    private double totalExposure;

    public ExposureCalculator(PositionBook positionBook, MarketData marketData) {
        this.positionBook = positionBook;
        this.marketData = marketData;
    }

    @OnTrigger
    public boolean revalue() {
        exposureBySymbol.clear();
        totalExposure = 0;
        for (Map.Entry<String, Integer> position : positionBook.positions().entrySet()) {
            double mid = marketData.midFor(position.getKey());
            if (Double.isNaN(mid)) {
                continue;
            }
            double exposure = Math.abs(position.getValue()) * mid;
            exposureBySymbol.put(position.getKey(), exposure);
            totalExposure += exposure;
        }
        auditLog.info("totalExposure", totalExposure);
        return true;
    }

    public double exposureFor(String symbol) {
        return exposureBySymbol.getOrDefault(symbol, 0d);
    }

    public Map<String, Double> exposures() {
        return Collections.unmodifiableMap(exposureBySymbol);
    }

    public double getTotalExposure() {
        return totalExposure;
    }
}
