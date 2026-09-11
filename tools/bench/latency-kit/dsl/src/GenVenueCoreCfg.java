package app;

import com.telamin.fluxtion.builder.compile.generation.EventProcessorFactory;
import com.telamin.fluxtion.builder.generation.config.EventProcessorConfig;
import com.telamin.fluxtion.runtime.annotations.Initialise;
import com.telamin.fluxtion.runtime.annotations.NoTriggerReference;
import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.OnTrigger;
import com.telamin.fluxtion.runtime.annotations.builder.AssignToField;
import com.telamin.fluxtion.runtime.annotations.builder.FluxtionIgnore;
import com.telamin.fluxtion.runtime.audit.EventLogNode;

/**
 * A single-venue quoting core with a REAL ORDER LIFECYCLE — the third graph in the progression.
 *
 * <p>{@code GenQuoteEngine} (6 nodes) and {@code GenQuotingCore} (10 nodes, event-count acks) are both
 * retained unchanged as controls. This graph keeps the ten-node application shape and changes two
 * things that were not defensible:
 *
 * <ol>
 *   <li><b>Acknowledgements are time-driven, not event-count-driven.</b> The harness schedules each
 *       ack on the data-driven clock with an explicit simulated venue round-trip. Market data continues
 *       independently while an order is in flight, so the proportion of cycles blocked on {@code
 *       pending} becomes an OBSERVED result of market rate, action rate and ack latency rather than a
 *       dial the workload sets directly.</li>
 *   <li><b>Executions originate from working orders.</b> An {@code Execution} carries the order's
 *       generation and cannot be constructed against a nonexistent, cancelled or fully-filled order.
 *       It reduces remaining working quantity and moves inventory <b>on the same causal path</b>.</li>
 * </ol>
 *
 * <p><b>The four causal paths</b>, produced by the graph shape and asserted from the generated source:
 * <pre>
 *   MarketTick  → MarketState → SignalState → FairValue → QuoteBuilder → Risk → OrderDiff → Intent
 *   Execution   → WorkingOrders → InventoryState → FairValue → QuoteBuilder → Risk → OrderDiff → Intent
 *   OrderUpdate → WorkingOrders →                                                 OrderDiff → Intent
 *   TimerTick   → FreshnessState →                                         Risk → OrderDiff → Intent
 * </pre>
 *
 * <p>The ack path stops at reconciliation and does NOT re-run pricing. That is what
 * {@code @NoTriggerReference} on {@link InventoryState}'s working-orders field buys: inventory reads
 * the order book as a data store without being fired by it, so an ack does not drag the pricing chain
 * behind it while an execution does. Without it every ack would recompute fair value.
 *
 * <p>All callbacks return {@code void}: the generated dispatch carries no dirty flags and no guards.
 * Per-symbol arrays are {@code @FluxtionIgnore} and allocated in {@code @Initialise}, because the
 * builder serialises constructor state into the generated source including array contents.
 */
public class GenVenueCoreCfg {

    public static final int SUB_TICK_SHIFT = 4;
    public static final int BASE_HALF_SPREAD = 4 << SUB_TICK_SHIFT;
    /** Book older than this cannot be quoted. Nanoseconds on the data-driven clock. */
    public static final int STALE_NANOS = 20_000;

    public static final int BID = 0, ASK = 1;
    public static final int ACK_NEW = 0, ACK_REPLACE = 1, ACK_CANCEL = 2, REJECT = 3;
    public static final int NONE = 0, NEW = 1, REPLACE = 2, CANCEL = 3;

    public static class MarketTick {
        public int symbol, bidPx, askPx, bidQty, askQty;
        public long timestamp;
    }

    /** A venue acknowledgement. Always references the request it answers, by generation. */
    public static class OrderUpdate {
        public int symbol, side, type, qty, px;
        public long generation, timestamp;
    }

