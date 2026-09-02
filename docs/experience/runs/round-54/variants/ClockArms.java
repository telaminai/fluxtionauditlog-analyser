import com.bench.*;
import com.bench.gen.BenchProcessor;
import com.bench.noaud.NoAudProcessor;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.time.ClockStrategy;
import java.lang.management.*;

public class ClockArms {
    static long replayTime = 1_700_000_000_000L;
    public static void main(String[] a) throws Exception {
        String arm = System.getProperty("arm");
        long warm = 5_000_000L, iters = Long.getLong("iters", 200_000_000L);
        DataFlow f = arm.equals("noAuditors") ? (DataFlow) new NoAudProcessor() : (DataFlow) new BenchProcessor();
        f.init();
        if (arm.equals("replay")) {
            // data-driven clock: the value comes from the stream, not the system
            f.onEvent(ClockStrategy.registerClockEvent(() -> replayTime));
        }
        MarketTick e = new MarketTick();
        for (long i = 0; i < warm; i++) { replayTime += 1; f.onEvent(e.set(100.0+(i&15), 100.5+(i&15), i)); }
        long a0 = alloc(), t0 = System.nanoTime();
        for (long i = 0; i < iters; i++) { replayTime += 1; f.onEvent(e.set(100.0+(i&15), 100.5+(i&15), i)); }
        long ns = System.nanoTime()-t0; long a1 = alloc();
        Limit l = (Limit) f.getNodeById("limit"); Buffer b = (Buffer) f.getNodeById("buffer");
        System.out.printf("  %-11s %,12.0f ev/s  %6.2f ns/ev  %7.4f B/ev   breaches=%,d buffer=%.4f%n",
                arm, iters/(ns/1e9), (double)ns/iters, (double)(a1-a0)/iters, l.breaches, b.value);
    }
    static long alloc(){ return ((com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes(); }
}
