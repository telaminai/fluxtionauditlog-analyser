package app;

import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.Initialise;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * A BOUNDED QUOTING CORE — the graph a reviewer asked for, built to their specification.
 *
 * <p>Scope, stated so it cannot drift: normalised market and order events enter; per-symbol state is
 * updated; fair value and desired quotes are computed; risk and freshness are applied; desired orders
 * are reconciled against live and pending orders; zero or more executable order intents emerge.
 * <b>No packet parsing, no kernel, no NIC, no FIX/OUCH/SBE, no exchange simulation, no hedging.</b>
 * Those are separate benchmarks and mixing them in would obscure this one.
 *
 * <p>{@link GenQuoteEngine} is retained as the CONTROL. This graph supersedes it in realism and the
 * two are reported side by side.
 *
 * <p><b>Every piece of state is per symbol.</b> That is the substantive correction over the control,
 * where the book, signal and quote state were scalar — so a tick for symbol 5 followed by a tick for
 * symbol 9 blended them. Here every node holds arrays indexed by symbol, and the symbol for the cycle
 * is published by whichever handler started it. Symbol X always reads state for symbol X.
 *
 * <p><b>Prices are fixed-point in 1/16 tick</b> and quantised to the venue's tick only when the
 * executable quote is constructed. That introduces the rounding step a real engine has — calculated
 * price → venue tick rounding → executable price — without introducing floating point.
 *
 * <p><b>Dispatch paths</b>, which the generated processor produces from the graph shape and which are
 * asserted by {@code QuotingCoreDispatchTest}:
 * <pre>
 *   MarketTick  → MarketState → SignalState → FairValue → QuoteBuilder → Risk → OrderDiff → Intent
 *   Fill        → InventoryState →            FairValue → QuoteBuilder → Risk → OrderDiff → Intent
 *   OrderUpdate → WorkingOrders →                                              OrderDiff → Intent
 *   TimerTick   → FreshnessState →                                     Risk → OrderDiff → Intent
 * </pre>
 * A Fill recomputes fair value from STORED market state for its symbol; an OrderUpdate reconciles
 * against the stored desired quote without recomputing it. Both are what a real engine does.
 *
 * <p>All callbacks return {@code void}, so the generated dispatch carries no dirty flags and no
 * guards — see {@link GenQuoteEngine} for why a {@code boolean} return would reinstate them.
 *
 * <p><b>Per-symbol arrays are {@code @FluxtionIgnore} and allocated in {@code @Initialise}, not passed
 * through the constructor.</b> Fluxtion serialises a hand-written node's state into the generated
 * source, and that includes array CONTENTS — so passing them in emitted every element as a literal and
 * the constructor failed to compile with "code too large" at 64 symbols, let alone the 4,096 this is
 * meant to sweep to. Ignoring the field and allocating it at init keeps the generated source O(1) in
 * the symbol count, which is the only way this graph scales.
 */
public class GenQuotingCore {

    // ---- venue and strategy constants ---------------------------------------------------------
    public static final int SUB_TICK_SHIFT = 4;        // prices held in 1/16 of a venue tick
    public static final int BASE_HALF_SPREAD = 4 << SUB_TICK_SHIFT;
    public static final int VOL_SHIFT = 1;
    // Damped from 3. At 3 the imbalance term moved half-spread by up to 8 whole ticks, so the desired
    // quote changed on almost every book update, the engine emitted an order on 21% of all events and
    // then spent most cycles waiting for acks. A spread adjustment of 0-2 ticks from book pressure is
    // both more realistic and stops the reconciler thrashing.
    public static final int IMBALANCE_SHIFT = 5;
    public static final int INVENTORY_SHIFT = 2;
    public static final int MOMENTUM_SHIFT = 2;
    public static final int BASE_QTY = 10;
    public static final int MAX_QUOTE_QTY = 25;
    // Sized against the workload rather than picked round. At 250 the limit bound on 29% of decisions,
    // because inventory random-walks: this core has no hedging (deliberately out of scope), so nothing
    // pulls the position back. A limit that binds a third of the time is not a risk check, it is the
    // benchmark. 2000 leaves it binding occasionally, which is what a real limit does.
    public static final int POSITION_LIMIT = 2000;
    public static final int QTY_THRESHOLD = 3;          // requote only on a material size change
    public static final int STALE_NANOS = 5_000;        // book older than this cannot be quoted

