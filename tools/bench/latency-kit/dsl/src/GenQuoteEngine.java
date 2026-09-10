package app;

import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * A market-making quote engine written as HAND-WRITTEN NODES, not as a DSL flow.
 *
 * <p>This is the low-latency path to trading, and it is a different thing from the DSL graph beside it
 * in {@link GenMarketMaker}. The DSL composes framework flow functions — a map is a node, a filter is a
 * node, a join is a node — and each carries the framework's own state and propagation machinery. A
 * hand-written node is a class with fields and an {@code @OnTrigger} method: the state is yours, the
 * arithmetic is yours, and the framework contributes dispatch and nothing else. Every trading system
 * worth the name is written this way, so this is the graph whose numbers mean something operationally.
 *
 * <p><b>Two arms, and they differ in exactly one setting.</b>
 * <ul>
 *   <li>{@code LOWEST_LATENCY}, with no other configuration — dispatch and arithmetic, nothing else.</li>
 *   <li>{@code LOW_LATENCY_AUDIT} + {@code BINARY}, with SPARSE logging — a handful of keys on the two
 *       nodes whose output a regulator or a post-mortem would actually ask about, rather than the
 *       full node trace {@code AUDITED} produces.</li>
 * </ul>
 * The difference between the two is the cost of being able to say afterwards what the engine did.
 *
 * <p><b>Every {@code @OnTrigger} here returns {@code void}, and that is the whole point.</b>
 * {@code setSupportDirtyFiltering(false)} does not blindly delete guards — it drops the dirty flags
 * that DECIDE NOTHING. A {@code void} trigger always propagates, so its guard is pure overhead and
 * goes. A {@code boolean} trigger is different: the return IS the node's propagation decision, and
 * discarding it would not make the graph faster, it would make it wrong. So the compiler keeps those
 * flags whatever the profile says, and an engine written with boolean triggers gets a full set of
 * {@code guardCheck_} methods on its hot path even under {@code LOWEST_LATENCY}. This one was, at
 * first, and the generated source duly carried a guard before every node.
 *
 * <p>So on the lowest-latency path the propagation decisions move OUT of return values and INTO node
 * state: {@link BookState} publishes {@code valid}, {@link RiskGate} publishes {@code quotable}, and
 * the nodes below read them. Identical behaviour, no dirty flags, no guards — the branch is in your
 * code where you can see it, rather than in the framework's where you cannot.
 */
public class GenQuoteEngine {

    public static class MarketTick {
        public int symbol, bidPx, askPx, bidQty, askQty;
    }

    public static class Fill {
        public int symbol, qty;
    }

    /** Top of book: mid and imbalance, held as fields because the next node reads them directly. */
    public static class BookState extends EventLogNode {
        public int symbol, mid, imbalance;
        public boolean valid;

        // failBuildIfMissingBooleanReturn = false is REQUIRED to return void here: the default is
        // true and the build lint rejects a void @OnEventHandler outright. Saying it explicitly is
        // the point - a void handler always propagates, which is the decision being made.
        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onTick(MarketTick t) {
            // The book check a real engine does first: both sides present, not crossed. It is a FLAG,
            // not a return value - see the class comment. Downstream reads it.
            valid = t.bidPx > 0 && t.askPx > t.bidPx;
            if (!valid) { return; }
            symbol = t.symbol;
            mid = (t.bidPx + t.askPx) >> 1;
            int total = t.bidQty + t.askQty;
            imbalance = total == 0 ? 0 : ((t.bidQty - t.askQty) * 64) / total;
        }
    }

    /** Volatility proxy: high-low range over a fixed ring of recent mids. No allocation after init. */
    public static class VolatilityWindow {
        private final BookState book;
        private final int[] ring;
        private int cursor;
        private int filled;
        public int range;

        // The constructor takes the RING, not its size. Fluxtion serialises a hand-written node's
        // state into the generated source, so it matches constructor parameters to fields by name and
        // type: `int size` has no field to match and the build fails with "cannot find matching
        // constructor ... failed to match for these fields: [ring]". Pass the field.
        public VolatilityWindow(BookState book, int[] ring) {
            this.book = book;
            this.ring = ring;
        }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onBook() {
            if (!book.valid) { return; }
            ring[cursor] = book.mid;
            cursor = cursor + 1 == ring.length ? 0 : cursor + 1;
            if (filled < ring.length) { filled++; }
            int high = Integer.MIN_VALUE, low = Integer.MAX_VALUE;
            for (int i = 0; i < filled; i++) {
                int v = ring[i];
                if (v > high) { high = v; }
                if (v < low) { low = v; }
            }
            range = high - low;
        }
    }

