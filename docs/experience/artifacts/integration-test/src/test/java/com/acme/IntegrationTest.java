package com.acme;

import com.telamin.fluxtion.Fluxtion;
import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent.LogLevel;
import com.vendor.Events;
import com.vendor.capital.*;
import com.vendor.marketdata.*;
import com.vendor.pricing.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A representative integration: five vendor libraries the consumer did not write, combined into one
 * engine. Every capability an application would actually use, asserted from the framework's own
 * audit log rather than from an output format of our own.
 */
class IntegrationTest {

    // ---------------------------------------------------------------- composition

    @Test
    @DisplayName("five vendor declarations produce one interleaved graph the consumer never wrote")
    void composition() {
        Engine e = new Engine();
        int m = e.mark();
        e.feed("TICK,DEMO,100.0,104.0");
        List<String> d = e.dispatchSince(m);

        // stages from all five vendors run on one TICK
        assertTrue(d.contains("marketdata.mid"),   d.toString());
        assertTrue(d.contains("pricing.adjusted"), d.toString());
        assertTrue(d.contains("liquidity.score"),  d.toString());
        assertTrue(d.contains("risk.exposure"),    d.toString());
        assertTrue(d.contains("capital.charge"),   d.toString());

        // and they run in dependency order - nobody wrote this order down
        assertOrder(d, "marketdata.mid", "pricing.adjusted");
        assertOrder(d, "marketdata.depth", "liquidity.book");
        assertOrder(d, "pricing.adjusted", "liquidity.score");
        assertOrder(d, "liquidity.score", "risk.exposure");
        assertOrder(d, "risk.exposure", "capital.charge");
    }

    @Test
    @DisplayName("a node shared by three vendors is ONE instance, computed once per event")
    void sharedInstance() {
        Engine e = new Engine();
        int m = e.mark();
        e.feed("TICK,DEMO,100.0,104.0");
        long mids = e.dispatchSince(m).stream().filter("marketdata.mid"::equals).count();
        assertEquals(1, mids, "mid is consumed by pricing, risk and marketdata - but computed once");
    }

    // ---------------------------------------------------------------- propagation control

    @Test
    @DisplayName("an event a vendor does not care about arrests the whole path")
    void arrestOnUninterestedAdapter() {
        Engine e = new Engine();
        int m = e.mark();
        e.feed("CONFIG,keyNobodyOwns,42.0");
        assertEquals(List.of(), e.dispatchSince(m), "no vendor claims this key, so nothing may run");
    }

    @Test
    @DisplayName("a detector that does not trip stops everything below it")
    void arrestOnDetector() {
        Engine e = new Engine();
        int m = e.mark();
        e.feed("TICK,DEMO,100.0,104.0");                 // exposure stays under the limit
        assertFalse(e.dispatchSince(m).contains("capital.breachCount"),
                "breachCount must not run while exposure is below its limit");

        m = e.mark();
        e.feedAll("TRADE,DEMO,5000.0,102.0");            // now it breaches
        assertTrue(e.dispatchSince(m).contains("capital.breachCount"));
    }

    // ---------------------------------------------------------------- shared state

    @Test
    @DisplayName("state accumulates across events and is never reset by dispatch")
    void statefulAccumulation() {
        Engine e = new Engine();
        e.feedAll("TICK,DEMO,100.0,104.0", "TRADE,DEMO,5000.0,102.0");
        int after1 = e.processor.capital.breachCount.breaches;
        e.feed("TRADE,DEMO,6000.0,102.0");
        int after2 = e.processor.capital.breachCount.breaches;
        assertTrue(after1 >= 1);
        assertEquals(after1 + 1, after2, "the counter accumulates - it is not recomputed from scratch");
    }

    // ---------------------------------------------------------------- runtime behaviour swap

    @Test
    @DisplayName("a strategy is replaced at runtime: values change, dispatch does not")
    void strategySwapAtRuntime() {
        Engine e = new Engine();
        e.feedAll("TICK,DEMO,100.0,104.0", "TRADE,DEMO,5000.0,102.0");

        int m = e.mark();
        e.feed("TRADE,DEMO,5000.0,102.0");
        List<String> before = e.dispatchSince(m);
        double feeBefore = e.processor.capital.fee.value;

        e.processor.registerService(
                new com.telamin.fluxtion.runtime.service.Service<>(new FeeStrategy() {
                    public double fee(double x) { return x * 0.05; }
                    public String name() { return "premium-5pct"; }
                }, FeeStrategy.class));

        m = e.mark();
        e.feed("TRADE,DEMO,5000.0,102.0");
        List<String> after = e.dispatchSince(m);
        double feeAfter = e.processor.capital.fee.value;

        assertEquals(before, after, "structure is static: the dispatch is identical across the swap");
        assertEquals(feeBefore * 5, feeAfter, 1e-6, "behaviour is dynamic: the function changed");
    }

    // ---------------------------------------------------------------- rebuild with live state

    @Test
    @DisplayName("the same node instances move into a new compiled graph with their state intact")
    void rebuildPreservesState() {
        List<String> log = new ArrayList<>();
        MdTick tick = new MdTick(); MdConfig cfg = new MdConfig();
        Mid mid = new Mid(tick); Depth depth = new Depth(tick);
        Counter counter = new Counter(tick);

        DataFlow a = build(log, mid, depth, cfg, counter);
        for (int i = 0; i < 3; i++) a.onEvent(new Events.Tick("DEMO", 100, 102));
        assertEquals(3, counter.count);

        // rebuild: same instances, plus a pricing subtree that did not exist before
        Adjusted adjusted = new Adjusted(mid, depth);
        DataFlow b = build(log, mid, depth, cfg, counter, adjusted);
        assertEquals(3, counter.count, "state survived the rebuild");

        int m = log.size();
        b.onEvent(new Events.Tick("DEMO", 200, 210));
        List<String> d = new ArrayList<>();
        for (String r : log.subList(m, log.size())) d.addAll(Engine.stagesOf(r));

        assertEquals(4, counter.count, "the counter continued rather than restarting");
        assertTrue(d.contains("pricing.adjusted"), "the new node entered the dispatch: " + d);
        assertEquals(205.0, mid.value, 1e-9, "the shared node kept computing across the rebuild");
    }

    static DataFlow build(List<String> log, Object... nodes) {
        DataFlow f = (DataFlow) Fluxtion.compileDispatcher(c -> {
            for (Object n : nodes) c.addNode(n);
            c.addEventAudit(LogLevel.INFO);
        });
        f.setAuditLogProcessor(r -> log.add(r.asCharSequence().toString()));
        f.setAuditLogLevel(LogLevel.INFO);
        f.init();
        return f;
    }

    // ---------------------------------------------------------------- helper

    static void assertOrder(List<String> seq, String first, String then) {
        int a = seq.indexOf(first), b = seq.indexOf(then);
        assertTrue(a >= 0 && b >= 0, first + " and " + then + " must both run: " + seq);
        assertTrue(a < b, first + " must run before " + then + ": " + seq);
    }
}
