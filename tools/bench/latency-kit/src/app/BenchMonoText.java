package app;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.E3;
import com.bench.E4;
import com.bench.tminimal.DagProcessor;
import com.telamin.fluxtion.runtime.audit.EventLogManager;

/**
 * Round 63 §9 — the text arm with <b>nothing to be polymorphic about</b>.
 *
 * <p>Deliberately references no {@code LogRecord} subclass and exactly one {@code LogRecordListener}
 * implementation, and is compiled against a class tree with {@code BinaryLogRecord} and
 * {@code ProcessTimeLogRecord} physically removed. Under closed-world AOT that makes every
 * {@code logrecord.addRecord(...)} call site in {@code EventLogger} statically provable — which the §8
 * image, carrying all three subclasses, did not.
 */
public class BenchMonoText {
    static double out;
    static long sink;

    /** One concrete final implementation, so the sink call site is provable too. */
    static final class OnlySink implements com.telamin.fluxtion.runtime.audit.LogRecordListener {
        @Override
        public void processLogRecord(com.telamin.fluxtion.runtime.audit.LogRecord r) {
            sink += r.asCharSequence().length();
        }
    }

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
        long w = Long.getLong("warm", 500_000L), it = Long.getLong("iters", 3_000_000L);
        DagProcessor p = new DagProcessor();
        EventLogManager mgr = p.getAuditorById(EventLogManager.NODE_NAME);
        mgr.setLogSink(new OnlySink());
        p.init();
        run(p, w);
        long t0 = System.nanoTime();
        run(p, it);
        long ns = System.nanoTime() - t0;
        System.out.printf("RESULT mono-text %8.3f nsPerEvent  %8.2f msgPerSec  v=%.4f sink=%d%n",
                (double) ns / it, 1e9 / ((double) ns / it) / 1e6, out, sink);
    }
}
