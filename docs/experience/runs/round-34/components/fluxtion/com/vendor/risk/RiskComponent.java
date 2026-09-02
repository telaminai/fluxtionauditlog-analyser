package com.vendor.risk;

import com.telamin.fluxtion.runtime.annotations.*;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/** VENDOR COMPONENT — do not modify. Root of the risk subtree. */
public class RiskComponent extends EventLogNode {
    public final Notional notional;
    public final Score score;
    public RiskComponent(Object midSource, Object adjustedSource) {
        this.notional = new Notional(midSource);
        this.score = new Score(adjustedSource);
    }

    public static class Notional extends EventLogNode {
        private final Object mid;
        public transient double value;
        public Notional(Object mid) { this.mid = mid; }
        @OnTrigger public boolean calc() {
            value = com.vendor.Bridge.midOf(mid) * 1000;
            auditLog.info("notional", value);
            return true;
        }
    }

    public static class Score extends EventLogNode {
        private final Object adjusted;
        public transient double value;
        public Score(Object adjusted) { this.adjusted = adjusted; }
        @OnTrigger public boolean calc() {
            value = com.vendor.Bridge.adjustedOf(adjusted) / 100;
            auditLog.info("score", value);
            return true;
        }
    }
}
