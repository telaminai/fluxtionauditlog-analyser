package com.vendor;
/**
 * Idiomatic component. Charge, buffer and fee, plus breach reporting. It POLLS risk for the breach
 * state, because a coarse component cannot be triggered by another component's internal detector.
 */
public class Capital {
    private final Risk risk;
    private double charge, buffer, fee, pct = 0.08, qty = 1.0;
    private FeeStrategy strategy;
    private AlertSink alerts = a -> {};
    private int breaches, alertCount;

    public Capital(Risk risk) {
        this.risk = risk;
        this.strategy = new FeeStrategy() {
            public double fee(double e) { return e * 0.01; }
            public String name() { return "default"; }
        };
    }
    public void feeStrategy(FeeStrategy s) { this.strategy = s; }
    public void alertSink(AlertSink s) { this.alerts = s; }

    public void onTick(Events.Tick t)   { recompute(); }
    public void onRate(Events.Rate r)   { recompute(); }
    public void onTrade(Events.Trade t) { qty = t.qty(); recompute(); }
    public boolean onConfig(Events.Config c) {
        if (!"chargePct".equals(c.key())) return false;
        pct = c.value(); recompute(); return true;
    }
    private void recompute() {
        charge = risk.exposure() * pct;
        buffer = charge + risk.var() * qty / 100;
        fee    = strategy.fee(risk.exposure());
        Audit.log("capital.charge", charge);
        Audit.log("capital.buffer", buffer);
        Audit.log("capital.fee", fee);
        if (risk.limitBreached()) {
            breaches++;   Audit.log("capital.breachCount", breaches);
            alerts.publish(String.format("BREACH charge=%.2f", charge));
            alertCount++; Audit.log("capital.alertCount", alertCount);
        }
    }
    public int breaches()   { return breaches; }
    public int alertCount() { return alertCount; }
}
