package com.vendor.risk;

import com.vendor.Stage;

/** VENDOR COMPONENT — do not modify. Root of the risk subtree. */
public class RiskComponent {
    public final Notional notional;
    public final Score score;
    public RiskComponent(Stage midSource, Stage adjustedSource) {
        this.notional = new Notional(midSource);
        this.score = new Score(adjustedSource);
    }

    public static class Notional implements Stage {
        private final Stage mid; private double value;
        public Notional(Stage mid) { this.mid = mid; }
        public String name() { return "risk.notional"; }
        public void evaluate() { value = mid.value() * 1000; }
        public double value() { return value; }
    }

    public static class Score implements Stage {
        private final Stage adjusted; private double value;
        public Score(Stage adjusted) { this.adjusted = adjusted; }
        public String name() { return "risk.score"; }
        public void evaluate() { value = adjusted.value() / 100; }
        public double value() { return value; }
    }
}
