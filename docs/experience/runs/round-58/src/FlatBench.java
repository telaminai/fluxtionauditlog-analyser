import com.bench.*;
import com.bench.gen.BenchProcessor;
import com.bench.gen.BenchProcessorFlat;
import com.plain.*;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.time.Clock;
import java.lang.management.*;

/**
 * Blog benchmark. One reused event object; every arm computes identical arithmetic and is asserted
 * to produce identical output. Run under -XX:+UseEpsilonGC so any per-event allocation is fatal.
 */
public class FlatBench {
    static long streamTime = 1_700_000_000_000L;

    public static void main(String[] a) throws Exception {
        String arm = System.getProperty("arm");
        long warm = Long.getLong("warm", 5_000_000L), iters = Long.getLong("iters", 200_000_000L);
        MarketTick e = new MarketTick();
        long breaches = 0, updates = 0; double buf = 0;
        long a0, t0, ns;

        switch (arm) {
            case "plainInline": {
                PlainInline p = new PlainInline();
                for (long i = 0; i < warm; i++) p.onTick(set(e, i));
                a0 = alloc(); t0 = System.nanoTime();
                for (long i = 0; i < iters; i++) p.onTick(set(e, i));
                ns = System.nanoTime() - t0;
                breaches = p.breaches; updates = p.updates; buf = p.buffer; break; }
            case "plainGuarded": {
                PlainGuarded p = new PlainGuarded();
                for (long i = 0; i < warm; i++) p.onTick(set(e, i));
                a0 = alloc(); t0 = System.nanoTime();
                for (long i = 0; i < iters; i++) p.onTick(set(e, i));
                ns = System.nanoTime() - t0;
                breaches = p.limit.breaches; updates = p.buffer.updates; buf = p.buffer.v; break; }
            default: {
                DataFlow f = arm.startsWith("fluxtionFlat")
                        ? (DataFlow) new BenchProcessorFlat()
                        : (DataFlow) new BenchProcessor();
                f.init();
                if (arm.endsWith("StreamClock")) {
                    // THE INJECTION: a long provider as the clock strategy, one lambda.
                    f.setClockStrategy(() -> streamTime);
                    // prove it is actually in force before measuring anything
                    streamTime = 424242L;
                    f.onEvent(set(e, 0));
                    Clock c = f.getAuditorById("clock");
                    if (c.getProcessTime() != 424242L)
                        throw new IllegalStateException("injected clock NOT in force: " + c.getProcessTime());
                    System.out.println("  [check] node-visible processTime == injected value (424242)");
                    streamTime = 1_700_000_000_000L;
                }
                for (long i = 0; i < warm; i++) { streamTime++; f.onEvent(set(e, i)); }
                a0 = alloc(); t0 = System.nanoTime();
                for (long i = 0; i < iters; i++) { streamTime++; f.onEvent(set(e, i)); }
                ns = System.nanoTime() - t0;
                Limit l = (Limit) f.getNodeById("limit"); Buffer b = (Buffer) f.getNodeById("buffer");
                breaches = l.breaches; updates = b.updates; buf = b.value; }
        }
        long a1 = alloc();
        System.out.printf("RESULT %s %.4f %.6f %d %d %.4f%n",
                arm, (double) ns / iters, (double)(a1 - a0) / iters, breaches, updates, buf);
    }
    static MarketTick set(MarketTick e, long i) { return e.set(100.0 + (i & 15), 100.5 + (i & 15), i); }
    static boolean allocOk = true;
    static long alloc() {
        if (!allocOk) return 0L;
        try { return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes(); }
        catch (Throwable t) { allocOk = false; return 0L; }   // native-image: no JMX; Epsilon is the proof
    }
}
