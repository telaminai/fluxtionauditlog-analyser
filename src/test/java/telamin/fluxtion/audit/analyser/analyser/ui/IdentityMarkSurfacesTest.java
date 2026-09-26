package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.7 (owner, Q4 2026-09-26): the charts and the detail pane state the file-identity verdict the table already states
 * (M68.5). The charts gate G14, because G14's pass condition lands on them: an unmarked chart beside a marked table
 * would let a run pass on content the session already knows is superseded.
 */
class IdentityMarkSurfacesTest {

    @Test
    @DisplayName("the charts and the detail pane speak exactly when the table does, and carry the reason")
    void theSurfacesShareTheTablesRule() {
        for (String verdict : new String[]{"UNVERIFIED", "REPLACEMENT"}) {
            String reason = "the file behind this log was rewritten in place";
            assertNotNull(LogTablePanel.identityBannerText(verdict, reason), "control: the table speaks for " + verdict);
            String chart = GraphTabs.identityBannerText(verdict, reason);
            String detail = DetailPanel.identityBannerText(verdict, reason);
            assertNotNull(chart, "the charts must state a " + verdict + " verdict");
            assertNotNull(detail, "the detail pane must state a " + verdict + " verdict");
            assertTrue(chart.contains(reason) && chart.contains("charts"), "the chart note carries the reason: " + chart);
            assertTrue(detail.contains(reason) && detail.contains("record"), "the detail note carries the reason: " + detail);
        }
        for (String verdict : new String[]{null, "VERIFIED", "REOPENED"}) {
            assertNull(GraphTabs.identityBannerText(verdict, "r"), "the charts say nothing for " + verdict);
            assertNull(DetailPanel.identityBannerText(verdict, "r"), "the detail pane says nothing for " + verdict);
        }
    }

    @Test
    @DisplayName("each banner is shown and cleared on its own panel")
    void theBannersAreOnThePanels() throws Exception {
        var chartShown = new AtomicReference<String>();
        var chartCleared = new AtomicReference<String>("not cleared");
        var detailShown = new AtomicReference<String>();
        var detailCleared = new AtomicReference<String>("not cleared");
        SwingUtilities.invokeAndWait(() -> {
            var charts = new GraphTabs();
            assertNull(charts.identityNote(), "hidden until something is observed");
            charts.setIdentityNote("⚠ changed");
            chartShown.set(charts.identityNote());
            charts.setIdentityNote(null);
            chartCleared.set(charts.identityNote());
            var detail = new DetailPanel();
            assertNull(detail.identityNote(), "hidden until something is observed");
            detail.setIdentityNote("⚠ changed");
            detailShown.set(detail.identityNote());
            detail.setIdentityNote(null);
            detailCleared.set(detail.identityNote());
        });
        assertEquals("⚠ changed", chartShown.get(), "the chart banner must show its note");
        assertNull(chartCleared.get(), "the chart banner must clear");
        assertEquals("⚠ changed", detailShown.get(), "the detail banner must show its note");
        assertNull(detailCleared.get(), "the detail banner must clear");
    }

    @Test
    @DisplayName("the frame renders all three from the one snapshot, in the one place")
    void theFrameRendersThemFromTheSnapshot() throws Exception {
        String frame = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        int at = frame.indexOf("private void onSessionSnapshot(");
        String body = frame.substring(at, frame.indexOf("\n    }\n", at));
        assertTrue(body.contains("graphTabs.setIdentityNote(GraphTabs.identityBannerText(next.logIdentity(), next.logIdentityReason()))"),
                "the charts' banner comes from the snapshot: " + body);
        assertTrue(body.contains("detailPanel.setIdentityNote(DetailPanel.identityBannerText(next.logIdentity(), next.logIdentityReason()))"),
                "the detail pane's banner comes from the snapshot: " + body);
    }
}
