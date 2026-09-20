package com.example.riskwatch.node;

import com.example.riskwatch.node.MarkToMarket.SymbolRisk;
import com.fluxtion.runtime.annotations.OnTrigger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Compares marked exposure against the limits and raises an alert on each state change.
 *
 * <p>Triggered by either parent: a new valuation or a limit change can both move a symbol across
 * its limit, and neither call site has to know about the other.
 */
public class RiskMonitor {

    private final MarkToMarket markToMarket;
    private final LimitStore limitStore;
    private final Set<String> breached = new HashSet<>();
    private final List<String> alerts = new ArrayList<>();

    public RiskMonitor(MarkToMarket markToMarket, LimitStore limitStore) {
        this.markToMarket = markToMarket;
        this.limitStore = limitStore;
    }

    @OnTrigger
    public boolean checkLimits() {
        int alertsBefore = alerts.size();
        for (SymbolRisk symbolRisk : markToMarket.getRisk()) {
            String symbol = symbolRisk.symbol();
            double limit = limitStore.limitFor(symbol);
            boolean overLimit = symbolRisk.exposure() > limit;
            boolean wasOverLimit = breached.contains(symbol);
            if (overLimit && !wasOverLimit) {
                breached.add(symbol);
                alerts.add(String.format("BREACH  %-5s exposure %,.0f > limit %,.0f",
                        symbol, symbolRisk.exposure(), limit));
            } else if (!overLimit && wasOverLimit) {
                breached.remove(symbol);
                alerts.add(String.format("CLEARED %-5s exposure %,.0f <= limit %,.0f",
                        symbol, symbolRisk.exposure(), limit));
            }
        }
        for (int i = alertsBefore; i < alerts.size(); i++) {
            System.out.println("   !! " + alerts.get(i));
        }
        return alerts.size() > alertsBefore;
    }

    public List<String> getAlerts() {
        return alerts;
    }

    public Set<String> getBreachedSymbols() {
        return breached;
    }
}
