package com.vendor.liquidity;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.pricing.Adjusted;
/** liquidity subsystem. Requires pricing.Adjusted. */
public class Score extends EventLogNode {
    private final Adjusted adjusted;
    public transient double value;
    public Score(Adjusted adjusted) { this.adjusted = adjusted; }
    @OnTrigger public boolean calc() {
        value = adjusted.value / 10;
        auditLog.info("stage", "liquidity.score").info("value", value);
        return true;
    }
}
