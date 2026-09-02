package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.liquidity.Score;
/** risk subsystem, LATE stage. Requires liquidity.Score — so it runs after pricing and liquidity. */
public class Exposure extends EventLogNode {
    private final Notional notional; private final Score score;
    public transient double value;
    public Exposure(Notional notional, Score score) { this.notional = notional; this.score = score; }
    @OnTrigger public boolean calc() {
        value = notional.value * (1 + score.value / 1000);
        auditLog.info("stage", "risk.exposure").info("value", value);
        return true;
    }
}
