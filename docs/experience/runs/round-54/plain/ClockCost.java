public class ClockCost {
    public static void main(String[] a) {
        int n = 100_000_000; long s = 0;
        for (int i = 0; i < 20_000_000; i++) s += System.currentTimeMillis();
        long t0 = System.nanoTime();
        for (int i = 0; i < n; i++) s += System.currentTimeMillis();
        double ms = (System.nanoTime() - t0) / (double) n;
        for (int i = 0; i < 20_000_000; i++) s += System.nanoTime();
        long t1 = System.nanoTime();
        for (int i = 0; i < n; i++) s += System.nanoTime();
        double nt = (System.nanoTime() - t1) / (double) n;
        System.out.printf("  System.currentTimeMillis() %5.2f ns/call%n  System.nanoTime()          %5.2f ns/call%n  (sink %d)%n", ms, nt, s & 1);
    }
}
