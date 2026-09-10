package app;

import app.gen.QuotingCoreProcessor;

/**
 * The quoting-core harness: a PRE-GENERATED event buffer, replayed.
 *
 * <p>Events are built before the clock starts and replayed from arrays, rather than computed from the
 * loop index inside the timed region. That removes the whole class of aliasing and predictability
 * defects the previous benchmark suffered — two of them, each of which produced a benchmark that ran
 * and measured the wrong path — and it means the timed loop contains dispatch and nothing else.
 *
 * <p><b>Deterministic.</b> A fixed-seed xorshift64 PRNG, implemented identically in the C++ harness,
 * so both languages replay the same stream and do the same work. Event type, symbol, price movement
 * and quantity are drawn from INDEPENDENT sub-streams, so no two of them can lock in phase.
 *
 * <p><b>Symbol selection is skewed, not round-robin.</b> Real books are not uniformly active; a
 * uniform sweep also happens to be the friendliest possible cache pattern, which would flatter every
 * arm equally and tell you nothing.
 *
 * <p><b>Two profiles.</b> {@code active} moves prices often, so the reconciler emits a high proportion
 * of NEW/REPLACE — the benchmark cannot win by doing nothing. {@code selective} moves them rarely, so
 * the correct answer is frequently NONE. Both report their branch mix, which is the honest way to
 * describe a workload: a claim that "1 fill per 32 ticks is realistic" is worth much less than the
 * measured proportion of decisions the engine actually reached.
 */
public class BenchQuotingCore {

    // ---- deterministic PRNG, identical in the C++ harness --------------------------------------
    private static final class Rng {
        private long s;
        Rng(long seed) { this.s = seed == 0 ? 0x9E3779B97F4A7C15L : seed; }
        long next() {
            s ^= s << 13;
            s ^= s >>> 7;      // LOGICAL shift — C++ uint64_t >> is logical, Java's >> is not
            s ^= s << 17;
            return s;
        }
        int nextInt(int bound) { return (int) ((next() >>> 33) % bound); }
    }

    // ---- event types, as small ints ------------------------------------------------------------
    private static final byte EV_MARKET = 0, EV_FILL = 1, EV_ORDER = 2, EV_TIMER = 3;

    // ---- the pre-generated buffer, struct-of-arrays --------------------------------------------
    private static byte[] evType;
    private static int[] evSymbol, evA, evB, evC, evD;
    private static long[] evTime;

    private static boolean SKEW = true;

