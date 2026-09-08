package app;

import com.bench.E0;
import com.bench.E1;
import com.bench.E2;
import com.bench.E3;
import com.bench.E4;
import com.bench.tminimal.DagProcessor;
import com.telamin.fluxtion.runtime.audit.LogRecord;
import com.telamin.fluxtion.runtime.audit.LogRecordListener;
import com.telamin.mongoose.internal.AgronaCountersService;
import com.telamin.mongoose.service.counters.MongooseCounter;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.wire.DocumentContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Round 63 §6 — decomposes {@code ChronicleAuditCaptureService$ProcessorSink.onRecord} into the five
 * things it actually does, one arm per prefix, so the 580 ns the sink adds can be attributed.
 *
 * <p>Arms (each a strict superset of the one before, except {@code noinstant}):
 * <ul>
 *   <li>{@code noop}      — discard the record (the §5 baseline)
 *   <li>{@code cs}        — {@code asCharSequence()} only
 *   <li>{@code book}      — cs + AtomicLong + {@code Instant.now()} + Agrona counter, no queue
 *   <li>{@code queue}     — cs + the Chronicle document write, no bookkeeping
 *   <li>{@code full}      — everything, i.e. a faithful re-implementation of {@code onRecord}
 *   <li>{@code noinstant} — {@code full} minus {@code Instant.now()} (the proposed fix)
 * </ul>
 */
public class BenchSink {
    static double out;
    static ChronicleQueue queue;
    static ExcerptAppender appender;
    static final AtomicLong recordCount = new AtomicLong();
    static volatile Instant lastWriteAt;
    static MongooseCounter counter;
    static long csSink;
    static final net.openhft.chronicle.bytes.Bytes<?> buf =
            net.openhft.chronicle.bytes.Bytes.allocateElasticDirect(512);

    interface Arm { LogRecordListener listener(); }

    static LogRecordListener listenerFor(String arm) {
        switch (arm) {
            case "noop":
                return r -> { };
            case "cs":
                return r -> { csSink += r.asCharSequence().length(); };
            case "book":
                return r -> {
                    csSink += r.asCharSequence().length();
                    recordCount.incrementAndGet();
                    lastWriteAt = Instant.now();
                    counter.increment();
                };
            case "queue":
                return r -> {
                    CharSequence cs = r.asCharSequence();
                    try (DocumentContext dc = appender.writingDocument()) {
                        dc.wire().getValueOut().text(cs);
                    }
                };
            case "copyonly":
                return r -> {
                    CharSequence cs = r.asCharSequence();
                    int n = cs.length();
                    buf.clear();
                    for (int i = 0; i < n; i++) { buf.writeByte((byte) cs.charAt(i)); }
                    csSink += buf.writePosition();
                };
            case "binbytes":
                return r -> {
                    CharSequence cs = r.asCharSequence();
                    int n = cs.length();
                    buf.clear();
                    for (int i = 0; i < n; i++) { buf.writeByte((byte) cs.charAt(i)); }
                    try (DocumentContext dc = appender.writingDocument()) {
                        dc.wire().getValueOut().bytes(buf);
                    }
                    recordCount.incrementAndGet();
                    counter.increment();
                };
            case "noinstant":
                return r -> {
                    CharSequence cs = r.asCharSequence();
                    try (DocumentContext dc = appender.writingDocument()) {
                        dc.wire().getValueOut().text(cs);
                    }
                    recordCount.incrementAndGet();
                    counter.increment();
                };
            default: // full
                return r -> {
                    CharSequence cs = r.asCharSequence();
                    try (DocumentContext dc = appender.writingDocument()) {
                        dc.wire().getValueOut().text(cs);
                    }
                    recordCount.incrementAndGet();
                    lastWriteAt = Instant.now();
                    counter.increment();
                };
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
        String arm = System.getProperty("arm", "full");
        boolean needsQueue = arm.equals("queue") || arm.equals("full") || arm.equals("noinstant") || arm.equals("binbytes");
        Path dir = Files.createTempDirectory("sink-bench");
        if (needsQueue) {
            queue = ChronicleQueue.singleBuilder(dir.resolve("bench")).build();
            appender = queue.createAppender();
        }
        counter = new AgronaCountersService().counter("audit.records.bench");

        DagProcessor p = new DagProcessor();
        com.telamin.fluxtion.runtime.audit.EventLogManager mgr = p.getAuditorById(
                com.telamin.fluxtion.runtime.audit.EventLogManager.NODE_NAME);
        mgr.setLogSink(listenerFor(arm));
        p.init();

        long w = Long.getLong("warm", 500_000L), it = Long.getLong("iters", 2_000_000L);
        run(p, w);
        long b0 = allocated();
        long t0 = System.nanoTime();
        run(p, it);
        long ns = System.nanoTime() - t0;
        long bytes = allocated() - b0;

        long qbytes = 0;
        if (queue != null) { queue.close(); }
        try (java.util.stream.Stream<Path> s = Files.walk(dir)) {
            qbytes = s.filter(Files::isRegularFile).mapToLong(f -> {
                try { return Files.size(f); } catch (Exception x) { return 0L; } }).sum();
        }
        System.out.printf("RESULT %-9s %9.4f nsPerEvent allocBytesPerEvent=%7.3f queueBytesPerEvent=%6.1f v=%.4f cs=%d%n",
                arm, (double) ns / it, (double) bytes / it, (double) qbytes / (it + w), out, csSink);
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
