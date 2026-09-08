package app;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.E3;
import com.bench.E4;

/**
 * Round 63 §17 — is the baseline executing real work?
 *
 * <p>Processor constructed inside the loop method (harness h3). The class is generated per depth, so
 * this file is compiled once per graph with {@code -Dpkg}. Prints the tail value, which depends on the
 * whole chain: if the compiler had eliminated the intermediate nodes the value could not be right.
 */
public class BenchDepth {
    static double out;

    static void run(long n) throws Exception {
        DEPTH_PKG.DepthProcessor p = new DEPTH_PKG.DepthProcessor();
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
        long w = Long.getLong("warm", 500_000L), it = Long.getLong("iters", 3_000_000L);
        run(w);
        long t0 = System.nanoTime();
        run(it);
        long ns = System.nanoTime() - t0;
        if (out == 0.0 || Double.isNaN(out)) {
            throw new IllegalStateException("tail value is " + out + " — the graph computed nothing");
        }
        System.out.printf("RESULT harness=%s depth=%s %8.4f ns %8.2f Mmsg/s v=%.6f%n",
                HarnessVersion.tag(), System.getProperty("depth", "?"),
                (double) ns / it, 1e9 / ((double) ns / it) / 1e6, out);
    }
}
