package com.vendor.liquidity;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.Tick;
import com.vendor.pricing.PricingComponent;

/** Vendor component: depth from the raw spread, then a score that needs the adjusted price. */
public class LiquidityComponent {
    public static class Depth extends EventLogNode {
        public transient double value;
        @OnEventHandler public boolean onTick(Tick t) {
            value = (t.ask() - t.bid()) * 100;
            auditLog.info("stage", "liquidity.depth").info("value", value);
            return true;
        }
    }
    public static class Score extends EventLogNode {
        private final PricingComponent.Adjusted adjusted;
        public transient double value;
        public Score(PricingComponent.Adjusted adjusted) { this.adjusted = adjusted; }
        @OnTrigger public boolean calc() {
            value = adjusted.value / 10;
            auditLog.info("stage", "liquidity.score").info("value", value);
            return true;
        }
    }
}
