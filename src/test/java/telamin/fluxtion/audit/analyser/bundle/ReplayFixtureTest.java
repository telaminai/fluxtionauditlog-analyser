package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The replay fixture (M70.R1): one recorded DEMO run's audit log, its replay records and its graph, written by
 * {@code examples/fixture-generator} {@code GenerateFixtures.writeReplay}, which refuses to write them unless the
 * replay reproduces the log. This holds what the fixture claims, so a regeneration that breaks it fails here:
 * <ul>
 *   <li>one replay record per INPUT, in order: the graph's own events are never recorded (spec §3.2, R-D10);</li>
 *   <li>each stamped with the instant its cycle ran at, the audit record's {@code eventTime} (§3.3);</li>
 *   <li>the three files from one build: every node the log names is in the graph, with the writer among them.</li>
 * </ul>
 */
class ReplayFixtureTest {

    private static final Path DIR = Path.of("src/test/resources/replay");
    private static final Path AUDIT = DIR.resolve("demo-quote-recorded-audit.yaml");
    private static final Path REPLAY = DIR.resolve("demo-quote-recorded.replay.yaml");
    private static final Path GRAPH = DIR.resolve("demo-quote-recorded-processor.graphml");
    /** The graph raises this one on itself; it is in the log, and must never be in the replay. */
    private static final String RAISED = "RiskBreachEvent";

    record AuditRecord(String event, long eventTime) { }

    record ReplayRecord(String event, long time) { }

    static List<AuditRecord> audit(String text) {
        List<AuditRecord> out = new ArrayList<>();
        for (String doc : text.split("(?m)^---$")) {
            Matcher e = Pattern.compile("(?m)^\\s*event: (\\S+)$").matcher(doc);
            Matcher t = Pattern.compile("(?m)^\\s*eventTime: (-?\\d+)$").matcher(doc);
            if (e.find() && t.find()) out.add(new AuditRecord(e.group(1), Long.parseLong(t.group(1))));
        }
        return out;
    }

    static List<ReplayRecord> replay(String text) {
        List<ReplayRecord> out = new ArrayList<>();
        for (String doc : text.split("(?m)^---$")) {
            Matcher e = Pattern.compile("(?m)^event: !!\\S+\\$(\\w+) \\{").matcher(doc);
            Matcher t = Pattern.compile("(?m)^wallClockTime: (-?\\d+)$").matcher(doc);
            if (e.find() && t.find()) out.add(new ReplayRecord(e.group(1), Long.parseLong(t.group(1))));
        }
        return out;
    }

    /** Every way the replay fails to be the log's inputs, stamped with their receipt instants; empty when it is. */
    static List<String> mismatches(String auditText, String replayText) {
        List<AuditRecord> inputs = audit(auditText).stream().filter(r -> !r.event().equals(RAISED)).toList();
        List<ReplayRecord> recorded = replay(replayText);
        List<String> out = new ArrayList<>();
        if (inputs.size() != recorded.size()) {
            out.add(inputs.size() + " inputs in the log, " + recorded.size() + " replay records");
        }
        for (int i = 0; i < Math.min(inputs.size(), recorded.size()); i++) {
            AuditRecord a = inputs.get(i);
            ReplayRecord r = recorded.get(i);
            if (!a.event().equals(r.event())) out.add("record " + i + ": log " + a.event() + ", replay " + r.event());
            else if (a.eventTime() != r.time()) {
                out.add("record " + i + ": replay stamped " + r.time() + ", the cycle ran at " + a.eventTime());
            }
        }
        recorded.stream().filter(r -> r.event().equals(RAISED))
                .forEach(r -> out.add("the replay records " + RAISED + ", which the graph raises itself"));
        return out;
    }

    @Test
    void theReplayIsTheLogsInputsStampedWithTheirReceiptInstants() throws IOException {
        String auditText = Files.readString(AUDIT);
        String replayText = Files.readString(REPLAY);
        List<String> found = mismatches(auditText, replayText);
        assertTrue(found.isEmpty(), String.join("\n", found));
        // the shape the fixture exists for: inputs only, and the graph raising its own event in the log
        assertEquals(7, replay(replayText).size());
        assertEquals(8, audit(auditText).size());
        assertEquals(1, audit(auditText).stream().filter(r -> r.event().equals(RAISED)).count());
    }

    @Test
    void theThreeFilesComeFromOneBuild() throws IOException {
        ProcessorTopology graph = GraphMlParser.parse(Files.readString(GRAPH));
        assertNotNull(graph.node("replayCapture"), "the writer is compiled into the recorded processor");
        Set<String> named = new LinkedHashSet<>();
        Matcher m = Pattern.compile("(?m)^\\s*- (\\w+):").matcher(Files.readString(AUDIT));
        while (m.find()) named.add(m.group(1));
        assertTrue(!named.isEmpty(), "the log names nodes");
        for (String id : named) assertNotNull(graph.node(id), id + " is in the log but not in the graph");
    }

    @Test
    void witnessTheCheckNamesABrokenReplay() throws IOException {
        String auditText = Files.readString(AUDIT);
        String replayText = Files.readString(REPLAY);
        // a recorder that read the clock again, instead of taking the receipt instant (UP-FLX-53)
        String restamped = replayText.replaceFirst("wallClockTime: (\\d+)", "wallClockTime: 1");
        assertTrue(mismatches(auditText, restamped).get(0).contains("replay stamped 1"), mismatches(auditText, restamped).toString());
        // a recorder that also recorded the graph's own event
        String withRaised = replayText + "---\n!!com.telamin.fluxtion.runtime.event.ReplayRecord\n"
                + "event: !!com.acme.demo.event.Events$RiskBreachEvent {orderId: \"ord-2\", liveOrders: 2}\n"
                + "wallClockTime: 1767258000180\n";
        assertTrue(String.join("\n", mismatches(auditText, withRaised)).contains("which the graph raises itself"));
        // a recorder that missed an input
        String dropped = replayText.substring(0, replayText.lastIndexOf("---"));
        assertTrue(mismatches(auditText, dropped).get(0).contains("7 inputs in the log, 6 replay records"));
    }

    @org.junit.jupiter.api.Test
    @org.junit.jupiter.api.DisplayName("PR #70 review 6 (RB-2): of two events of one type, the external one is recorded, the graph's is not")
    void onlyTheExternalObjectIsRecorded(@org.junit.jupiter.api.io.TempDir Path tmp) throws Exception {
        // the generated processor, driven live as a producer drives it: the recorded run's inputs, then an EXTERNAL
        // RiskBreachEvent, the type the graph also raises itself on the fifth input. Only identity tells them apart
        Path build = ReplayRunnerEndToEndTest.build(tmp, "same", null);
        String[] live = LiveRecording.run(tmp, build, Files.readString(REPLAY), true);
        List<ReplayRecord> recorded = replay(live[0]);
        List<AuditRecord> logged = audit(live[1]);
        assertEquals(2, logged.stream().filter(r -> r.event().equals(RAISED)).count(),
                "the log holds both breaches, the graph's and the external one: " + logged);
        assertEquals(8, recorded.size(), "the seven inputs and the external breach, never the graph's: " + live[0]);
        assertEquals(1, recorded.stream().filter(r -> r.event().equals(RAISED)).count(), live[0]);
        assertTrue(live[0].contains("RiskBreachEvent {orderId: \"DEMO-external\", liveOrders: 8}"),
                "the one recorded breach is the external object: " + live[0]);
        // and the first seven are the fixture's inputs, as it recorded them
        assertTrue(live[0].startsWith(Files.readString(REPLAY)), "the live run records the fixture's inputs exactly");
    }
}
