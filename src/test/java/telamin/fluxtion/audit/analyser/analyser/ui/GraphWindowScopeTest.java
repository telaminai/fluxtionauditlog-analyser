package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.GraphKey;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import javax.swing.SwingUtilities;
import java.util.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

/** Constructed regression: a saved pin restored on a disjoint log, not participant-session replay. */
class GraphWindowScopeTest {
    private static HeapLogStore log(long t) {
        return new HeapLogStore("eventLogRecord:\n  event: Tick\n  logTime: " + t
                + "\n  nodeLogs:\n    - n: {value: 10}\n---\n"
                + "eventLogRecord:\n  event: Tick\n  logTime: " + (t+1000)
                + "\n  nodeLogs:\n    - n: {value: 20}\n---\n");
    }
    @Test void restoredPinExplainsEmptyWindowAndFiltersWithoutChangingThePin() throws Exception {
        var tabs = new GraphTabs(); var f = new FilterState();
        SwingUtilities.invokeAndWait(() -> {
            tabs.bind(log(1000),f); var p=tabs.addGraph("scope");
            p.setExtractionRunner(ChartAxisWindowTest.INLINE);
            p.addKeys(List.of(new GraphKey("n","value"))); p.pin(1000L,2000L);
            assertEquals("none",p.scopeFacts().get("emptyReason"),"same-log pin is a positive control");
            var saved=tabs.specs(); tabs.bind(log(100000),f); tabs.restore(saved);
        });
        long deadline=System.nanoTime()+10_000_000_000L; boolean[] ready={false};
        while (!ready[0] && System.nanoTime()<deadline) {
            SwingUtilities.invokeAndWait(() -> ready[0]="complete".equals(tabs.graphNamed("scope").scopeFacts().get("extraction"))
                    && !tabs.graphNamed("scope").chart().plottedSeries().isEmpty());
            if (!ready[0]) Thread.sleep(20);
        }
        assertTrue(ready[0],"restored extraction completed");
        SwingUtilities.invokeAndWait(() -> {
            var p=tabs.graphNamed("scope"); p.setExtractionRunner(ChartAxisWindowTest.INLINE);
            var facts=p.scopeFacts();
            assertEquals("window-outside-data",facts.get("emptyReason"),"disjoint saved pin must explain emptiness");
            assertEquals(1000L,facts.get("pinFrom")); assertEquals(2000L,facts.get("pinTo"));
            assertEquals(0L,facts.get("pointsInWindow")); assertEquals(2L,facts.get("finiteSeriesPoints"));
            assertTrue(p.scopeText().contains("Pinned 1000 → 2000"));
            f.setDimensions(Set.of()); f.setText("no-match");
            assertEquals("pending",p.scopeFacts().get("extraction"));
            assertFalse(p.scopeFacts().containsKey("pointsInWindow"),"pending must not assert old counts");
            assertTrue(p.scopeText().contains("DIMENSION: [] · text: no-match"));
            p.runPendingExtractionNow();
            assertEquals("no-finite-series-points",p.scopeFacts().get("emptyReason"));
            p.unpin(); assertEquals(0L,p.scopeFacts().get("finiteSeriesPoints"),"clearing pin does not clear filters");
            f.setDimensions(null); f.setText(""); p.runPendingExtractionNow();
            assertEquals("none",p.scopeFacts().get("emptyReason"));
            assertEquals(2L,p.scopeFacts().get("pointsInWindow"));
            assertTrue(p.scopeText().contains("Follows time filter · DIMENSION: all · text: none"));
            p.setExtractionRunner(new ExtractionRunner() {
                public <T> void run(Supplier<T> work, Consumer<T> ok, Consumer<Throwable> fail) { fail.accept(new RuntimeException("constructed")); }
            });
            p.onRecordsAppended(); p.runPendingExtractionNow();
            assertEquals("failed",p.scopeFacts().get("extraction"));
            assertFalse(p.scopeFacts().containsKey("pointsInWindow"));
            assertTrue(p.scopeText().contains("extraction failed"));
            tabs.unbind();
        });
    }
}
