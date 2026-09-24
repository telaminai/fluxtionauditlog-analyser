package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.1 re-review R2 and O2: discovery states the same scope and facts as the frame and the session; and a
 * whole-log membership comparison qualifies a sampled pairing and says that it did (acceptance 3).
 */
class PairingScopeSurfacesTest {

    @Test
    @DisplayName("discovery's candidates carry scope and the pairing facts beside appliesToOpenLog")
    void discoveryIsScopedAndCarriesItsFacts(@TempDir Path dir) throws Exception {
        Files.copy(EvidenceIntegrityCoverageTest.PACKET_GRAPH, dir.resolve("MyProcessor.graphml"));
        var c = GraphmlDiscovery.scan(List.of(dir.toString()), Set.of("checked", "child", "rootNode"), 500, 600)
                .candidates().get(0);
        assertTrue(c.pairing().sampled(), c.pairing().toString());
        assertEquals("first 500 of 600 records", c.pairing().scope());
        Map<String, Object> map = c.toMap();
        assertEquals(Boolean.TRUE, map.get("appliesToOpenLog"));
        assertEquals("first 500 of 600 records", map.get("pairingScope"), map.toString());
        assertEquals(Boolean.TRUE, map.get("pairingSampled"));
        assertEquals(Boolean.TRUE, map.get("membershipEstablished"));
        assertNotNull(map.get("appliesMeans"), "applies is labelled a retention policy on this surface too");

        var unscoped = GraphmlDiscovery.scan(List.of(dir.toString()), Set.of("child")).candidates().get(0);
        assertEquals("scope not recorded", unscoped.pairing().scope(), "a caller that says nothing gets no scope");
    }

    private static Map<String, Object> wholeLogEcho(int records, int loggedIds, int declared, List<String> foreign) {
        return Map.of("scope", "whole log", "recordsScanned", records, "logRecords", records,
                "loggedButNotInTopology", foreign,
                "membership", Map.of("scope", "whole log", "established", true, "loggedIds", loggedIds,
                        "declaredOfLogged", declared));
    }

    private static Map<String, Object> filteredEcho(int records, int logRecords, int loggedIds, int declared) {
        return Map.of("scope", "current filter", "recordsScanned", records, "logRecords", logRecords,
                "membership", Map.of("scope", "current filter", "established", true, "loggedIds", loggedIds,
                        "declaredOfLogged", declared));
    }

    @Test
    @DisplayName("round 3 N1: a grown log makes the whole-log verdict stale, and it stops claiming the whole log")
    void aGrownLogMakesTheWholeLogVerdictStale() {
        GraphPairing sampled = GraphPairing.of(Set.of("a", "b", "c"), Set.of("a", "b", "c")).withScope(500, 600);
        PairingQualification fresh = PairingQualification.fromCoverage(sampled, wholeLogEcho(600, 3, 3, List.of()));
        assertFalse(fresh.stale());
        assertTrue(fresh.note().contains("confirms the sampled pairing for the whole log"), fresh.note());

        PairingQualification grown = fresh.atLogSize(601);
        assertTrue(grown.stale());
        assertFalse(grown.note().contains("confirms the sampled pairing for the whole log"), grown.note());
        assertTrue(grown.note().contains("the log has grown since"), grown.note());
        assertTrue(grown.headline().startsWith("first 600 of 601 records: all 3 logged id(s) declared"), grown.headline());
        assertEquals(Boolean.TRUE, grown.toMap().get("stale"));
        assertEquals(600, grown.toMap().get("logRecordsAtComparison"));
        assertEquals(601, grown.toMap().get("logRecordsNow"));
        String panel = PairingQualification.panelNote(sampled.rescoped(601), grown, null);
        assertTrue(panel.startsWith("first 600 of 601 records: all 3 logged id(s) declared \u2014 the log has grown"),
                panel);
        assertFalse(panel.contains("confirms"), "a stale confirmation confirms nothing: " + panel);
        assertEquals(fresh, fresh.atLogSize(600), "an unchanged size is not stale");
    }

