package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.GraphKey;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M65 — follow refreshes open graphs. Each test drives a follow tick the way {@code MainFrame.pollFollow}
 * does: append to the store, then the slider's echo ({@code FilterState.setTimeRange} with the SAME window —
 * what {@code extendAbsMax → publish} sends), then the hook. Driving the hook alone would pass against a
 * chart the echo had already reset (pass-2 C4). The extraction lands through a runner the test controls, so
 * nothing here waits on the pool.
 */
class FollowRefreshesGraphTest {

    private static String rec(long logTime, int x) {
        return "#00:00:00.000 [t] INFO L\neventLogRecord:\n  logTime: " + logTime
                + "\n  nodeLogs:\n    - nodeA: { x: " + x + "}\n---\n";
    }

    /** Runs the walk and delivers inline — the extraction "lands" before the call returns. */
    private static final ExtractionRunner INLINE = new ExtractionRunner() {
        @Override public <T> void run(Supplier<T> work, Consumer<T> ok, Consumer<Throwable> err) {
            T r;
            try { r = work.get(); } catch (Throwable t) { err.accept(t); return; }
            ok.accept(r);
        }
    };

    /** Parks each extraction until the test releases it — the D-F6 seam. */
    private static final class Parking implements ExtractionRunner {
        record Held(Supplier<?> work, Consumer<Object> ok) { }
        final Deque<Held> held = new ArrayDeque<>();
        int started;
        @SuppressWarnings("unchecked")
        @Override public <T> void run(Supplier<T> work, Consumer<T> ok, Consumer<Throwable> err) {
            started++;
            held.add(new Held(work, (Consumer<Object>) ok));
        }
        void releaseOne() {
            Held h = held.remove();
            h.ok().accept(h.work().get());
        }
    }

    private record Rig(Path file, HeapLogStore store, FilterState filter, GraphPanel panel) {
        void tick(long logTime, int x) throws IOException {
            Files.writeString(file, rec(logTime, x), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            assertEquals(1, store.appendFrom(file));
            filter.setTimeRange(filter.fromMillis(), filter.toMillis());   // the slider's echo: same window, fired anyway
            panel.onRecordsAppended();
            panel.runPendingExtractionNow();
        }
        int points() { return panel.chart().plottedSeries().stream().mapToInt(s -> s.size()).sum(); }
        double[] view() { return panel.chart().viewX(); }
    }

    private static Rig rig(ExtractionRunner runner) throws IOException {
        Path p = Files.createTempFile("follow-graph", ".log");
        p.toFile().deleteOnExit();
        Files.writeString(p, "---\n" + rec(1000, 1) + rec(2000, 2), StandardCharsets.UTF_8);
        HeapLogStore store = HeapLogStore.fromFile(p);
        FilterState filter = new FilterState();
        GraphPanel panel = new GraphPanel();
        panel.setExtractionRunner(runner);
        panel.bind(store, filter);
        panel.addKeys(List.of(new GraphKey("nodeA", "x")));
        panel.setMarkers(List.of(new GraphSpec.MarkerSpec("every x", "circle", "nodeA.x", null, null)));
        return new Rig(p, store, filter, panel);
    }

    private static void assertView(double lo, double hi, double[] view) {
        assertNotNull(view, "a view");
        assertEquals(lo, view[0], 0.5, "view lo");
        assertEquals(hi, view[1], 0.5, "view hi");
    }

    @Test
    void aChartShowingTheWholeLogExtendsWithIt() throws IOException {
        Rig r = rig(INLINE);
        assertEquals(2, r.points());
        assertEquals(2, r.panel.markerPointCount());
        assertView(1000, 2000, r.view());

        r.tick(3000, 3);
        assertEquals(3, r.points(), "the appended record is plotted without a reopen");
        assertEquals(3, r.panel.markerPointCount(), "and the marker legend count follows the same landing");
        assertView(1000, 3000, r.view());   // extend: left edge stays, right edge to the new maximum
    }