    // ---- event types --------------------------------------------------------------------------
    public static class MarketTick {
        public int symbol, bidPx, askPx, bidQty, askQty;
        public long timestamp;
    }

    /** side: +1 buy, -1 sell — explicit rather than a signed quantity. */
    public static class Fill {
        public int symbol, side, qty, px;
    }

    public static final int ACK_NEW = 0, ACK_REPLACE = 1, ACK_CANCEL = 2, PARTIAL_FILL = 3, REJECT = 4;

    public static class OrderUpdate {
        public int symbol, side, type, qty, px;
    }

    public static class TimerTick {
        public long now;
    }

    /** Actions the reconciler can emit. */
    public static final int NONE = 0, NEW = 1, REPLACE = 2, CANCEL = 3;

    /**
     * The cycle's subject: which symbol, and what time it is. Published by whichever handler started
     * the cycle and read by every node below it. No callbacks, so it never fires anything — it is
     * shared state that happens to live in the graph.
     */
    public static class Cycle {
        public int symbol;
        public long now;
    }

    // ---- 1. MarketState — per symbol -----------------------------------------------------------
    public static class MarketState {
        private final Cycle cycle;
        private final int symbolCount;
        @FluxtionIgnore public int[] bidPx, askPx, bidQty, askQty, mid, spread, imbalance;
        @FluxtionIgnore public long[] lastMarketTime;
        @FluxtionIgnore public boolean[] valid;

        public MarketState(Cycle cycle, @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle;
            this.symbolCount = symbolCount;
        }

        @Initialise
        public void init() {
            bidPx = new int[symbolCount]; askPx = new int[symbolCount];
            bidQty = new int[symbolCount]; askQty = new int[symbolCount];
            mid = new int[symbolCount]; spread = new int[symbolCount];
            imbalance = new int[symbolCount];
            lastMarketTime = new long[symbolCount]; valid = new boolean[symbolCount];
        }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onTick(MarketTick t) {
            final int s = t.symbol;
            cycle.symbol = s;
            cycle.now = t.timestamp;
            bidPx[s] = t.bidPx; askPx[s] = t.askPx; bidQty[s] = t.bidQty; askQty[s] = t.askQty;
            final boolean ok = t.bidPx > 0 && t.askPx > t.bidPx && t.bidQty > 0 && t.askQty > 0;
            valid[s] = ok;
            if (!ok) { return; }
            // Fixed-point: the venue quotes in ticks, we hold 1/16 of one.
            mid[s] = ((t.bidPx + t.askPx) << SUB_TICK_SHIFT) >> 1;
            spread[s] = (t.askPx - t.bidPx) << SUB_TICK_SHIFT;
            final int total = t.bidQty + t.askQty;
            imbalance[s] = total == 0 ? 0 : ((t.bidQty - t.askQty) * 64) / total;
            lastMarketTime[s] = t.timestamp;
        }
    }

    // ---- 2. SignalState — per-symbol dynamics --------------------------------------------------
    public static class SignalState {
        private final Cycle cycle;
        private final MarketState market;
        private final int symbolCount;
        @FluxtionIgnore public int[] vol, momentum, lastMid;
        @FluxtionIgnore public boolean[] seeded;

        public SignalState(Cycle cycle, MarketState market,
                           @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle; this.market = market; this.symbolCount = symbolCount;
        }

        @Initialise
        public void init() {
            vol = new int[symbolCount]; momentum = new int[symbolCount];
            lastMid = new int[symbolCount]; seeded = new boolean[symbolCount];
        }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onMarket() {
            final int s = cycle.symbol;
            if (!market.valid[s]) { return; }
            final int m = market.mid[s];
            if (!seeded[s]) { seeded[s] = true; lastMid[s] = m; return; }
            final int delta = m - lastMid[s];
            final int absMove = delta < 0 ? -delta : delta;
            // Fixed-point EWMA — a real per-instrument signal with temporal meaning, not a window rescan.
            vol[s] += (absMove - vol[s]) >> 3;
            momentum[s] += (delta - momentum[s]) >> 2;
            lastMid[s] = m;
        }
    }

