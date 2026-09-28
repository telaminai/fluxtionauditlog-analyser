package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.config.FocusSpec;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M69 S2 (spec-spotlight-walks.md §3.3, §3.4; review R2): the presenter applies a step's view through TRANSIENT
 * primitives, in the documented order, and writes nothing — no chart opened, no definition changed, no profile save
 * requested. Headless: a real GraphTabs, FilterState and TopologyPanel behind a recording frame.
 */
class WalkPresenterTest {

    private static final Path GRAPH = Path.of("src/test/resources/topology/sample-processor.graphml");

    /** The recording frame: counts every primitive the presenter calls. */
    static final class Rig implements WalkPresenter.Frame {
        final AppConfig config = new AppConfig();
        final LogStore store = new HeapLogStore(Samples.sample());
        final FilterState filter = new FilterState();
        final GraphTabs graphs = new GraphTabs();
        final TopologyPanel topology = new TopologyPanel();
        final List<String> tabs = new ArrayList<>();
        final List<Object> posted = new ArrayList<>();
        int chartChanges, filterChanges;
        boolean recordVisible = true;

        Rig() {
            graphs.bind(store, filter);
            graphs.setChangeListener(() -> chartChanges++);                // the persisting path's funnel
            graphs.setKnownNames(() -> Set.of("closedOne"));
            config.savedGraphs.add(spec("closedOne", false));
            graphs.setSavedDefinitions(() -> config.savedGraphs);
            graphs.addGraph("openOne");
            chartChanges = 0;                                               // count only what the walk does
            filter.addListener(() -> filterChanges++);
            topology.load(GRAPH);
            topology.bindNamedFocuses(() -> config.namedFocuses, null);
        }

        public AppConfig config() { return config; }
        public LogStore store() { return store; }
        public FilterState filter() { return filter; }
        public GraphTabs graphs() { return graphs; }
        public TopologyPanel topology() { return topology; }
        public void selectTab(String tab) { tabs.add(tab); }
        public boolean selectRecord(int row) { return recordVisible; }
        public boolean recordSelected(int row) { return recordVisible; }
        public List<String> runBasisNow() { return List.of("sha256:run"); }
        public SpotlightTarget.Resolution resolve(String target) {
            return new SpotlightTarget.Resolution(SpotlightTarget.Outcome.LIT, SpotlightTarget.parse(target).target(),
                    new java.awt.Rectangle(1, 1, 5, 5), null);
        }
        public WalkPresenter.LitResult light(List<WalkPresenter.Numbered> requests) {
            return new WalkPresenter.LitResult(requests.size(), "");
        }
        public void clearWalkSpotlight() { }
        public void post(Object fact) { posted.add(fact); }
    }

    static GraphSpec spec(String name, boolean open) {
        return new GraphSpec(name, List.of("a\u0001x"), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "step", open);
    }

    private static WalkSpec.View view(WalkSpec.Filter filter, Integer record, String graph, WalkSpec.FocusRef focus) {
        return new WalkSpec.View("graph", filter, record, graph, focus);
    }

    @Test
    @DisplayName("P-4: a step's complete filter is ONE change, grouping kept with its dimensions")
    void aStepFilterIsOneChange() {
        Rig rig = new Rig();
        new WalkPresenter(rig).apply(view(new WalkSpec.Filter(10L, 90L, "RAW_EVENT", List.of("Tick"), "x"), null, null, null));
        assertEquals(1, rig.filterChanges, "four setters would have fired four changes");
        assertEquals(FilterState.GroupMode.RAW_EVENT, rig.filter.groupMode());
        assertEquals(Set.of("Tick"), rig.filter.dimensions(),
                "grouping applied before dimensions: setGroupMode alone would have reset them to all");
        assertEquals(10L, rig.filter.fromMillis());
        assertEquals("x", rig.filter.text());
    }

    @Test
    @DisplayName("W-A4: a dirty filter does not survive a step that states the filter at its defaults")
    void aDirtyFilterDoesNotLeak() {
        Rig rig = new Rig();
        rig.filter.setAll(1L, 2L, FilterState.GroupMode.RAW_EVENT, Set.of("Other"), "dirty");
        new WalkPresenter(rig).apply(view(WalkSpec.Filter.ALL, null, null, null));
        assertNull(rig.filter.fromMillis());
        assertNull(rig.filter.dimensions());
        assertEquals("", rig.filter.text());
        assertEquals(FilterState.GroupMode.DIMENSION, rig.filter.groupMode());
    }

