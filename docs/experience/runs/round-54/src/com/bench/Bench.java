package com.bench;

import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.Fluxtion;
import java.lang.management.*;

/**
 * Zero-GC benchmark. One event object, reused. Audit logging OFF - it allocates a record per event
 * by design, so leaving it on would measure the tracing, not the dispatch.
 */
public class Bench {
    public static void main(String[] a) throws Exception {
        long warm = Long.getLong("warm", 5_000_000L);
        long iters = Long.getLong("iters", 50_000_000L);

        TickIn tick = new TickIn();
        Mid mid = new Mid(tick);
        Spread sp = new Spread(tick);
        Ewma ewma = new Ewma(mid);
        Vol vol = new Vol(mid, ewma);
        Notional no = new Notional(mid, sp);
        Exposure ex = new Exposure(no, vol);
        Limit lim = new Limit(ex);
        Charge ch = new Charge(lim, ex);
        Buffer buf = new Buffer(ch);

        DataFlow p = (DataFlow) Fluxtion.compileDispatcher(c -> {
            c.addNode(mid); c.addNode(sp); c.addNode(ewma); c.addNode(vol);
            c.addNode(no); c.addNode(ex); c.addNode(lim); c.addNode(ch); c.addNode(buf);
        });
        p.init();

        MarketTick evt = new MarketTick();          // ONE event, reused forever

        for (long i = 0; i < warm; i++) p.onEvent(evt.set(100.0 + (i & 15), 100.5 + (i & 15), i));

        long allocBefore = allocated();
        long gcBefore = gcCount();
        long t0 = System.nanoTime();
        for (long i = 0; i < iters; i++) p.onEvent(evt.set(100.0 + (i & 15), 100.5 + (i & 15), i));
        long ns = System.nanoTime() - t0;
        long allocAfter = allocated();
        long gcAfter = gcCount();

        double perEvent = (double) ns / iters;
        System.out.printf("  events            %,d%n", iters);
        System.out.printf("  elapsed           %.3f s%n", ns / 1e9);
        System.out.printf("  throughput        %,.0f events/sec%n", iters / (ns / 1e9));
        System.out.printf("  latency           %.1f ns/event  (10 nodes -> %.2f ns/node)%n", perEvent, perEvent / 10);
        System.out.printf("  bytes allocated   %,d  (%.4f bytes/event)%n",
                allocAfter - allocBefore, (double)(allocAfter - allocBefore) / iters);
        System.out.printf("  GC collections    %d%n", gcAfter - gcBefore);
        System.out.printf("  sanity: breaches=%d bufferUpdates=%d buffer=%.2f%n",
                lim.breaches, buf.updates, buf.value);
    }
    static long allocated() {
        com.sun.management.ThreadMXBean b = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        return b.getCurrentThreadAllocatedBytes();
    }
    static long gcCount() {
        long n = 0;
        for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) n += g.getCollectionCount();
        return n;
    }
}
