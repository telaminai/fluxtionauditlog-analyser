import com.bench.*;
import com.bench.gen.BenchProcessor;
import com.plain.*;
import java.lang.management.*;

/** Same arithmetic, same reused event, same JVM. Fluxtion vs hand-written Java. */
public class Head2Head {
    public static void main(String[] a) throws Exception {
        String arm = System.getProperty("arm");
        long warm = Long.getLong("warm", 5_000_000L), iters = Long.getLong("iters", 200_000_000L);
        MarketTick e = new MarketTick();
        long breaches, updates; double buf;
        long a0, t0, ns;

        switch (arm) {
            case "fluxtion": {
                BenchProcessor p = new BenchProcessor(); p.init();
                for (long i = 0; i < warm; i++) p.onEvent(e.set(100.0+(i&15), 100.5+(i&15), i));
                a0 = alloc(); t0 = System.nanoTime();
                for (long i = 0; i < iters; i++) p.onEvent(e.set(100.0+(i&15), 100.5+(i&15), i));
                ns = System.nanoTime()-t0;
                Limit l = (Limit) p.getNodeById("limit"); Buffer b = (Buffer) p.getNodeById("buffer");
                breaches=l.breaches; updates=b.updates; buf=b.value; break; }
            case "inline": {
                PlainInline p = new PlainInline();
                for (long i = 0; i < warm; i++) p.onTick(e.set(100.0+(i&15), 100.5+(i&15), i));
                a0 = alloc(); t0 = System.nanoTime();
                for (long i = 0; i < iters; i++) p.onTick(e.set(100.0+(i&15), 100.5+(i&15), i));
                ns = System.nanoTime()-t0;
                breaches=p.breaches; updates=p.updates; buf=p.buffer; break; }
            case "components": {
                PlainComponents p = new PlainComponents();
                for (long i = 0; i < warm; i++) p.onTick(e.set(100.0+(i&15), 100.5+(i&15), i));
                a0 = alloc(); t0 = System.nanoTime();
                for (long i = 0; i < iters; i++) p.onTick(e.set(100.0+(i&15), 100.5+(i&15), i));
                ns = System.nanoTime()-t0;
                breaches=p.breaches; updates=p.buffer.updates; buf=p.buffer.v; break; }
            case "guarded": {
                PlainGuarded p = new PlainGuarded();
                for (long i = 0; i < warm; i++) p.onTick(e.set(100.0+(i&15), 100.5+(i&15), i));
                a0 = alloc(); t0 = System.nanoTime();
                for (long i = 0; i < iters; i++) p.onTick(e.set(100.0+(i&15), 100.5+(i&15), i));
                ns = System.nanoTime()-t0;
                breaches=p.limit.breaches; updates=p.buffer.updates; buf=p.buffer.v; break; }
            default: throw new IllegalArgumentException(arm);
        }
        long a1 = alloc();
        System.out.printf("  %-11s %,12.0f ev/s  %6.2f ns/ev  %7.4f B/ev   breaches=%,d updates=%,d buffer=%.4f%n",
                arm, iters/(ns/1e9), (double)ns/iters, (double)(a1-a0)/iters, breaches, updates, buf);
    }
    static long alloc(){ return ((com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes(); }
}
