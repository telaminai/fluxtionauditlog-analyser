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
