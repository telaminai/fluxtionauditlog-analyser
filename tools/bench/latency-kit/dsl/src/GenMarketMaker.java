package app;

import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.flowfunction.aggregate.function.primitive.IntMaxFlowFunction;
import com.telamin.fluxtion.runtime.flowfunction.aggregate.function.primitive.IntMinFlowFunction;
import com.telamin.fluxtion.runtime.flowfunction.aggregate.function.primitive.IntSumFlowFunction;
import com.telamin.fluxtion.runtime.flowfunction.builder.IntFlowBuilder;
import com.telamin.fluxtion.runtime.flowfunction.groupby.GroupBy;

import static com.telamin.fluxtion.builder.DataFlowBuilder.subscribe;

/**
 * A REPRESENTATIVE market-making graph, emitted for both targets from one generator.
 *
 * <p>Everything else in this kit is a shape: two or three nodes chosen to isolate one operator. Those
 * shapes answer "what does a merge cost", and they answer it well, but no one runs a two-node graph.
 * Asked in review for an experiment on a realistic graph, this is it — eighteen nodes doing the work a
 * quoting engine actually does, on the same harness, under the same measurement gate.
 *
 * <p><b>The pricing is deliberately conventional.</b> Mid from the touch, a volatility proxy from the
 * range of a sliding window of mids, a book-imbalance term, inventory per symbol accumulated from
 * fills, a skew that leans quotes against the position, and a risk gate that refuses to quote past a
 * limit. No edge lives here — the point is the SHAPE and its cost, not a strategy. Every price is an
 * int in ticks, so nothing on the measured path allocates in either language.
 *
 * <p><b>Two event types, at a realistic ratio.</b> Ticks dominate and fills are occasional, which is
 * what makes the inventory path a cold branch rather than half the work. A benchmark feeding one event
 * type through one chain would flatter both targets by keeping every branch hot.
 *
 * <p>The per-symbol inventory read is {@code GroupBy.lastValue()} — the position of the symbol whose
 * fill just arrived. Writing this graph is what found that the C++ store had no spelling for it: a
 * stub is handed the store and not the key, so {@code valueFor()} could only read a key fixed when the
 * graph was written. That gap is now closed in the emitter.
 */
public class GenMarketMaker {

    /** Top of book. Prices in ticks, quantities in lots — ints, so the path never allocates. */
    public static class MarketTick {
        public int symbol, bidPx, askPx, bidQty, askQty;
        public int getSymbol() { return symbol; }
        public int getBidPx()  { return bidPx; }
        public int getAskPx()  { return askPx; }
    }

    /** Our own fill: moves inventory, and nothing else in the graph. */
    public static class Fill {
        public int symbol, qty;
        public int getSymbol() { return symbol; }
        public int getQty()    { return qty; }
    }

    /** Where a quote goes. Counted so neither compiler may delete the graph it terminates. */
    public static class QuoteSink {
        public int quotes;
        @OnTrigger
        public boolean onQuote() { quotes++; return true; }
    }

    // ---- the strategy, such as it is -----------------------------------------------------------
    // Static method references, not inline lambdas: the closed-world constructor path resolves a
    // method reference at build time and an inline lambda needs serialization metadata that the AOT
    // image does not carry. That is not style — it is the flatMap AOT break, in a comment.

    /** A tick is quotable only if both sides are present and the book is not crossed. */
    public static boolean twoSided(MarketTick t) { return t.bidPx > 0 && t.askPx > t.bidPx; }

    public static int mid(MarketTick t) { return (t.bidPx + t.askPx) >> 1; }

    /** Book pressure, scaled to ticks: positive means the bid is heavier. */
    public static int imbalance(MarketTick t) {
        int total = t.bidQty + t.askQty;
        return total == 0 ? 0 : ((t.bidQty - t.askQty) * 64) / total;
    }

    /** Volatility proxy: the high-low range of the sliding window of mids. */
    public static int range(int high, int low) { return high - low; }

    /** Half spread widens with volatility and with one-sided pressure. */
    public static int halfSpread(int vol, int imb) {
        int pressure = imb < 0 ? -imb : imb;
        return 4 + (vol >> 1) + (pressure >> 3);
    }

    /** Inventory of the symbol whose fill just arrived — null before the first fill. */
    public static int position(GroupBy<Integer, Integer> inventory) {
        Integer v = inventory.lastValue();
        return v == null ? 0 : v;
    }

    /** Lean the quote against the position: long inventory quotes lower. */
    public static int skew(int position) { return -(position >> 2); }

    public static int applySkew(int mid, int skew) { return mid + skew; }

    public static int bidPx(int centre, int half) { return centre - half; }

    public static int askPx(int centre, int half) { return centre + half; }

    /** The risk gate: a quote wider than this is a pricing fault, not a quote. */
    public static boolean withinLimit(int px) { return px > 0 && px < 1_000_000; }

    /** Both sides in one int so the sink has something a compiler must compute. */
    public static int packQuote(int bid, int ask) { return (bid << 8) ^ ask; }

    static void graph(EventProcessorConfig c) {
        if (Boolean.getBoolean("audit")) {
            c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOW_LATENCY_AUDIT);
            c.addLowLatencyEventLog(
                    com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO,
                    EventProcessorConfig.AuditRecordFormat.BINARY);
        }
        int windowSize = Integer.getInteger("window", 16);

        var tick = subscribe(MarketTick.class).filter(GenMarketMaker::twoSided);

        IntFlowBuilder mid = tick.mapToInt(GenMarketMaker::mid);
        IntFlowBuilder imb = tick.mapToInt(GenMarketMaker::imbalance);

        // Volatility as the range of a sliding count window. Two windows over ONE mid flow, joined —
        // the same mid feeds both, so this measures a diamond, which is the shape real graphs have and
        // no chain benchmark ever exercises.
        IntFlowBuilder vol = mid.slidingAggregateByCount(IntMaxFlowFunction::new, windowSize)
                .mapBi(mid.slidingAggregateByCount(IntMinFlowFunction::new, windowSize),
                        GenMarketMaker::range);

        IntFlowBuilder half = vol.mapBi(imb, GenMarketMaker::halfSpread);

        // Inventory: its own event type, its own sub-graph, joined into the quote.
        IntFlowBuilder position = subscribe(Fill.class)
                .groupBy(Fill::getSymbol, Fill::getQty, IntSumFlowFunction::new)
                .mapToInt(GenMarketMaker::position);

        IntFlowBuilder centre = mid.mapBi(position.map(GenMarketMaker::skew),
                GenMarketMaker::applySkew);

        IntFlowBuilder bid = centre.mapBi(half, GenMarketMaker::bidPx).filter(GenMarketMaker::withinLimit);
        IntFlowBuilder ask = centre.mapBi(half, GenMarketMaker::askPx).filter(GenMarketMaker::withinLimit);

        bid.mapBi(ask, GenMarketMaker::packQuote).id("quote").notify(new QuoteSink());
    }

    public static void main(String[] a) throws Exception {
        String target = System.getProperty("target");
        String dir = System.getProperty("outDir");
        System.setProperty("fluxtion.sourceGeneratorId", "cpp".equals(target) ? "cpp" : "local");
        EventProcessorFactory.compile(GenMarketMaker::graph, cfg -> {
            cfg.setPackageName("app.gen");
            cfg.setClassName("MarketMakerProcessor");
            cfg.setOutputDirectory(dir);
            cfg.setResourcesOutputDirectory(dir);
            cfg.setWriteSourceToFile(true);
            cfg.setWriteGraphMlToFile(false);
            cfg.setCompileSource(false);
            cfg.setFormatSource(false);
        });
    }
}
