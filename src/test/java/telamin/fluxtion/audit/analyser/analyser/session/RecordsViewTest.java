package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.filter.RecordsViewCounts;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Records view's counts are the session's (rule 9): the {@code recordsView} node decides WHEN the view is measured,
 * the adapter performs the measurement and answers with the counts, and the snapshot publishes them. These tests drive
 * facts into the real generated processor and read the published state out; the counts themselves are measured over the
 * sample log with the same predicates the table filters with.
 */
class RecordsViewTest {

    private static List<SessionEffects.MeasureRecordsViewEffect> measures(FakeSessionAdapter a) {
        return a.performed.stream().filter(SessionEffects.MeasureRecordsViewEffect.class::isInstance)
                .map(SessionEffects.MeasureRecordsViewEffect.class::cast).toList();
    }

    private static HeapLogStore sample() throws Exception {
        return new HeapLogStore(Files.readString(Path.of("src/test/resources/sample.yml")));
    }

    private static RecordsViewCounts measure(FilterState f) throws Exception {
        HeapLogStore s = sample();
        f.setTextSource(s::rawText);
        return RecordsViewCounts.measure(s.index(), f, false, r -> false, 7L);
    }

    private static SessionDriver opened(FakeSessionAdapter a) {
        SessionDriver d = new SessionDriver(a);
        SessionFixtures.openLog(d, a, "/f.yaml", "DECLARED", Set.of("a"), 1, 21, "TRACE");
        return d;
    }

    @Test
    @DisplayName("Nothing is measured until the view is installed; then once, and the snapshot publishes the counts")
    void installedViewIsMeasuredAndPublished() throws Exception {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        long g = d.snapshot().logGeneration();
        d.post(new SessionEvents.ViewFilterChanged("k0"));
        assertEquals(0, measures(a).size(), "a filter key before the view is installed asks for nothing: " + a.performed);
        a.recordsView = measure(new FilterState());
        d.post(new SessionEvents.RecordsViewInstalled(g));
        assertEquals(1, measures(a).size(), "the installed view is measured once");
        assertEquals(g, measures(a).get(0).generation());
        assertEquals(a.recordsView, d.snapshot().recordsView(), "the snapshot publishes what was measured");
        assertEquals(21, d.snapshot().recordsView().total());
        assertEquals(21, d.snapshot().recordsView().shown());
    }

    @Test
    @DisplayName("A moved filter key, a Follow append and flagged-only each ask again; an unchanged key does not")
    void whenItAsks() throws Exception {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        long g = d.snapshot().logGeneration();
        a.recordsView = measure(new FilterState());
        d.post(new SessionEvents.RecordsViewInstalled(g));
        d.post(new SessionEvents.ViewFilterChanged("k1"));
        assertEquals(2, measures(a).size(), "a moved key");
        d.post(new SessionEvents.ViewFilterChanged("k1"));
        assertEquals(2, measures(a).size(), "the same key again asks for nothing");
        d.post(new SessionEvents.LogAppended(g, Set.of("a"), 1, 22, "TRACE"));
        assertEquals(3, measures(a).size(), "a Follow append");
        d.post(new SessionEvents.RecordFlagsChanged(g, false, 1));
        assertEquals(3, measures(a).size(), "a flag hides nothing while flagged only is off");
        d.post(new SessionEvents.RecordFlagsChanged(g, true, 1));
        assertEquals(4, measures(a).size(), "flagged only turned on");
        d.post(new SessionEvents.RecordFlagsChanged(g, true, 2));
        assertEquals(5, measures(a).size(), "a flag changed while flagged only is on");
    }

    @Test
    @DisplayName("A measurement of another generation is refused, a refusal (null counts) changes nothing, and a new log clears")
    void staleAndEmptyAreRefused() throws Exception {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = opened(a);
        long g = d.snapshot().logGeneration();
        RecordsViewCounts counts = measure(new FilterState());
        a.recordsView = counts;
        d.post(new SessionEvents.RecordsViewInstalled(g));
        assertEquals(counts, d.snapshot().recordsView());
        d.post(new SessionEvents.RecordsViewMeasured(0L, g, null));
        assertEquals(counts, d.snapshot().recordsView(), "no installed view to measure is not a zero");
        SessionFixtures.openLog(d, a, "/g.yaml", "DECLARED", Set.of("a"), 1, 21, "TRACE");
        assertNull(d.snapshot().recordsView(), "the counts described the previous log");
        d.post(new SessionEvents.RecordsViewMeasured(0L, g, counts));
        assertNull(d.snapshot().recordsView(), "a measurement of the replaced log is never shown over the new one");
        d.post(new SessionEvents.LogCleared(d.snapshot().logGeneration()));
        assertNull(d.snapshot().recordsView());
    }

    @Test
    @DisplayName("The counts: shown, hidden per filter, and each type's shown/total under the OTHER filters")
    void theCounts() throws Exception {
        FilterState f = new FilterState();
        f.setDimensions(Set.of("LifecycleEvent"));
        f.setText("hedging");
        RecordsViewCounts c = measure(f);
        assertEquals(21, c.total());
        assertEquals(0, c.shown(), "no LifecycleEvent record mentions hedging");
        assertEquals(20, c.hiddenByTypes(), "one LifecycleEvent record of 21");
        assertEquals(9, c.hiddenByText(), "12 records mention hedging");
        assertTrue(c.typesFiltered());
        assertEquals(1, c.typesSelected());
        assertEquals(7, c.typesTotal());
        assertTrue(c.searching());
        assertEquals(7L, c.textRef(), "the search text by reference, never the words");
        var lifecycle = c.types().stream().filter(t -> t.type().equals("LifecycleEvent")).findFirst().orElseThrow();
        assertEquals(1, lifecycle.total());
        assertEquals(0, lifecycle.shown(), "under the search, none of it");
        assertTrue(lifecycle.selected());
        int shownUnderSearch = c.types().stream().mapToInt(RecordsViewCounts.TypeCount::shown).sum();
        assertEquals(12, shownUnderSearch, "per type, the search's 12 — the type selection is not applied to its own rows");
        for (int i = 1; i < c.types().size(); i++) {
            assertTrue(c.types().get(i - 1).total() >= c.types().get(i).total(), "sorted by total: " + c.types());
        }
        // the table's own test agrees with "shown"
        HeapLogStore s = sample();
        f.setTextSource(s::rawText);
        int table = 0;
        for (int row = 0; row < s.index().size(); row++) if (f.test(s.index(), row)) table++;
        assertEquals(table, c.shown());
    }

    @Test
    @DisplayName("Flagged only hides the unflagged; with no filter nothing is hidden")
    void flaggedOnly() throws Exception {
        HeapLogStore s = sample();
        FilterState f = new FilterState();
        f.setTextSource(s::rawText);
        RecordsViewCounts none = RecordsViewCounts.measure(s.index(), f, false, r -> false, 7L);
        assertFalse(none.anyHidden());
        assertEquals(0, none.hiddenByTypes() + none.hiddenByText() + none.hiddenByTime() + none.hiddenByFlags());
        RecordsViewCounts flagged = RecordsViewCounts.measure(s.index(), f, true, r -> r == 3 || r == 4, 0L);
        assertEquals(2, flagged.shown());
        assertEquals(19, flagged.hiddenByFlags());
        assertTrue(flagged.flaggedOnly());
    }
}
