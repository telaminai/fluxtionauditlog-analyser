package com.benchv;

import com.bench.MarketTick;
import com.telamin.fluxtion.runtime.audit.EventLogNode;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;

/**
 * Round 63 — what does the audit trail cost?
 *
 * <p>10 nodes, of which <b>three</b> publish audit values ({@code tickIn}, {@code exposure},
 * {@code buffer}); the rest are silent. That matches the owner's requirement that audit records are
 * <i>"not on every node"</i> — a real system logs at the boundaries and the decision points, not at
 * every arithmetic step.
 */
public class AuditGraph {

    /** Logs. */
    public static final class TickIn extends EventLogNode {
        public transient double bid, ask;
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onTick(MarketTick t) {
            bid = t.bid; ask = t.ask;
            auditLog.info("bid", bid).info("ask", ask);
        }
    }

    public static final class Mid {
        private final TickIn t; public transient double value;
        public Mid(TickIn t) { this.t = t; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = (t.bid + t.ask) * 0.5; }
    }

    public static final class Spread {
        private final TickIn t; public transient double value;
        public Spread(TickIn t) { this.t = t; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = t.ask - t.bid; }
    }

    public static final class Ewma {
        private final Mid m; public transient double value; private transient long n;
        public Ewma(Mid m) { this.m = m; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = n++ == 0 ? m.value : 0.3 * m.value + 0.7 * value; }
    }

    public static final class Vol {
        private final Mid m; private final Ewma e; public transient double value;
        public Vol(Mid m, Ewma e) { this.m = m; this.e = e; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { double d = m.value - e.value; value = d < 0 ? -d : d; }
    }

    public static final class Notional {
        private final Mid m; private final Spread s; public transient double value;
        public Notional(Mid m, Spread s) { this.m = m; this.s = s; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = m.value * 1000.0 - s.value; }
    }

    /** Logs. */
    public static final class Exposure extends EventLogNode {
        private final Notional n; private final Vol v; public transient double value;
        public Exposure(Notional n, Vol v) { this.n = n; this.v = v; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() {
            value = n.value * (1.0 + v.value * 0.001);
            auditLog.info("exp", value);
        }
    }

    public static final class Limit {
        private final Exposure e; public transient long breaches;
        public Limit(Exposure e) { this.e = e; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { if (e.value > 108_000.0) breaches++; }
    }

    public static final class Charge {
        private final Exposure e; public transient double value;
        public Charge(Exposure e) { this.e = e; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { value = e.value * 0.08; }
    }

    /** Logs. */
    public static final class Buffer extends EventLogNode {
        private final Charge c; public transient double value; public transient long updates;
        public Buffer(Charge c) { this.c = c; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() {
            value = c.value * 1.25; updates++;
            auditLog.info("buf", value).info("upd", updates);
        }
    }
}
