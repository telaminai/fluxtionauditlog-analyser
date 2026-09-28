package com.acme.demo.r1;

import com.acme.demo.event.Events;
import com.acme.demo.replay.ReplayCapture;
import com.acme.demo.replay.ReplayReader;
import com.telamin.fluxtion.runtime.CloneableDataFlow;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.time.ClockStrategy;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** R1: record with ReplayCapture on a clock that ticks per read, replay with the matching runner, compare. */
public class R1Run {
    static final long START = 1767258000080L;
    static final String CAPTURE = "com.acme.demo.generated.DemoQuoteCaptureProcessor";
    static final String CHANGED = "com.acme.demo.generated.DemoQuoteChangedProcessor";

    static void append(StringBuilder log, String record) {
        if (record.contains("event: EventLogControlEvent")) return;
        log.append("---\n").append(record).append('\n');
    }

    static CloneableDataFlow<?> make(String cls) throws Exception {
        return (CloneableDataFlow<?>) Class.forName(cls).getDeclaredConstructor().newInstance();
    }

    public static void main(String[] a) throws Exception {
        Path out = Path.of(a[0]);
        Files.createDirectories(out);
        // ---- capture: the wall-clock case, a clock that ticks on EVERY read
        StringBuilder captured = new StringBuilder();
        StringWriter replay = new StringWriter();
        CloneableDataFlow<?> p = make(CAPTURE);
        p.init();
        p.start();
        long[] tick = {START};
        p.onEvent(ClockStrategy.registerClockEvent(() -> tick[0] += 10));
        p.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
        p.setAuditLogProcessor(r -> append(captured, r.toString()));
        ((ReplayCapture) p.getAuditorById(ReplayCapture.NAME)).setTarget(replay);
        for (Object e : List.of(
                new Events.MarketDataEvent("DEMO-A", 100.10, 100.30),
                new Events.OrderUpdateEvent("ord-1", "LIVE"),
                new Events.MarketDataEvent("DEMO-A", 100.12, 100.28),
                new Events.OrderUpdateEvent("ord-1", "DONE"),
                new Events.MarketDataEvent("DEMO-B", 55.01, 55.09),
                new Events.OrderUpdateEvent("ord-2", "LIVE"),
                new Events.OrderUpdateEvent("ord-3", "LIVE"))) {
            p.onEvent(e);
        }
        Files.writeString(out.resolve("captured-audit.yaml"), captured);
        Files.writeString(out.resolve("replay.yaml"), replay.toString());
        java.util.Set<Class<?>> handled = ((ReplayCapture) make(CAPTURE).getAuditorById(ReplayCapture.NAME)).getHandled();
        System.out.println("handled, derived at build time: " + handled.stream().map(Class::getSimpleName).sorted().toList()
                + "; raised: " + ((ReplayCapture) p.getAuditorById(ReplayCapture.NAME)).getRaised().stream().map(Class::getSimpleName).toList());
        List<ReplayReader.Entry> entries = ReplayReader.read(replay.toString(), handled);
        // witness: a replay naming a type this build does not handle is refused, never loaded
        try {
            ReplayReader.read(replay.toString().replaceFirst("Events\\$MarketDataEvent", "Events\\$NotHandled"), handled);
            System.out.println("WITNESS FAILED: an unhandled type was read");
        } catch (IllegalArgumentException refused) {
            System.out.println("refused: " + refused.getMessage());
        }
        System.out.println("captured: audit records " + count(captured) + ", replay records " + entries.size()
                + " (" + entries.stream().filter(ReplayReader.Entry::raised).count() + " raised)");

        // witness: a handled type the writer cannot encode fails when the processor is BUILT, by name
        try {
            new ReplayCapture().handles(java.util.Set.of(Unencodable.class));
            System.out.println("WITNESS FAILED: an unencodable type was accepted");
        } catch (IllegalArgumentException refused) {
            System.out.println("build-time refusal: " + refused.getMessage());
        }
        replay(out, "same-build", CAPTURE, entries, captured);
        replay(out, "changed-build", CHANGED, entries, captured);
    }

    static void replay(Path out, String label, String cls, List<ReplayReader.Entry> entries, StringBuilder captured)
            throws Exception {
        StringBuilder replayed = new StringBuilder();
        CloneableDataFlow<?> q = make(cls);
        q.init();
        q.start();
        long[] now = {0};
        q.onEvent(ClockStrategy.registerClockEvent(() -> now[0]));      // data-driven: the recorded instant
        q.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
        q.setAuditLogProcessor(r -> append(replayed, r.toString()));
        List<Object> heard = new ArrayList<>();
        ((ReplayCapture) q.getAuditorById(ReplayCapture.NAME)).setObserver(heard::add);   // observe, never write
        Deque<Object> raised = new ArrayDeque<>();
        List<String> divergences = new ArrayList<>();
        int injected = 0, matched = 0;
        for (int i = 0; i < entries.size(); i++) {
            ReplayReader.Entry r = entries.get(i);
            if (r.raised()) {
                Object got = raised.poll();
                if (r.event().equals(got)) matched++;
                else divergences.add("record " + i + ": the recorded run raised " + r.event() + "; this build raised "
                        + (got == null ? "nothing" : got));
                continue;
            }
            if (!raised.isEmpty()) {
                divergences.add("record " + i + ": this build raised " + raised + ", which the recorded run did not");
                raised.clear();
            }
            now[0] = r.time();
            heard.clear();
            q.onEvent(r.event());
            injected++;
            if (heard.isEmpty() || heard.get(0) != r.event()) divergences.add("record " + i + ": the input was not heard first");
            else raised.addAll(heard.subList(1, heard.size()));
        }
        if (!raised.isEmpty()) divergences.add("end: this build raised " + raised + ", which the recorded run did not");
        Files.writeString(out.resolve(label + "-replayed-audit.yaml"), replayed);
        System.out.println(label + ": injected " + injected + ", matched " + matched + ", audit records "
                + count(replayed) + ", byte-identical " + captured.toString().equals(replayed.toString())
                + (divergences.isEmpty() ? ", no divergence" : ", DIVERGES: " + divergences));
    }

    record Unencodable(java.util.List<String> items) { }

    static long count(CharSequence log) { return log.toString().lines().filter(l -> l.equals("---")).count(); }
}