    @Test
    void aChartAtTheLiveEdgeSlidesWithIt() throws IOException {
        Rig r = rig(INLINE);
        r.panel.chart().setViewWindow(1500L, 2000L);   // zoomed to the last stretch, right edge at the data max
        r.tick(3000, 3);
        assertEquals(3, r.points());
        assertView(2500, 3000, r.view());   // slide: same width, right edge to the new maximum
    }

    @Test
    void aChartZoomedIntoTheMiddleHolds() throws IOException {
        Rig r = rig(INLINE);
        r.panel.chart().setViewWindow(1200L, 1700L);
        r.tick(3000, 3);
        assertEquals(3, r.points(), "the data is there …");
        assertView(1200, 1700, r.view());   // … and the person studying the middle is not disturbed
    }

    @Test
    void aPinnedChartReExtractsButKeepsItsWindow() throws IOException {
        Rig r = rig(INLINE);
        r.panel.pin(1000L, 1500L);
        r.tick(3000, 3);
        assertEquals(3, r.points(), "pinning fixes the window, not which records exist (D-F2)");
        assertView(1000, 1500, r.view());
    }

    @Test
    void theEchoAloneNoLongerMovesAZoomedChart_butARealRangeChangeStillDoes() throws IOException {
        Rig r = rig(INLINE);
        r.panel.chart().setViewWindow(1200L, 1700L);
        r.filter.setTimeRange(null, null);          // extendAbsMax's echo at full extent: unchanged (null, null)
        assertView(1200, 1700, r.view());           // D-F8: inert
        r.filter.setTimeRange(1000L, 1500L);        // a real slider move
        assertView(1000, 1500, r.view());
        r.filter.setTimeRange(1000L, 1500L);        // a verb re-sending the range the chart last applied
        r.panel.chart().setViewWindow(1100L, 1400L);
        r.filter.setTimeRange(1000L, 1500L);
        assertView(1100, 1400, r.view(), "an identical programmatic range is the same non-event (pass-3 F3)");
    }

    private static void assertView(double lo, double hi, double[] view, String why) {
        assertNotNull(view, why);
        assertEquals(lo, view[0], 0.5, why);
        assertEquals(hi, view[1], 0.5, why);
    }

    @Test
    void oneExtractionInFlight_andADefinitionChangeWinsTheMerge() throws IOException {
        Parking parking = new Parking();
        Rig r = rig(parking);
        // bind → addKeys → setMarkers each requested; D-F6 lets exactly one start and merges the rest
        assertEquals(1, parking.started, "one in flight while the definition was being built");
        parking.releaseOne();
        assertEquals(2, parking.started, "the merged follow-up ran once");
        parking.releaseOne();
        assertEquals(2, r.points());
        assertFalse(r.panel.isExtracting());

        r.panel.chart().setViewWindow(1200L, 1700L);   // a person zoomed into the middle
        Files.writeString(r.file, rec(3000, 3), StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        assertEquals(1, r.store.appendFrom(r.file));

        r.panel.onRecordsAppended(); r.panel.runPendingExtractionNow();     // tick 1 → starts (DATA)
        assertEquals(3, parking.started);
        r.panel.onRecordsAppended(); r.panel.runPendingExtractionNow();     // tick 2 → dirty, nothing new starts
        r.panel.onRecordsAppended(); r.panel.runPendingExtractionNow();     // tick 3 → still dirty
        assertEquals(3, parking.started, "a burst behind an in-flight extraction coalesces to one follow-up");

        r.panel.addKeys(List.of(new GraphKey("nodeB", "y")));               // a DEFINITION change while held
        parking.releaseOne();                                               // tick 1 lands (DATA) → view holds …
        assertView(1200, 1700, r.view());
        assertEquals(4, parking.started, "… and exactly one follow-up starts");
        parking.releaseOne();                                               // the follow-up lands as DEFINITION
        assertView(1000, 3000, r.view(), "DEFINITION wins the merge: the view resets to the filter window");
        assertEquals(4, parking.started);
        assertFalse(r.panel.isExtracting());
    }
}
