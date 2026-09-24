package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.5 — the rule that decides whether a chart survives.
 *
 * <p>These assertions exist because the equivalent ones did NOT exist when this logic shipped. It was
 * written inline in {@code MainFrame}, which no headless test can construct, so reverting it to its
 * original destructive form (clear the list, refill from the open tabs) left the whole suite green while
 * closing a chart silently destroyed it. Extracting it to {@link SavedGraphMerge} is what makes these real.
 */
class SavedGraphMergeTest {

    private static GraphSpec chart(String name, boolean open, String explanation) {
        return new GraphSpec(name, List.of(), List.of(), null, null, null, explanation,
                List.of(new GraphSpec.NoteSpec(1L, "a finding worth keeping", null)),
                List.of(), List.of(), List.of(), List.of(), List.of(), "line", open);
    }

    @Test
    void aChartThatIsNoLongerATabIsKeptAndMarkedClosed() {
        List<GraphSpec> merged = SavedGraphMerge.merge(
                List.of(chart("Prices", true, "why prices"), chart("Ticks", true, "why ticks")),
                List.of(chart("Prices", true, "why prices")));   // "Ticks" was closed

        assertEquals(2, merged.size(), "closing a chart must not remove it — this is the data loss");
        assertEquals("Ticks", merged.get(1).name());
        assertFalse(merged.get(1).open(), "it is remembered as closed");
        assertEquals("why ticks", merged.get(1).explanation(), "with its annotations intact");
        assertEquals(1, merged.get(1).notes().size());
    }

    @Test
    void anOpenTabWinsOverTheStoredCopy() {
        List<GraphSpec> merged = SavedGraphMerge.merge(
                List.of(chart("Prices", false, "stale")),
                List.of(chart("Prices", true, "edited just now")));

        assertEquals(1, merged.size());
        assertEquals("edited just now", merged.get(0).explanation(), "the live tab is the truth");
        assertTrue(merged.get(0).open());
    }

    @Test
    void aNewTabIsAppendedAndOrderFollowsTheProfile() {
        List<GraphSpec> merged = SavedGraphMerge.merge(
                List.of(chart("A", true, "a"), chart("B", false, "b")),
                List.of(chart("B", true, "b"), chart("A", true, "a"), chart("C", true, "c")));

        assertEquals(List.of("A", "B", "C"), merged.stream().map(GraphSpec::name).toList(),
                "profile order first so charts do not shuffle on every save, new ones appended");
        assertTrue(merged.get(1).open(), "B was reopened");
    }

    @Test
    void aDeletedChartStaysDeleted() {
        // deletion removes the definition BEFORE the merge runs; the merge must not resurrect it
        List<GraphSpec> merged = SavedGraphMerge.merge(List.of(chart("Kept", true, "k")), List.of());
        assertEquals(1, merged.size());
        assertFalse(merged.get(0).open());
        assertTrue(SavedGraphMerge.merge(List.of(), List.of()).isEmpty(),
                "nothing saved and nothing open is not an invitation to invent a chart");
    }

    @Test
    void aDuplicateNameInTheProfileIsNotMergedTwice() {
        List<GraphSpec> merged = SavedGraphMerge.merge(
                List.of(chart("Same", true, "first"), chart("Same", true, "second")),
                List.of(chart("Same", true, "live")));

        assertEquals(1, merged.size(), "one name is one chart — keeping both would double on every save");
        assertEquals("live", merged.get(0).explanation());
    }

    @Test
    void theEmptyPlaceholderTabCannotReplaceAnAnnotatedClosedChart() {
        // the destruction scenario: a fresh unnamed tab lands on a closed chart's name. The merge keys on
        // the name, so if that ever happens the annotated definition is gone. GraphTabs prevents the name
        // collision; this pins what the merge would do if it did not, so the guarantee is stated somewhere.
        GraphSpec annotated = chart("Graph 2", false, "three months of findings");
        GraphSpec placeholder = new GraphSpec("Graph 2", List.of(), List.of(), null, null, null, null,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "step", true);

        List<GraphSpec> merged = SavedGraphMerge.merge(List.of(annotated), List.of(placeholder));
        assertEquals("", merged.get(0).explanation(),
                "the merge CANNOT tell these apart — which is exactly why name collisions must be "
                        + "prevented upstream, in GraphTabs, and why that is tested there");
    }
}
