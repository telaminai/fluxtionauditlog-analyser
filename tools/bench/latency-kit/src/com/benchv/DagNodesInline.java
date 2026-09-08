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
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteOrder;

/**
 * Round 63 §6 — the converging-tail shape, now with an audit trail available.
 *
 * <p><b>The generated-writer CEILING.</b> Stands in for what a generator could emit: each node writes
 * its audit entry as bits at a constant offset, with its node id and key id as literals — no
 * {@code EventLogger} call, no name lookup, no virtual dispatch to a record.
 *
 * <p>This is <b>not an implementation</b>. It has no level check, no record swap, no sink contract and
 * no header or terminator. It bounds what generating the writer could reach; it does not deliver it.
 *
 * <p>Original note: Unlike {@code DagNodes}, where only {@code Tail} publishes,
 * <b>every node on the path writes audit values</b> and they all converge into one record per event.
 * That is the realistic audited shape and it is far more demanding: a path of 7–8 nodes produces 7–8×
 * the entries, so the record's per-entry cost — a node name, a key and a value, written into the record
 * during the cycle — is multiplied rather than amortised.
 */
public class DagNodesInline {

    /** One shared buffer, as a generated processor would hold. */
    public static final byte[] BUF = new byte[8192];
    public static int pos;
    private static final VarHandle LONG_VIEW =
            MethodHandles.byteArrayViewVarHandle(long[].class, ByteOrder.BIG_ENDIAN);
    private static final VarHandle SHORT_VIEW =
            MethodHandles.byteArrayViewVarHandle(short[].class, ByteOrder.BIG_ENDIAN);

    /** What {@code auditLog.info("v", v)} would compile to: literal ids, constant layout, no call. */
    static void writeEntry(int nodeId, int keyId, double value) {
        int p = pos;
        SHORT_VIEW.set(BUF, p, (short) nodeId);
        SHORT_VIEW.set(BUF, p + 2, (short) keyId);
        BUF[p + 4] = 1;
        LONG_VIEW.set(BUF, p + 5, Double.doubleToRawLongBits(value));
        pos = p + 13;
    }

    public abstract static class DagNode extends EventLogNode {
        public transient double v;
    }

    public static final class R0 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E0 e) { v = e.v + 0; writeEntry(1, 1, v); }
    }

    public static final class R1 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E1 e) { v = e.v + 1; writeEntry(2, 1, v); }
    }

    public static final class R2 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E2 e) { v = e.v + 2; writeEntry(3, 1, v); }
    }

    public static final class R3 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E3 e) { v = e.v + 3; writeEntry(4, 1, v); }
    }

    public static final class R4 extends DagNode {
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void on(E4 e) { v = e.v + 4; writeEntry(5, 1, v); }
    }

    public static final class D1 extends DagNode {
        private final DagNode p;
        public D1(DagNode p) { this.p = p; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { v = p.v * 1.05 + 0.5; writeEntry(6, 1, v); }
    }

    public static final class D3 extends DagNode {
        private final DagNode a, b, c;
        public D3(@AssignToField("a") DagNode a, @AssignToField("b") DagNode b,
                  @AssignToField("c") DagNode c) { this.a = a; this.b = b; this.c = c; }
        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void calc() { v = a.v + b.v + c.v; writeEntry(7, 1, v); }
    }

    /** The end of the shared tail — every node before it has already written into this record. */
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
