package app;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.E3;
import com.bench.E4;
import com.bench.tminimal.DagProcessor;
import com.benchv.BinaryLogRecord;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.audit.EventLogManager;

/**
 * Round 63 §7 — graph + audit record construction, no sink write at all.
 *
 * <p>{@code -Drecord=text|binary}. {@code text} is the stock {@code LogRecord}; {@code binary}
 * installs {@link BinaryLogRecord} through the seam that already exists,
 * {@code new EventLogControlEvent(record)}. The sink is a no-op in both arms, so this measures
 * exactly what the target excludes disk and network from: graph + fully-audited record, in a buffer.
 */
public class BenchBinary {
    static double out;
    static long sink;

    static void run(DagProcessor p, long n) throws Exception {
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
        out = ((com.benchv.DagNodes.DagNode) p.getNodeById("t5")).v;
    }

    public static void main(String[] a) throws Exception {
        String mode = System.getProperty("record", "binary");
        long w = Long.getLong("warm", 500_000L), it = Long.getLong("iters", 2_000_000L);

        DagProcessor p = new DagProcessor();
        EventLogManager mgr = p.getAuditorById(EventLogManager.NODE_NAME);
        BinaryLogRecord bin = "binary".equals(mode) ? new BinaryLogRecord(mgr.clock, 4096) : null;
        com.benchv.ProcessTimeLogRecord txtProc =
                "textproc".equals(mode) ? new com.benchv.ProcessTimeLogRecord(mgr.clock) : null;

        if (bin != null) {
            // the sink sees the binary record and takes its bytes; nothing is written anywhere
            mgr.setLogSink(r -> { sink += ((BinaryLogRecord) r).length(); });
        } else {
            mgr.setLogSink(r -> { sink += r.asCharSequence().length(); });
        }
        p.init();
        if (bin != null) {
            p.onEvent(new EventLogControlEvent(bin));   // the existing plugin-encoder seam
        } else if (txtProc != null) {
            p.onEvent(new EventLogControlEvent(txtProc));
        }

        run(p, w);
        long b0 = allocated();
        long t0 = System.nanoTime();
        run(p, it);
        long ns = System.nanoTime() - t0;
        long bytes = allocated() - b0;

        double perRecord = (double) sink / (it + w);
        System.out.printf("RESULT %-6s %8.3f nsPerEvent  %8.2f msgPerSec  allocB=%6.3f  recordBytes=%.1f%s%n",
                mode, (double) ns / it, 1e9 / ((double) ns / it) / 1e6, (double) bytes / it, perRecord,
                bin == null ? "" : String.format("  dict=%d cacheHit=%.4f%% overflow=%b",
                        bin.dictionarySize(),
                        100.0 * bin.cacheHits() / (bin.cacheHits() + bin.cacheMisses()),
                        bin.overflowed()));
    }

    static long allocated() {
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        return bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }
}