    /** Per-symbol walk state, used only during generation. */
    private static void generate(int count, int symbols, boolean active, long seed) {
        evType = new byte[count];
        evSymbol = new int[count];
        evA = new int[count]; evB = new int[count]; evC = new int[count]; evD = new int[count];
        evTime = new long[count];

        // Independent sub-streams: type, symbol, price, size, anomaly. Drawing all of them from one
        // stream is how a generator ends up with correlations nobody intended.
        Rng rType = new Rng(seed), rSym = new Rng(seed ^ 0x1111), rPx = new Rng(seed ^ 0x2222),
            rQty = new Rng(seed ^ 0x3333), rOdd = new Rng(seed ^ 0x4444), rSide = new Rng(seed ^ 0x5555);

        int[] mid = new int[symbols];
        int[] spread = new int[symbols];
        int[] lastBq = new int[symbols];
        int[] lastAq = new int[symbols];
        for (int i = 0; i < symbols; i++) { lastBq[i] = 100; lastAq[i] = 100; }
        for (int i = 0; i < symbols; i++) { mid[i] = 10_000 + rPx.nextInt(200); spread[i] = 1 + rPx.nextInt(4); }

        long now = 1_000_000L;
        for (int i = 0; i < count; i++) {
            // Mix: market dominates, then order updates, then fills, then timers. Order updates are
            // frequent because every intent this engine emits would be acked in a real system, and if
            // acks are too rare the pending flags never clear and every decision degrades to NONE -
            // the benchmark would then measure the no-op path. The mix report below is what catches it.
            final int roll = rType.nextInt(100);
            final byte type = roll < 70 ? EV_MARKET : roll < 92 ? EV_ORDER : roll < 98 ? EV_FILL : EV_TIMER;

            // Skewed symbol choice: squaring a uniform concentrates activity on low-numbered symbols
            // without being degenerate. Some books are busy, most are not.
            //
            // BUT the skew hides the working set. With it on, raising the symbol count from 64 to 4096
            // moved causal latency by 0.6 ns, because the HOT set stayed in L1 however many symbols
            // were allocated. That is realistic and it is also the wrong instrument for a
            // working-set question, so -Dskew=false selects uniform selection and the sweep is
            // published both ways.
            final int sym;
            if (SKEW) {
                final int u = rSym.nextInt(1 << 14);
                sym = (int) (((long) u * u >>> 28) % symbols);
            } else {
                sym = rSym.nextInt(symbols);
            }

            now += 50 + rOdd.nextInt(200);
            evType[i] = type;
            evSymbol[i] = sym;
            evTime[i] = now;

            switch (type) {
                case EV_MARKET -> {
                    // A per-symbol random walk. In the active profile it moves nearly every tick; in
                    // the selective profile it usually does not, so the desired quote is unchanged and
                    // the reconciler's correct answer is NONE.
                    final int r = rPx.nextInt(active ? 5 : 40);
                    final int step = r == 0 ? -1 : r == 1 ? 1 : 0;
                    mid[sym] += step;
                    if (rOdd.nextInt(512) == 0) { mid[sym] += rPx.nextInt(9) - 4; }   // occasional jump
                    if (rOdd.nextInt(256) == 0) { spread[sym] = 1 + rPx.nextInt(4); }
                    final int half = spread[sym] >> 1;
                    int bid = mid[sym] - half - 1;
                    int ask = mid[sym] + half + 1;
                    // In the selective profile the book is QUIET - sizes hold as well as prices. A
                    // profile that stabilises the mid but reshuffles the sizes every tick still moves
                    // the imbalance term, so the desired quote still changes and "selective" measures
                    // the same thing as "active", which is what the first version did.
                    if (active || rOdd.nextInt(8) == 0) {
                        lastBq[sym] = 1 + rQty.nextInt(200);
                        lastAq[sym] = 1 + rQty.nextInt(200);
                    }
                    int bq = lastBq[sym];
                    int aq = lastAq[sym];
                    if (rOdd.nextInt(128) == 0) { bq = 1 + rQty.nextInt(20); aq = 200 + rQty.nextInt(200); }
                    if (rOdd.nextInt(1024) == 0) { bid = 0; }        // an invalid book, occasionally
                    evA[i] = bid; evB[i] = ask; evC[i] = bq; evD[i] = aq;
                }
                case EV_FILL -> {
                    evA[i] = (rSide.next() & 1) == 0 ? 1 : -1;
                    evB[i] = 1 + rQty.nextInt(10);
                    evC[i] = mid[sym];
                }
                case EV_ORDER -> {
                    // evD carries how far back in the intent ring this ack reaches - pre-generated, so
                    // the sequence is still fixed by the seed; the TARGET is resolved at dispatch.
                    evD[i] = rOdd.nextInt(16);
                    evA[i] = (rSide.next() & 1) == 0 ? 1 : -1;
                    // Acks dominate; partials and rejects are the minority but must be exercised
                    // because they are the paths that clear state on the unhappy route.
                    final int t = rOdd.nextInt(100);
                    evB[i] = t < 45 ? GenQuotingCore.ACK_NEW
                            : t < 80 ? GenQuotingCore.ACK_REPLACE
                            : t < 90 ? GenQuotingCore.ACK_CANCEL
                            : t < 97 ? GenQuotingCore.PARTIAL_FILL
                            : GenQuotingCore.REJECT;
                    evC[i] = 1 + rQty.nextInt(10);
                }
                default -> { }   // timer carries only its time
            }
        }
    }

    // Reused event objects — the buffer holds the data, these carry it in. No allocation per event.
    private static final GenQuotingCore.MarketTick TICK = new GenQuotingCore.MarketTick();
    private static final GenQuotingCore.Fill FILL = new GenQuotingCore.Fill();
    private static final GenQuotingCore.OrderUpdate ORDER = new GenQuotingCore.OrderUpdate();
    private static final GenQuotingCore.TimerTick TIMER = new GenQuotingCore.TimerTick();

    /**
     * The intent ring, so an ACK can reference an order the engine ACTUALLY SENT.
     *
     * <p>The first version drew the ack's symbol and side from the same skewed random stream as
     * everything else. That is not how a venue behaves, and it broke the benchmark: OrderDiff sets a
     * pending flag when it emits, and only an ack for that exact symbol and side clears it, so slots
     * went pending and stayed pending. 90.3% of all decisions came out NONE and both workload profiles
     * produced identical results — the engine had stopped reacting to the market at all and the
     * benchmark was measuring its no-op path, which is the same failure the previous benchmark had.
     *
     * <p>So the event SEQUENCE stays pre-generated and deterministic — type, timing and the offset
     * below are all drawn before the clock starts — but an ack resolves its target from the intent
     * ring at dispatch time. Acks then follow orders, which is both realistic and self-correcting.
     */
    private static GenQuotingCore.IntentPublisher intents;
    /** Trails the intent cursor: every order sent is eventually acked, roughly in order, as a venue does. */
    private static int ackCursor;

