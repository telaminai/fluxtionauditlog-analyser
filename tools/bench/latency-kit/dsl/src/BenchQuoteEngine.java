package app;

import app.gen.QuoteEngineProcessor;

/**
 * Throughput AND per-event latency for the hand-written quote engine.
 *
 * <p>Both, because they are different measurements and this kit has only ever published the first.
 * Throughput is {@code t0 = now; loop N; (now - t0)/N} — steady-state reciprocal rate, with successive
 * events overlapping in the pipeline. Latency times each event individually, which costs a clock read
 * per event and only means anything if the event is large against the clock.
 *
 * <p>The event stream is a realistic mix: ticks dominate, fills are one in {@code fillEvery}. A stream
 * of one event type through one branch keeps every branch hot and flatters the result.
 */
public class BenchQuoteEngine {

    public static void main(String[] a) throws Exception {
        int iters = Integer.getInteger("iters", 20_000_000);
        int warm = Integer.getInteger("warm", 2_000_000);
        int batches = Integer.getInteger("batches", 6);
        int symbols = Integer.getInteger("symbols", 64);
        int fillEvery = Integer.getInteger("fillEvery", 32);
        boolean audit = Boolean.getBoolean("audit");
        boolean latency = Boolean.getBoolean("latency");

        QuoteEngineProcessor p = new QuoteEngineProcessor();
        long[] auditRecords = {0};
        if (audit) {
            // A counting sink, not a writer: the claim is the cost of the audit PATH, not the disk.
            com.telamin.fluxtion.runtime.audit.EventLogManager manager =
                    p.getAuditorById(com.telamin.fluxtion.runtime.audit.EventLogManager.NODE_NAME);
            manager.setLogSink(record -> auditRecords[0]++);
        }
        p.init();

        GenQuoteEngine.MarketTick tick = new GenQuoteEngine.MarketTick();
        GenQuoteEngine.Fill fill = new GenQuoteEngine.Fill();

        for (int i = 0; i < warm; i++) { feed(p, tick, fill, i, symbols, fillEvery); }

        if (audit && auditRecords[0] == 0) {
            // The failure this exists to catch: an audited build that publishes nothing reads as a
            // speed-up. An earlier profile did exactly that, and only an assertion like this found it.
            throw new IllegalStateException("audit=true but the sink saw no records - the audit log is dead");
        }

        if (Boolean.getBoolean("dependent")) {
            runDependentChain(p, tick, fill, iters, warm, batches, symbols, fillEvery, audit, auditRecords);
            return;
        }
        if (Boolean.getBoolean("alloc")) {
            runAllocation(p, tick, fill, iters, symbols, fillEvery, audit, auditRecords);
            return;
        }
        if (latency) {
            runLatency(p, tick, fill, iters, symbols, fillEvery, audit, auditRecords);
            return;
        }

        double best = Double.MAX_VALUE;
        for (int b = 0; b < batches; b++) {
            long start = System.nanoTime();
            for (int i = 0; i < iters; i++) { feed(p, tick, fill, i, symbols, fillEvery); }
            long ns = System.nanoTime() - start;
            best = Math.min(best, ns / (double) iters);
        }
        System.out.printf("RESULT harness=%s %s java-quoteengine audit=%s %.4f ns published=%d records=%d%n",
                HarnessVersion.tag(), HarnessVersion.runtimeTag(), audit, best,
                publishedCount(p), auditRecords[0]);
    }

