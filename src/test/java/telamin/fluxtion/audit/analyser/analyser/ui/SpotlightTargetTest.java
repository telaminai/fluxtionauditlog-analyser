package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import telamin.fluxtion.audit.analyser.analyser.llm.SpotlightVocabulary;
import telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget.Family;
import telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget.Outcome;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M64 acceptance 1 — every vocabulary entry resolves to non-empty bounds when its target is visible, and
 * to a TYPED not-visible / unknown answer otherwise; headless, one case per entry, plus negative controls.
 *
 * <p>The surface here is a map. That is the point of D-SP3: resolution is a pure function over "where is
 * this, and can you bring it on screen?", so none of it needs a window.
 */
class SpotlightTargetTest {

    /** A surface that knows where some targets are, and records what it was asked, in order. */
    private static final class FakeSurface implements SpotlightTarget.Surface {
        final Map<String, Rectangle> where = new HashMap<>();
        final List<String> calls = new ArrayList<>();

        @Override public void reveal(SpotlightTarget t) {
            calls.add("reveal " + t.name());
        }

        @Override public Optional<Rectangle> bounds(SpotlightTarget t) {
            calls.add("bounds " + t.name());
            return Optional.ofNullable(where.get(t.name()));
        }

        @Override public String whyNotVisible(SpotlightTarget t) {
            return t.name() + " is not on screen";
        }
    }

    // ---- one per vocabulary entry: it parses to its family, and lights when the surface has it ------