    /**
     * Serial mode folds one bit of the PREVIOUS cycle's output into this event's price, creating a
     * read-after-write dependency the hardware cannot speculate past. Elapsed/N is then a causal
     * latency rather than a reciprocal throughput. One bit only, so the workload is otherwise
     * unchanged - see BenchQuoteEngine for the full argument and the read-only control.
     */
    private static int dispatch(QuotingCoreProcessor p, int i, int dep) {
        switch (evType[i]) {
            case EV_MARKET -> {
                TICK.symbol = evSymbol[i]; TICK.bidPx = evA[i] + (dep & 1); TICK.askPx = evB[i];
                TICK.bidQty = evC[i]; TICK.askQty = evD[i]; TICK.timestamp = evTime[i];
                p.onEvent(TICK);
            }
            case EV_FILL -> {
                FILL.symbol = evSymbol[i]; FILL.side = evA[i]; FILL.qty = evB[i]; FILL.px = evC[i];
                p.onEvent(FILL);
            }
            case EV_ORDER -> {
                // A FIFO ack cursor trailing the intent cursor. Reaching back a bounded random window
                // instead left intents older than the window never acked, so their slots stayed pending
                // and 64% of all NONE decisions were "something is in flight" - a stuck state machine
                // rather than genuine selectivity. Acking in order is also what a venue actually does.
                // NOT `break` - inside an arrow-switch case that leaves the whole switch, silently
                // DROPPING the event rather than skipping the lookup. Order events stopped reaching the
                // processor at all and the decision count fell by a fifth with nothing to show why.
                if (ackCursor < intents.cursor) {
                    final int slot = ackCursor++ & intents.mask;
                    ORDER.symbol = intents.intentSymbol[slot];
                    ORDER.side = intents.intentSide[slot] == 0 ? 1 : -1;
                    ORDER.type = evB[i];
                    ORDER.qty = intents.intentQty[slot] == 0 ? 1 : intents.intentQty[slot];
                    ORDER.px = intents.intentPx[slot];
                    p.onEvent(ORDER);
                }
            }
            default -> { TIMER.now = evTime[i]; p.onEvent(TIMER); }
        }
        return intents.cursor;
    }

    /** Independent dispatch — no feedback, the throughput path. */
    private static void dispatch(QuotingCoreProcessor p, int i) { dispatch(p, i, 0); }

