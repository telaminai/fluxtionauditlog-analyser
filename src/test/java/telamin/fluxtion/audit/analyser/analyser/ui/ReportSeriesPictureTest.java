package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import javax.swing.SwingUtilities;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent review's gap table, M68.2 (set 13, C): a report's series section printed NOT RENDERED because PDF
 * assembly for it did not exist. It is extracted now, synchronously, under the current filter, and drawn on a detached
 * chart at page size — the same extraction and the same chart the Graph tab uses.
 */
class ReportSeriesPictureTest {

    private static String rec(long t, double v) {
        return "eventLogRecord:\n  event: Tick\n  logTime: " + t + "\n  nodeLogs:\n    - rootNode: {v: " + v + "}\n---\n";
    }

    private static final HeapLogStore STORE = new HeapLogStore(rec(1000, 1) + rec(2000, 3) + rec(3000, 2));

    @Test
    @DisplayName("C: a key's series is drawn at the page size, and its caption counts its points")
    void aKeySeriesIsDrawn() throws Exception {
        // witness: ReportSeriesPicture.of returning no picture
        var out = new AtomicReference<ReportSeriesPicture.Result>();
        SwingUtilities.invokeAndWait(() -> out.set(ReportSeriesPicture.of(STORE,
                Map.of("key", "rootNode.v"), 1200, 600)));
        assertNull(out.get().problem(), out.get().problem());
        assertNotNull(out.get().image(), "C: drawn");
        assertEquals(1200, out.get().image().getWidth());
        assertTrue(out.get().caption().contains("3 points"), out.get().caption());
    }

    @Test
    @DisplayName("C: an expression is drawn the same way; a call naming neither says why it cannot be")
    void anExpressionIsDrawnAndANamelessCallIsNot() throws Exception {
        var expr = new AtomicReference<ReportSeriesPicture.Result>();
        var none = new AtomicReference<ReportSeriesPicture.Result>();
        SwingUtilities.invokeAndWait(() -> {
            expr.set(ReportSeriesPicture.of(STORE, Map.of("expr", "rootNode.v * 2"), 1200, 600));
            none.set(ReportSeriesPicture.of(STORE, Map.of(), 1200, 600));
        });
        assertNotNull(expr.get().image(), String.valueOf(expr.get().problem()));
        assertTrue(expr.get().caption().contains("3 points"), expr.get().caption());
        assertNull(none.get().image());
        assertNotNull(none.get().problem());
    }
}