    /**
     * A real execution against a real working order. {@code generation} associates it with the order
     * as acknowledged; the engine refuses it otherwise.
     */
    public static class Execution {
        public int symbol, side, px, qty;
        public long generation, timestamp;
    }

    /**
     * A per-symbol freshness DEADLINE, not a global sweep tick.
     *
     * <p>The rolling-cursor design does not scale and its safety semantics change silently with the
     * symbol count: worst-case detection is symbols x interval, so holding a 20us bound at 4096
     * symbols needs a 5ns timer. Scheduling one deadline per symbol makes detection latency a
     * property of the DEADLINE, independent of how many symbols exist — which is what makes the
     * 64-to-4096 sweep a working-set experiment rather than a safety-semantics experiment.
     */
    public static class TimerTick {
        public long now;
        public int symbol;
    }

    /** The cycle's subject. No callbacks — shared state that happens to live in the graph. */
    public static class Cycle {
        public int symbol;
        public long now;
    }

    // ---- 1. MarketState -------------------------------------------------------------------------
    public static class MarketState {
        private final Cycle cycle;
        private final int symbolCount;
        @FluxtionIgnore public int[] bidPx, askPx, bidQty, askQty, mid, spread, imbalance;
        @FluxtionIgnore public long[] lastMarketTime;
        @FluxtionIgnore public boolean[] valid;

        public MarketState(Cycle cycle, @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle; this.symbolCount = symbolCount;
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
            mid[s] = ((t.bidPx + t.askPx) << SUB_TICK_SHIFT) >> 1;
            spread[s] = (t.askPx - t.bidPx) << SUB_TICK_SHIFT;
            final int total = t.bidQty + t.askQty;
            imbalance[s] = total == 0 ? 0 : ((t.bidQty - t.askQty) * 64) / total;
            lastMarketTime[s] = t.timestamp;
        }
    }

