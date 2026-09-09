package app;

import app.gen.ShapeProcessor;

public class BenchJavaShapes {
    public static void main(String[] a) {
        int iters = Integer.getInteger("iters", 20_000_000);
        int warm = Integer.getInteger("warm", 2_000_000);
        int batches = Integer.getInteger("batches", 6);
        String shape = System.getProperty("shape", "merge");
        ShapeProcessor p = new ShapeProcessor();
        p.init();
        GenShapes.Tick t = new GenShapes.Tick();
        for (int i = 0; i < warm; i++) { t.price = (i & 15) - 8; p.onEvent(t); }
        double best = Double.MAX_VALUE;
        long checksum = 0;
        for (int b = 0; b < batches; b++) {
            long start = System.nanoTime();
            for (int i = 0; i < iters; i++) { t.price = (i & 15) - 8; p.onEvent(t); }
            long ns = System.nanoTime() - start;
            best = Math.min(best, ns / (double) iters);
            checksum = p.total.getAsInt();
        }
        System.out.printf("RESULT java-%s ns=%.4f checksum=%d%n", shape, best, checksum);
    }
}
