package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget.Requests;
import telamin.fluxtion.audit.analyser.analyser.ui.SpotlightTarget.SetResolution;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M64.6 — several spotlights at once: what a call may ask for, and the rule that a set lights WHOLE or not
 * at all. Headless: the surface is a map of tabs, which is all "cannot be on screen together" needs.
 */
class SpotlightSetTest {

    /** A surface with TABS: a target is measurable only while its tab is the one showing. */
    private static final class TabbedSurface implements SpotlightTarget.Surface {
        final Map<String, String> tabOf = new HashMap<>();          // target → tab ("" = always on screen)
        final List<String> calls = new ArrayList<>();
        String showing = "records";

        TabbedSurface put(String target, String tab) {
            tabOf.put(target, tab);
            return this;
        }

        @Override public void reveal(SpotlightTarget t) {
            calls.add("reveal " + t.name());
            String tab = tabOf.get(t.name());
            if (tab != null && !tab.isEmpty()) showing = tab;
        }

        @Override public Optional<Rectangle> bounds(SpotlightTarget t) {
            String tab = tabOf.get(t.name());
            if (tab == null) return Optional.empty();
            return tab.isEmpty() || tab.equals(showing) ? Optional.of(new Rectangle(10, 10, 80, 30)) : Optional.empty();
        }

        @Override public String whyNotVisible(SpotlightTarget t) {
            return t.name() + " is not in the graph";
        }
    }

    // ---- what a call may ask for ----------------------------------------------------------------------

    @Test
    void oneTargetIsStillOneRequest_theOriginalFormIsUnchanged() {
        Requests r = SpotlightTarget.requests(Map.of("target", "status", "caption", "  the pairing verdict  "));
        assertTrue(r.ok(), r.error());
        assertEquals(List.of(new SpotlightTarget.Request("status", "the pairing verdict")), r.requests());
        assertFalse(r.add(), "a call REPLACES what is lit unless it says add");
    }

    @Test
    void targetsIsAListOfObjects_orOfBareNames_eachWithItsOwnCallout() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("target", "topology:node:a");
        first.put("caption", "feeds the next one");
        Requests r = SpotlightTarget.requests(Map.of("targets", List.of(first, "topology:node:b"), "add", true));