    public static void main(String[] a) throws Exception {
        final int iters = Integer.getInteger("iters", 20_000_000);
        final int warm = Integer.getInteger("warm", 2_000_000);
        final int batches = Integer.getInteger("batches", 6);
        final int symbols = Integer.getInteger("symbols", 64);
        final int bufferSize = Integer.getInteger("buffer", 1 << 20);
        final boolean active = !"selective".equals(System.getProperty("profile", "active"));
        final boolean audit = Boolean.getBoolean("audit");
        final long seed = Long.getLong("seed", 0xC0FFEEL);

        SKEW = !"false".equals(System.getProperty("skew"));
        generate(bufferSize, symbols, active, seed);
        final int mask = bufferSize - 1;
        if ((bufferSize & mask) != 0) { throw new IllegalArgumentException("buffer must be a power of two"); }

        QuotingCoreProcessor p = new QuotingCoreProcessor();
        long[] auditRecords = {0};
        if (audit) {
            com.telamin.fluxtion.runtime.audit.EventLogManager manager =
                    p.getAuditorById(com.telamin.fluxtion.runtime.audit.EventLogManager.NODE_NAME);
            manager.setLogSink(r -> auditRecords[0]++);
        }
        p.init();
        intents = p.getNodeById("intent");

        for (int i = 0; i < warm; i++) { dispatch(p, i & mask); }
        if (audit && auditRecords[0] == 0) {
            throw new IllegalStateException("audit=true but the sink saw no records - the audit log is dead");
        }

        if (Boolean.getBoolean("mix")) { reportMix(p, bufferSize, symbols, active, warm); return; }

        if (Boolean.getBoolean("dependent")) {
            // control mode reads the same output but never feeds it back, isolating the read's cost
            // from the serialisation's.
            final boolean control = "control".equals(System.getProperty("dependent"));
            int dep = 0;
            for (int i = 0; i < warm; i++) { dep = dispatch(p, i & mask, control ? 0 : dep); }
            double bestSerial = Double.MAX_VALUE;
            for (int b = 0; b < batches; b++) {
                long start = System.nanoTime();
                if (control) {
                    int sink = 0;
                    for (int i = 0; i < iters; i++) { sink += dispatch(p, i & mask, 0); }
                    dep = sink;
                } else {
                    for (int i = 0; i < iters; i++) { dep = dispatch(p, i & mask, dep); }
                }
                long ns = System.nanoTime() - start;
                bestSerial = Math.min(bestSerial, ns / (double) iters);
            }
            GenQuotingCore.IntentPublisher ip = p.getNodeById("intent");
            System.out.printf("RESULT harness=%s %s java-quotingcore-%s audit=%s symbols=%d profile=%s skew=%s "
                            + "%.4f ns intents=%d sink=%d records=%d%n",
                    HarnessVersion.tag(), HarnessVersion.runtimeTag(),
                    control ? "readcontrol" : "serial", audit, symbols,
                    active ? "active" : "selective", SKEW, bestSerial, ip.emitted, dep, auditRecords[0]);
            return;
        }

        double best = Double.MAX_VALUE;
        for (int b = 0; b < batches; b++) {
            long start = System.nanoTime();
            for (int i = 0; i < iters; i++) { dispatch(p, i & mask); }
            long ns = System.nanoTime() - start;
            best = Math.min(best, ns / (double) iters);
        }
        // Validate the ring OUTSIDE the timed region, so the work cannot be optimised away and the
        // checksum cannot cost anything measured.
        GenQuotingCore.IntentPublisher intent = p.getNodeById("intent");
        long checksum = 0;
        for (int i = 0; i < intent.intentSymbol.length; i++) {
            checksum = checksum * 31 + intent.intentSymbol[i] + intent.intentAction[i] * 7L
                    + intent.intentPx[i] * 13L + intent.intentQty[i] * 17L;
        }
        GenQuotingCore.OrderDiff diff = p.getNodeById("diff");
        System.out.printf("RESULT harness=%s %s java-quotingcore audit=%s symbols=%d profile=%s %.4f ns "
                        + "intents=%d checksum=%d records=%d%n",
                HarnessVersion.tag(), HarnessVersion.runtimeTag(), audit, symbols,
                active ? "active" : "selective", best, intent.emitted, checksum, auditRecords[0]);
        System.out.printf("   decisions NONE=%d NEW=%d REPLACE=%d CANCEL=%d%n",
                diff.none, diff.neu, diff.replace, diff.cancel);
    }

    /** The workload's own shape, which is the honest way to describe it. */
    private static void reportMix(QuotingCoreProcessor p, int bufferSize, int symbols,
                                  boolean active, int warm) throws NoSuchFieldException {
        long m = 0, f = 0, o = 0, t = 0;
        for (int i = 0; i < bufferSize; i++) {
            switch (evType[i]) {
                case EV_MARKET -> m++; case EV_FILL -> f++; case EV_ORDER -> o++; default -> t++;
            }
        }
        GenQuotingCore.OrderDiff diff = p.getNodeById("diff");
        GenQuotingCore.RiskLimits risk = p.getNodeById("risk");
        GenQuotingCore.IntentPublisher intent = p.getNodeById("intent");
        long decisions = diff.none + diff.neu + diff.replace + diff.cancel;
        double pct = 100.0 / bufferSize;
        double dpct = decisions == 0 ? 0 : 100.0 / decisions;
        System.out.printf("MIX profile=%s symbols=%d buffer=%d warmEvents=%d%n",
                active ? "active" : "selective", symbols, bufferSize, warm);
        System.out.printf("  events   market=%.1f%%  order=%.1f%%  fill=%.1f%%  timer=%.1f%%%n",
                m * pct, o * pct, f * pct, t * pct);
        // Raw counts as well as percentages: "CANCEL=0.0%" and "CANCEL never happens" are different
        // claims and a rounded percentage cannot tell them apart.
        System.out.printf("  decisions NONE=%.1f%% (%d)  NEW=%.1f%% (%d)  REPLACE=%.1f%% (%d)  CANCEL=%.2f%% (%d)  n=%d%n",
                diff.none * dpct, diff.none, diff.neu * dpct, diff.neu,
                diff.replace * dpct, diff.replace, diff.cancel * dpct, diff.cancel, decisions);
        System.out.printf("  suppressed  risk=%d  stale=%d   intents emitted=%d%n",
                risk.riskSuppressed, risk.staleSuppressed, intent.emitted);
        long n = diff.none == 0 ? 1 : diff.none;
        System.out.printf("  NONE breakdown  notAllowed=%.1f%%  pending=%.1f%%  unchanged=%.1f%%%n",
                diff.noneNotAllowed * 100.0 / n, diff.nonePending * 100.0 / n,
                diff.noneUnchanged * 100.0 / n);
    }
}