    // ---- 3. InventoryState ---------------------------------------------------------------------
    public static class InventoryState extends EventLogNode {
        private final Cycle cycle;
        private final int symbolCount;
        @FluxtionIgnore public int[] position;

        public InventoryState(Cycle cycle, @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle; this.symbolCount = symbolCount;
        }

        @Initialise
        public void init() { position = new int[symbolCount]; }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onFill(Fill f) {
            final int s = f.symbol;
            cycle.symbol = s;
            position[s] += f.side > 0 ? f.qty : -f.qty;
            // SPARSE: a fill is the thing a post-mortem asks about first.
            auditLog.info("sym", s).info("pos", position[s]);
        }
    }

    // ---- 4. WorkingOrders — the quoting state machine ------------------------------------------
    public static class WorkingOrders {
        private final Cycle cycle;
        private final int symbolCount;
        // [symbol*2 + side] where side 0 = bid, 1 = ask.
        @FluxtionIgnore public int[] livePx, liveQty, remainingQty;
        @FluxtionIgnore public boolean[] live, pending;
        @FluxtionIgnore public long[] generation;

        public WorkingOrders(Cycle cycle, @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle; this.symbolCount = symbolCount;
        }

        @Initialise
        public void init() {
            final int n = symbolCount * 2;
            livePx = new int[n]; liveQty = new int[n]; remainingQty = new int[n];
            live = new boolean[n]; pending = new boolean[n]; generation = new long[n];
        }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onOrderUpdate(OrderUpdate u) {
            cycle.symbol = u.symbol;
            final int i = (u.symbol << 1) + (u.side > 0 ? 0 : 1);
            switch (u.type) {
                case ACK_NEW, ACK_REPLACE -> {
                    live[i] = true; pending[i] = false;
                    livePx[i] = u.px; liveQty[i] = u.qty; remainingQty[i] = u.qty;
                    generation[i]++;
                }
                case ACK_CANCEL -> {
                    live[i] = false; pending[i] = false;
                    livePx[i] = 0; liveQty[i] = 0; remainingQty[i] = 0;
                }
                case PARTIAL_FILL -> {
                    // A fill PROVES the order is at the venue, so it clears pending as surely as an
                    // ack does. Without this the flag leaked: any slot that received a partial while a
                    // request was in flight stayed pending for the rest of the run, and the engine
                    // stopped quoting that symbol and side entirely - 100% NONE and 80 intents from a
                    // million events.
                    live[i] = true;
                    pending[i] = false;
                    remainingQty[i] -= u.qty;
                    if (remainingQty[i] <= 0) { live[i] = false; remainingQty[i] = 0; }
                }
                // A reject clears the pending flag and the order with it — the engine must not sit
                // waiting on an ack that will never come.
                case REJECT -> { pending[i] = false; live[i] = false; }
                default -> { }
            }
        }

        /** Marks an intent in flight so the reconciler does not re-send it. */
        public void markPending(int slot) { pending[slot] = true; }
    }

    // ---- 5. FreshnessState ---------------------------------------------------------------------
    public static class FreshnessState {
        private final Cycle cycle;
        private final int symbolCount;
        public int sweepCursor;
        public long now;

        public FreshnessState(Cycle cycle, @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle; this.symbolCount = symbolCount;
        }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onTimer(TimerTick t) {
            now = t.now;
            cycle.now = t.now;
            // A ROLLING sweep: one symbol per timer, not all of them. A full sweep per tick would be
            // O(symbols) on the hot path and would dominate the benchmark; a rolling cursor is what a
            // real engine does and keeps the timer path O(1).
            sweepCursor = sweepCursor + 1 == symbolCount ? 0 : sweepCursor + 1;
            cycle.symbol = sweepCursor;
        }
    }

    // ---- 6. FairValue --------------------------------------------------------------------------
    public static class FairValue {
        private final Cycle cycle;
        private final MarketState market;
        private final SignalState signal;
        // InventoryState is a TRIGGER parent, not an arithmetic input: fair value is a property of the
        // market, and a fill does not move it. It is here because the specified Fill path is
        // Inventory -> FairValue -> Quote, recomputing fair from STORED market state for that symbol.
        // Without the edge the generated dispatch sent a fill straight to QuoteBuilder and skipped
        // this node entirely - arguably the more efficient graph, and not the one asked for.
        private final InventoryState inventory;
        private final int symbolCount;
        @FluxtionIgnore public int[] fair;

