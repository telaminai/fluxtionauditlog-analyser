package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.report.FilterSnapshot;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;
import telamin.fluxtion.audit.analyser.analyser.report.ReportResolver;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;
import telamin.fluxtion.audit.analyser.analyser.report.ReportVerb;

import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #183: a report's {@code topology} section was SKIPPED unless it named a saved focus, and saving a focus needs a
 * non-trivial one first — so "put the topology in the report" silently produced a report without it. Now a topology
 * section with no focus is the whole authored graph: parsed and kept, resolved, and drawn. A named focus that is not
 * saved still resolves as unresolved, by name, and a section that cannot be drawn says why.
 */
class TopologySectionWithoutFocusTest {

    private static final Path GRAPH =
            Path.of("docs/handoff/evidence/unguided-session-2026-09-21/fixtures/MarketProcessor.src-round3.graphml");

    private static final LogFingerprint FP = new LogFingerprint("t.yaml", 10, 1L, 2L);

    private static ReportVerb.Parsed parse(Object sections) {
        return ReportVerb.parse(Map.of("name", "inv", "sections", sections), FP, FilterSnapshot.all());
    }

    @Test
    @DisplayName("★ #183: a topology section with no focus is KEPT (red before: skipped with a warning), resolves, and draws the whole authored graph")
    void noFocusIsTheWholeGraph() throws Exception {
        var p = parse(List.of(Map.of("kind", "topology")));
        assertEquals(1, p.spec().sections().size(), "★ the section is kept, not skipped: " + p.warnings());
        ReportSpec.SectionSpec s = p.spec().sections().get(0);
        assertEquals(ReportSpec.Kind.TOPOLOGY, s.kind());
        assertNull(s.ref(), "no focus named: the whole graph");
        assertTrue(p.warnings().isEmpty(), "and nothing is warned about: " + p.warnings());

        var resolution = ReportResolver.resolve(p.spec(), null, Map.of(), Set.of(), Set.of(), null);
        assertTrue(resolution.sections().get(0).resolved(), "★ resolved with NO saved focus: " + resolution.sections().get(0));

        var drawn = new AtomicReference<TopologyPanel.FocusPicture>();
        var authored = new AtomicReference<Set<String>>();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new TopologyPanel();
            panel.load(GRAPH);
            authored.set(panel.authoredNodeIds());
            drawn.set(panel.renderWholeGraphForReport(1200, 800));
        });
        assertNotNull(drawn.get(), "★ the whole graph is drawn");
        assertEquals(1200, drawn.get().image().getWidth());
        assertTrue(drawn.get().caption().startsWith("whole graph · " + authored.get().size() + " authored node"),
                "the caption says what was drawn: " + drawn.get().caption());
    }

    @Test
    @DisplayName("#183: with no graph loaded the whole-graph section is not drawn — the caller then says why")
    void noGraphIsNotDrawn() throws Exception {
        var drawn = new AtomicReference<TopologyPanel.FocusPicture>(new TopologyPanel.FocusPicture(null, "unset"));
        SwingUtilities.invokeAndWait(() -> drawn.set(new TopologyPanel().renderWholeGraphForReport(1200, 800)));
        assertNull(drawn.get());
    }

    @Test
    @DisplayName("#183: a NAMED focus that is not saved is still unresolved, by name — never quietly widened to the whole graph")
    void aNamedMissingFocusIsUnresolvedByName() {
        var p = parse(List.of(Map.of("kind", "topology", "focus", "hotPath")));
        assertEquals("hotPath", p.spec().sections().get(0).ref());
        var resolution = ReportResolver.resolve(p.spec(), null, Map.of(), Set.of(), Set.of(), null);
        var r = resolution.sections().get(0);
        assertFalse(r.resolved());
        assertTrue(r.reason().contains("'hotPath'"), r.reason());
    }
}
