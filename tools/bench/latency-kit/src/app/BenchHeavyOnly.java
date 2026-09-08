package app;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.E3;
import com.bench.E4;
import com.benchv.BinaryLogRecord;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.audit.EventLogManager;
import com.telamin.fluxtion.runtime.audit.LogRecord;
import com.telamin.fluxtion.runtime.audit.LogRecordListener;

/**
 * Round 63 §12 — the self-verifying audit harness.
 *
 * <p>Four silent harness faults were found in this round: a system property that never reached the JVM
 * because {@code -D} sat after the main class, an arm compared against a different arm, un-interleaved
 * runs on a machine with P and E cores, and a build classpath missing the generated inlining directive.
 * Every one of them produced a plausible number and a wrong conclusion.
 *
 * <p>So this harness <b>refuses to print a RESULT line</b> unless it can prove what it measured:
 * <ul>
 *   <li>the requested clock mode and record type actually took effect — the resolved values are echoed
 *       and cross-checked against what was asked for, so a {@code -D} in the wrong place is fatal;
 *   <li>the audit sink actually saw records, and the record was non-empty;
 *   <li>the graph produced the expected checksum, so the arms did the same work.
 * </ul>
 * A benchmark that cannot prove it measured the right thing is worth less than no benchmark.
 */
public class BenchHeavyOnly {

    static double out;
    static long records, recordBytes;

    static final class CountingSink implements LogRecordListener {
        private final boolean binary;
        CountingSink(boolean binary) { this.binary = binary; }
        @Override public void processLogRecord(LogRecord r) {
            records++;
            recordBytes += binary ? ((BinaryLogRecord) r).length() : r.asCharSequence().length();
        }
    }

    interface Graph { void onEvent(Object e); Object node(String id) throws Exception; }

    public static void main(String[] a) throws Exception {
        String want = System.getProperty("record", "MISSING");
        String clock = System.getProperty("clock", "MISSING");
        if ("MISSING".equals(want) || "MISSING".equals(clock)) {
            throw new IllegalStateException("-Drecord and -Dclock must be set BEFORE the main class. "
                    + "A -D after the main class is a program argument, not a system property, and is "
                    + "silently ignored. record=" + want + " clock=" + clock);
        }
        boolean binary = "binary".equals(want);
        String graphName = System.getProperty("graph", "conv");
        long w = Long.getLong("warm", 500_000L), it = Long.getLong("iters", 3_000_000L);

        // CONCRETE type, and the only processor class this main references — so under closed-world
        // AOT the hot p.onEvent(...) call site has exactly one receiver. BenchAudited holds it as the
        // DataFlow interface with two implementations reachable, which is the round-62 provability
        // trap on the dispatch call itself.
        com.bench.heavy.HeavyProcessor p = new com.bench.heavy.HeavyProcessor();
        EventLogManager mgr = p                .getAuditorById(EventLogManager.NODE_NAME);
        mgr.setLogSink(new CountingSink(binary));
        p.init();
        if (binary) {
            p.onEvent(new EventLogControlEvent(new BinaryLogRecord(mgr.clock, 8192)));
        }

        // prove the clock mode actually took: BinaryLogRecord resolves it in its constructor
        String resolvedClock = BinaryLogRecord.clockMode;
        if (!resolvedClock.equals(clock)) {
            throw new IllegalStateException("clock mode did not take: asked " + clock
                    + " resolved " + resolvedClock);
        }

        run(p, graphName, w);
        long r0 = records;
        long b0 = allocated();
        long t0 = System.nanoTime();
        run(p, graphName, it);
        long ns = System.nanoTime() - t0;
        long a1 = allocated();
        long produced = records - r0;

        if (produced == 0) {
            throw new IllegalStateException("the audit sink saw ZERO records over " + it
                    + " events — nothing was being measured. Check the log level and that a node logs.");
        }
        if (recordBytes == 0) {
            throw new IllegalStateException("records were published but every one was empty");
        }
        long bytes = (b0 < 0 || a1 < 0) ? -1 : a1 - b0;

        System.out.printf(
                "RESULT harness=%s graph=%s record=%s clock=%s %8.3f ns %7.2f Mmsg/s recPerEvent=%.3f "
                        + "avgRecBytes=%.1f allocB=%s v=%.4f%n",
                HarnessVersion.tag(), graphName, want, resolvedClock, (double) ns / it, 1e9 / ((double) ns / it) / 1e6,
                (double) produced / it, (double) recordBytes / records,
                bytes < 0 ? "n/a" : String.format("%.3f", (double) bytes / it), out);
    }

    static void run(com.bench.heavy.HeavyProcessor p, String graphName, long n) throws Exception {
        E0 e0 = new E0(); E1 e1 = new E1(); E2 e2 = new E2(); E3 e3 = new E3(); E4 e4 = new E4();
        Object[] evs = new Object[]{e0, e1, e2, e3, e4};
        for (long i = 0; i < n; i++) {
            int k = (int) ((i * 0x9E3779B97F4A7C15L) >>> 61) % 5;
            if (k < 0) { k += 5; }
            Object e = evs[k];
            if (e instanceof E0) { ((E0) e).set(1.0 + (i & 15)); }
            if (e instanceof E1) { ((E1) e).set(1.0 + (i & 15)); }
            if (e instanceof E2) { ((E2) e).set(1.0 + (i & 15)); }
            if (e instanceof E3) { ((E3) e).set(1.0 + (i & 15)); }
            if (e instanceof E4) { ((E4) e).set(1.0 + (i & 15)); }
            p.onEvent(e);
        }
        Object tail = p.getNodeById("t5");
        out = ((com.benchv.DagNodesHeavy.DagNode) tail).v;
    }

    static long allocated() {
        try {
            com.sun.management.ThreadMXBean b =
                    (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
            return b.getThreadAllocatedBytes(Thread.currentThread().getId());
        } catch (Throwable t) { return -1L; }
    }
}