    @ParameterizedTest
    @ValueSource(strings = {
            "tab:summary", "tab:source", "tab:graph", "tab:topology", "tab:reports", "tab:assistant",
            "source:design", "source:design:bean:x", "source:design:bean:com.acme::x", "source:design:line:3",
            "records", "records:row:12", "detail", "detail:node:priceListener",
            "topology", "topology:node:priceListener", "topology:verdict",
            "graph", "graph:note:2", "graph:series:quotePublisher.spread",
            "graph:Spread", "graph:Spread:note:2", "graph:Spread:series:quotePublisher.spread",
            "graph:Series A", "graph:Notes on spread:note:2", "graph:Series A:series:x", "graph:note",
            "project", "project:log", "project:graph", "project:processors", "project:roots",
            "toolbar:open", "toolbar:flag", "toolbar:explain", "toolbar:follow",
            "menu:Audit log", "menu:Audit log:Open log…", "status"})
    void everyVocabularyEntryLightsWhenItsTargetIsVisible(String name) {
        FakeSurface surface = new FakeSurface();
        surface.where.put(name, new Rectangle(10, 20, 100, 30));

        SpotlightTarget.Resolution r = SpotlightTarget.resolve(name, surface);

        assertEquals(Outcome.LIT, r.outcome(), name + " → " + r.reason());
        assertEquals(new Rectangle(10, 20, 100, 30), r.bounds());
        assertEquals(List.of("reveal " + name, "bounds " + name), surface.calls,
                "revealed FIRST, then measured — a target measured before it is brought on screen is measured wrong");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tab:topology", "records:row:12", "detail:node:priceListener",
            "topology:node:priceListener", "topology:verdict", "graph:note:2", "graph:series:spread", "project:log",
            "toolbar:flag", "status"})
    void aTargetTheSurfaceCannotShowIsNOT_VISIBLE_withTheSurfacesReason_neverASpotlightOnNothing(String name) {
        SpotlightTarget.Resolution r = SpotlightTarget.resolve(name, new FakeSurface());

        assertEquals(Outcome.NOT_VISIBLE, r.outcome());
        assertNull(r.bounds());
        assertEquals(name + " is not on screen", r.reason());
    }

    @Test
    void aZeroAreaRectangleIsNotVisibleToo_aTabNeverLaidOutHasBoundsButNoArea() {
        FakeSurface surface = new FakeSurface();
        surface.where.put("topology", new Rectangle(0, 0, 0, 0));
        assertEquals(Outcome.NOT_VISIBLE, SpotlightTarget.resolve("topology", surface).outcome());
    }

    // ---- unknown: a plain error naming the vocabulary, and the surface is never consulted ------------

    @ParameterizedTest
    @ValueSource(strings = {"topolgy", "tab:topolgy", "tab", "tab:", "records:row", "records:row:abc", "records:row:-1",
            "records:col:3", "topology:node:", "graph:note:two", "graph:legend:x", "project:reports", "toolbar:next",
            "status:line", "coverage", "coverage:panel", "topology:verdict:line", "topology:verdicts", "",
            "graph:", "graph:a:b:series:x", "graph:Spread:note:", "graph:Spread:series:", "graph:note:0", "graph:Spread:note:0",
            "menu", "menu:", "menu::Open log…"})
    void aMisspeltOrMalformedTargetIsUNKNOWN_namesTheVocabulary_andNeverTouchesTheSurface(String name) {
        FakeSurface surface = new FakeSurface();

        SpotlightTarget.Resolution r = SpotlightTarget.resolve(name, surface);

        assertEquals(Outcome.UNKNOWN, r.outcome(), name);
        assertTrue(r.reason().contains("The vocabulary is:"), r.reason());
        assertTrue(r.reason().contains("topology:node:<instanceId>"), "the error teaches the vocabulary: " + r.reason());
        assertTrue(surface.calls.isEmpty(), "an unknown name must not reveal or move anything: " + surface.calls);
    }

    @Test
    void nullIsUnknown_notAnException() {
        assertEquals(Outcome.UNKNOWN, SpotlightTarget.resolve(null, new FakeSurface()).outcome());
    }

    // ---- parsing details that would otherwise bite --------------------------------------------------

    @Test
    void familyWordsAreCaseInsensitive_butAnArgumentKeepsItsCase_itIsTheUsersOwnSpelling() {
        SpotlightTarget t = SpotlightTarget.parse("Topology:Node:priceListener").target();
        assertEquals(Family.TOPOLOGY_NODE, t.family());
        assertEquals("priceListener", t.argument());

        assertEquals("Spread (bid/ask)", SpotlightTarget.parse("graph:series:Spread (bid/ask)").target().argument());
        assertEquals("topology", SpotlightTarget.parse("TAB:Topology").target().argument(), "a tab is a closed word");
    }

    @Test
    void anInstanceIdMayItselfContainAColon() {
        SpotlightTarget t = SpotlightTarget.parse("topology:node:com.acme::handler").target();
        assertEquals("com.acme::handler", t.argument());
    }

    @Test
    void theNumericFamiliesGiveTheirNumber() {
        assertEquals(12, SpotlightTarget.parse("records:row:12").target().number());
        assertEquals(2, SpotlightTarget.parse("graph:note:2").target().number());
    }

    // ---- one vocabulary, stated once ----------------------------------------------------------------

    @Test
    void aGraphTargetMayNameItsChart_orMeanTheSelectedOne_M64_10() {
        assertNull(SpotlightTarget.parse("graph:note:2").target().graph(), "no name: the SELECTED chart");
        assertNull(SpotlightTarget.parse("graph:series:spread").target().graph());
        SpotlightTarget named = SpotlightTarget.parse("graph:Spread (bid/ask):note:2").target();
        assertEquals(SpotlightTarget.Family.GRAPH_NOTE, named.family());
        assertEquals("Spread (bid/ask)", named.graph(), "the chart, exactly as named");
        assertEquals(2, named.number());
        SpotlightTarget series = SpotlightTarget.parse("graph:Spread:series:quotePublisher.spread").target();
        assertEquals(SpotlightTarget.Family.GRAPH_SERIES, series.family());
        assertEquals("Spread", series.graph());
        assertEquals("quotePublisher.spread", series.argument());
        SpotlightTarget plot = SpotlightTarget.parse("graph:Spread").target();
        assertEquals(SpotlightTarget.Family.GRAPH, plot.family());
        assertEquals("Spread", plot.graph(), "the chart's plot itself");
        assertTrue(SpotlightTarget.parse("graph:a:b:series:x").error().contains("cannot contain ':'"),
                "a chart name with a colon is unreachable, and the refusal says why");
        // review F1: a NAME that starts with a keyword is a name; only "note:"/"series:" (with the colon) are the bare forms
        assertEquals("Series A", SpotlightTarget.parse("graph:Series A").target().graph());
        assertEquals("Notes on spread", SpotlightTarget.parse("graph:Notes on spread:note:2").target().graph());
        assertEquals("Series A", SpotlightTarget.parse("graph:Series A:series:x").target().graph());
        assertEquals("note", SpotlightTarget.parse("graph:note").target().graph(), "a chart named 'note': its plot is reachable");
        assertNull(SpotlightTarget.parse("graph:note:2").target().graph(), "…but graph:note:2 is the bare form, by design");
        // review F5: notes are numbered from 1
        assertTrue(SpotlightTarget.parse("graph:note:0").error().contains("numbered from 1"));
    }

    @Test
    void aMenuTargetNamesTheMenu_andOptionallyOneItem_M64_11() {
        SpotlightTarget menu = SpotlightTarget.parse("menu:Audit log").target();
        assertEquals(SpotlightTarget.Family.MENU, menu.family());
        assertEquals("Audit log", menu.menuName());
        SpotlightTarget item = SpotlightTarget.parse("menu:Project:New project from template…").target();
        assertEquals(SpotlightTarget.Family.MENU_ITEM, item.family());
        assertEquals("Project", item.menuName());
        assertEquals("New project from template…", item.menuItem());
        assertEquals(SpotlightTarget.Family.MENU_ITEM, SpotlightTarget.parse("menu:AI:Connect an AI client…").target().family());
        assertFalse(SpotlightTarget.parse("menu:").ok());
    }

    @Test
    void theWordsAnAgentIsGivenAreTheVocabularyTheParserAccepts() {
        assertEquals(SpotlightTarget.vocabulary(), SpotlightVocabulary.TEXT,
                "VerbSchemas prints SpotlightVocabulary.TEXT; SpotlightTarget parses Family.form(). If they "
                        + "differ an agent is taught targets that do not exist, or not taught ones that do.");
    }

    @Test
    void theTwoVerbsTheTutorChecksWithNeverEndASpotlight() {
        // WHICH verbs end one is pinned verb by verb in SpotlightEndsWhenTheViewChangesTest, so that removing
        // one turns exactly its own test red (the spec's mutation check). This pins only the two that must
        // never be added: they are how the tutor confirms what it lit.
        assertFalse(SpotlightTarget.VIEW_CHANGING_VERBS.contains("screenshot"));
        assertFalse(SpotlightTarget.VIEW_CHANGING_VERBS.contains("context"));
    }
}
