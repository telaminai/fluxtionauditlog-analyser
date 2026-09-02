import com.bench.*;
import com.bench.gen.BenchProcessor;
import com.plain.PlainGuarded;
import com.telamin.fluxtion.runtime.DataFlow;
import java.lang.management.*;

/** Latency distribution. Batched so System.nanoTime() (~16 ns/pair) does not dominate a ~8 ns op;
 *  each sample is the mean of `batch` events, so this shows steady-state shape, not per-event tail. */
public class BlogLatency {
    static long streamTime = 1_700_000_000_000L;
    public static void main(String[] a) throws Exception {
        String arm = System.getProperty("arm");
        int iters = Integer.getInteger("iters", 200_000_000), batch = Integer.getInteger("batch", 50);
        MarketTick e = new MarketTick();
        DataFlow f = null; PlainGuarded g = null;
        if (arm.startsWith("fluxtion")) {
            f = (DataFlow) new BenchProcessor(); f.init();
            if (arm.equals("fluxtionStreamClock")) f.setClockStrategy(() -> streamTime);
        } else g = new PlainGuarded();
        for (long i = 0; i < 20_000_000L; i++) { streamTime++; fire(f, g, e, i); }
        int nb = iters / batch; int[] h = new int[100_000];
        long a0 = alloc(), t0 = System.nanoTime();
        for (int b = 0; b < nb; b++) {
            long s = System.nanoTime();
            for (int k = 0; k < batch; k++) { long i = (long) b * batch + k; streamTime++; fire(f, g, e, i); }
            int d = (int) ((System.nanoTime() - s) / batch);
            h[Math.max(0, Math.min(d, h.length - 1))]++;
        }
        long wall = System.nanoTime() - t0, a1 = alloc();
        System.out.printf("  %-20s %,11.0f ev/s  %6.2f B/ev |", arm, iters / (wall / 1e9), (double)(a1-a0)/iters);
        for (double q : new double[]{50, 90, 99, 99.9, 99.99, 100})
            System.out.printf("  p%-5s=%-5d", q == 100 ? "max" : String.valueOf(q), pct(h, nb, q));
        System.out.println();
    }
    static void fire(DataFlow f, PlainGuarded g, MarketTick e, long i) {
        e.set(100.0 + (i & 15), 100.5 + (i & 15), i);
        if (f != null) f.onEvent(e); else g.onTick(e);
    }
    static int pct(int[] h, long n, double q) {
        long t = (long) Math.ceil(n * q / 100.0), c = 0;
        for (int i = 0; i < h.length; i++) { c += h[i]; if (c >= t) return i; }
        return h.length - 1;
    }
    static long alloc() { return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes(); }
}
