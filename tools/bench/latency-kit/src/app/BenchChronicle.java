package app;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.E3;
import com.bench.E4;
import com.bench.tminimal.DagProcessor;
import com.telamin.mongoose.config.AuditCaptureConfig;
import com.telamin.mongoose.internal.ChronicleAuditCaptureService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Round 63 §7 — the same 30-node / 5-event / shared-tail graph as {@code BenchTail_minimal}, with the
 * ONLY change being the sink: Mongoose's {@code ChronicleAuditCaptureService} instead of a no-op.
 *
 * <p>Every audit figure measured so far is the cost of BUILDING a record and throwing it away. This
 * one carries it through the encoder a deployed Mongoose actually uses, and out to a memory-mapped
 * queue on disk — so the number includes encode + write, and the queue size gives bytes/record.
 */
public class BenchChronicle {
    static double out;

    static void run(DagProcessor p, long n) throws Exception {
        E0 e0 = new E0();
        E1 e1 = new E1();
        E2 e2 = new E2();
        E3 e3 = new E3();
        E4 e4 = new E4();
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
        boolean chronicle = !"noop".equals(System.getProperty("sink"));
        Path dir = Files.createTempDirectory("audit-bench");
        long w = Long.getLong("warm", 2_000_000L), it = Long.getLong("iters", 10_000_000L);

        DagProcessor p = new DagProcessor();
        com.telamin.fluxtion.runtime.audit.EventLogManager mgr = p.getAuditorById(
                com.telamin.fluxtion.runtime.audit.EventLogManager.NODE_NAME);
        mgr.setLogSink(new com.benchv.NullSink.NoOp());
        p.init();

        ChronicleAuditCaptureService svc = null;
        if (chronicle) {
            AuditCaptureConfig cfg = new AuditCaptureConfig();
            cfg.setEnabled(true);
            cfg.setDirectory(dir.toString());
            cfg.setBackend("chronicle");
            cfg.setRollSize("1g");
            cfg.setRetainHours(1);
            svc = new ChronicleAuditCaptureService(cfg, new com.telamin.mongoose.internal.AgronaCountersService());
            svc.attach(p, "bench");
            svc.start("bench");
            System.out.println("recording=" + svc.isRecording("bench"));
        }

        run(p, w);
        long b0 = allocated();
        long t0 = System.nanoTime();
        run(p, it);
        long ns = System.nanoTime() - t0;
        long bytes = allocated() - b0;

        long qbytes = 0;
        if (svc != null) {
            svc.stop("bench");
            qbytes = Files.walk(dir).filter(Files::isRegularFile).mapToLong(f -> {
                try { return Files.size(f); } catch (Exception x) { return 0L; } }).sum();
        }
        System.out.printf("RESULT %s %.4f nsPerEvent allocBytesPerEvent=%.3f queueBytes=%d queueBytesPerEvent=%.1f v=%.4f%n",
                chronicle ? "chronicle" : "noop", (double) ns / it, (double) bytes / it,
                qbytes, (double) qbytes / (it + w), out);
        try (java.util.stream.Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(f -> { try { Files.delete(f); } catch (Exception ignored) { } });
        }
    }

    static long allocated() {
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        return bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }
}
