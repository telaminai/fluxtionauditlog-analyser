import com.bench2.*;
import com.bench2.gen.AuditProcessor;
import com.telamin.fluxtion.runtime.audit.*;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import java.lang.management.*;

/** Same graph, audit log ON. Measures what the RCA artefact costs per event. */
public class AuditBench {
    public static void main(String[] args) throws Exception {
        long warm = Long.getLong("warm", 2_000_000L);
        long iters = Long.getLong("iters", 20_000_000L);
        String lvl = System.getProperty("level", "INFO");

        AuditProcessor p = new AuditProcessor();
        // a sink that does nothing: isolates the RECORD cost from any I/O
        final long[] records = {0};
        final long[] chars = {0};
        String sinkMode = System.getProperty("sink", "count");
        switch (sinkMode) {
            case "noop":   p.setAuditLogProcessor(r -> { }); break;
            case "count":  p.setAuditLogProcessor(r -> records[0]++); break;
            case "chars":  p.setAuditLogProcessor(r -> { records[0]++; chars[0] += r.asCharSequence().length(); }); break;
            default: throw new IllegalArgumentException(sinkMode);
        }
        p.init();
        p.onEvent(new EventLogControlEvent(LogLevel.valueOf(lvl)));

        MarketTick evt = new MarketTick();
        for (long i = 0; i < warm; i++) p.onEvent(evt.set(100.0 + (i & 15), 100.5 + (i & 15), i));

        long a0 = alloc(), g0 = gc(), t0 = System.nanoTime();
        for (long i = 0; i < iters; i++) p.onEvent(evt.set(100.0 + (i & 15), 100.5 + (i & 15), i));
        long ns = System.nanoTime() - t0;
        long a1 = alloc(), g1 = gc();
        System.out.printf("  level=%-5s sink=%-5s  %,12.0f ev/s   %6.1f ns/ev   %,14d B  (%8.1f B/ev)   GCs %d%n",
                lvl, sinkMode, iters / (ns / 1e9), (double) ns / iters, a1 - a0, (double)(a1 - a0) / iters, g1 - g0);
        System.out.printf("               records seen by sink: %,d (%.2f/event)   log chars: %,d%n",
                records[0], (double) records[0] / iters, chars[0]);
    }
    static long alloc() { return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes(); }
    static long gc() { long n = 0; for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) n += g.getCollectionCount(); return n; }
}