        public FairValue(Cycle cycle, MarketState market, SignalState signal,
                         InventoryState inventory, @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle; this.market = market; this.signal = signal;
            this.inventory = inventory; this.symbolCount = symbolCount;
        }

        @Initialise
        public void init() { fair = new int[symbolCount]; }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onInputs() {
            final int s = cycle.symbol;
            if (!market.valid[s]) { return; }
            // Cheap signal fusion of the kind that really does live on a quoting path: a microprice
            // tilt from book imbalance scaled by the spread, plus a momentum term.
            final int microPriceAdj = (market.imbalance[s] * market.spread[s]) >> 7;
            final int momentumAdj = signal.momentum[s] >> MOMENTUM_SHIFT;
            fair[s] = market.mid[s] + microPriceAdj + momentumAdj;
        }
    }

    // ---- 7. QuoteBuilder — price AND size, then venue rounding ---------------------------------
    public static class QuoteBuilder {
        private final Cycle cycle;
        private final MarketState market;
        private final SignalState signal;
        private final InventoryState inventory;
        private final FairValue fairValue;
        private final int symbolCount;
        @FluxtionIgnore public int[] desiredBidPx, desiredAskPx, desiredBidQty, desiredAskQty;

        public QuoteBuilder(Cycle cycle, MarketState market, SignalState signal,
                            InventoryState inventory, FairValue fairValue,
                            @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle; this.market = market; this.signal = signal;
            this.inventory = inventory; this.fairValue = fairValue; this.symbolCount = symbolCount;
        }

        @Initialise
        public void init() {
            desiredBidPx = new int[symbolCount]; desiredAskPx = new int[symbolCount];
            desiredBidQty = new int[symbolCount]; desiredAskQty = new int[symbolCount];
        }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onFairValue() {
            final int s = cycle.symbol;
            if (!market.valid[s]) { return; }
            final int position = inventory.position[s];
            final int imb = market.imbalance[s];
            final int pressure = imb < 0 ? -imb : imb;
            final int halfSpread = BASE_HALF_SPREAD
                    + (signal.vol[s] >> VOL_SHIFT)
                    + ((pressure << SUB_TICK_SHIFT) >> IMBALANCE_SHIFT);
            final int centre = fairValue.fair[s] - (position >> INVENTORY_SHIFT);

            // Venue tick rounding — calculated price becomes executable price. Bid rounds DOWN and
            // ask rounds UP, because rounding a quote the wrong way crosses your own spread.
            desiredBidPx[s] = (centre - halfSpread) >> SUB_TICK_SHIFT;
            desiredAskPx[s] = ((centre + halfSpread) + ((1 << SUB_TICK_SHIFT) - 1)) >> SUB_TICK_SHIFT;

            // Size, not just price: lean size against inventory as well as price.
            final int longPenalty = position > 0 ? position >> INVENTORY_SHIFT : 0;
            final int shortPenalty = position < 0 ? (-position) >> INVENTORY_SHIFT : 0;
            desiredBidQty[s] = clampQty(BASE_QTY - longPenalty);
            desiredAskQty[s] = clampQty(BASE_QTY - shortPenalty);
        }

        private static int clampQty(int q) {
            return q < 1 ? 1 : (q > MAX_QUOTE_QTY ? MAX_QUOTE_QTY : q);
        }
    }

    // ---- 8. Risk / limits — independent per side ------------------------------------------------
    public static class RiskLimits {
        private final Cycle cycle;
        private final MarketState market;
        private final InventoryState inventory;
        private final QuoteBuilder quote;
        private final FreshnessState freshness;
        private final int symbolCount;
        @FluxtionIgnore public boolean[] bidAllowed, askAllowed;
        public long staleSuppressed, riskSuppressed;

        public RiskLimits(Cycle cycle, MarketState market, InventoryState inventory,
                          QuoteBuilder quote, FreshnessState freshness,
                          @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle; this.market = market; this.inventory = inventory;
            this.quote = quote; this.freshness = freshness; this.symbolCount = symbolCount;
        }

