package app;

/** Round 63 §7.4 Z1 — what one wall-clock read costs, with nothing else in the way. */
public class ClockProbe {
    public static void main(String[] a) {
        long n = Long.getLong("iters", 50_000_000L);
        com.telamin.fluxtion.runtime.time.Clock c = new com.telamin.fluxtion.runtime.time.Clock();
        c.init();
        long s = 0;
        for (long i = 0; i < n / 10; i++) { s += c.getWallClockTime(); }
        long t0 = System.nanoTime();
        for (long i = 0; i < n; i++) { s += c.getWallClockTime(); }
        long viaClock = System.nanoTime() - t0;
        for (long i = 0; i < n / 10; i++) { s += System.currentTimeMillis(); }
        t0 = System.nanoTime();
        for (long i = 0; i < n; i++) { s += System.currentTimeMillis(); }
        long direct = System.nanoTime() - t0;
        System.out.printf("RESULT clock viaClockStrategy=%.3f ns  directCurrentTimeMillis=%.3f ns  sink=%d%n",
                (double) viaClock / n, (double) direct / n, s);
    }
}
