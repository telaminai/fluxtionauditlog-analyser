import com.acme.demo.event.Events;
import com.acme.demo.generated.DemoQuoteProcessor;
import com.telamin.fluxtion.builder.replay.YamlReplayRecordWriter;
import com.telamin.fluxtion.builder.replay.YamlReplayRunner;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.time.Clock;
import com.telamin.fluxtion.runtime.time.ClockStrategy;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Evidence-bundle replay spike: does replaying the recorded inputs, with data-driven time, reproduce the audit log?
 * Capture: one DemoQuoteProcessor run with a ticking synthetic clock, writing BOTH the replay record (YAML, via
 * YamlReplayRecordWriter) and the audit log. Replay: a FRESH processor fed only the replay YAML by YamlReplayRunner,
 * writing its own audit log. Then the two audit logs, record by record.
 */
public class ReplaySpike {
    static final long START = 1767258000080L;

    /** The fixture generator's framing: a separator before each record, the log's own control records dropped. */
    static void append(StringBuilder log, String record) {
        if (record.contains("event: EventLogControlEvent")) return;
        log.append("---\n").append(record).append('\n');
    }

    public static void main(String[] a) throws Exception {
        Path out = Path.of(a.length > 0 ? a[0] : "out");
        // per-read: the clock ticks on EVERY read (the fixture generator's strategy); per-event: it moves only between events
        boolean perRead = a.length < 2 || a[1].equals("per-read");
        Files.createDirectories(out);

        // ---- capture
        StringBuilder capturedAudit = new StringBuilder();
        StringWriter replayYaml = new StringWriter();
        DemoQuoteProcessor p = new DemoQuoteProcessor();
        p.init();
        long[] tick = {START};
        p.onEvent(ClockStrategy.registerClockEvent(perRead ? () -> tick[0] += 10 : () -> tick[0]));
        p.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
        p.setAuditLogProcessor(r -> append(capturedAudit, r.toString()));
        // the recorder reads the same clock the processor does, as a production auditor would
        Clock recorderClock = new Clock();
        recorderClock.setClockStrategy(ClockStrategy.registerClockEvent(() -> tick[0]));
        YamlReplayRecordWriter recorder = new YamlReplayRecordWriter(recorderClock);
        recorder.setTargetWriter(replayYaml);
        recorder.init();
        List<Object> events = List.of(
                new Events.MarketDataEvent("DEMO-A", 100.10, 100.30),
                new Events.OrderUpdateEvent("ord-1", "LIVE"),
                new Events.MarketDataEvent("DEMO-A", 100.12, 100.28),
                new Events.OrderUpdateEvent("ord-1", "DONE"),
                new Events.MarketDataEvent("DEMO-B", 55.01, 55.09),
                new Events.OrderUpdateEvent("ord-2", "LIVE"),
                new Events.OrderUpdateEvent("ord-3", "LIVE"));
        for (Object e : events) {
            if (!perRead) tick[0] += 10;             // time moves between events, never within one
            recorder.eventReceived(e);
            p.onEvent(e);
        }
        Files.writeString(out.resolve("captured-audit.yaml"), capturedAudit);
        Files.writeString(out.resolve("replay-record.yaml"), replayYaml.toString());

        // ---- replay: a fresh processor, fed only the replay YAML, with data-driven time
        StringBuilder replayedAudit = new StringBuilder();
        DemoQuoteProcessor q = new DemoQuoteProcessor();
        YamlReplayRunner runner = YamlReplayRunner.newSession(new StringReader(replayYaml.toString()), q).callInit();
        q.setAuditLogLevel(EventLogControlEvent.LogLevel.INFO);
        q.setAuditLogProcessor(r -> append(replayedAudit, r.toString()));
        runner.callStart().runReplay();
        Files.writeString(out.resolve("replayed-audit.yaml"), replayedAudit);
        System.out.println("captured " + capturedAudit.length() + " chars, replayed " + replayedAudit.length() + " chars");
    }
}
