package app;

import app.gen.GroupByProcessor;

/** The Java arm. -Dkeys sets cardinality; the store grows to exactly that many groups. */
public class BenchJavaGroupBy {
    public static void main(String[] a) {
        int iters = Integer.getInteger("iters", 20_000_000);
        int warm = Integer.getInteger("warm", 2_000_000);
        int batches = Integer.getInteger("batches", 6);
        int keys = Integer.getInteger("keys", 4);
        GroupByProcessor p = new GroupByProcessor();
        p.init();
        GenGroupBy.Tick t = new GenGroupBy.Tick();
        for (int i = 0; i < warm; i++) { t.price = (i & 15) - 8; t.key = i % keys; p.onEvent(t); }
        double best = Double.MAX_VALUE;
        long checksum = 0;
        for (int b = 0; b < batches; b++) {
            long start = System.nanoTime();
            for (int i = 0; i < iters; i++) { t.price = (i & 15) - 8; t.key = i % keys; p.onEvent(t); }
            long ns = System.nanoTime() - start;
            best = Math.min(best, ns / (double) iters);
            checksum = p.total.getAsInt();
        }
        System.out.printf("RESULT java-groupby keys=%d ns=%.4f checksum=%d%n", keys, best, checksum);
    }
}
