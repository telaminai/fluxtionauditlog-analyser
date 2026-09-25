package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 35eeb320 — the plot style is part of a saved chart.
 *
 * <p>{@code GraphPanel} has always treated style as a persistable mutation (it calls {@code mutated()} and
 * its own doc lists style beside series, pins and notes), but {@link GraphSpec} had no such component and
 * {@code ConfigStore} never wrote one. The omission was silent rather than deliberate: stairs is the
 * default in {@code ChartPanel}, so a stairs chart round-tripped by accident and only a DELIBERATE line or
 * points chart came back changed — the reading of the chart altered without anyone touching it.
 */
class GraphStylePersistenceTest {

    private static GraphSpec chart(String name, String style) {
        return new GraphSpec(name, List.of("a" + (char) 1 + "x"), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), style);
    }

    private static AppConfig roundTrip(GraphSpec... graphs) throws Exception {
        Path dir = Files.createTempDirectory("cfg");
        ConfigStore store = new ConfigStore(dir.resolve("config"));
        AppConfig cfg = new AppConfig();
        cfg.savedGraphs.addAll(List.of(graphs));
        store.save(cfg);
        return store.load();
    }

    @Test
    void aDeliberateStyleSurvivesTheProfile() throws Exception {
        AppConfig back = roundTrip(chart("line chart", "line"), chart("points chart", "points"));
        assertEquals("line", back.savedGraphs.get(0).style(),
                "a line chart must reopen as a line chart — reverting it to stairs rewrites what the chart claims");
        assertEquals("points", back.savedGraphs.get(1).style());
    }

    @Test
    void stairsIsTheDefaultAndACharWithNoDeclaredStyleStaysThatWay() throws Exception {
        AppConfig back = roundTrip(chart("unstyled", null));
        assertEquals(GraphSpec.DEFAULT_STYLE, back.savedGraphs.get(0).style(),
                "an undeclared style answers the default rather than null, so no caller has to know it");
        assertNull(back.savedGraphs.get(0).declaredStyle(),
                "and it is still UNDECLARED: a profile written before this round must not gain a key it never had");
    }

    @Test
    void aStyleTheRendererDoesNotKnowIsDroppedOnTheWayBackIn() throws Exception {
        AppConfig back = roundTrip(chart("hand edited", "staircase"));
        assertNull(back.savedGraphs.get(0).declaredStyle(),
                "an unrecognised style must not reach the panel, which would fall back silently and leave the "
                        + "profile claiming something the chart is not");
        assertEquals(GraphSpec.DEFAULT_STYLE, back.savedGraphs.get(0).style(),
                "dropping it means the default, the same as never having declared one");
    }

    @Test
    void theSpecNormalisesWhatItIsGiven() {
        assertEquals("line", chart("g", "LINE").style(), "the stored form is lower case, so the writer is stable");
        assertNull(chart("g", "   ").declaredStyle(), "blank is not a style");
    }

    @Test
    void thePanelRoundTripsItsOwnStyleName() {
        GraphPanel panel = new GraphPanel();
        assertEquals("step", panel.styleName(), "stairs is the default the combo opens on");
        for (String style : List.of("line", "points", "step")) {
            panel.setStyleByName(style);
            assertEquals(style, panel.styleName(), "setStyleByName/styleName must be the same vocabulary");
        }
    }

    @Test
    void styleIsAPersistableMutation() {
        GraphPanel panel = new GraphPanel();
        int[] mutations = {0};
        panel.setOnMutation(() -> mutations[0]++);
        panel.setStyleByName("line");
        assertEquals(1, mutations[0],
                "B-M20-3: an edit that changes the saved chart must say so, or it is lost on the next load");
    }
}
