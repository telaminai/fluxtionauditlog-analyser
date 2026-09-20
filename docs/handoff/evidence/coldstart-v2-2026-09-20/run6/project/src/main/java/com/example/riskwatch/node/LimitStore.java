package com.example.riskwatch.node;

import com.example.riskwatch.event.RiskLimitUpdate;
import com.fluxtion.runtime.annotations.OnEventHandler;

import java.util.HashMap;
import java.util.Map;

/** Per symbol gross exposure limits, with a desk-wide default until the risk desk overrides one. */
public class LimitStore {

    private final double defaultLimit;
    private final Map<String, Double> limits = new HashMap<>();

    public LimitStore(double defaultLimit) {
        this.defaultLimit = defaultLimit;
    }

    @OnEventHandler
    public boolean onLimitUpdate(RiskLimitUpdate update) {
        limits.put(update.symbol(), update.maxExposure());
        return true;
    }

    public double limitFor(String symbol) {
        return limits.getOrDefault(symbol, defaultLimit);
    }
}
