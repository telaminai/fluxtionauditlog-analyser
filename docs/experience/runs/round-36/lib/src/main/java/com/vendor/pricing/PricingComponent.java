package com.vendor.pricing;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.Tick;
import com.vendor.liquidity.LiquidityComponent;

/** Vendor component: mid price, then a spread-adjusted price that needs a liquidity depth. */
public class PricingComponent {
    public static class Mid extends EventLogNode {
        public transient double value;
        @OnEventHandler public boolean onTick(Tick t) {
            value = (t.bid() + t.ask()) / 2;
            auditLog.info("stage", "pricing.mid").info("value", value);
            return true;
        }
    }
    public static class Adjusted extends EventLogNode {
        private final Mid mid; private final LiquidityComponent.Depth depth;
        public transient double value;
        public Adjusted(Mid mid, LiquidityComponent.Depth depth) { this.mid = mid; this.depth = depth; }
        @OnTrigger public boolean calc() {
            value = mid.value * (1 + depth.value / 10_000);
            auditLog.info("stage", "pricing.adjusted").info("value", value);
            return true;
        }
    }
}