    /** Per-symbol inventory, indexed not hashed — the array IS the book of positions. */
    public static class InventoryBook extends EventLogNode {
        private final int[] positions;
        public int lastSymbol;
        public int lastPosition;

        public InventoryBook(int[] positions) { this.positions = positions; }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onFill(Fill f) {
            int slot = f.symbol & (positions.length - 1);
            positions[slot] += f.qty;
            lastSymbol = f.symbol;
            lastPosition = positions[slot];
            // SPARSE: two keys, on the rare event. A fill is the thing a post-mortem asks about, and
            // this is the whole of what auditing costs on this path.
            auditLog.info("sym", f.symbol).info("pos", lastPosition);
        }

        public int positionOf(int symbol) { return positions[symbol & (positions.length - 1)]; }
    }

    /** The quote: mid, skewed by inventory, widened by volatility and one-sided pressure. */
    public static class QuoteCalculator {
        private final BookState book;
        private final VolatilityWindow vol;
        private final InventoryBook inventory;
        public int bidPx, askPx;

        public QuoteCalculator(BookState book, VolatilityWindow vol, InventoryBook inventory) {
            this.book = book;
            this.vol = vol;
            this.inventory = inventory;
        }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onInputs() {
            if (!book.valid) { return; }
            int position = inventory.positionOf(book.symbol);
            int pressure = book.imbalance < 0 ? -book.imbalance : book.imbalance;
            int half = 4 + (vol.range >> 1) + (pressure >> 3);
            int centre = book.mid - (position >> 2);
            bidPx = centre - half;
            askPx = centre + half;
        }
    }

    /**
     * The risk gate. Publishes a FLAG rather than returning false — a boolean return would work, and
     * would also reinstate a dirty flag and a guard on the hot path. See the class comment.
     */
    public static class RiskGate {
        private final QuoteCalculator quote;
        private final InventoryBook inventory;
        private final int positionLimit;
        public boolean quotable;

        public RiskGate(QuoteCalculator quote, InventoryBook inventory, int positionLimit) {
            this.quote = quote;
            this.inventory = inventory;
            this.positionLimit = positionLimit;
        }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onQuote() {
            int position = inventory.lastPosition;
            int magnitude = position < 0 ? -position : position;
            quotable = quote.bidPx > 0 && quote.askPx > quote.bidPx && magnitude < positionLimit;
        }
    }

    /** Publishes, or does not. Counted so neither compiler may delete the graph above it. */
    public static class QuotePublisher extends EventLogNode {
        private final RiskGate gate;
        private final QuoteCalculator quote;
        public int published, suppressed;

        public QuotePublisher(RiskGate gate, QuoteCalculator quote) {
            this.gate = gate;
            this.quote = quote;
        }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onGate() {
            if (!gate.quotable) { suppressed++; return; }
            published++;
            // SPARSE: the two prices, and nothing else. Not the book, not the window, not the node
            // trace - the fields someone reconstructing the decision would need and no more.
            auditLog.info("bid", quote.bidPx).info("ask", quote.askPx);
        }
    }

    static void graph(EventProcessorConfig c) {
        boolean audit = Boolean.getBoolean("audit");
        if (audit) {
            c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOW_LATENCY_AUDIT);
            c.addLowLatencyEventLog(
                    com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO,
                    EventProcessorConfig.AuditRecordFormat.BINARY);
        } else {
            // "No configuration" literally: the profile, and not one setting beside it.
            c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
        }

        int window = Integer.getInteger("window", 16);
        int symbols = Integer.getInteger("symbols", 64);

        BookState book = c.addNode(new BookState(), "book");
        VolatilityWindow vol = c.addNode(new VolatilityWindow(book, new int[window]), "vol");
        InventoryBook inventory = c.addNode(new InventoryBook(new int[symbols]), "inventory");
        QuoteCalculator quote = c.addNode(new QuoteCalculator(book, vol, inventory), "quote");
        RiskGate gate = c.addNode(new RiskGate(quote, inventory, 10_000), "gate");
        c.addNode(new QuotePublisher(gate, quote), "publisher");
    }

    public static void main(String[] a) throws Exception {
        String target = System.getProperty("target");
        String dir = System.getProperty("outDir");
        System.setProperty("fluxtion.sourceGeneratorId", "cpp".equals(target) ? "cpp" : "local");
        EventProcessorFactory.compile(GenQuoteEngine::graph, cfg -> {
            cfg.setPackageName("app.gen");
            cfg.setClassName("QuoteEngineProcessor");
            cfg.setOutputDirectory(dir);
            cfg.setResourcesOutputDirectory(dir);
            cfg.setWriteSourceToFile(true);
            cfg.setWriteGraphMlToFile(false);
            cfg.setCompileSource(false);
            cfg.setFormatSource(false);
        });
    }
}
