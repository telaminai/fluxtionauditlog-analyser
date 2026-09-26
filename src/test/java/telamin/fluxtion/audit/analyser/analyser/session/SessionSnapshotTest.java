package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M44.4b (spec §13, D-S13.1/.3/.5). Events in, snapshot out: no Swing, no timing, no frame fields. Each test names the
 * mutation that must turn it red.
 */
class SessionSnapshotTest {

    private static final Set<String> DECLARED = Set.of("checked", "child", "rootNode");

    private static SessionDriver pairOpen(FakeSessionAdapter adapter, int sampled, int total) {
        SessionDriver d = new SessionDriver(adapter);
        SessionFixtures.openLog(d, adapter, "/l.yaml", "DECLARED", DECLARED, sampled, total, "TRACE");
        d.submit(SessionFixtures.graph("/g.graphml", "OPENED", DECLARED, List.of("EventLogManager")));
        return d;
    }

    @Test
    @DisplayName("the snapshot is the decided state after each operation, and empty before the first")
    void theSnapshotFollowsEachOperation() {
        // witness: publishSnapshot() not called at the end of submit
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(adapter);
        assertSame(SessionSnapshot.EMPTY, d.snapshot());
        SessionFixtures.openLog(d, adapter, "/l.yaml", "DECLARED", DECLARED, 500, 600, "TRACE");
        d.submit(SessionFixtures.graph("/g.graphml", "OPENED", DECLARED, List.of("EventLogManager")));

        SessionSnapshot s = d.snapshot();
        assertTrue(s.logOpen() && s.graphOpen());
        assertEquals("first 500 of 600 records", s.pairing().scope());
        assertEquals(d.processor().coverageClaim.assessment(), s.claim(), "one verdict, read two ways, identical");
    }

    @Test
    @DisplayName("an append re-scopes the snapshot's verdict, and it is still the same pair")
    void anAppendIsTheSamePairReScoped() {
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = pairOpen(adapter, 500, 600);
        SessionSnapshot before = d.snapshot();

        d.post(new SessionEvents.LogAppended(before.logGeneration(), DECLARED, 500, 601, "TRACE"));

        SessionSnapshot after = d.snapshot();
        assertEquals("first 500 of 601 records", after.pairing().scope());
        assertTrue(after.samePairAs(before), "a re-scope keeps whatever qualified the verdict");
        assertTrue(after.claim().reason().contains("of 601"), "the claim moved with it: " + after.claim().reason());
    }

    @Test
    @DisplayName("a different graph, or a reopened log, is a different pair")
    void aDifferentArtefactIsADifferentPair() {
        // witness: OpenGraph.revision not incremented on a moved open
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = pairOpen(adapter, 10, 10);
        SessionSnapshot first = d.snapshot();

        d.submit(SessionFixtures.graph("/other.graphml", "OPENED", DECLARED, List.of("EventLogManager")));
        assertFalse(d.snapshot().samePairAs(first), "another graph: nothing earlier qualifies it");

        SessionSnapshot second = d.snapshot();
        SessionFixtures.openLog(d, adapter, "/l.yaml", "DECLARED", DECLARED, 10, 10, "TRACE");
        assertFalse(d.snapshot().samePairAs(second), "the same file reopened is a new log generation");
    }

    @Test
    @DisplayName("listeners hear each change once, and nothing when nothing changed")
    void listenersHearChangesOnly() {
        // witness: publishSnapshot() without the equals() short-circuit
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = pairOpen(adapter, 500, 600);
        List<SessionSnapshot> heard = new ArrayList<>();
        d.onSnapshot(heard::add);
        long g = d.snapshot().logGeneration();

        d.post(new SessionEvents.LogAppended(g, DECLARED, 500, 601, "TRACE"));
        d.post(new SessionEvents.LogAppended(g, DECLARED, 500, 601, "TRACE"));   // a poll with nothing new

        assertEquals(1, heard.size(), "a repaint per real change, not per fact");
    }

