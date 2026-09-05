import com.bench.*;
import com.bench.gen.BenchProcessor;
import com.plain.*;
import com.telamin.fluxtion.runtime.DataFlow;

/** Isolates INTERFACE dispatch from the generated dispatch itself.
 *  Only ONE DataFlow implementation is reachable, so a JIT and closed-world AOT
 *  see the same type situation. Arms differ only in the STATIC type of the reference. */
public class TypedBench {
    static long streamTime = 1_700_000_000_000L;
    public static void main(String[] a) {
        String arm = System.getProperty("arm");
        long warm = Long.getLong("warm", 5_000_000L), iters = Long.getLong("iters", 200_000_000L);
        MarketTick e = new MarketTick();
        long breaches, updates; double buf; long t0, ns;
        BenchProcessor p = new BenchProcessor();
        p.init();
        p.setClockStrategy(() -> streamTime);
        if (arm.equals("viaInterface")) {
            DataFlow f = p;                                   // interface-typed reference
            for (long i = 0; i < warm; i++) { streamTime++; f.onEvent(set(e, i)); }
            t0 = System.nanoTime();
            for (long i = 0; i < iters; i++) { streamTime++; f.onEvent(set(e, i)); }
            ns = System.nanoTime() - t0;
        } else if (arm.equals("viaConcrete")) {
            for (long i = 0; i < warm; i++) { streamTime++; p.onEvent((Object) set(e, i)); }
            t0 = System.nanoTime();
            for (long i = 0; i < iters; i++) { streamTime++; p.onEvent((Object) set(e, i)); }
            ns = System.nanoTime() - t0;
        } else {                                              // typed entry, no Object dispatch
            for (long i = 0; i < warm; i++) { streamTime++; p.onEvent(set(e, i)); }
            t0 = System.nanoTime();
            for (long i = 0; i < iters; i++) { streamTime++; p.onEvent(set(e, i)); }
            ns = System.nanoTime() - t0;
        }
        Limit l = p.limit; Buffer b = p.buffer;
        breaches = l.breaches; updates = b.updates; buf = b.value;
        System.out.printf("RESULT %s %.4f %.6f %d %d %.4f%n", arm, (double) ns / iters, 0.0, breaches, updates, buf);
    }
    static MarketTick set(MarketTick e, long i) { return e.set(100.0 + (i & 15), 100.5 + (i & 15), i); }
}