    /**
     * BURST latency, because per-event latency is not measurable here and saying so is not a result.
     *
     * <p>System.nanoTime resolves to 41 ns on this machine and this graph costs 9-22 ns per event, so
     * timing events individually produces a distribution of the counter: 74% of unaudited events did
     * not move the clock at all. That refusal is correct and it is also unhelpful, because the tail is
     * exactly what a quoting engine is judged on.
     *
     * <p>So time a BURST of {@code burst} events instead. It is a real quantity - a market-data batch
     * arriving in one agent-loop pass is precisely this - and it puts the measurement comfortably
     * above the counter's floor: at 16 events the quantisation is 41.67/16 = 2.6 ns per event. What it
     * buys is the tail: a stall inside a burst still lands in that burst's sample, so p99.9 and max
     * reflect real outliers rather than counter granularity. What it gives up is the ability to say
     * anything about ONE event's latency, and this does not pretend otherwise.
     */
    private static void runLatency(QuoteEngineProcessor p, GenQuoteEngine.MarketTick tick,
                                   GenQuoteEngine.Fill fill, int iters, int symbols, int fillEvery,
                                   boolean audit, long[] auditRecords) throws NoSuchFieldException {
        final int burst = Integer.getInteger("burst", 16);
        // The instrument's RESOLUTION, not its call cost: they differ by an order of magnitude on
        // Apple Silicon and only resolution bounds what can be seen. Measured here rather than
        // assumed, because it is a property of the machine the run happens on.
        long resolution = Long.MAX_VALUE;
        for (int i = 0; i < 500_000; i++) {
            long x = System.nanoTime(), y = System.nanoTime();
            long d = y - x;
            if (d > 0 && d < resolution) { resolution = d; }
        }
        final int buckets = 65536;   // 1 ns buckets to 65 us, then saturating
        int[] counts = new int[buckets];
        long worst = 0, zero = 0;
        final int bursts = iters / burst;
        for (int b = 0; b < bursts; b++) {
            final int base = b * burst;
            long start = System.nanoTime();
            for (int k = 0; k < burst; k++) { feed(p, tick, fill, base + k, symbols, fillEvery); }
            long d = System.nanoTime() - start;
            if (d < 0) { d = 0; }
            if (d == 0) { zero++; }
            if (d > worst) { worst = d; }
            counts[(int) Math.min(d, buckets - 1)]++;
        }
        long p50 = percentile(counts, bursts, 0.50);
        double zeroShare = zero / (double) bursts;
        if (p50 < resolution * 2 || zeroShare > 0.5) {
            System.out.printf("REFUSED harness=%s %s java-quoteengine audit=%s burst=%d: %.1f%% of bursts "
                            + "did not move the clock (resolution %d ns) and p50 is %d ns. Every "
                            + "percentile here would be quantisation - raise -Dburst.%n",
                    HarnessVersion.tag(), HarnessVersion.runtimeTag(), audit, burst, zeroShare * 100.0,
                    resolution, p50);
            System.exit(2);
        }
        // Per-event figures are the burst divided by its size, and are labelled as such. They are a
        // per-event AVERAGE WITHIN A BURST, not a per-event latency - the difference matters and the
        // key names keep it visible.
        // The WHOLE distribution, not four points. p99.99 is where a quoting engine actually lives -
        // one bad quote in ten thousand is a real event at these rates - and it needs 1M bursts to have
        // 100 samples behind it rather than 20. The per-event column divides by the burst size and is
        // an average within a burst, which the name says.
        System.out.printf("RESULT harness=%s %s java-quoteengine-burstlatency audit=%s burst=%d "
                        + "p50=%d p90=%d p99=%d p999=%d p9999=%d max=%d "
                        + "perEventP50=%.2f perEventP99=%.2f perEventP9999=%.2f "
                        + "timerResolution=%d ns bursts=%d published=%d records=%d%n",
                HarnessVersion.tag(), HarnessVersion.runtimeTag(), audit, burst, p50,
                percentile(counts, bursts, 0.90), percentile(counts, bursts, 0.99),
                percentile(counts, bursts, 0.999), percentile(counts, bursts, 0.9999), worst,
                p50 / (double) burst, percentile(counts, bursts, 0.99) / (double) burst,
                percentile(counts, bursts, 0.9999) / (double) burst,
                resolution, bursts, publishedCount(p), auditRecords[0]);
        if (Boolean.getBoolean("cdf")) {
            // The histogram itself, so the shape can be read rather than inferred from percentiles.
            StringBuilder sb = new StringBuilder("CDF ");
            long seen = 0;
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] == 0) { continue; }
                seen += counts[i];
                sb.append(i).append(':').append(String.format("%.6f", seen / (double) bursts)).append(' ');
            }
            System.out.println(sb);
        }
    }

    /**
     * SINGLE-EVENT LATENCY, by making the events serially dependent.
     *
     * <p>Every other figure in this kit — including the burst latencies — is reciprocal throughput.
     * The machine overlaps work from successive events, so N independent events take far less than N
     * times one event, and no amount of timing them more precisely turns that into a latency. The
     * clock cannot help either: it resolves to 41.67 ns and an unaudited event costs single-digit ns.
     *
     * <p>So change the EXPERIMENT rather than the instrument. Each event's input is derived from the
     * PREVIOUS event's output — one bit of the computed bid price is folded into the next tick — which
     * creates a read-after-write dependency the hardware cannot speculate past. Event i+1 cannot begin
     * until event i's result exists. The loop is then a serial chain of graph traversals, and elapsed
     * time divided by N IS the causal latency of one event: entry to result.
     *
     * <p>Only ONE bit is fed back, so prices stay inside their intended range and the graph keeps
     * doing the same work with the same publish rate. The dependency is real to the CPU regardless of
     * how few bits carry it — it cannot know the value is nearly constant, and must wait for the load.
     *
     * <p>What this measures INCLUDES the loop's own address arithmetic and the field read, so it is a
     * slight over-estimate of the graph's own latency. It is an upper bound, which is the right
     * direction for a latency claim.
     */
    private static void runDependentChain(QuoteEngineProcessor p, GenQuoteEngine.MarketTick tick,
                                          GenQuoteEngine.Fill fill, int iters, int warm, int batches,
                                          int symbols, int fillEvery, boolean audit,
                                          long[] auditRecords) throws NoSuchFieldException {
        GenQuoteEngine.QuoteCalculator quote = p.getNodeById("quote");
        // THE CONTROL. Dependent mode adds a field read per event as well as a dependency, and without
        // separating them the read's cost would be reported as latency. In control mode the output is
        // read and ACCUMULATED into a sink that never reaches the next input — same reads, same loop,
        // no read-after-write chain. The difference between the two modes is then the serialisation
        // alone, which is what a latency claim needs.
        final boolean control = "control".equals(System.getProperty("dependent"));
        int dep = 0;
        for (int i = 0; i < warm; i++) { dep = feedDependent(p, tick, fill, i, symbols, fillEvery, control ? 0 : dep, quote); }
        double best = Double.MAX_VALUE;
        for (int b = 0; b < batches; b++) {
            long start = System.nanoTime();
            if (control) {
                int sink = 0;
                for (int i = 0; i < iters; i++) {
                    sink += feedDependent(p, tick, fill, i, symbols, fillEvery, 0, quote);
                }
                dep = sink;
            } else {
                for (int i = 0; i < iters; i++) {
                    dep = feedDependent(p, tick, fill, i, symbols, fillEvery, dep, quote);
                }
            }
            long ns = System.nanoTime() - start;
            best = Math.min(best, ns / (double) iters);
        }
        System.out.printf("RESULT harness=%s %s java-quoteengine-%s audit=%s %.4f ns "
                        + "published=%d records=%d sink=%d%n",
                HarnessVersion.tag(), HarnessVersion.runtimeTag(), control ? "readcontrol" : "serial",
                audit, best, publishedCount(p), auditRecords[0], dep);
    }

    /** Returns the graph's output so the caller can feed it back in — that IS the dependency. */
    private static int feedDependent(QuoteEngineProcessor p, GenQuoteEngine.MarketTick tick,
                                     GenQuoteEngine.Fill fill, int i, int symbols, int fillEvery,
                                     int dep, GenQuoteEngine.QuoteCalculator quote) {
        if ((i % fillEvery) == 0) {
            int fillNo = i / fillEvery;
            fill.symbol = fillNo % symbols;
            fill.qty = ((fillNo % 7) - 3) * 10;
            p.onEvent(fill);
            return quote.bidPx;
        }
        tick.symbol = i % symbols;
        // ONE bit of the previous result, so the range is unchanged and the dependency is real.
        tick.bidPx = 10_000 + ((i + (dep & 1)) & 63);
        tick.askPx = tick.bidPx + 2 + (i & 3);
        tick.bidQty = 100 + (i & 31);
        tick.askQty = 100 + ((i >> 3) & 31);
        p.onEvent(tick);
        return quote.bidPx;
    }

    /**
     * Bytes allocated PER EVENT, measured rather than asserted.
     *
     * <p>"Allocation-free" is the kind of claim that is easy to make from reading the code and wrong
     * the moment one autobox or one varargs array is on the path. {@code getThreadAllocatedBytes} is
     * the JVM's own accounting for this thread, so it counts what the code actually did — including
     * anything the framework allocates inside the audit call that a reader of the node would not see.
     *
     * <p>Measured AFTER warmup, because a JIT still compiling allocates profiling structures that have
     * nothing to do with the steady state, and because the first pass through the audit path interns
     * its keys. Both would be charged to the graph otherwise.
     */
    private static void runAllocation(QuoteEngineProcessor p, GenQuoteEngine.MarketTick tick,
                                      GenQuoteEngine.Fill fill, int iters, int symbols, int fillEvery,
                                      boolean audit, long[] auditRecords) throws NoSuchFieldException {
        com.sun.management.ThreadMXBean threads =
                (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        if (!threads.isThreadAllocatedMemorySupported()) {
            System.out.println("REFUSED: this JVM does not account thread allocation - no figure to give");
            System.exit(2);
        }
        threads.setThreadAllocatedMemoryEnabled(true);
        final long id = Thread.currentThread().threadId();
        // A second warm pass: the first touched the audit path's key interning, which is a one-off and
        // would otherwise be divided across the measured events and reported as a per-event cost.
        for (int i = 0; i < 200_000; i++) { feed(p, tick, fill, i, symbols, fillEvery); }
        final long before = threads.getThreadAllocatedBytes(id);
        for (int i = 0; i < iters; i++) { feed(p, tick, fill, i, symbols, fillEvery); }
        final long bytes = threads.getThreadAllocatedBytes(id) - before;
        System.out.printf("RESULT harness=%s %s java-quoteengine-alloc audit=%s bytes=%d events=%d "
                        + "bytesPerEvent=%.4f published=%d records=%d%n",
                HarnessVersion.tag(), HarnessVersion.runtimeTag(), audit, bytes, iters,
                bytes / (double) iters, publishedCount(p), auditRecords[0]);
    }

    private static long percentile(int[] counts, long n, double p) {
        long target = (long) (p * n), seen = 0;
        for (int i = 0; i < counts.length; i++) {
            seen += counts[i];
            if (seen >= target) { return i; }
        }
        return counts.length - 1;
    }

    private static void feed(QuoteEngineProcessor p, GenQuoteEngine.MarketTick tick,
                             GenQuoteEngine.Fill fill, int i, int symbols, int fillEvery) {
        if ((i % fillEvery) == 0) {
            // The fill INDEX, not the event index. Fills happen only when (i % fillEvery) == 0, and
            // for fillEvery = 32 every such i is a multiple of 32 - so (i & 7) is always 0 and every
            // fill carried the SAME quantity. Inventory drifted to -54705, the risk gate suppressed
            // 82% of quotes, and the graph spent the benchmark on its early-return path.
            int fillNo = i / fillEvery;
            fill.symbol = fillNo % symbols;
            // MEAN ZERO over each cycle of 8: -35 -25 -15 -5 +5 +15 +25 +35 sums to nothing. The
            // obvious ((i & 7) - 4) * 10 sums to -40, so inventory drifts monotonically short, breaks
            // the position limit within a few thousand fills, and the risk gate then suppresses
            // everything - 94% of events took the early return and the publish path this is supposed
            // to be measuring, audit logging included, was barely executed.
            // The quantity cycle length must be COPRIME with the symbol count, or the two alias and
            // every symbol receives the same quantity forever. symbols = 64 and an 8-long cycle share
            // a factor of 8: for a fixed symbol, fillNo is congruent mod 64, so (fillNo & 7) is
            // CONSTANT. Inventory drifted per symbol, the risk gate suppressed 94% of quotes, and the
            // benchmark measured the early-return path. 7 is coprime with 64, so each symbol sees
            // every phase and the flow sums to zero over each cycle.
            fill.qty = ((fillNo % 7) - 3) * 10;
            p.onEvent(fill);
            return;
        }
        tick.symbol = i % symbols;
        tick.bidPx = 10_000 + (i & 63);
        tick.askPx = tick.bidPx + 2 + (i & 3);
        tick.bidQty = 100 + (i & 31);
        tick.askQty = 100 + ((i >> 3) & 31);
        p.onEvent(tick);
    }

    private static int publishedCount(QuoteEngineProcessor p) throws NoSuchFieldException {
        GenQuoteEngine.QuotePublisher publisher = p.getNodeById("publisher");
        return publisher.published;
    }
}