    @Test
    @DisplayName("D-S13.5: a thousand re-scopes evict no transition, and the record says what it dropped")
    void reScopesNeverEvictTransitions() {
        // witness: SessionAuditSink routing LogAppended into the transition ring
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionAuditSink sink = new SessionAuditSink(50);
        SessionDriver d = new SessionDriver(adapter, sink);
        SessionFixtures.openLog(d, adapter, "/l.yaml", "DECLARED", DECLARED, 10, 10, "TRACE");
        d.submit(SessionFixtures.graph("/g.graphml", "OPENED", DECLARED, List.of("EventLogManager")));
        List<String> transitions = sink.transitions();
        long g = d.snapshot().logGeneration();

        for (int n = 1; n <= 1_000; n++) {
            d.post(new SessionEvents.LogAppended(g, DECLARED, 10, 10 + n, "TRACE"));
        }

        assertEquals(transitions, sink.transitions(), "every transition is still held");
        assertEquals(0, sink.droppedTransitions());
        assertEquals(1_000 - SessionAuditSink.RESCOPE_CAPACITY, sink.droppedRescopes());
        assertFalse(sink.isComplete(), "and the record does not claim to be whole");
    }

    @Test
    @DisplayName("no UI class computes a pairing, and the coverage claim is read from the snapshot")
    void theFrameRendersAndDoesNotCompute() throws Exception {
        // witness: restore MainFrame.pairingAgainst's GraphPairing.of, or read processor() in bindSessionSnapshot
        Path ui = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui");
        List<String> offenders = new ArrayList<>();
        try (var files = Files.list(ui)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String src = Files.readString(f);
                for (String call : List.of("GraphPairing.of(", ".withScope(", ".rescoped(")) {
                    if (src.contains(call)) offenders.add(f.getFileName() + " calls " + call);
                }
            }
        }
        assertEquals(List.of(), offenders, "the session owns the pairing (spec §13 D-S13.1); a surface renders it");

        String frame = Files.readString(ui.resolve("MainFrame.java"));
        int at = frame.indexOf("actionExecutor.bindSessionSnapshot(");
        String binding = frame.substring(at, frame.indexOf("});", at));
        assertFalse(binding.contains("processor()"), "the socket thread must read the snapshot, not live processor fields");
        assertFalse(binding.contains("invokeAndWait"), "and must not block on the EDT to do it");

        // M44.4c: the frame holds no verdict STATE either — a field of these types is a second copy waiting to drift
        List<String> held = new ArrayList<>();
        for (var field : telamin.fluxtion.audit.analyser.analyser.ui.MainFrame.class.getDeclaredFields()) {
            var type = field.getType();
            if (type == telamin.fluxtion.audit.analyser.analyser.topology.GraphPairing.class
                    || type == telamin.fluxtion.audit.analyser.analyser.topology.PairingQualifications.class
                    || type == telamin.fluxtion.audit.analyser.analyser.topology.PairingQualification.class) {
                held.add(field.getName() + ": " + type.getSimpleName());
            }
        }
        assertEquals(List.of(), held, "MainFrame renders the session snapshot; it keeps no pairing or qualification");
    }

    /** A pair with one whole-log comparison held against it — something a consumer could try to erase. */
    private static SessionDriver qualified(FakeSessionAdapter adapter) {
        SessionDriver d = pairOpen(adapter, 1, 1);
        d.post(new SessionEvents.MembershipCompared(d.snapshot().logGeneration(), d.snapshot().graphRevision(),
                java.util.Map.of("scope", "whole log", "recordsScanned", 1, "logRecords", 1,
                        "loggedButNotInTopology", List.of("foreign"),
                        "membership", java.util.Map.of("scope", "whole log", "established", true, "loggedIds", 2,
                                "declaredOfLogged", 1))));
        return d;
    }

    @Test
    @DisplayName("R4: a consumer cannot change the published qualifications — not by clear(), not by record()")
    void aConsumerCannotChangeThePublishedQualifications() {
        // witness: SessionSnapshot's compact constructor without the freeze
        FakeSessionAdapter adapter = new FakeSessionAdapter();
        SessionDriver d = qualified(adapter);
        SessionSnapshot published = d.snapshot();
        assertNotNull(published.qualifications(), "precondition: a qualification is published");
        List<SessionSnapshot> heard = new ArrayList<>();
        d.onSnapshot(heard::add);

        assertThrows(UnsupportedOperationException.class, () -> published.qualifications().clear(),
                "R4: the published truth is not the consumer's to erase");
        assertThrows(UnsupportedOperationException.class, () -> published.qualifications().record(
                telamin.fluxtion.audit.analyser.analyser.topology.PairingQualification.fromCoverage(published.pairing(),
                        java.util.Map.of("scope", "whole log", "recordsScanned", 1, "logRecords", 1))),
                "R4: nor to add to");
        assertFalse(d.snapshot().qualifications().isEmpty(), "R4: later reads are unchanged");
        assertEquals(published, d.snapshot(), "R4: equality is unchanged");
        assertEquals(List.of(), heard, "R4: and no listener heard of a change that no fact made");
    }

}
