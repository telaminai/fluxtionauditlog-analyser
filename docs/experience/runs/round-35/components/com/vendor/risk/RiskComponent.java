package com.vendor.risk;

import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.vendor.pricing.PricingComponent;

/** VENDOR COMPONENT — do not modify. Typed references, so the graph is readable from the constructors. */
public class RiskComponent {
    public final Notional notional;
    public final Score score;

    public RiskComponent(PricingComponent.Mid mid) {
        this.notional = new Notional(mid);
        this.score = new Score(null);     // bound by the two-arg form below
    }
    public RiskComponent(PricingComponent.Mid mid, PricingComponent.Adjusted adjusted) {
        this.notional = new Notional(mid);
        this.score = new Score(adjusted);
    }

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

    public static class Score extends EventLogNode {
        private final PricingComponent.Adjusted adjusted;
        public transient double value;
        public Score(PricingComponent.Adjusted adjusted) { this.adjusted = adjusted; }
        @OnTrigger public boolean calc() {
            value = adjusted.value / 100;
            auditLog.info("stage", "risk.score").info("value", value);
            return true;
        }
    }
}