    // ---- 2. SignalState -------------------------------------------------------------------------
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
            vol[s] += (absMove - vol[s]) >> 3;
            momentum[s] += (delta - momentum[s]) >> 2;
            lastMid[s] = m;
        }
    }

    // ---- 3. WorkingOrders — acks AND executions -------------------------------------------------
    public static class WorkingOrders {
        private final Cycle cycle;
        private final int symbolCount;
        // [symbol*2 + side]
        @FluxtionIgnore public int[] livePx, liveQty, remainingQty;
        @FluxtionIgnore public boolean[] live, pending;
        @FluxtionIgnore public long[] generation, pendingGeneration;
        /** The quantity the last Execution actually applied — read by InventoryState this cycle. */
        public int lastFillSide, lastFillQty, lastFillSymbol;
        public long rejectedExecutions;

        public WorkingOrders(Cycle cycle, @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle; this.symbolCount = symbolCount;
        }

        @Initialise
        public void init() {
            final int n = symbolCount * 2;
            livePx = new int[n]; liveQty = new int[n]; remainingQty = new int[n];
            live = new boolean[n]; pending = new boolean[n];
            generation = new long[n]; pendingGeneration = new long[n];
        }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onOrderUpdate(OrderUpdate u) {
            cycle.symbol = u.symbol;
            cycle.now = u.timestamp;
            final int i = (u.symbol << 1) + u.side;
            // An ack that does not match the request in flight is stale and must be ignored, or a
            // late ack for a superseded order resurrects it.
            if (u.generation != pendingGeneration[i]) { return; }
            switch (u.type) {
                case ACK_NEW, ACK_REPLACE -> {
                    live[i] = true; pending[i] = false;
                    livePx[i] = u.px; liveQty[i] = u.qty; remainingQty[i] = u.qty;
                    generation[i] = u.generation;
                }
                case ACK_CANCEL, REJECT -> {
                    live[i] = false; pending[i] = false;
                    livePx[i] = 0; liveQty[i] = 0; remainingQty[i] = 0;
                    generation[i] = u.generation;
                }
                default -> { }
            }
        }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onExecution(Execution e) {
            cycle.symbol = e.symbol;
            cycle.now = e.timestamp;
            final int i = (e.symbol << 1) + e.side;
            lastFillQty = 0;
            lastFillSymbol = e.symbol;
            lastFillSide = e.side;
            // REFUSE an execution against an order that is not live, has been superseded, or has
            // nothing left. The harness must not be able to manufacture inventory.
            if (!live[i] || generation[i] != e.generation || remainingQty[i] <= 0) {
                rejectedExecutions++;
                return;
            }
            final int applied = Math.min(e.qty, remainingQty[i]);
            remainingQty[i] -= applied;
            lastFillQty = applied;
            if (remainingQty[i] == 0) { live[i] = false; }
        }

        public void markPending(int slot, long gen) { pending[slot] = true; pendingGeneration[slot] = gen; }
    }

    // ---- 4. InventoryState — fired by Execution, reads working orders as DATA -------------------
    public static class InventoryState extends EventLogNode {
        private final Cycle cycle;
        /**
         * Data only. Without {@code @NoTriggerReference} this node would be a child of WorkingOrders
         * and every ACK would drag the whole pricing chain behind it — the ack path is supposed to
         * stop at reconciliation.
         */
        @NoTriggerReference
        private final WorkingOrders working;
        private final int symbolCount;
        @FluxtionIgnore public int[] position;

        public InventoryState(Cycle cycle, WorkingOrders working,
                              @AssignToField("symbolCount") int symbolCount) {
            this.cycle = cycle; this.working = working; this.symbolCount = symbolCount;
        }

        @Initialise
        public void init() { position = new int[symbolCount]; }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onExecution(Execution e) {
            // WorkingOrders ran first and decided how much of this execution is real. Applying e.qty
            // here instead would let inventory exceed what was actually working.
            final int applied = working.lastFillQty;
            if (applied == 0) { return; }
            final int s = e.symbol;
            position[s] += e.side == BID ? applied : -applied;
            auditLog.info("sym", s).info("pos", position[s]).info("fill", applied);
        }
    }

    // ---- 5. FreshnessState ----------------------------------------------------------------------
    public static class FreshnessState {
        private final Cycle cycle;
        public long now;
        public long deadlinesFired;

        public FreshnessState(Cycle cycle) { this.cycle = cycle; }

        @OnEventHandler(failBuildIfMissingBooleanReturn = false)
        public void onTimer(TimerTick t) {
            now = t.now;
            cycle.now = t.now;
            cycle.symbol = t.symbol;
            deadlinesFired++;
        }
    }

    // ---- 6. FairValue ---------------------------------------------------------------------------
    public static class FairValue {
        private final Cycle cycle;
        private final MarketState market;
        private final SignalState signal;
        private final InventoryState inventory;
        private final int symbolCount;
        /** Tuning, configured at BUILD time. Static in the control variant; a field here. */
        private final int momentumShift;
        @FluxtionIgnore public int[] fair;

        public FairValue(Cycle cycle, MarketState market, SignalState signal, InventoryState inventory,
                         @AssignToField("symbolCount") int symbolCount,
                         @AssignToField("momentumShift") int momentumShift) {
            this.cycle = cycle; this.market = market; this.signal = signal;
            this.inventory = inventory; this.symbolCount = symbolCount;
            this.momentumShift = momentumShift;
        }

        @Initialise
        public void init() { fair = new int[symbolCount]; }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onInputs() {
            final int s = cycle.symbol;
            if (!market.valid[s]) { return; }
            final int microPriceAdj = (market.imbalance[s] * market.spread[s]) >> 7;
            final int momentumAdj = signal.momentum[s] >> momentumShift;
            fair[s] = market.mid[s] + microPriceAdj + momentumAdj;
        }
    }

    // ---- 7. QuoteBuilder ------------------------------------------------------------------------
    public static class QuoteBuilder {
        private final Cycle cycle;
        private final MarketState market;
        private final SignalState signal;
        private final InventoryState inventory;
        private final FairValue fairValue;
        private final int symbolCount;
        /** Tuning, configured at BUILD time. Static in the control variant; fields here. */
        private final int volShift, imbalanceShift, inventoryShift, baseQty, maxQuoteQty;
        @FluxtionIgnore public int[] desiredBidPx, desiredAskPx, desiredBidQty, desiredAskQty;

        public QuoteBuilder(Cycle cycle, MarketState market, SignalState signal,
                            InventoryState inventory, FairValue fairValue,
                            @AssignToField("symbolCount") int symbolCount,
                            @AssignToField("volShift") int volShift,
                            @AssignToField("imbalanceShift") int imbalanceShift,
                            @AssignToField("inventoryShift") int inventoryShift,
                            @AssignToField("baseQty") int baseQty,
                            @AssignToField("maxQuoteQty") int maxQuoteQty) {
            this.cycle = cycle; this.market = market; this.signal = signal;
            this.inventory = inventory; this.fairValue = fairValue; this.symbolCount = symbolCount;
            this.volShift = volShift; this.imbalanceShift = imbalanceShift;
            this.inventoryShift = inventoryShift; this.baseQty = baseQty;
            this.maxQuoteQty = maxQuoteQty;
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
                    + (signal.vol[s] >> volShift)
                    + ((pressure << SUB_TICK_SHIFT) >> imbalanceShift);
            final int centre = fairValue.fair[s] - (position >> inventoryShift);
            // Venue rounding: bid DOWN, ask UP, so a quote can never cross itself.
            desiredBidPx[s] = floorDiv16(centre - halfSpread);
            desiredAskPx[s] = ceilDiv16(centre + halfSpread);
            final int longPenalty = position > 0 ? position >> inventoryShift : 0;
            final int shortPenalty = position < 0 ? (-position) >> inventoryShift : 0;
            desiredBidQty[s] = clampQty(baseQty - longPenalty);
            desiredAskQty[s] = clampQty(baseQty - shortPenalty);
        }

        /** Arithmetic shift already floors for negatives; named so the intent is checkable. */
        private static int floorDiv16(int v) { return v >> SUB_TICK_SHIFT; }

        private static int ceilDiv16(int v) { return -((-v) >> SUB_TICK_SHIFT); }

        private int clampQty(int q) {
            return q < 1 ? 1 : (q > maxQuoteQty ? maxQuoteQty : q);
        }
    }

    // ---- 8. RiskLimits --------------------------------------------------------------------------
    public static class RiskLimits {
        private final Cycle cycle;
        private final MarketState market;
        private final InventoryState inventory;
        private final QuoteBuilder quote;
        private final FreshnessState freshness;
        private final int symbolCount;
        /**
         * Tuning, configured at BUILD time. Static in the control variant; a field here.
         *
         * <p>PUBLIC because the harness drives the graph into its risk path and needs the limit it was
         * built with. In the control it reads {@code GenVenueCore.POSITION_LIMIT} — a second copy of
         * the value that happens to agree. Reading it off the node means the test follows the
         * configuration instead of restating it.
         */
        public final int positionLimit;
        @FluxtionIgnore public boolean[] bidAllowed, askAllowed;
        public long staleSuppressed, riskSuppressed;

        public RiskLimits(Cycle cycle, MarketState market, InventoryState inventory,
                          QuoteBuilder quote, FreshnessState freshness,
                          @AssignToField("symbolCount") int symbolCount,
                          @AssignToField("positionLimit") int positionLimit) {
            this.cycle = cycle; this.market = market; this.inventory = inventory;
            this.quote = quote; this.freshness = freshness; this.symbolCount = symbolCount;
            this.positionLimit = positionLimit;
        }

        @Initialise
        public void init() {
            bidAllowed = new boolean[symbolCount]; askAllowed = new boolean[symbolCount];
        }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onQuote() {
            final int s = cycle.symbol;
            final boolean marketValid = market.valid[s];
            final boolean stale = cycle.now - market.lastMarketTime[s] > STALE_NANOS;
            final int position = inventory.position[s];
            final int projectedLong = position + quote.desiredBidQty[s];
            final int projectedShort = position - quote.desiredAskQty[s];
            final boolean bidOk = marketValid && !stale && projectedLong <= positionLimit;
            final boolean askOk = marketValid && !stale && projectedShort >= -positionLimit;
            bidAllowed[s] = bidOk;
            askAllowed[s] = askOk;
            if (stale) { staleSuppressed++; }
            else if (!bidOk || !askOk) { riskSuppressed++; }
        }
    }

    // ---- 9. OrderDiff ---------------------------------------------------------------------------
    public static class OrderDiff {
        private final Cycle cycle;
        private final QuoteBuilder quote;
        private final RiskLimits risk;
        private final WorkingOrders working;
        private final int symbolCount;
        /** Tuning, configured at BUILD time. Static in the control variant; a field here. */
        private final int qtyThreshold;
        @FluxtionIgnore public int[] action;
        public long none, neu, replace, cancel;
        public long noneNotAllowed, nonePending, noneUnchanged;

        public OrderDiff(Cycle cycle, QuoteBuilder quote, RiskLimits risk, WorkingOrders working,
                         @AssignToField("symbolCount") int symbolCount,
                         @AssignToField("qtyThreshold") int qtyThreshold) {
            this.cycle = cycle; this.quote = quote; this.risk = risk; this.working = working;
            this.symbolCount = symbolCount; this.qtyThreshold = qtyThreshold;
        }

        @Initialise
        public void init() { action = new int[symbolCount * 2]; }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onInputs() {
            final int s = cycle.symbol;
            action[s << 1] = decide(s, BID, risk.bidAllowed[s],
                    quote.desiredBidPx[s], quote.desiredBidQty[s]);
            action[(s << 1) + 1] = decide(s, ASK, risk.askAllowed[s],
                    quote.desiredAskPx[s], quote.desiredAskQty[s]);
        }

        private int decide(int symbol, int side, boolean allowed, int desiredPx, int desiredQty) {
            final int i = (symbol << 1) + side;
            final int a;
            if (!allowed) {
                if (working.live[i] && !working.pending[i]) { a = CANCEL; }
                else { a = NONE; noneNotAllowed++; }
            } else if (working.pending[i]) {
                a = NONE; nonePending++;
            } else if (!working.live[i]) {
                a = NEW;
            } else {
                final int dq = desiredQty - working.liveQty[i];
                final int adq = dq < 0 ? -dq : dq;
                if (desiredPx != working.livePx[i] || adq >= qtyThreshold) { a = REPLACE; }
                else { a = NONE; noneUnchanged++; }
            }
            switch (a) {
                case NEW -> neu++;
                case REPLACE -> replace++;
                case CANCEL -> cancel++;
                default -> none++;
            }
            return a;
        }
    }

    // ---- 10. IntentPublisher --------------------------------------------------------------------
    public static class IntentPublisher extends EventLogNode {
        private final Cycle cycle;
        private final OrderDiff diff;
        private final QuoteBuilder quote;
        private final WorkingOrders working;
        private final int ringSize;
        @FluxtionIgnore public int[] intentSymbol, intentSide, intentAction, intentPx, intentQty;
        @FluxtionIgnore public long[] intentGeneration;
        public int mask, cursor;
        public long emitted, nextGeneration;

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
            nextGeneration = 1;
        }

        @OnTrigger(failBuildIfMissingBooleanReturn = false)
        public void onDiff() {
            final int s = cycle.symbol;
            publish(s, BID, diff.action[s << 1], quote.desiredBidPx[s], quote.desiredBidQty[s]);
            publish(s, ASK, diff.action[(s << 1) + 1], quote.desiredAskPx[s], quote.desiredAskQty[s]);
        }

        private void publish(int symbol, int side, int action, int px, int qty) {
            if (action == NONE) { return; }
            final int slot = (symbol << 1) + side;
            final long gen = nextGeneration++;
            // Marking pending HERE, with the generation the venue will echo back, is what makes the
            // ack model closed: an ack whose generation does not match is a late ack for a superseded
            // order and WorkingOrders drops it.
            working.markPending(slot, gen);
            final int i = cursor++ & mask;
            intentSymbol[i] = symbol;
            intentSide[i] = side;
            intentAction[i] = action;
            intentPx[i] = action == CANCEL ? working.livePx[slot] : px;
            intentQty[i] = action == CANCEL ? 0 : qty;
            intentGeneration[i] = gen;
            emitted++;
            // SPARSE, and enough to reconstruct the executable decision: what, which side, at what
            // price and size, and which order it is.
            auditLog.info("sym", symbol).info("side", side).info("act", action)
                    .info("px", intentPx[i]).info("qty", intentQty[i]).info("gen", gen);
        }
    }

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
        final int ring = 1 << Integer.getInteger("ringBits", 16);

        // THE POINT OF THIS VARIANT. In the control these eight are `public static final int` — Java
        // compile-time constants the JIT folds, mirrored by hand as `constexpr` in the C++ arm. Here
        // they are configuration, declared ONCE and passed into the nodes at build time, so the
        // generated processor carries the values it was built with and the C++ arm receives them as
        // captured node state instead of a hand-kept duplicate.
        //
        // The values are identical to the control's, deliberately: the audit oracle then has to
        // produce a byte-identical log, which is what proves the change is representational and not
        // behavioural. A tuning change and a mechanism change measured together explain nothing.
        final int volShift       = 1;
        final int imbalanceShift = 5;
        final int inventoryShift = 2;
        final int momentumShift  = 2;
        final int baseQty        = 10;
        final int maxQuoteQty    = 25;
        final int positionLimit  = 2000;
        final int qtyThreshold   = 3;

        Cycle cycle = c.addNode(new Cycle(), "cycle");
        MarketState market = c.addNode(new MarketState(cycle, n), "market");
        SignalState signal = c.addNode(new SignalState(cycle, market, n), "signal");
        WorkingOrders working = c.addNode(new WorkingOrders(cycle, n), "working");
        InventoryState inventory = c.addNode(new InventoryState(cycle, working, n), "inventory");
        FreshnessState freshness = c.addNode(new FreshnessState(cycle), "freshness");
        FairValue fair = c.addNode(new FairValue(cycle, market, signal, inventory, n, momentumShift), "fair");
        QuoteBuilder quote = c.addNode(new QuoteBuilder(cycle, market, signal, inventory, fair, n,
                volShift, imbalanceShift, inventoryShift, baseQty, maxQuoteQty), "quote");
        RiskLimits risk = c.addNode(new RiskLimits(cycle, market, inventory, quote, freshness, n, positionLimit), "risk");
        OrderDiff diff = c.addNode(new OrderDiff(cycle, quote, risk, working, n, qtyThreshold), "diff");
        c.addNode(new IntentPublisher(cycle, diff, quote, working, ring), "intent");
    }

    public static void main(String[] a) throws Exception {
        String target = System.getProperty("target");
        String dir = System.getProperty("outDir");
        System.setProperty("fluxtion.sourceGeneratorId", "cpp".equals(target) ? "cpp" : "local");
        EventProcessorFactory.compile(GenVenueCoreCfg::graph, cfg -> {
            cfg.setPackageName("app.gen");
            cfg.setClassName("VenueCoreCfgProcessor");
            cfg.setOutputDirectory(dir);
            cfg.setResourcesOutputDirectory(dir);
            cfg.setWriteSourceToFile(true);
            cfg.setWriteGraphMlToFile(false);
            cfg.setCompileSource(false);
            cfg.setFormatSource(false);
        });
    }
}
