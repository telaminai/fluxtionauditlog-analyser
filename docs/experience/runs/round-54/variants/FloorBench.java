import com.bench.*;
import com.bench.floor.FloorProcessor;
import com.bench.mono.TypedProcessor;
import com.bench.mono.MonoProcessor;
import com.plain.*;
import java.lang.management.*;

public class FloorBench {
    public static void main(String[] a) throws Exception {
        String arm = System.getProperty("arm");
        long warm = 5_000_000L, iters = Long.getLong("iters", 200_000_000L);
        MarketTick e = new MarketTick();
        long t0, ns, a0; long breaches = 0, updates = 0; double buf = 0;

        switch (arm) {
            case "plainInline": { PlainInline p = new PlainInline();
                for (long i=0;i<warm;i++) p.onTick(s(e,i));
                a0=alloc(); t0=System.nanoTime(); for (long i=0;i<iters;i++) p.onTick(s(e,i)); ns=System.nanoTime()-t0;
                breaches=p.breaches; updates=p.updates; buf=p.buffer; break; }
            case "plainComponents": { PlainComponents p = new PlainComponents();
                for (long i=0;i<warm;i++) p.onTick(s(e,i));
                a0=alloc(); t0=System.nanoTime(); for (long i=0;i<iters;i++) p.onTick(s(e,i)); ns=System.nanoTime()-t0;
                breaches=p.breaches; updates=p.buffer.updates; buf=p.buffer.v; break; }
            case "floorObject": { FloorProcessor p = new FloorProcessor(); p.init();
                for (long i=0;i<warm;i++) p.onEvent((Object) s(e,i));
                a0=alloc(); t0=System.nanoTime(); for (long i=0;i<iters;i++) p.onEvent((Object) s(e,i)); ns=System.nanoTime()-t0;
                breaches=((Limit)p.getNodeById("limit")).breaches; Buffer b=(Buffer)p.getNodeById("buffer"); updates=b.updates; buf=b.value; break; }
            case "floorTyped": { FloorProcessor p = new FloorProcessor(); p.init();
                for (long i=0;i<warm;i++) p.onEvent(s(e,i));
                a0=alloc(); t0=System.nanoTime(); for (long i=0;i<iters;i++) p.onEvent(s(e,i)); ns=System.nanoTime()-t0;
                breaches=((Limit)p.getNodeById("limit")).breaches; Buffer b=(Buffer)p.getNodeById("buffer"); updates=b.updates; buf=b.value; break; }
            case "typedDirect": { TypedProcessor p = new TypedProcessor(); p.init();
                for (long i=0;i<warm;i++) p.onEvent(s(e,i));
                a0=alloc(); t0=System.nanoTime(); for (long i=0;i<iters;i++) p.onEvent(s(e,i)); ns=System.nanoTime()-t0;
                breaches=((Limit)p.getNodeById("limit")).breaches; Buffer b=(Buffer)p.getNodeById("buffer"); updates=b.updates; buf=b.value; break; }
            case "mono": { MonoProcessor p = new MonoProcessor(); p.init();
                for (long i=0;i<warm;i++) p.onEvent(s(e,i));
                a0=alloc(); t0=System.nanoTime(); for (long i=0;i<iters;i++) p.onEvent(s(e,i)); ns=System.nanoTime()-t0;
                breaches=((Limit)p.getNodeById("limit")).breaches; Buffer b=(Buffer)p.getNodeById("buffer"); updates=b.updates; buf=b.value; break; }
            default: throw new IllegalArgumentException(arm);
        }
        long a1 = alloc();
        System.out.printf("RESULT %s %.4f %.6f %d %d %.4f%n", arm, (double)ns/iters, (double)(a1-a0)/iters, breaches, updates, buf);
    }
    static MarketTick s(MarketTick e, long i){ return e.set(100.0+(i&15), 100.5+(i&15), i); }
    static long alloc(){ return ((com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes(); }
}
