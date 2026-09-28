package telamin.fluxtion.audit.analyser.analyser.walk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M69 S2 (spec-spotlight-walks.md §3.5; P1's acceptance cases W-A5, W-A13–W-A16): each target's identity verdict
 * against what is loaded now. Pure — the facts are stubbed, so every case states exactly what differs.
 */
class WalkResolverTest {

    /** A world with records r0..r2, one chart "c", a graph with nodes a and b, all at known digests. */
    static final class World implements WalkResolver.Facts {
        String representation = "HeapLogStore";
        public String recordRepresentation() { return representation; }
        List<String> records = List.of("sha256:r0", "sha256:r1", "sha256:r2");
        List<String> run = List.of("sha256:run");
        Map<String, String> charts = Map.of("c", "sha256:def");
        Set<String> external = Set.of();
        WalkResolver.Drawn drawn = new WalkResolver.Drawn(true, "drawn");
        String graph = "sha256:graph";
        Set<String> nodes = Set.of("a", "b");

        public int recordCount() { return records.size(); }
        public String recordDigest(int i) { return records.get(i); }
        public List<String> runBasis() { return run; }
        public String chartDefinitionDigest(String c) { return charts.get(c); }
        public boolean chartHasExternalSeries(String c) { return external.contains(c); }
        public WalkResolver.Drawn chartDrawn(String c) { return charts.containsKey(c) ? drawn : null; }
        public String graphDigest() { return graph; }
        public boolean nodeExists(String id) { return nodes.contains(id); }
    }

    private static WalkSpec.Target t(String target, String kind, String digest) {
        return new WalkSpec.Target(target, "cap", new WalkSpec.Basis(kind, digest, "HeapLogStore"));
    }

    private static WalkResolver.Verdict v(WalkSpec.Target t, World w) {
        return WalkResolver.verdict(t, WalkSpec.View.NONE, List.of("sha256:run"), w);
    }

    @Test
    @DisplayName("a record whose digest matches is current and lit")
    void aMatchingRecordIsCurrent() {
        var r = v(t("records:row:1", "record", "sha256:r1"), new World());
        assertEquals(WalkIdentity.State.CURRENT, r.state());
        assertTrue(r.available());
    }

    @Test
    @DisplayName("M69 review R4: equal text from a different or unknown representation is not current")
    void recordRepresentationMustBeKnownAndMatch() {
        World now = new World();
        for (String saved : List.of("DEMO-other-representation", "")) {
            var target = new WalkSpec.Target("records:row:1", "claim",
                    new WalkSpec.Basis("record", "sha256:r1", saved));
            var verdict = v(target, now);
            assertEquals(WalkIdentity.State.UNRESOLVED, verdict.state(),
                    "equal text must not certify a different or unknown record representation: " + saved);
            assertFalse(verdict.available(), "an unbound representation must not light the record");
            assertTrue(verdict.reason().contains("representation"), "the refusal must name its actual basis");
        }
        now.representation = null;
        var unknown = v(t("records:row:1", "record", "sha256:r1"), now);
        assertEquals(WalkIdentity.State.UNRESOLVED, unknown.state(), "unknown current representation is not equal");
        now.representation = "HeapLogStore";
        var matching = v(t("records:row:1", "record", "sha256:r1"), now);
        assertEquals(WalkIdentity.State.CURRENT, matching.state(), "a known matching representation still works");
        assertTrue(matching.available(), "the positive control remains available");
    }

    @Test
    @DisplayName("W-A5: a record whose digest differs is historical and NOT lit — never re-pointed")
    void aChangedRecordIsNotLit() {
        var r = v(t("records:row:1", "record", "sha256:OTHER"), new World());
        assertEquals(WalkIdentity.State.HISTORICAL, r.state());
        assertFalse(r.available(), "pointing at record 1 now would put the author's caption on other text");
        assertTrue(r.reason().contains("differs"));
    }

    @Test
    @DisplayName("a record out of range, or with no saved digest, is unresolved and not lit")
    void aMissingRecordIsUnresolved() {
        var out = v(t("records:row:9", "record", "sha256:r9"), new World());
        assertEquals(WalkIdentity.State.UNRESOLVED, out.state());
        assertFalse(out.available());
        assertTrue(out.reason().contains("not in this log"));
        var unknown = v(t("records:row:1", "record", ""), new World());
        assertEquals(WalkIdentity.State.UNRESOLVED, unknown.state());
        assertFalse(unknown.available());
    }

    @Test
    @DisplayName("a detail target is checked against the step's selected record")
    void aDetailTargetUsesTheSelectedRecord() {
        var view = new WalkSpec.View(null, null, 2, null, null);
        var r = WalkResolver.verdict(t("detail:node:a", "record", "sha256:r2"), view, List.of("sha256:run"), new World());
        assertEquals(WalkIdentity.State.CURRENT, r.state());
    }

    @Test
    @DisplayName("W-A13: same graph, new run — structural current, the chart historical")
    void sameGraphNewRun() {
        World w = new World();
        w.run = List.of("sha256:another-run");
        assertEquals(WalkIdentity.State.CURRENT, v(t("topology:node:a", "graph", "sha256:graph"), w).state());
        var chart = v(t("graph:c:note:1", "chart", "sha256:def"), w);
        assertEquals(WalkIdentity.State.HISTORICAL, chart.state());
        assertTrue(chart.available(), "a drawn chart is lit, and says it is historical");
    }

    @Test
    @DisplayName("W-A14: a changed graph with the same node names is historical; an unknown graph digest unresolved")
    void aChangedGraphIsHistorical() {
        World w = new World();
        w.graph = "sha256:changed";
        assertEquals(WalkIdentity.State.HISTORICAL, v(t("topology:node:a", "graph", "sha256:graph"), w).state(),
                "every name resolves, and it is still not the graph the walk was written against");
        w.graph = null;                                   // a source-supplied graph (S0): no content identity
        assertEquals(WalkIdentity.State.UNRESOLVED, v(t("topology:node:a", "graph", "sha256:graph"), w).state());
    }

    @Test
    @DisplayName("W-A15: a renamed or auto-renamed node is unresolved and not lit")
    void aRenamedNodeIsUnresolved() {
        var r = v(t("topology:node:varCalculator_14", "graph", "sha256:graph"), new World());
        assertEquals(WalkIdentity.State.UNRESOLVED, r.state());
        assertFalse(r.available());
        assertTrue(r.reason().contains("not in this graph"));
    }

    @Test
    @DisplayName("W-A16: a changed middle record — the run basis catches it for a chart-only step")
    void theRunBasisCatchesAChartOnlyStep() {
        World w = new World();
        w.run = List.of("sha256:same-count-same-times-different-middle");
        assertEquals(WalkIdentity.State.HISTORICAL, v(t("graph:c", "chart", "sha256:def"), w).state());
    }

    @Test
    @DisplayName("§3.6: a chart that did not draw is not lit, with the paint's own reason")
    void anUndrawnChartIsNotLit() {
        World w = new World();
        w.drawn = new WalkResolver.Drawn(false, "no room at 360×180 px — widen the window");
        var r = v(t("graph:c:note:1", "chart", "sha256:def"), w);
        assertFalse(r.available());
        assertTrue(r.reason().contains("no room"), r.reason());
    }

    @Test
    @DisplayName("a chart with external series is unresolved: its population is not the log's alone")
    void externalSeriesAreUnresolved() {
        World w = new World();
        w.external = Set.of("c");
        assertEquals(WalkIdentity.State.UNRESOLVED, v(t("graph:c", "chart", "sha256:def"), w).state());
    }

    @Test
    @DisplayName("a missing chart, or a changed definition, says so")
    void missingOrChangedChart() {
        assertFalse(v(t("graph:gone", "chart", "sha256:def"), new World()).available());
        assertEquals(WalkIdentity.State.HISTORICAL, v(t("graph:c", "chart", "sha256:older"), new World()).state());
    }

    @Test
    @DisplayName("captions are marked when not current, within the spotlight's length")
    void captionsAreMarked() {
        assertEquals("x", WalkResolver.caption("x", WalkIdentity.State.CURRENT));
        assertEquals("x (historical)", WalkResolver.caption("x", WalkIdentity.State.HISTORICAL));
        String longOne = "y".repeat(500);
        String marked = WalkResolver.caption(longOne, WalkIdentity.State.UNRESOLVED);
        assertTrue(marked.endsWith(" (unresolved)"));
        assertTrue(marked.length() <= telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget.MAX_CAPTION);
    }
}
