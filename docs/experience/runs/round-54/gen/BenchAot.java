import com.bench.*;
import com.bench.gen.BenchProcessor;
import java.lang.management.*;

/** RUN TIME. Runtime jar + generated processor only. No builder, no compiler. */
public class BenchAot {
    public static void main(String[] a) throws Exception {
        long warm = Long.getLong("warm", 5_000_000L);
        long iters = Long.getLong("iters", 100_000_000L);
        boolean typed = Boolean.getBoolean("typed");

        BenchProcessor p = new BenchProcessor();
        p.init();
        MarketTick evt = new MarketTick();       // ONE event object for the whole run

        for (long i = 0; i < warm; i++) fire(p, evt, i, typed);

        long a0 = alloc(), g0 = gc();
        long t0 = System.nanoTime();
        for (long i = 0; i < iters; i++) fire(p, evt, i, typed);
        long ns = System.nanoTime() - t0;
        long a1 = alloc(), g1 = gc();

        Buffer buf = (Buffer) p.getNodeById("buffer");
        Limit lim = (Limit) p.getNodeById("limit");
        System.out.printf("  dispatch          %s%n", typed ? "typed onEvent(MarketTick)" : "onEvent(Object) + instanceof");
        System.out.printf("  events            %,d%n", iters);
        System.out.printf("  throughput        %,.0f events/sec%n", iters / (ns / 1e9));
        System.out.printf("  latency           %.1f ns/event%n", (double) ns / iters);
        System.out.printf("  bytes allocated   %,d   (%.5f bytes/event)%n", a1 - a0, (double)(a1 - a0) / iters);
        System.out.printf("  GC collections    %d%n", g1 - g0);
        System.out.printf("  sanity            breaches=%,d bufferUpdates=%,d buffer=%.2f%n",
                lim.breaches, buf.updates, buf.value);
    }
    static void fire(BenchProcessor p, MarketTick e, long i, boolean typed) {
        e.set(100.0 + (i & 15), 100.5 + (i & 15), i);
        if (typed) p.onEvent(e); else p.onEvent((Object) e);
    }
    static long alloc() {
        return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean())
                .getCurrentThreadAllocatedBytes();
    }
    static long gc() {
        long n = 0;
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) n += g.getCollectionCount();
        return n;
    }
}
