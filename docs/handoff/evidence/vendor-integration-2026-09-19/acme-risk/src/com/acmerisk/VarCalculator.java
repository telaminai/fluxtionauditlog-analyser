package com.acmerisk;

import com.acmerisk.api.RiskPosition;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** One-day 99% parametric VaR: sum over symbols of |quantity * multiplier * price| * volatility * 2.326. */
public class VarCalculator extends EventLogNode implements com.telamin.fluxtion.runtime.node.NamedNode {
    @Override
    public String getName() { return "acmeVarCalculator"; }

    static final double Z_99 = 2.326;
    private final QuoteFeed quotes;
    private final PositionFeed positions;
    private double totalVar;

    public VarCalculator(QuoteFeed quotes, PositionFeed positions) {
        this.quotes = quotes;
        this.positions = positions;
    }

    double totalVar() { return totalVar; }

    @OnTrigger
    public boolean calculate() {
        double total = 0;
        for (RiskPosition p : positions.positions().values()) {
            total += Math.abs(p.quantity() * p.multiplier() * quotes.price(p.symbol())) * quotes.volatility(p.symbol()) * Z_99;
        }
        totalVar = total;
        auditLog.info("totalVar", totalVar);
        return true;
    }
}
