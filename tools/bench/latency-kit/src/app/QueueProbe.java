package app;

import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.wire.DocumentContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/**
 * Round 63 §6.4 — Chronicle append cost with no Fluxtion in the picture, as a function of record
 * shape. Answers whether the 571 ns the audit sink pays is per-byte (so a binary record helps) or
 * per-append (so it does not).
 *
 * <p>{@code -Dshape=text221|text32|long8|bytes32|bytes221}
 */
public class QueueProbe {
    public static void main(String[] a) throws Exception {
        String shape = System.getProperty("shape", "text221");
        long w = Long.getLong("warm", 500_000L), it = Long.getLong("iters", 2_000_000L);
        Path dir = Files.createTempDirectory("qprobe");

        StringBuilder sb = new StringBuilder();
        int width = shape.endsWith("221") ? 221 : 32;
        for (int i = 0; i < width; i++) { sb.append((char) ('a' + (i % 26))); }
        CharSequence cs = sb;
        byte[] blob = new byte[width];
        for (int i = 0; i < width; i++) { blob[i] = (byte) ('a' + (i % 26)); }

        try (ChronicleQueue q = ChronicleQueue.singleBuilder(dir.resolve("q")).build()) {
            ExcerptAppender app = q.createAppender();
            write(app, shape, cs, blob, w);
            long b0 = allocated();
            long t0 = System.nanoTime();
            write(app, shape, cs, blob, it);
            long ns = System.nanoTime() - t0;
            long bytes = allocated() - b0;
            long qbytes;
            try (java.util.stream.Stream<Path> s = Files.walk(dir)) {
                qbytes = s.filter(Files::isRegularFile).mapToLong(f -> {
                    try { return Files.size(f); } catch (Exception x) { return 0L; } }).sum();
            }
            System.out.printf("RESULT %-8s %8.2f nsPerAppend allocB=%6.3f queueBytesPerAppend=%6.1f%n",
                    shape, (double) ns / it, (double) bytes / it, (double) qbytes / (it + w));
        }
        try (java.util.stream.Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(f -> { try { Files.delete(f); } catch (Exception ignored) { } });
        }
    }

    static void write(ExcerptAppender app, String shape, CharSequence cs, byte[] blob, long n) {
        for (long i = 0; i < n; i++) {
            try (DocumentContext dc = app.writingDocument()) {
                switch (shape) {
                    case "long8":
                        dc.wire().getValueOut().int64(i);
                        break;
                    case "bytes32":
                    case "bytes221":
                        dc.wire().getValueOut().bytes(blob);
                        break;
                    default:
                        dc.wire().getValueOut().text(cs);
                }
            }
        }
    }

    static long allocated() {
        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        return bean.getThreadAllocatedBytes(Thread.currentThread().getId());
    }
}
