package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.report.ReportSpec;
import javax.swing.JList;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.ChartLifecycleReviewFrameTest.*;

/** Actual row buttons and modal Cancel, using the shared loaded-frame fixture. */
class ProjectPanelChartLifecycleFrameTest {
    @TempDir Path tmp;
    private ChartLifecycleReviewFrameTest fixtureOwner() {
        var owner = new ChartLifecycleReviewFrameTest();
        owner.tmp = tmp;
        return owner;
    }

    @Test void openOnASavedChartRowOpensThatChartInTheFrame() throws Exception {
        try (var f = fixtureOwner().new Fixture(chart("Prices", true), chart("Tick rate", false))) {
            edt(() -> {
                assertNull(f.tabs.graphNamed("Tick rate"));
                f.openRow("Tick rate");
                GraphPanel opened = f.tabs.graphNamed("Tick rate");
                assertNotNull(opened, "the named closed chart must actually open");
                assertEquals("Tick rate", f.tabs.selectedGraphName());
                opened.addSpecs(List.of("node\u0001later"));
                var series = opened.seriesSpecs();
                f.openRow("Prices");
                f.openRow("Tick rate");
                assertSame(opened, f.tabs.graphNamed("Tick rate"), "an open chart is selected, not rebuilt");
                assertEquals(series, opened.seriesSpecs(), "reveal keeps edits made after restore");
                return null;
            });
        }
    }

    @Test void openOnEachReportRowRevealsTheReportsTab() throws Exception {
        try (var f = fixtureOwner().new Fixture(chart("Prices", true))) {
            edt(() -> {
                f.config.reports.add(new ReportSpec("first-id", "First title", null, null, null, null, List.of()));
                f.config.reports.add(new ReportSpec("second-id", "Second title", null, null, null, null, List.of()));
                ReportsPanel reports = (ReportsPanel) field(f.frame, "reportsPanel");
                reports.refresh();
                JList<?> list = (JList<?>) field(reports, "list");
                f.openRow("Second title");
                assertEquals("second-id", list.getSelectedValue(), "the second row selects its report identity");
                f.openRow("First title");
                assertEquals("first-id", list.getSelectedValue(), "the first row selects its report identity");
                return null;
            });
        }
    }

    @Test void cancellingDeleteInARealFrameChangesNothing() throws Exception {
        try (var f = fixtureOwner().new Fixture(chart("Keep me", true))) {
            edt(() -> {
                GraphPanel before = f.tabs.graphNamed("Keep me");
                var definitions = List.copyOf(f.config.savedGraphs);
                byte[] bytes = java.nio.file.Files.readAllBytes(f.profile);
                f.delete(false); // asserts that the real modal question was found and Cancel was pressed
                f.flush();
                assertSame(before, f.tabs.graphNamed("Keep me"), "Cancel keeps the actual bound chart");
                assertEquals(definitions, f.config.savedGraphs);
                assertArrayEquals(bytes, java.nio.file.Files.readAllBytes(f.profile));
                return null;
            });
        }
    }
}