        @Initialise
        public void init() {
            bidAllowed = new boolean[symbolCount]; askAllowed = new boolean[symbolCount];
        }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onQuote() {
            final int s = cycle.symbol;
            final boolean marketValid = market.valid[s];
            // Staleness is measured against the clock the timer publishes, not against wall time —
            // the benchmark's time is the event stream's time.
            final boolean stale = freshness.now - market.lastMarketTime[s] > STALE_NANOS;
            final int position = inventory.position[s];
            final int projectedLong = position + quote.desiredBidQty[s];
            final int projectedShort = position - quote.desiredAskQty[s];

            // INDEPENDENT suppression per side. Shutting the whole quote off when only one side
            // breaches is both unrealistic and, for a benchmark, a way to skip work.
            final boolean bidOk = marketValid && !stale && projectedLong <= POSITION_LIMIT;
            final boolean askOk = marketValid && !stale && projectedShort >= -POSITION_LIMIT;
            bidAllowed[s] = bidOk;
            askAllowed[s] = askOk;
            if (stale) { staleSuppressed++; }
            else if (!bidOk || !askOk) { riskSuppressed++; }
        }
    }

    // ---- 9. OrderDiff — reconciliation, the node this benchmark is really about -----------------
    public static class OrderDiff {
        private final Cycle cycle;
        private final QuoteBuilder quote;
        private final RiskLimits risk;
        private final WorkingOrders working;
        private final int symbolCount;
        @FluxtionIgnore public int[] action;          // [symbol*2 + side]
        public long none, neu, replace, cancel;
        // Branch attribution for the NONEs. A benchmark reporting "90% NONE" says nothing about
        // whether that is realistic selectivity or a stuck state machine, and those need different
        // fixes. Diagnostic only - off the measured path's critical work, and reported once.
        public long noneNotAllowed, nonePending, noneUnchanged;

        public OrderDiff(Cycle cycle, QuoteBuilder quote, RiskLimits risk, WorkingOrders working,
                         @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle; this.quote = quote; this.risk = risk; this.working = working;
            this.symbolCount = symbolCount;
        }

        @Initialise
        public void init() { action = new int[symbolCount * 2]; }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onInputs() {
            final int s = cycle.symbol;
            action[(s << 1)] = decide(s, 0, risk.bidAllowed[s],
                    quote.desiredBidPx[s], quote.desiredBidQty[s]);
            action[(s << 1) + 1] = decide(s, 1, risk.askAllowed[s],
                    quote.desiredAskPx[s], quote.desiredAskQty[s]);
        }

        private int decide(int symbol, int side, boolean allowed, int desiredPx, int desiredQty) {
            final int i = (symbol << 1) + side;
            final int a;
            if (!allowed) {
                if (working.live[i] && !working.pending[i]) { a = CANCEL; }
                else { a = NONE; noneNotAllowed++; }
            } else if (working.pending[i]) {
                // Something is already in flight; sending again is how you end up double-quoted.
                a = NONE; nonePending++;
            } else if (!working.live[i]) {
                a = NEW;
            } else {
                final int dq = desiredQty - working.liveQty[i];
                final int adq = dq < 0 ? -dq : dq;
                if (desiredPx != working.livePx[i] || adq >= QTY_THRESHOLD) { a = REPLACE; }
                else { a = NONE; noneUnchanged++; }
            }
            switch (a) {
                case NEW -> { neu++; working.markPending(i); }
                case REPLACE -> { replace++; working.markPending(i); }
                case CANCEL -> { cancel++; working.markPending(i); }
                default -> none++;
            }
            return a;
        }
    }

    // ---- 10. IntentPublisher — a real fixed-layout value into a preallocated ring ---------------
    public static class IntentPublisher extends EventLogNode {
        private final Cycle cycle;
        private final OrderDiff diff;
        private final QuoteBuilder quote;
        private final WorkingOrders working;
        // Struct-of-arrays ring. Preallocated, power-of-two mask, never grows.
        @FluxtionIgnore public int[] intentSymbol, intentSide, intentAction, intentPx, intentQty;
        @FluxtionIgnore public long[] intentGeneration;
        private final int ringSize;
        public int mask;
        public int cursor;
        public long emitted;