    @Test
    @DisplayName("round 3 N1, one level down: the published pairing's scope counts the appended records")
    void aRescopedPairingCountsTheAppendedRecords() {
        GraphPairing sampled = GraphPairing.of(Set.of("a", "b", "c"), Set.of("a", "b", "c")).withScope(500, 600);
        GraphPairing grown = sampled.rescoped(601);
        assertEquals("first 500 of 601 records", grown.scope());
        assertTrue(grown.reason().endsWith("(judged on the first 500 of 601 records)"), grown.reason());
        assertEquals(1, grown.reason().split("judged on the first", -1).length - 1, "one scope clause: " + grown.reason());
        assertEquals(sampled.matched(), grown.matched(), "the compared sample is unchanged by an append");
        GraphPairing small = GraphPairing.of(Set.of("a"), Set.of("a")).withScope(3, 3).rescoped(4);
        assertTrue(small.sampled(), "a log that outgrew its fully compared size becomes a sample: " + small.scope());
        assertEquals("first 3 of 4 records", small.scope());
    }

    @Test
    @DisplayName("round 3 N2: a narrower comparison never replaces a wider one; a wider one drops a narrower one")
    void aNarrowerComparisonNeverReplacesAWiderOne() {
        GraphPairing sampled = GraphPairing.of(Set.of("a", "b", "c"), Set.of("a", "b", "c")).withScope(500, 600);
        PairingQualifications held = new PairingQualifications();
        held.record(PairingQualification.fromCoverage(sampled, wholeLogEcho(600, 4, 3, List.of("foreignAfter500"))));
        String reply = held.record(PairingQualification.fromCoverage(sampled, filteredEcho(2, 600, 2, 2)));

        Map<String, Object> ctx = held.toMap(600);
        assertEquals("whole log", ctx.get("scope"), "the wider comparison still leads: " + ctx);
        assertEquals(List.of("foreignAfter500"), ctx.get("notDeclared"));
        @SuppressWarnings("unchecked")
        Map<String, Object> narrower = (Map<String, Object>) ctx.get("narrower");
        assertEquals("current filter", narrower.get("scope"), "the narrower one rides beside it");
        String panel = held.panelNote(sampled, 600);
        assertTrue(panel.startsWith("whole log: 1 of 4 logged id(s) not declared (foreignAfter500)"), panel);
        assertTrue(panel.endsWith("current filter: all 2 logged id(s) declared"), panel);
        assertTrue(reply.contains("still in force from the whole log: whole log: 1 of 4"), reply);

        PairingQualifications reverse = new PairingQualifications();
        reverse.record(PairingQualification.fromCoverage(sampled, filteredEcho(2, 600, 2, 2)));
        reverse.record(PairingQualification.fromCoverage(sampled, wholeLogEcho(600, 4, 3, List.of("foreignAfter500"))));
        Map<String, Object> rev = reverse.toMap(600);
        assertEquals("whole log", rev.get("scope"));
        assertNull(rev.get("narrower"), "an earlier filtered comparison is dominated by a later whole-log one");
    }

    @Test
    @DisplayName("set 3: a sampled note leads with its scope, so a clipped line still says it was a sample")
    void theNoteLeadsWithItsScope() {
        GraphPairing sampled = GraphPairing.of(Set.of("a", "b", "c"), Set.of("a", "b", "c")).withScope(500, 600);
        assertTrue(sampled.note().startsWith("first 500 of 600 records: every node id checked is declared (3/3)"),
                sampled.note());
        GraphPairing whole = GraphPairing.of(Set.of("a", "b"), Set.of("a", "b")).withScope(21, 21);
        assertTrue(whole.note().startsWith("every node id checked is declared (2/2)"),
                "an unsampled note has nothing to qualify, so it is unchanged: " + whole.note());
    }

