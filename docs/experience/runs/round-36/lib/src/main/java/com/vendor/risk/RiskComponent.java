package com.vendor.risk;
import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.liquidity.LiquidityComponent;
import com.vendor.pricing.PricingComponent;

/** Vendor component: notional from the mid, then exposure needing a liquidity score. */
public class RiskComponent {
    public static class Notional extends EventLogNode {
        private final PricingComponent.Mid mid;
        public transient double value;
        public Notional(PricingComponent.Mid mid) { this.mid = mid; }
        @OnTrigger public boolean calc() {
            value = mid.value * 1000;
            auditLog.info("stage", "risk.notional").info("value", value);
            return true;
        }
    }
    public static class Exposure extends EventLogNode {
        private final Notional notional; private final LiquidityComponent.Score score;
        public transient double value;
        public Exposure(Notional notional, LiquidityComponent.Score score) { this.notional = notional; this.score = score; }
        @OnTrigger public boolean calc() {
            value = notional.value * (1 + score.value / 1000);
            auditLog.info("stage", "risk.exposure").info("value", value);
            return true;
        }
    }
}
