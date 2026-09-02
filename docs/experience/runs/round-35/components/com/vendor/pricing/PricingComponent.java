package com.vendor.pricing;

import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.Tick;
import com.vendor.risk.RiskComponent;

/** VENDOR COMPONENT — do not modify. Typed references, so the graph is readable from the constructors. */
public class PricingComponent {
    public final Mid mid = new Mid();
    public final Adjusted adjusted;

    public PricingComponent(RiskComponent.Notional notional) {
        this.adjusted = new Adjusted(mid, notional);
    }

    public static class Mid extends EventLogNode {
        public transient double value;
        @OnEventHandler public boolean onTick(Tick t) {
            value = (t.bid() + t.ask()) / 2;
            auditLog.info("stage", "pricing.mid").info("value", value);
            return true;
        }
    }

    public static class Adjusted extends EventLogNode {
        private final Mid mid;
        private final RiskComponent.Notional notional;
        public transient double value;
        public Adjusted(Mid mid, RiskComponent.Notional notional) { this.mid = mid; this.notional = notional; }
        @OnTrigger public boolean calc() {
            value = mid.value * (1 + notional.value / 1_000_000);
            auditLog.info("stage", "pricing.adjusted").info("value", value);
            return true;
        }
    }
}