        public IntentPublisher(Cycle cycle, OrderDiff diff, QuoteBuilder quote, WorkingOrders working,
                               @AssignToField("ringSize") int ringSize) {
            this.cycle = cycle; this.diff = diff; this.quote = quote; this.working = working;
            this.ringSize = ringSize;
        }

        @Initialise
        public void init() {
            intentSymbol = new int[ringSize]; intentSide = new int[ringSize];
            intentAction = new int[ringSize]; intentPx = new int[ringSize];
            intentQty = new int[ringSize]; intentGeneration = new long[ringSize];
            mask = ringSize - 1;
        }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onDiff() {
            final int s = cycle.symbol;
            publish(s, 0, diff.action[s << 1], quote.desiredBidPx[s], quote.desiredBidQty[s]);
            publish(s, 1, diff.action[(s << 1) + 1], quote.desiredAskPx[s], quote.desiredAskQty[s]);
        }

        private void publish(int symbol, int side, int action, int px, int qty) {
            if (action == NONE) { return; }
            final int i = cursor++ & mask;
            intentSymbol[i] = symbol;
            intentSide[i] = side;
            intentAction[i] = action;
            intentPx[i] = action == CANCEL ? working.livePx[(symbol << 1) + side] : px;
            intentQty[i] = action == CANCEL ? 0 : qty;
            intentGeneration[i] = working.generation[(symbol << 1) + side];
            emitted++;
            // SPARSE: the decision and the price it acts on. What a post-mortem needs to reconstruct
            // why an order went out, and no more.
            auditLog.info("sym", symbol).info("act", action).info("px", intentPx[i]);
        }
    }

    // ---- graph ---------------------------------------------------------------------------------
    static void graph(EventProcessorConfig c) {
        if (Boolean.getBoolean("audit")) {
            c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOW_LATENCY_AUDIT);
            c.addLowLatencyEventLog(
                    com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel.INFO,
                    "text".equals(System.getProperty("format"))
                            ? EventProcessorConfig.AuditRecordFormat.TEXT
                            : EventProcessorConfig.AuditRecordFormat.BINARY);
        } else {
            c.performanceProfile(EventProcessorConfig.PerformanceProfile.LOWEST_LATENCY);
        }
        final int n = Integer.getInteger("symbols", 64);
        final int ringBits = Integer.getInteger("ringBits", 16);
        final int ring = 1 << ringBits;

        Cycle cycle = c.addNode(new Cycle(), "cycle");
        MarketState market = c.addNode(new MarketState(cycle, n), "market");
        SignalState signal = c.addNode(new SignalState(cycle, market, n), "signal");
        InventoryState inventory = c.addNode(new InventoryState(cycle, n), "inventory");
        WorkingOrders working = c.addNode(new WorkingOrders(cycle, n), "working");
        FreshnessState freshness = c.addNode(new FreshnessState(cycle, n), "freshness");
        FairValue fair = c.addNode(new FairValue(cycle, market, signal, inventory, n), "fair");
        QuoteBuilder quote = c.addNode(new QuoteBuilder(cycle, market, signal, inventory, fair, n), "quote");
        RiskLimits risk = c.addNode(new RiskLimits(cycle, market, inventory, quote, freshness, n), "risk");
        OrderDiff diff = c.addNode(new OrderDiff(cycle, quote, risk, working, n), "diff");
        c.addNode(new IntentPublisher(cycle, diff, quote, working, ring), "intent");
    }

    public static void main(String[] a) throws Exception {
        String target = System.getProperty("target");
        String dir = System.getProperty("outDir");
        System.setProperty("fluxtion.sourceGeneratorId", "cpp".equals(target) ? "cpp" : "local");
        EventProcessorFactory.compile(GenQuotingCore::graph, cfg -> {
            cfg.setPackageName("app.gen");
            cfg.setClassName("QuotingCoreProcessor");
            cfg.setOutputDirectory(dir);
            cfg.setResourcesOutputDirectory(dir);
            cfg.setWriteSourceToFile(true);
            cfg.setWriteGraphMlToFile(false);
            cfg.setCompileSource(false);
            cfg.setFormatSource(false);
        });
    }
}
