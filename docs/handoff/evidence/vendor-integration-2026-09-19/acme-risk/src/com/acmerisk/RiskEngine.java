package com.acmerisk;

import com.acmerisk.api.RiskControl;
import com.telamin.fluxtion.runtime.annotations.ExportService;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * Acme Risk engine — the ONE class a customer references. It builds its own calculation chain; the customer
 * supplies RiskQuote and RiskPosition events, reads {@link #getTotalVar()} / {@link #isBreached()} downstream,
 * and may change the limit at runtime through the exported {@link RiskControl} service.
 */
public class RiskEngine extends EventLogNode implements @ExportService RiskControl, com.telamin.fluxtion.runtime.node.NamedNode {
    @Override
    public String getName() { return "acmeRiskEngine"; }

    private final VarCalculator calculator;
    private double varLimit = 10_000;
    private boolean breached;

    public RiskEngine() {
        this(new VarCalculator(new QuoteFeed(), new PositionFeed()));
    }

    /** Wiring constructor. Public because Fluxtion's generated processor rebuilds the graph with it. */
    public RiskEngine(VarCalculator calculator) {
        this.calculator = calculator;
    }

    public double getTotalVar() { return calculator.totalVar(); }
    public boolean isBreached() { return breached; }
    public double getVarLimit() { return varLimit; }

    @Override
    public void setVarLimit(double varLimit) {
        this.varLimit = varLimit;
    }

    @OnTrigger
    public boolean evaluate() {
        breached = calculator.totalVar() > varLimit;
        auditLog.info("totalVar", calculator.totalVar());
        auditLog.info("varLimit", varLimit);
        auditLog.info("breached", breached);
        return true;
    }
}
