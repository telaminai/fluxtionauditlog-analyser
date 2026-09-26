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
            assertTrue(chart.contains(reason) && chart.toLowerCase().contains("charts"), "the chart note carries the reason: " + chart);
            assertTrue(detail.contains(reason) && detail.toLowerCase().contains("record"), "the detail note carries the reason: " + detail);
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

    @Test
    @DisplayName("review O1: the verdict and the recovery lead each note, so a narrow pane still reads a whole sentence")
    void theVerdictLeadsTheNote() {
        String reason = "a long reason that a narrow pane would otherwise clip before the verdict was ever shown";
        String chart = GraphTabs.identityBannerText("UNVERIFIED", reason);
        String detail = DetailPanel.identityBannerText("UNVERIFIED", reason);
        assertTrue(chart.startsWith("⚠ Charts not verified against the file on disk — reopen the log"),
                "the chart note leads with its verdict: " + chart);
        assertTrue(detail.startsWith("⚠ Record not verified against the file on disk — reopen the log"),
                "the detail note leads with its verdict: " + detail);
        assertTrue(chart.indexOf(reason) > chart.indexOf("not verified"), "the reason follows the verdict: " + chart);
    }

    @Test
    @DisplayName("review O2: the warning colour follows a theme change, on both banners")
    void theWarningColourFollowsTheTheme() throws Exception {
        var previous = javax.swing.UIManager.getLookAndFeel();
        var colours = new AtomicReference<String>();
        try {
            SwingUtilities.invokeAndWait(() -> {
                ThemeManager.apply("Light");
                var charts = new GraphTabs();
                var detail = new DetailPanel();
                charts.setIdentityNote("⚠ changed");
                detail.setIdentityNote("⚠ changed");
                String light = Integer.toHexString(UiTheme.warnForeground().getRGB());
                ThemeManager.apply("Dark");
                SwingUtilities.updateComponentTreeUI(charts);
                SwingUtilities.updateComponentTreeUI(detail);
                String dark = Integer.toHexString(UiTheme.warnForeground().getRGB());
                colours.set(light + " " + dark + " "
                        + Integer.toHexString(((javax.swing.JLabel) AsyncOpenInterleavingFrameTest.field(charts, "identityBanner")).getForeground().getRGB()) + " "
                        + Integer.toHexString(((javax.swing.JLabel) AsyncOpenInterleavingFrameTest.field(detail, "identityBanner")).getForeground().getRGB()));
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                try { javax.swing.UIManager.setLookAndFeel(previous); } catch (Exception e) { throw new RuntimeException(e); }
            });
        }
        String[] c = colours.get().split(" ");
        assertNotEquals(c[0], c[1], "control: the two themes have different warning colours");
        assertEquals(c[1], c[2], "the chart banner must take the dark warning colour after the switch");
        assertEquals(c[1], c[3], "the detail banner must take the dark warning colour after the switch");
    }
}