        assertTrue(r.ok(), r.error());
        assertEquals(2, r.requests().size());
        assertEquals("feeds the next one", r.requests().get(0).caption());
        assertNull(r.requests().get(1).caption(), "a cut-out needs no words");
        assertTrue(r.add());
    }

    @Test
    void theBoundIsStated_andOneMoreIsRefused_notTruncated() {
        List<Object> seven = new ArrayList<>();
        for (int i = 0; i <= SpotlightTarget.MAX_LIT; i++) seven.add("records:row:" + i);
        Requests r = SpotlightTarget.requests(Map.of("targets", seven));
        assertFalse(r.ok());
        assertTrue(r.error().contains("at most " + SpotlightTarget.MAX_LIT), r.error());

        assertTrue(SpotlightTarget.requests(Map.of("targets", seven.subList(0, SpotlightTarget.MAX_LIT))).ok());
    }

    @Test
    void malformedRequestsAreRefusedWithTheReason() {
        assertTrue(SpotlightTarget.requests(Map.of("target", "status", "targets", List.of("detail"))).error().contains("not both"));
        assertTrue(SpotlightTarget.requests(Map.of("targets", List.of())).error().contains("non-empty"));
        assertTrue(SpotlightTarget.requests(Map.of("targets", "status")).error().contains("list"));
        assertTrue(SpotlightTarget.requests(Map.of("targets", List.of(7))).error().contains("target name"));
        assertTrue(SpotlightTarget.requests(Map.of("targets", List.of("status"), "caption", "whose?")).error()
                .contains("its own"), "a top-level caption beside a list belongs to none of them");
        assertTrue(SpotlightTarget.requests(Map.of("targets", List.of(Map.of("target", "status", "colour", "red"))))
                .error().contains("colour"), "an unknown field is refused, never ignored");
        assertTrue(SpotlightTarget.requests(Map.of("targets", List.of("status", "STATUS"))).error().contains("twice"));
    }

    @Test
    void everyCalloutIsHeldToTheOneLineRule_inASetToo() {
        Map<String, Object> bad = Map.of("target", "status", "caption", "line one\nline two");
        assertTrue(SpotlightTarget.requests(Map.of("targets", List.of("detail", bad))).error().contains("ONE short line"));
        assertTrue(SpotlightTarget.captionError("x".repeat(SpotlightTarget.MAX_CAPTION + 1)).contains("ONE short line"));
        assertNull(SpotlightTarget.captionError("x".repeat(SpotlightTarget.MAX_CAPTION)));
        assertNull(SpotlightTarget.captionError(null));
    }

    // ---- judged WHOLE before anything is revealed (review of M64.6, F2) -----------------------------------

    @Test
    void aMisspeltMemberIsCaughtBeforeAnyReveal_soTheRowBesideItCannotHaveMovedTheFilter() {
        Requests asked = SpotlightTarget.requests(Map.of("targets", List.of("records:row:15", "not-a-target")));
        assertTrue(asked.ok(), "the GRAMMAR is fine — it is a list of names; one of the names is wrong");

        String why = SpotlightTarget.precheck(asked, List.of());
        assertTrue(why != null && why.contains("unknown spotlight target 'not-a-target'"), why);
        assertTrue(why.contains("The vocabulary is:"), "and it still teaches the vocabulary");
    }

    @Test
    void theBoundIsOverTheUNION_ofWhatIsLitAndWhatIsAsked_andIsCheckedBeforeAnyReveal() {
        List<String> six = List.of("status", "toolbar:open", "toolbar:flag", "toolbar:explain", "toolbar:follow", "records");
        Requests seventh = SpotlightTarget.requests(Map.of("target", "records:row:10", "add", true));

        String why = SpotlightTarget.precheck(seventh, six);
        assertTrue(why != null && why.contains("at most " + SpotlightTarget.MAX_LIT) && why.contains("6 are lit"), why);

        assertNull(SpotlightTarget.precheck(SpotlightTarget.requests(Map.of("target", "STATUS", "add", true)), six),
                "re-lighting one ALREADY lit adds nothing to the union, however it is capitalised");
        assertNull(SpotlightTarget.precheck(SpotlightTarget.requests(Map.of("target", "records:row:10")), six),
                "and without add the call REPLACES the set, so what is lit does not count");
    }

    @Test
    void aGrammarErrorIsThePrechecksAnswerToo_oneFunctionSaysEverythingThatCanBeSaidWithoutTheScreen() {
        assertTrue(SpotlightTarget.precheck(SpotlightTarget.requests(Map.of("targets", List.of())), List.of()).contains("non-empty"));
        assertTrue(SpotlightTarget.precheck(SpotlightTarget.requests(Map.of()), List.of()).contains("'target' is required"));
        assertNull(SpotlightTarget.precheck(SpotlightTarget.requests(Map.of("target", "topology:node:ghost")), List.of()),
                "a well-formed name that turns out not to exist is NOT a precheck matter — finding out is the reveal");
    }

    // ---- a set lights WHOLE, or not at all ------------------------------------------------------------

    @Test
    void thingsOnTheSameTabLightTogether_inTheOrderAsked() {
        TabbedSurface s = new TabbedSurface().put("topology:node:a", "topology").put("topology:node:b", "topology")
                .put("status", "");
        SetResolution r = SpotlightTarget.resolveAll(List.of("topology:node:a", "status", "topology:node:b"), s);

        assertTrue(r.ok(), r.reason());
        assertEquals(List.of("topology:node:a", "status", "topology:node:b"),
                r.lit().stream().map(x -> x.target().name()).toList());
    }

    @Test
    void aMisspellingANYWHEREInTheSetRevealsNothing_theSurfaceIsNeverTouched() {
        TabbedSurface s = new TabbedSurface().put("topology:node:a", "topology");
        SetResolution r = SpotlightTarget.resolveAll(List.of("topology:node:a", "topolgy:node:b"), s);

        assertFalse(r.ok());
        assertTrue(r.reason().contains("The vocabulary is:"), r.reason());
        assertTrue(s.calls.isEmpty(), "every name is parsed BEFORE anything moves: " + s.calls);
        assertEquals("records", s.showing);
    }

    @Test
    void oneMemberThatIsNotThere_refusesTheWholeSet_andNamesIt() {
        TabbedSurface s = new TabbedSurface().put("topology:node:a", "topology");
        SetResolution r = SpotlightTarget.resolveAll(List.of("topology:node:a", "topology:node:ghost"), s);

        assertFalse(r.ok());
        assertTrue(r.lit().isEmpty(), "never a set lit half-true");
        assertTrue(r.reason().startsWith("'topology:node:ghost': "), r.reason());
    }

    @Test
    void twoThingsThatCannotBeOnScreenTogetherAreRefused_namingThePair() {
        TabbedSurface s = new TabbedSurface().put("topology:node:a", "topology").put("graph:note:1", "graph");
        SetResolution r = SpotlightTarget.resolveAll(List.of("topology:node:a", "graph:note:1"), s);

        assertFalse(r.ok());
        assertTrue(r.reason().contains("'topology:node:a' and 'graph:note:1' cannot be on screen at the same time"), r.reason());
        assertTrue(r.reason().contains("one after the other"), "the refusal says what to do instead: " + r.reason());
    }

    @Test
    void aSetOfOneIsExactlyTheSingleResolution() {
        TabbedSurface s = new TabbedSurface().put("status", "");
        assertEquals(SpotlightTarget.resolve("status", s).bounds(),
                SpotlightTarget.resolveAll(List.of("status"), s).lit().get(0).bounds());
        assertEquals(SpotlightTarget.resolve("detail", s).reason(),
                SpotlightTarget.resolveAll(List.of("detail"), s).reason(), "and refuses with the same words");
    }

    @Test
    void anEmptySetIsRefused_notAnException() {
        assertFalse(SpotlightTarget.resolveAll(List.of(), new TabbedSurface()).ok());
        assertFalse(SpotlightTarget.resolveAll(null, new TabbedSurface()).ok());
    }
}
