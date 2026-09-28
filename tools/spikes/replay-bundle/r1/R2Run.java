package com.acme.demo.r2;

import com.acme.demo.event.Events;
import com.acme.demo.replay.ReplayCapture;
import com.acme.demo.replay.ReplayReader;
import com.telamin.fluxtion.runtime.CloneableDataFlow;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.time.ClockStrategy;

import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * R2: record at the consumption point by IDENTITY. The caller names each input to the writer before onEvent, so a
 * graph-raised event is never recorded, even when an input of the same type also arrives from outside. Replay then
 * just injects every record: no matcher, no declaration of what the graph raises.
 */
public class R2Run {
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
        StringBuilder captured = new StringBuilder();
        StringWriter replay = new StringWriter();
        CloneableDataFlow<?> p = make(CAPTURE);
        p.init();
        p.start();
        long[] tick = {START};
        p.onEvent(ClockStrategy.registerClockEvent(() -> tick[0] += 10));   // ticks on every read
        p.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
        p.setAuditLogProcessor(r -> append(captured, r.toString()));
        ReplayCapture writer = p.getAuditorById(ReplayCapture.NAME);
        writer.setTarget(replay);
        List<Object> inputs = List.of(
                new Events.RiskBreachEvent("ext-1", 9),          // a breach from OUTSIDE: the same type the graph raises
                new Events.MarketDataEvent("DEMO-A", 100.10, 100.30),
                new Events.OrderUpdateEvent("ord-1", "LIVE"),
                new Events.MarketDataEvent("DEMO-A", 100.12, 100.28),
                new Events.OrderUpdateEvent("ord-1", "DONE"),
                new Events.MarketDataEvent("DEMO-B", 55.01, 55.09),
                new Events.OrderUpdateEvent("ord-2", "LIVE"),
                new Events.OrderUpdateEvent("ord-3", "LIVE"));   // the graph raises its own breach here
        for (Object e : inputs) {
            writer.expect(e);                                     // the consumption point names the input
            p.onEvent(e);
        }
        Files.writeString(out.resolve("captured-audit.yaml"), captured);
        Files.writeString(out.resolve("replay.yaml"), replay.toString());
        Set<Class<?>> handled = writer.getHandled();
        List<ReplayReader.Entry> entries = ReplayReader.read(replay.toString(), handled);
        long breaches = entries.stream().filter(e -> e.event() instanceof Events.RiskBreachEvent).count();
        System.out.println("captured: inputs " + inputs.size() + ", audit records " + count(captured)
                + ", replay records " + entries.size() + " (breaches recorded: " + breaches + ", the external one only)");
        replay(out, "same-build", CAPTURE, entries, captured);
        replay(out, "changed-build", CHANGED, entries, captured);
    }

    /** Plain replay: every record is an input, injected at its recorded instant. */
    static void replay(Path out, String label, String cls, List<ReplayReader.Entry> entries, StringBuilder captured)
            throws Exception {
        StringBuilder replayed = new StringBuilder();
        CloneableDataFlow<?> q = make(cls);
        q.init();
        q.start();
        long[] now = {0};
        q.onEvent(ClockStrategy.registerClockEvent(() -> now[0]));
        q.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
        q.setAuditLogProcessor(r -> append(replayed, r.toString()));
        for (ReplayReader.Entry r : entries) {
            now[0] = r.time();
            q.onEvent(r.event());
        }
        Files.writeString(out.resolve(label + "-replayed-audit.yaml"), replayed);
        System.out.println(label + ": injected " + entries.size() + ", audit records " + count(replayed)
                + " (captured " + count(captured) + "), byte-identical " + captured.toString().equals(replayed.toString()));
    }

    static long count(CharSequence log) { return log.toString().lines().filter(l -> l.equals("---")).count(); }
}
