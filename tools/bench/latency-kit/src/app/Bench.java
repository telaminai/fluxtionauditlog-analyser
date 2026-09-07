package app;

import com.bench.MarketTick;
import com.benchv.HandBase;

/**
 * The measured loop. Two arms, one binary, selected by {@code -Darm=}.
 *
 * <p>Shape matters and is deliberate, per round 59:
 * <ul>
 *   <li>the processor is constructed INSIDE the method that runs the loop, and never escapes it;</li>
 *   <li>there is NOTHING between the constructor and the loop — no timing call, no logging. The
 *       caller does the timing. A {@code System.nanoTime()} placed here costs 3.3x, because an
 *       opaque call between an allocation and its use blocks the escape proof.</li>
 * </ul>
 *
 * <p>Emits one line, the contract the conformance bench parses:
 * {@code RESULT <arm> <ns_per_event> <check> <check> <check>}
 */
public class Bench {
    static double outBuf;
    static long outBreaches, outUpdates;

    static void hand(long n) {
        HandBase h = new HandBase();
        MarketTick e = new MarketTick();
        for (long i = 0; i < n; i++) h.onTick(e.set(100.0 + (i & 15), 100.5 + (i & 15), i));
        outBuf = h.buffer; outBreaches = h.breaches; outUpdates = h.updates;
    }

    public static void main(String[] a) {
        String arm = System.getProperty("arm", "generated");
        long w = Long.getLong("warm", 5_000_000L), it = Long.getLong("iters", 200_000_000L);
        long t0, ns;
        if (arm.equals("hand")) {
            hand(w); t0 = System.nanoTime(); hand(it); ns = System.nanoTime() - t0;
        } else {
            Runner.loop(w); t0 = System.nanoTime(); Runner.loop(it); ns = System.nanoTime() - t0;
            outBuf = Runner.outBuf; outBreaches = Runner.outBreaches; outUpdates = Runner.outUpdates;
        }
        System.out.printf("RESULT %s %.4f buf=%.4f upd=%d brch=%d%n",
                arm, (double) ns / it, outBuf, outUpdates, outBreaches);
    }
}
