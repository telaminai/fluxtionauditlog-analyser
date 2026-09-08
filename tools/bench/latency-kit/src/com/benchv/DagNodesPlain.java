package com.benchv;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.E3;
import com.bench.E4;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;

/**
 * Round 63 §6 — the converging-tail shape, now with an audit trail available.
 *
 * <p><b>The no-audit baseline for this shape.</b> Identical graph, identical node bodies, but the
 * nodes do not extend {@code EventLogNode} and make no {@code auditLog} call at all — so there is no
 * audit machinery to be dropped by a profile and nothing to measure by accident. Built with
 * {@code LOWEST_LATENCY}. This is the denominator: audit cost is (audited - this), and without it the
 * audited numbers cannot be decomposed.
 *
 * <p>Original note: Unlike {@code DagNodes}, where only {@code Tail} publishes,
 * <b>every node on the path writes audit values</b> and they all converge into one record per event.
 * That is the realistic audited shape and it is far more demanding: a path of 7–8 nodes produces 7–8×
 * the entries, so the record's per-entry cost — a node name, a key and a value, written into the record
 * during the cycle — is multiplied rather than amortised.
 */
public class DagNodesPlain {

    public abstract static class DagNode {
        public transient double v;
    }

    public static final class R0 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E0 e) { v = e.v + 0; }
    }

    public static final class R1 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E1 e) { v = e.v + 1; }
    }

    public static final class R2 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E2 e) { v = e.v + 2; }
    }

    public static final class R3 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E3 e) { v = e.v + 3; }
    }

    public static final class R4 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E4 e) { v = e.v + 4; }
    }

    public static final class D1 extends DagNode {
        private final DagNode p;
        public D1(DagNode p) { this.p = p; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { v = p.v * 1.05 + 0.5; }
    }

    public static final class D3 extends DagNode {
        private final DagNode a, b, c;
        public D3(@AssignToField("a") DagNode a, @AssignToField("b") DagNode b,
                  @AssignToField("c") DagNode c) { this.a = a; this.b = b; this.c = c; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { v = a.v + b.v + c.v; }
    }

    /** The end of the shared tail — every node before it has already written into this record. */
    public static final class Tail extends DagNode {
        private final DagNode p;
        public Tail(DagNode p) { this.p = p; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() {
            v = p.v * 1.05 + 0.5;
        }
    }
}
