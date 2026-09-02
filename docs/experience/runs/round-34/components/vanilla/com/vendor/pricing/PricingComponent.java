package com.vendor.pricing;

import com.vendor.Stage;
import com.vendor.Tick;

/** VENDOR COMPONENT — do not modify. Root of the pricing subtree. */
public class PricingComponent {
    public final Mid mid;
    public final Adjusted adjusted;
    public PricingComponent(Stage notionalSource) {
        this.mid = new Mid();
        this.adjusted = new Adjusted(mid, notionalSource);
    }

    public static class Mid implements Stage {
        private double value; private Tick last;
        public void onTick(Tick t) { last = t; }
        public String name() { return "pricing.mid"; }
        public void evaluate() { if (last != null) value = (last.bid() + last.ask()) / 2; }
        public double value() { return value; }
    }

    public static class Adjusted implements Stage {
        private final Mid mid; private final Stage notional; private double value;
        public Adjusted(Mid mid, Stage notional) { this.mid = mid; this.notional = notional; }
        public String name() { return "pricing.adjusted"; }
        public void evaluate() { value = mid.value() * (1 + notional.value() / 1_000_000); }
        public double value() { return value; }
    }
}
