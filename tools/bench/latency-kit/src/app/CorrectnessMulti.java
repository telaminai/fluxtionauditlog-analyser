package app;

import com.bench.LimitEvent;
import com.bench.MarketTick;
import com.bench.TradeEvent;
import com.benchv.HandMulti;
import com.bench.genmulti.MultiProcessor;

/**
 * Proves the generated processor and the hand-rolled equivalent compute the SAME THING before
 * anything is timed — the gate rounds 58–60 established as non-negotiable, applied to three event
 * types and three dispatch paths.
 *
 * <p>It is stricter than the throughput harness in three ways, each covering a way this could pass
 * while being wrong:
 * <ul>
 *   <li>it compares <b>every</b> published field, not the three the benchmark prints;</li>
 *   <li>it compares after <b>every event</b>, not once at the end — an ordering error that cancels
 *       out by the final event is still an ordering error;</li>
 *   <li>the event sequence <b>interleaves all three types</b> with a deliberately irregular pattern,
 *       so a path that is only ever entered from the same predecessor is not what gets tested.</li>
 * </ul>
 *
 * <p>Exit 0 when they agree on every field after every event, 1 on the first divergence, naming the
 * event index, the event type and the field.
 */
public class CorrectnessMulti {

    private static int failures;

    public static void main(String[] args) {
        int n = Integer.getInteger("events", 100_000);
        MultiProcessor p = new MultiProcessor();
        p.init();
        HandMulti h = new HandMulti();
        MarketTick tick = new MarketTick();
        TradeEvent trade = new TradeEvent();
        LimitEvent limit = new LimitEvent();

        for (int i = 0; i < n && failures == 0; i++) {
            // irregular interleave: ticks dominate, trades are frequent, limits are rare —
            // and the pattern is not a simple alternation, so consecutive same-type runs occur.
            int kind = (i % 7 == 3 || i % 7 == 4) ? 1 : (i % 101 == 17 ? 2 : 0);
            String what;
            switch (kind) {
                case 1:
                    p.onEvent(trade.set(100.0 + (i & 31), 10.0 + (i & 7), (i & 1) == 0 ? 1 : -1));
                    h.onTrade(trade);
                    what = "TradeEvent";
                    break;
                case 2:
                    p.onEvent(limit.set(90_000.0 + (i & 255) * 100.0));
                    h.onLimit(limit);
                    what = "LimitEvent";
                    break;
                default:
                    p.onEvent(tick.set(100.0 + (i & 15), 100.5 + (i & 15), i));
                    h.onTick(tick);
                    what = "MarketTick";
            }
            compare(i, what, p, h);
        }

        if (failures == 0) {
            System.out.printf("CORRECT %d events, 3 types, every field after every event%n", n);
            System.out.printf("  final: exposure=%.6f charge=%.6f buffer=%.6f net=%.4f traded=%.4f "
                            + "fills=%d breaches=%d updates=%d%n",
                    h.exposure, h.charge, h.buffer, h.net, h.traded, h.fills, h.breaches, h.updates);
        }
        System.exit(failures == 0 ? 0 : 1);
    }

    private static void compare(int i, String what, MultiProcessor p, HandMulti h) {
        eq(i, what, "mid", p.mid.value, h.mid);
        eq(i, what, "spread", p.spread.value, h.spread);
        eq(i, what, "ewma", p.ewma.value, h.ewma);
        eq(i, what, "vol", p.vol.value, h.vol);
        eq(i, what, "notional", p.notional.value, h.notional);
        eq(i, what, "position.net", p.position.net, h.net);
        eq(i, what, "position.traded", p.position.traded, h.traded);
        eq(i, what, "exposure", p.exposure.value, h.exposure);
        eq(i, what, "charge", p.charge.value, h.charge);
        eq(i, what, "buffer", p.buffer.value, h.buffer);
        eq(i, what, "limitIn", p.limitIn.value, h.limit);
        eq(i, what, "position.fills", p.position.fills, h.fills);
        eq(i, what, "limit.breaches", p.limit.breaches, h.breaches);
        eq(i, what, "buffer.updates", p.buffer.updates, h.updates);
    }

    /** Bit-exact: both arms do the same operations in the same order, so anything else is a defect. */
    private static void eq(int i, String what, String field, double generated, double hand) {
        if (Double.doubleToLongBits(generated) != Double.doubleToLongBits(hand)) {
            System.out.printf("DIVERGED at event %d (%s) field %s: generated=%s hand=%s%n",
                    i, what, field, generated, hand);
            failures++;
        }
    }

    private static void eq(int i, String what, String field, long generated, long hand) {
        if (generated != hand) {
            System.out.printf("DIVERGED at event %d (%s) field %s: generated=%d hand=%d%n",
                    i, what, field, generated, hand);
            failures++;
        }
    }
}
