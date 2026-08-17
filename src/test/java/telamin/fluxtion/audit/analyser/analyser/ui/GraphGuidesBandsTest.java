package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Guides and condition bands through the {@code graph} verb (M28.5/M28.6), plus the share-surface
 * obligations the F1 lesson attached to any new persisted graph state: the artifact must survive the
 * spec round-trip and the share path, and malformed input must be named, never silently dropped.
 */
class GraphGuidesBandsTest {

    private final HeapLogStore store = new HeapLogStore(Samples.sample());

    private ActionExecutor executor(GraphTabs tabs) {
        tabs.bind(store, new FilterState());
        FilterState filter = new FilterState();
        return new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f) -> { });
    }

    @Test
    void theVerbAppliesGuidesAndBands_andTheSpecCarriesThem() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        var r = ex.render("graph", Map.of(
                "newTab", true, "name", "g",
                "series", List.of("bidMakerOrder.price"),
                "guides", List.of(Map.of("value", 0.004, "label", "4bp limit")),
                "bands", List.of(Map.of(
                        "expr", "askMakerOrder.price - bidMakerOrder.price > 0.004",
                        "label", "in breach"))));
        assertTrue(r.ok(), () -> "graph action failed: " + r);
        assertEquals(1, r.payload().get("guides"));
        assertEquals(1, r.payload().get("bands"));

        GraphSpec spec = tabs.specs().stream()
                .filter(s -> "g".equals(s.name())).findFirst().orElseThrow();
        assertEquals(0.004, spec.guides().get(0).value(), 1e-12);
        assertEquals("4bp limit", spec.guides().get(0).label());
        assertEquals("in breach", spec.bands().get(0).label());
    }

    @Test
    void presentMeansReplace_soAnEmptyListClears() {
        GraphTabs tabs = new GraphTabs();
        ActionExecutor ex = executor(tabs);
        assertTrue(ex.render("graph", Map.of("newTab", true, "name", "g",
                "series", List.of("bidMakerOrder.price"),
                "guides", List.of(Map.of("value", 1.0)))).ok());
        assertTrue(ex.render("graph", Map.of("name", "g", "guides", List.of())).ok());
        assertTrue(tabs.specs().stream().filter(s -> "g".equals(s.name())).findFirst().orElseThrow()
                .guides().isEmpty(), "guides: [] clears the set");
    }

    @Test
    void malformedItemsAreNamedNotSilentlyDropped() {
        GraphTabs tabs = new GraphTabs();
        var r = executor(tabs).render("graph", Map.of(
                "newTab", true, "name", "g",
                "series", List.of("bidMakerOrder.price"),
                "guides", List.of(Map.of("label", "no value")),
                "bands", List.of(Map.of("expr", "1 < 2 < 3"))));
        assertTrue(r.ok(), "still applied — the valid remainder must not be held hostage");
        @SuppressWarnings("unchecked")
        List<String> warnings = (List<String>) r.payload().get("warnings");
        assertNotNull(warnings);
        assertTrue(warnings.stream().anyMatch(w -> w.contains("no numeric 'value'")), warnings.toString());
        assertTrue(warnings.stream().anyMatch(w -> w.contains("does not parse")), warnings.toString());
        assertEquals(0, r.payload().get("guides"));
        assertEquals(0, r.payload().get("bands"));
    }

    @Test
    void guidesAndBandsSurviveTheSharePath() {
        // share reuses ConfigStore.writeGraphs/readGraphs — assert the ride-along, don't assume it
        AppConfig sender = new AppConfig();
        sender.savedGraphs.add(new GraphSpec("Spread", List.of("quoteNodespread"), List.of(),
                null, null, null, null, List.of(), List.of(),
                List.of(new GraphSpec.GuideSpec(0.004, "4bp limit", false)),
                List.of(new GraphSpec.BandSpec("a.x > 1", "hot"))));

        SettingsShare share = new SettingsShare("/home/tester");
        String text = share.export(sender, java.util.EnumSet.of(SettingsShare.Category.GRAPHS));
        AppConfig receiver = new AppConfig();
        share.apply(share.preview(text, receiver),
                java.util.EnumSet.of(SettingsShare.Category.GRAPHS), receiver);

        GraphSpec got = receiver.savedGraphs.get(0);
        assertEquals("4bp limit", got.guides().get(0).label());
        assertEquals("a.x > 1", got.bands().get(0).expr());
    }

    @Test
    void restoreAppliesGuidesAndBandsWithoutEchoingAnEdit() {
        GraphTabs tabs = new GraphTabs();
        tabs.bind(store, new FilterState());
        int[] fired = {0};
        tabs.setChangeListener(() -> fired[0]++);
        tabs.restore(List.of(new GraphSpec("g", List.of(), List.of(), null, null, null, null,
                List.of(), List.of(),
                List.of(new GraphSpec.GuideSpec(2.0, "cap", false)),
                List.of(new GraphSpec.BandSpec("bidMakerOrder.price > 1", null)))));
        assertEquals(0, fired[0], "rebuilding persisted annotations is not a user edit");
        assertEquals(1, tabs.specs().get(0).guides().size());
        assertEquals(1, tabs.specs().get(0).bands().size());
    }
}
