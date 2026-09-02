import com.bench.*;
import com.bench.gen.BenchProcessor;
import java.lang.management.*;

/**
 * Latency distribution, per event. System.nanoTime() costs roughly as much as the dispatch itself
 * at this scale, so the timer is calibrated on an empty loop and reported alongside, never hidden.
 * Batch mode amortises the timer over N events to see the shape without that overhead.
 */
public class Latency {
    public static void main(String[] args) throws Exception {
        long warm = Long.getLong("warm", 20_000_000L);
        int iters = Integer.getInteger("iters", 50_000_000);
        int batch = Integer.getInteger("batch", 1);

        BenchProcessor p = new BenchProcessor();
        p.init();
        MarketTick evt = new MarketTick();
        for (long i = 0; i < warm; i++) p.onEvent(evt.set(100.0 + (i & 15), 100.5 + (i & 15), i));

        // calibrate: same loop shape, same timer calls, no dispatch
        int cal = Math.min(iters, 5_000_000);
        long calT = System.nanoTime();
        long sink = 0;
        for (int i = 0; i < cal; i++) { long s = System.nanoTime(); sink += s; long e = System.nanoTime(); sink += e - s; }
        double timerNs = (double)(System.nanoTime() - calT) / cal;

        int nb = iters / batch;
        int[] h = new int[200_000];          // 1ns buckets to 200us
        long a0 = alloc(), g0 = gc(), t0 = System.nanoTime();
        for (int b = 0; b < nb; b++) {
            long s = System.nanoTime();
            for (int k = 0; k < batch; k++) {
                long i = (long) b * batch + k;
                p.onEvent(evt.set(100.0 + (i & 15), 100.5 + (i & 15), i));
            }
            int d = (int) ((System.nanoTime() - s) / batch);
            h[d < h.length ? (d < 0 ? 0 : d) : h.length - 1]++;
        }
        long wall = System.nanoTime() - t0;
        long a1 = alloc(), g1 = gc();

        System.out.printf("GC=%s  batch=%d  samples=%,d  timerOverhead=%.1f ns/sample (%.1f ns/event)%n",
                System.getProperty("gcname", "?"), batch, nb, timerNs, timerNs / batch);
        System.out.printf("  throughput   %,.0f events/sec   wall %.2f s   alloc %,d B   GCs %d%n",
                iters / (wall / 1e9), wall / 1e9, a1 - a0, g1 - g0);
        System.out.print("  ns/event ");
        for (double q : new double[]{50, 90, 99, 99.9, 99.99, 99.999, 100})
            System.out.printf(" p%-7s=%-7d", trim(q), pct(h, nb, q));
        System.out.println();
        System.out.printf("           (sink %d)%n", sink & 1);
    }
    static String trim(double q) { return q == 100 ? "max" : (q == (long) q ? String.valueOf((long) q) : String.valueOf(q)); }
    static int pct(int[] h, long n, double q) {
        long target = (long) Math.ceil(n * q / 100.0), c = 0;
        for (int i = 0; i < h.length; i++) { c += h[i]; if (c >= target) return i; }
        return h.length - 1;
    }
    static long alloc() { return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getCurrentThreadAllocatedBytes(); }
    static long gc() { long n = 0; for (GarbageCollectorMXBean g : ManagementFactory.getGarbageCollectorMXBeans()) n += g.getCollectionCount(); return n; }
}
