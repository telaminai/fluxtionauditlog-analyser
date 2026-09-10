package app;

import app.gen.ShapeProcessor;

public class BenchJavaShapes {
    public static void main(String[] a) throws Exception {
        int iters = Integer.getInteger("iters", 20_000_000);
        int warm = Integer.getInteger("warm", 2_000_000);
        int batches = Integer.getInteger("batches", 6);
        String shape = System.getProperty("shape", "merge");
        ShapeProcessor p = new ShapeProcessor();
        long[] auditRecords = {0};
        if (Boolean.getBoolean("audit")) {
            // A counting sink, not a file: the claim under test is the cost of the audit PATH, and a
            // writer would measure the disk. Java and C++ use the same shape of sink for that reason.
            com.telamin.fluxtion.runtime.audit.EventLogManager manager =
                    p.getAuditorById(com.telamin.fluxtion.runtime.audit.EventLogManager.NODE_NAME);
            manager.setLogSink(record -> auditRecords[0]++);
        }
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
        System.out.printf("RESULT java-%s audit=%s ns=%.4f checksum=%d records=%d%n",
                shape, System.getProperty("audit", "false"), best, checksum, auditRecords[0]);
    }
}
