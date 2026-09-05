public class ExeRun {
    public static void main(String[] a) {
        String m = System.getProperty("m","batchLocal");
        long w = Long.getLong("warm",5_000_000L), it = Long.getLong("iters",200_000_000L);
        long t0, ns;
        switch (m) {
            case "batchStatic": FxLib2.batchStatic(w); t0=System.nanoTime(); FxLib2.batchStatic(it); ns=System.nanoTime()-t0; break;
            case "batchHand":   FxLib2.batchHand(w);   t0=System.nanoTime(); FxLib2.batchHand(it);   ns=System.nanoTime()-t0; break;
            default:            FxLib2.batchLocal(w);  t0=System.nanoTime(); FxLib2.batchLocal(it);  ns=System.nanoTime()-t0;
        }
        System.out.printf("RESULT %s %.4f buf=%.4f%n", m, (double)ns/it, FxLib2.outBuf);
    }
}
