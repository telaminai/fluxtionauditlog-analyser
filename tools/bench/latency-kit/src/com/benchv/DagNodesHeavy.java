package com.benchv;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.E3;
import com.bench.E4;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * Round 63 §6 — the converging-tail shape, now with an audit trail available.
 *
 * <p><b>Heavy nodes, converging logs.</b> Identical to {@code DagNodesConverging} except that each
 * node does real work — a short polynomial loop — instead of one multiply and one add. Round 62 found
 * node weight to be regime-defining on the dispatch path; this is the same axis in the audit regime.
 *
 * <p>Original note: Unlike {@code DagNodes}, where only {@code Tail} publishes,
 * <b>every node on the path writes audit values</b> and they all converge into one record per event.
 * That is the realistic audited shape and it is far more demanding: a path of 7–8 nodes produces 7–8×
 * the entries, so the record's per-entry cost — a node name, a key and a value, written into the record
 * during the cycle — is multiplied rather than amortised.
 */
public class DagNodesHeavy {

    public abstract static class DagNode extends EventLogNode {
        public transient double v;

        /** Real per-node work: a short Horner evaluation. No allocation, no branches on data. */
        protected static double work(double x) {
            double acc = 0.0;
            for (int i = 0; i < 24; i++) {
                acc = acc * 1.000001 + x * 0.5 + i;
            }
            return acc * 1e-6;
        }
    }

    public static final class R0 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E0 e) { v = work(e.v + 0); auditLog.info("v", v); }
    }

    public static final class R1 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E1 e) { v = work(e.v + 1); auditLog.info("v", v); }
    }

    public static final class R2 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E2 e) { v = work(e.v + 2); auditLog.info("v", v); }
    }

    public static final class R3 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E3 e) { v = work(e.v + 3); auditLog.info("v", v); }
    }

    public static final class R4 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E4 e) { v = work(e.v + 4); auditLog.info("v", v); }
    }

    public static final class D1 extends DagNode {
        private final DagNode p;
        public D1(DagNode p) { this.p = p; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { v = work(p.v * 1.05 + 0.5); auditLog.info("v", v); }
    }

    public static final class D3 extends DagNode {
        private final DagNode a, b, c;
        public D3(@AssignToField("a") DagNode a, @AssignToField("b") DagNode b,
                  @AssignToField("c") DagNode c) { this.a = a; this.b = b; this.c = c; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { v = work(a.v + b.v + c.v); auditLog.info("v", v); }
    }

    /** The end of the shared tail — every node before it has already written into this record. */
    public static final class Tail extends DagNode {
        private final DagNode p;
        public Tail(DagNode p) { this.p = p; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() {
            v = work(p.v * 1.05 + 0.5);
            auditLog.info("v", v).info("n", 1L);
        }
    }
}
