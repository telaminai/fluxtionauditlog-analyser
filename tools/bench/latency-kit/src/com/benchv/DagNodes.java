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
 * <p>{@code DagNode} extends {@link EventLogNode}, so every node <b>can</b> publish audit values;
 * only {@code Tail} does. Node-invocation tracing needs no cooperation from the node at all — the
 * generated dispatch is what records it, which is the case a hand-written system cannot reach for
 * third-party nodes it does not own.
 */
public class DagNodes {

    public abstract static class DagNode extends EventLogNode {
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

    /** The one node that publishes values - the end of the shared tail. */
    public static final class Tail extends DagNode {
        private final DagNode p;
        public Tail(DagNode p) { this.p = p; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() {
            v = p.v * 1.05 + 0.5;
            auditLog.info("v", v).info("n", 1L);
        }
    }
}