    @Test
    @DisplayName("set 3: once a whole-log comparison exists, the panel note leads with what is currently true")
    void thePanelNoteLeadsWithWhatQualifiesIt() {
        GraphPairing sampled = GraphPairing.of(Set.of("a", "b", "c"), Set.of("a", "b", "c")).withScope(500, 600);
        PairingQualification superseding = PairingQualification.fromCoverage(sampled, Map.of(
                "scope", "whole log", "recordsScanned", 600, "loggedButNotInTopology", List.of("foreignAfter500"),
                "membership", Map.of("scope", "whole log", "established", true, "loggedIds", 4, "declaredOfLogged", 3)));
        String note = PairingQualification.panelNote(sampled, superseding);
        assertTrue(note.startsWith("whole log: 1 of 4 logged id(s) not declared (foreignAfter500) \u2014 supersedes"),
                note);
        assertTrue(note.contains("on open: first 500 of 600 records"), "the sampled verdict follows: " + note);

        PairingQualification confirming = PairingQualification.fromCoverage(sampled, Map.of(
                "scope", "whole log", "recordsScanned", 600,
                "membership", Map.of("scope", "whole log", "established", true, "loggedIds", 3, "declaredOfLogged", 3)));
        assertTrue(PairingQualification.panelNote(sampled, confirming)
                .startsWith("whole log: all 3 logged id(s) declared \u2014 confirms"));

        PairingQualification filtered = PairingQualification.fromCoverage(sampled, Map.of(
                "scope", "current filter", "recordsScanned", 40,
                "membership", Map.of("scope", "current filter", "established", true, "loggedIds", 2, "declaredOfLogged", 2)));
        String narrower = PairingQualification.panelNote(sampled, filtered);
        assertTrue(narrower.startsWith("first 500 of 600 records:"), "a narrower comparison does not lead: " + narrower);
        assertTrue(narrower.endsWith("current filter: all 2 logged id(s) declared"), narrower);

        assertEquals(sampled.note(), PairingQualification.panelNote(sampled, null));
    }

    @Test
    @DisplayName("a whole-log comparison that finds a foreign id supersedes the sampled pairing, and says so")
    void aWholeLogComparisonSupersedesTheSample() {
        GraphPairing sampled = GraphPairing.of(Set.of("a", "b", "c"), Set.of("a", "b", "c")).withScope(500, 600);
        Map<String, Object> echo = Map.of(
                "scope", "whole log", "recordsScanned", 600,
                "membership", Map.of("scope", "whole log", "established", true, "loggedIds", 4, "declaredOfLogged", 3),
                "loggedButNotInTopology", List.of("foreignAfter500"));
        PairingQualification q = PairingQualification.fromCoverage(sampled, echo);
        assertTrue(q.supersedesSample());
        assertFalse(q.everyObservedIdDeclared());
        assertEquals(List.of("foreignAfter500"), q.notDeclared());
        assertTrue(q.note().contains("1 of 4 logged id(s) are not declared (foreignAfter500)"), q.note());
        assertTrue(q.note().contains("supersedes the sampled pairing"), q.note());
        assertFalse(q.note().toLowerCase().contains("build"), q.note());
        assertEquals(Boolean.TRUE, q.toMap().get("supersedesSample"));
    }

    @Test
    @DisplayName("a whole-log comparison that finds nothing new confirms the sample; a filtered one supersedes nothing")
    void confirmationAndScopeLimits() {
        GraphPairing sampled = GraphPairing.of(Set.of("a", "b"), Set.of("a", "b")).withScope(500, 600);
        Map<String, Object> clean = Map.of("scope", "whole log", "recordsScanned", 600,
                "membership", Map.of("scope", "whole log", "established", true, "loggedIds", 2, "declaredOfLogged", 2));
        PairingQualification confirmed = PairingQualification.fromCoverage(sampled, clean);
        assertTrue(confirmed.everyObservedIdDeclared());
        assertTrue(confirmed.note().contains("confirms the sampled pairing for the whole log"), confirmed.note());

        Map<String, Object> filtered = Map.of("scope", "current filter", "recordsScanned", 40,
                "membership", Map.of("scope", "current filter", "established", true, "loggedIds", 2, "declaredOfLogged", 2));
        assertFalse(PairingQualification.fromCoverage(sampled, filtered).supersedesSample(),
                "a filtered comparison is a different scope, not a broader one");

        GraphPairing whole = GraphPairing.of(Set.of("a", "b"), Set.of("a", "b")).withScope(21, 21);
        assertFalse(PairingQualification.fromCoverage(whole, clean).supersedesSample(),
                "a pairing that already covered the whole log has no sample to supersede");
    }
}
