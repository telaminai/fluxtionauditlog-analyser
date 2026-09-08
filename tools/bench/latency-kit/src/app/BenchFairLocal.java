package app;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.E3;
import com.bench.E4;
import com.bench.fair.FairProcessor;

/**
 * Round 63 §15 — the <b>no-audit baseline</b> for the 30-node converging shape.
 *
 * <p>Same graph, same node bodies, same event mix as {@code BenchConvOnly}; the nodes simply have no
 * audit machinery at all and the processor is built with {@code LOWEST_LATENCY}. Without this number
 * the audited figures cannot be decomposed — "audit costs X" is only meaningful against a denominator
 * measured on the same shape, with the same compiler flags, on the same machine.
 *
 * <p>The checksum is printed so it can be compared against the audited arms: the graph must compute the
 * same answer whether or not it is audited.
 */
public class BenchFairLocal {
    static double out;

    /**
     * The processor is constructed <b>inside this method</b> and never escapes it — the runtime-shape
     * requirement on the performance-page checklist, and the condition for its node objects to be
     * scalar-replaced under native AOT. {@code BenchPlain} constructed it in {@code main} and passed it
     * in, which lets it escape; that single difference is worth ~2.8x.
     */
    static void run(long n) throws Exception {
        FairProcessor p = new FairProcessor();
        p.init();
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
        out = ((com.benchv.DagNodesPlain.DagNode) p.getNodeById("t5")).v;
    }

    public static void main(String[] a) throws Exception {
        long w = Long.getLong("warm", 500_000L), it = Long.getLong("iters", 5_000_000L);
        run(w);
        long t0 = System.nanoTime();
        run(it);
        long ns = System.nanoTime() - t0;
        if (out == 0.0) { throw new IllegalStateException("graph produced nothing — not measuring work"); }
        System.out.printf(
                "RESULT harness=%s fair-baseline no-audit %8.3f ns %7.2f Mmsg/s v=%.4f%n",
                HarnessVersion.tag(), (double) ns / it, 1e9 / ((double) ns / it) / 1e6, out);
    }
}
