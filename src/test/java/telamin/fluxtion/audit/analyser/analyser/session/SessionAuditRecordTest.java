package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M44 §8 — the processor's own audit record: does it exist, does it go where we said, and does it
 * separate what was decided from what happened?
 */
class SessionAuditRecordTest {

    private static final String A = "/projects/alpha.properties";
    private static final String B = "/projects/beta.properties";

    @Test
    @DisplayName("nothing reaches stdout — the default sink is System.out and it must never win")
    void theDefaultStdoutSinkNeverGetsARecord() {
        PrintStream realOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        String printed;
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            // Construction included: init() audits a lifecycle event, so a sink attached one line too
            // late already loses. This test failed the first time it was run, which is why it exists in
            // this shape rather than only covering the request.
            FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A).withProfile(B);
            SessionDriver driver = new SessionDriver(adapter);
            driver.submit(new SessionEvents.LogObserved(true, "/logs/run.yaml", "DECLARED"));
            driver.submit(new SessionEvents.OpenProjectRequested(
                    driver.nextOpId(), A, TransitionKind.EXPLICIT_SWITCH, "test"));
            driver.submit(new SessionEvents.OpenProjectRequested(
                    driver.nextOpId(), B, TransitionKind.EXPLICIT_SWITCH, "test"));
        } finally {
            System.setOut(realOut);
            printed = captured.toString(StandardCharsets.UTF_8);
        }
        assertEquals("", printed,
                "Fluxtion's no-arg EventLogManager defaults its sink to System.out::println. "
                        + "Anything here means the analyser is printing its audit log to the console:\n"
                        + printed);
    }

    @Test
    @DisplayName("the record captures the session, and says so honestly when it is incomplete")
    void recordsAreCapturedAndBounded() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A);
        SessionAuditSink sink = new SessionAuditSink(4);
        SessionDriver driver = new SessionDriver(adapter, sink);

        driver.submit(new SessionEvents.OpenProjectRequested(
                driver.nextOpId(), A, TransitionKind.EXPLICIT_SWITCH, "test"));

        assertTrue(sink.total() > 4, "one transition produces more than four records");
        assertEquals(4, sink.records().size(), "and the ring is bounded");
        assertTrue(sink.dropped() > 0);
        assertFalse(sink.isComplete(), "a truncated record must not claim to be the whole session");
    }

    @Test
    @DisplayName("decision and outcome are separate rows — 'asked to close' is not 'closed'")
    void theRecordDistinguishesDecisionFromOutcome() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A).withProfile(B);
        SessionDriver driver = new SessionDriver(adapter);

        driver.submit(new SessionEvents.OpenProjectRequested(
                driver.nextOpId(), A, TransitionKind.STARTUP_ACTIVATION, "test"));
        driver.submit(new SessionEvents.LogObserved(true, "/logs/run.yaml", "DECLARED"));
        driver.submit(new SessionEvents.OpenProjectRequested(
                driver.nextOpId(), B, TransitionKind.EXPLICIT_SWITCH, "test"));

        // What sessionBoundary decided.
        assertFalse(driver.auditSink().matching("closingLog").isEmpty(), "the decision is recorded");
        // What actually happened. Without this row, closingLog=true is an intention being read as
        // evidence — the exact defect the M44 review found in the first draft of the spec.
        List<String> outcomes = driver.auditSink().matching("EffectOutcome");
        assertFalse(outcomes.isEmpty(), "the outcome is recorded");
        assertTrue(outcomes.stream().anyMatch(r -> r.contains("closeLog")),
                "and it names the effect that completed");
    }

    @Test
    @DisplayName("export writes a snapshot the analyser can open")
    void exportWritesASnapshot(@TempDir Path dir) throws Exception {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A);
        SessionDriver driver = new SessionDriver(adapter);
        driver.submit(new SessionEvents.OpenProjectRequested(
                driver.nextOpId(), A, TransitionKind.EXPLICIT_SWITCH, "test"));

        Path out = driver.auditSink().export(dir.resolve("session-audit.yaml"));

        String text = Files.readString(out);
        assertTrue(text.contains("eventLogRecord"), "it is an audit log, in the record format");
        assertTrue(text.contains("sessionBoundary"), "and our decision node is in it");

        // The snapshot is fixed: continuing the session does not rewrite what was exported. Inspecting
        // the evidence must not change the evidence.
        long sizeBefore = Files.size(out);
        driver.submit(new SessionEvents.LogObserved(true, "/logs/later.yaml", "DECLARED"));
        assertEquals(sizeBefore, Files.size(out));
    }

    @Test
    @DisplayName("a record that cannot be written is counted, not thrown — and the sink admits it")
    void aFailingWriteIsCountedNotFatal() {
        SessionAuditSink sink = new SessionAuditSink();
        com.telamin.fluxtion.runtime.audit.LogRecord broken =
                new com.telamin.fluxtion.runtime.audit.LogRecord(new com.telamin.fluxtion.runtime.time.Clock()) {
                    @Override
                    public CharSequence asCharSequence() {
                        throw new RuntimeException("record is broken");
                    }
                };

        // A user asked to open a project, not to write a diagnostic — so this must not propagate...
        sink.processLogRecord(broken);

        assertEquals(1, sink.sinkFailures());
        assertTrue(sink.firstSinkFailure().contains("record is broken"));
        // ...and must not leave the record claiming to be complete when it is not.
        assertFalse(sink.isComplete());
    }
}
