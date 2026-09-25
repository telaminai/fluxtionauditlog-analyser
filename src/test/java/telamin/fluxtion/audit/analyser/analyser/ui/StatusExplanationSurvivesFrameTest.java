package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * R12-6: the two fixes that shipped without one. My commit claimed every fix had a regression test; two
 * did not, and the reviewer checked the diff rather than the claim.
 *
 * <p><b>R12-2</b> — an explanation on the status bar was erased about a second later while Follow was on,
 * because a follow tick rewrote the label even when nothing had arrived. The earlier frame test missed it
 * by reading the label once, with no log and Follow off: it asserted the message appears, never that it
 * stays.
 *
 * <p><b>R12-3</b> — a report's chart link called {@code selectGraph} and ignored its result, so a link to
 * a chart that is saved but closed brought the Graph tab forward and did nothing.
 */
class StatusExplanationSurvivesFrameTest {

    @TempDir Path tmp;

    private static GraphSpec chart(String name) {
        return new GraphSpec(name, List.of("node" + (char) 1 + "value"), List.of(), null, null, null,
                "why it exists", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                "step", false);   // SAVED BUT CLOSED — the case R12-3 is about
    }

    private Path writeLog() throws Exception {
        return Files.writeString(tmp.resolve("sample.yml"),
                "---\neventLogRecord:\n  logTime: 1000\n  event: Tick\n  nodeLogs:\n    - node: { value: 1}\n---\n");
    }

    private static JLabel status(MainFrame frame) {
        return (JLabel) field(frame, "status");
    }

    /**
     * R12-2. Follow on, nothing arriving, and the explanation must still be readable afterwards. The wait
     * is real elapsed time because the defect was a timer: a tick with nothing new used to overwrite the
     * label roughly a second later.
     */
    @Test
    void anExplanationSurvivesAnIdleFollowTick() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = writeLog();

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
            for (int i = 0; i < 400 && f.status().startsWith("Loading "); i++) Thread.sleep(25);
            assertTrue(f.ex.render("open", Map.of("follow", true)).ok(), "Follow must be on for this test");

            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                // the same call the Project panel's Open makes when it cannot act
                invokeSay(f.frame, "No chart called \"Ghost\" is open, and the project has no saved "
                        + "definition for it.");
                assertTrue(status(f.frame).getText().contains("Ghost"), "the explanation is shown");
            });

            Thread.sleep(2500);   // longer than a follow tick; nothing is arriving on this log

            onEdt(() -> assertTrue(status(f.frame).getText().contains("Ghost"),
                    "an explanation must survive an idle follow tick — a tick with nothing new carries no "
                            + "news and must not erase news someone is reading. Saw: "
                            + status(f.frame).getText()));
        }
    }

    /**
     * R12-3. A report's chart link, pointed at a chart that is saved but closed. Since closing a chart
     * keeps its definition, this is the ordinary case, not an edge one.
     */
    @Test
    void aReportLinkToAClosedChartOpensIt() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = writeLog();

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
            for (int i = 0; i < 400 && f.status().startsWith("Loading "); i++) Thread.sleep(25);

            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                AppConfig config = (AppConfig) field(f.frame, "config");
                config.savedGraphs.clear();
                config.savedGraphs.add(chart("Closed chart"));

                GraphTabs tabs = (GraphTabs) field(f.frame, "graphTabs");
                assertNull(tabs.graphNamed("Closed chart"), "it starts closed, as a saved definition");

                invokeRevealGraph(f.frame, "Closed chart");

                assertNotNull(tabs.graphNamed("Closed chart"),
                        "a report link to a closed chart must open it — bringing the Graph tab forward and "
                                + "doing nothing is the silent failure this PR is about");
                assertEquals("Closed chart", tabs.selectedGraphName());
            });
        }
    }

    /** And a link to a chart that exists nowhere must say so rather than do nothing. */
    @Test
    void aReportLinkToAChartThatIsGoneSaysSo() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        Path log = writeLog();

        try (AsyncOpenInterleavingFrameTest.Frame f = new AsyncOpenInterleavingFrameTest.Frame(tmp)) {
            assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
            for (int i = 0; i < 400 && f.status().startsWith("Loading "); i++) Thread.sleep(25);

            onEdt(() -> {
                f.frame.setSize(1300, 850);
                f.frame.setVisible(true);
                ((AppConfig) field(f.frame, "config")).savedGraphs.clear();

                invokeRevealGraph(f.frame, "Vanished");

                assertTrue(status(f.frame).getText().contains("Vanished"),
                        "it must name the chart it could not find: " + status(f.frame).getText());
            });
        }
    }

    private static void invokeSay(MainFrame frame, String message) {
        try {
            var m = MainFrame.class.getDeclaredMethod("sayToStatus", String.class);
            m.setAccessible(true);
            m.invoke(frame, message);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }

    private static void invokeRevealGraph(MainFrame frame, String name) {
        try {
            var m = MainFrame.class.getDeclaredMethod("revealGraphByName", String.class);
            m.setAccessible(true);
            m.invoke(frame, name);
        } catch (ReflectiveOperationException e) { throw new RuntimeException(e); }
    }
}