    @Test
    @DisplayName("review PR57 R8: a step with NO filter object applies the documented defaults, not the dirty selection")
    void anOmittedFilterAppliesTheDefaults() {
        Rig rig = new Rig();
        rig.filter.setAll(1L, 2L, FilterState.GroupMode.RAW_EVENT, Set.of("Other"), "dirty");
        var parsed = telamin.fluxtion.audit.analyser.analyser.walk.WalkSteps.parse(
                List.of(Map.of("view", Map.of("tab", "topology"), "targets", List.of("status"))));
        assertTrue(parsed.ok(), parsed.error());
        assertNull(parsed.steps().get(0).view().filter(), "control: the step really carries no filter object");

        new WalkPresenter(rig).apply(parsed.steps().get(0).view());

        assertEquals(FilterState.GroupMode.DIMENSION, rig.filter.groupMode(), "grouping defaults");
        assertEquals("", rig.filter.text(), "no text filter survives into the step");
        assertNull(rig.filter.dimensions(), "every dimension");
        assertNull(rig.filter.fromMillis(), "the whole time range");

        // an older or imported definition arrives the same way: a view whose filter is null
        rig.filter.setAll(1L, 2L, FilterState.GroupMode.RAW_EVENT, Set.of("Other"), "dirty");
        new WalkPresenter(rig).apply(new WalkSpec.View("summary", null, null, null, null));
        assertEquals("", rig.filter.text(), "a stored step without a filter takes the defaults too");
    }

    @Test
    @DisplayName("W-A4 / R2: an open chart is selected, and nothing is persisted")
    void anOpenChartIsSelectedWithoutAWrite() {
        Rig rig = new Rig();
        rig.graphs.addGraph("second");
        rig.chartChanges = 0;
        var notes = new WalkPresenter(rig).apply(view(null, null, "openOne", null));
        assertEquals(List.of(), notes);
        assertEquals("openOne", rig.graphs.selectedGraphName());
        assertEquals(0, rig.chartChanges, "selecting a chart must not reach the save funnel");
    }

    @Test
    @DisplayName("R2: a CLOSED chart is not opened by the walk — the step says so, and nothing is written")
    void aClosedChartIsNotOpened() {
        Rig rig = new Rig();
        var notes = new WalkPresenter(rig).apply(view(null, null, "closedOne", null));
        assertFalse(notes.isEmpty(), "a closed chart must be named");
        assertTrue(notes.get(0).contains("closed"), notes.toString());
        assertFalse(rig.graphs.graphNames().contains("closedOne"), "the walk never opens a chart");
        assertEquals(0, rig.chartChanges, "and never persists an open state");
    }

    @Test
    @DisplayName("R6: a focus is applied only when its definition matches the one the walk was written against")
    void aFocusIsBoundToItsDefinition() {
        Rig rig = new Rig();
        FocusSpec focus = new FocusSpec("price path", "why", List.of("marketDataEvent", "priceListener_2"));
        rig.config.namedFocuses.add(focus);
        var ok = new WalkPresenter(rig).apply(view(null, null, null,
                new WalkSpec.FocusRef("price path", ConfigStore.focusDefinitionDigest(focus))));
        assertEquals(List.of(), ok, "a matching focus applies cleanly");

        rig.config.namedFocuses.clear();
        rig.config.namedFocuses.add(new FocusSpec("price path", "why", List.of("clock")));   // deleted, recreated
        var recreated = new WalkPresenter(rig).apply(view(null, null, null,
                new WalkSpec.FocusRef("price path", ConfigStore.focusDefinitionDigest(focus))));
        assertFalse(recreated.isEmpty(), "the step must say why the focus was not applied");
        assertTrue(recreated.get(0).contains("different definition"), recreated.toString());

        rig.config.namedFocuses.clear();
        var missing = new WalkPresenter(rig).apply(view(null, null, null,
                new WalkSpec.FocusRef("price path", ConfigStore.focusDefinitionDigest(focus))));
        assertFalse(missing.isEmpty(), "the step must say why the focus was not applied");
        assertTrue(missing.get(0).contains("not saved"), missing.toString());
    }

    @Test
    @DisplayName("a record the step's filter hides is named, not silently skipped")
    void aHiddenRecordIsNamed() {
        Rig rig = new Rig();
        rig.recordVisible = false;
        var notes = new WalkPresenter(rig).apply(view(null, 3, null, null));
        assertFalse(notes.isEmpty(), "a hidden record must be named");
        assertTrue(notes.get(0).contains("record 3"), notes.toString());
    }

    @Test
    @DisplayName("a missing walk, a step past its end, or an invalid step is refused whole, and nothing is applied")
    void refusalsApplyNothing() {
        Rig rig = new Rig();
        WalkPresenter p = new WalkPresenter(rig);
        var missing = (SessionEvents.WalkViewApplied) p.applyView(new SessionEffects.ApplyWalkViewEffect(0, 1, 1, "nope", 0));
        assertFalse(missing.ok());
        rig.config.walks.add(new WalkSpec("w", "", "assistant", "", "", null, List.of(),
                List.of(new WalkSpec.Step("", view(new WalkSpec.Filter(5L, 6L, "DIMENSION", null, ""), null, null, null),
                        List.of(new WalkSpec.Target("status", "", null)))), Map.of()));
        var past = (SessionEvents.WalkViewApplied) p.applyView(new SessionEffects.ApplyWalkViewEffect(0, 2, 1, "w", 4));
        assertFalse(past.ok());
        assertTrue(past.reason().contains("1 step"), past.reason());
        assertEquals(0, rig.filterChanges, "a refused step changes nothing");
    }
}
