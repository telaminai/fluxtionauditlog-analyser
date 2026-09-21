package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.graph.*;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import javax.swing.SwingUtilities;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed regression: independent magnitudes, pin/filter/restore paths, no session-replay claim. */
class ChartAxisWindowTest {
    static final ExtractionRunner INLINE = new ExtractionRunner() {
        public <T> void run(Supplier<T> work, Consumer<T> ok, Consumer<Throwable> error) { ok.accept(work.get()); }
    };
    static double[] range(ChartPanel c, String side) {
        try {
            var lo = ChartPanel.class.getDeclaredField(side + "y0"); lo.setAccessible(true);
            var hi = ChartPanel.class.getDeclaredField(side + "y1"); hi.setAccessible(true);
            return new double[]{lo.getDouble(c), hi.getDouble(c)};
        } catch (Exception e) { throw new RuntimeException(e); }
    }
    static void separated(ChartPanel c) {
        assertArrayEquals(new double[]{9.5,20.5}, range(c,"v"), 0.001, "left window must exclude right-axis values");
        assertArrayEquals(new double[]{987500,1262500}, range(c,"r"), 0.001, "right window must fit its own values");
    }
    @Test void windowUsesEachAxisAndAnEmptySideDoesNotBorrowValues() {
        var c = new ChartPanel();
        var a = new Series("small"); a.add(1000,10); a.add(2000,20);
        var b = new Series("large"); b.add(1000,1000000); b.add(2000,1250000); b.add(3000,1500000);
        c.setSeries(List.of(a,b)); c.setAxes(new AxisAssignment(List.of("large")));
        c.setViewWindow(1000L,2000L); separated(c);
        c.setViewWindow(3000L,3000L);
        assertArrayEquals(new double[]{0,1},range(c,"v"));
        assertTrue(range(c,"r")[0] > 1400000);
        c.setViewWindow(4000L,5000L);
        assertArrayEquals(new double[]{0,1},range(c,"v")); assertArrayEquals(new double[]{0,1},range(c,"r"));
        c.setAxes(new AxisAssignment()); c.setViewWindow(1000L,2000L);
        assertTrue(range(c,"v")[1] > 1200000, "unassigned series deliberately share the left scale");
    }
    private static HeapLogStore log() {
        return new HeapLogStore("eventLogRecord:\n  event: Tick\n  logTime: 1000\n  nodeLogs:\n    - n: {small: 10, large: 1000000}\n---\n"
                + "eventLogRecord:\n  event: Tick\n  logTime: 2000\n  nodeLogs:\n    - n: {small: 20, large: 1250000}\n---\n"
                + "eventLogRecord:\n  event: Tick\n  logTime: 3000\n  nodeLogs:\n    - n: {small: 30, large: 1500000}\n---\n");
    }
    @Test void pinFilterRefreshAndSavedRestoreKeepSeparateScales() throws Exception {
        var tabs = new GraphTabs(); var filter = new FilterState(); var log = log();
        final List<GraphSpec>[] saved = new List[1];
        SwingUtilities.invokeAndWait(() -> {
            tabs.bind(log,filter); var p = tabs.addGraph("axes"); p.setExtractionRunner(INLINE);
            p.addKeys(List.of(new GraphKey("n","small"),new GraphKey("n","large")));
            p.setAxes(new AxisAssignment(List.of("n.large"))); p.pin(1000L,2000L);
            p.setGuides(List.of(new GraphSpec.GuideSpec(99999999,"guide",false)));
            p.setMarkers(List.of(new GraphSpec.MarkerSpec("marker","circle","n.small","n.large",null)));
            separated(p.chart());
            p.onRecordsAppended(); p.runPendingExtractionNow(); separated(p.chart());
            saved[0] = tabs.specs();
            p.unpin(); filter.setTimeRange(1000L,2000L); separated(p.chart());
            tabs.restore(saved[0]);
        });
        long deadline = System.nanoTime()+10_000_000_000L;
        boolean[] done = {false};
        while (!done[0] && System.nanoTime()<deadline) {
            SwingUtilities.invokeAndWait(() -> {
                var p=tabs.graphNamed("axes");
                done[0]=!p.isExtracting() && p.chart().plottedSeries().size()==2;
                if (done[0]) { assertTrue(p.isPinned()); separated(p.chart()); }
            });
            if (!done[0]) Thread.sleep(20);
        }
        assertTrue(done[0],"saved chart extraction must finish");
        SwingUtilities.invokeAndWait(tabs::unbind);
    }
}
