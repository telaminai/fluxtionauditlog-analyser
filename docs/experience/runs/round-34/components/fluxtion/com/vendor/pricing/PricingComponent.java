package com.vendor.pricing;

import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** VENDOR COMPONENT — do not modify. Root of the pricing subtree. */
public class PricingComponent extends EventLogNode {
    public final Mid mid;
    public final Adjusted adjusted;
    public PricingComponent(Object notionalSource) {
        this.mid = new Mid();
        this.adjusted = new Adjusted(mid, notionalSource);
    }

    public static class Mid extends EventLogNode {
        public transient double value;
        @OnEventHandler public boolean onTick(com.vendor.Tick t) {
            value = (t.bid() + t.ask()) / 2;
            auditLog.info("mid", value);
            return true;
        }
    }

    public static class Adjusted extends EventLogNode {
        private final Mid mid;
        private final Object notional;
        public transient double value;
        public Adjusted(Mid mid, Object notional) { this.mid = mid; this.notional = notional; }
        @OnTrigger public boolean calc() {
            double n = com.vendor.Bridge.notionalOf(notional);
            value = mid.value * (1 + n / 1_000_000);
            auditLog.info("adjusted", value);
            return true;
        }
    }
}
