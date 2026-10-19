package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;

import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;

/**
 * Usability scorecard (2026-10-18): shown, the Event types checklist's three-row floor pushed the sample's breach
 * record below the fold at 1200x800, and right-click Ask went from 4 acts to 5. The checklist therefore starts
 * COLLAPSED on a fresh install. That is safe only because a type filter stays visible without the list — the
 * toggle's badge and the hidden line — so this test also shows that a filter set through the action socket leaves
 * the list collapsed and is still stated, and that a person who shows the list keeps it shown across a restart.
 *
 * <p>Mutation witness: {@link AppConfig#eventFilterCollapsed} defaulting to {@code false} again fails
 * {@link #aFreshInstallStartsWithTheListCollapsed} on its first assertion.
 */
class EventTypesStartCollapsedFrameTest {

    @Test
    void aFreshInstallStartsWithTheListCollapsed(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path log = Files.copy(Path.of("src/test/resources/sample.yml"), tmp.resolve("run.yml"));
        assertFalse(Files.exists(tmp.resolve("home/.fluxtion-analyser/config")), "an isolated, fresh home");
        try (var f = open(tmp, log)) {
            onEdt(() -> {
                assertFalse(panel(f).isVisible(), "a fresh install starts with the checklist collapsed");
                JToggleButton t = toggle(f);
                assertFalse(t.isSelected());
                assertTrue(t.getText().startsWith("▸"), "the arrow says the list is hidden: " + t.getText());
                assertNull(t.getIcon(), "no badge while every type is selected");
            });

            // a type filter through the real action socket: the badge and the hidden line state it, the list stays shut
            onEdt(() -> {
                ((AppConfig) field(f.frame, "config")).assistantActionsRest = true;
                ExternalRestWithHostedSessionFrameTest.invoke(f.frame, "reportRestSwitch");
            });
            Path wk = Path.of(System.getProperty("user.home"), ".fluxtion-analyser/rest-endpoint");
            assertTrue(Files.exists(wk), "REST on: the well-known endpoint file");
            var ep = new telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile(wk).read();
            String type = typeWithTotal(f, 3);
            var reply = ExternalRestWithHostedSessionFrameTest.post(java.net.http.HttpClient.newHttpClient(), ep.url(),
                    "{\"action\":\"filter\",\"params\":{\"dimensions\":[\"" + type + "\"]}}", ep.token(), null);
            assertEquals(200, reply.statusCode(), reply.body());
            settle();
            onEdt(() -> {
                assertFalse(panel(f).isVisible(), "a filter applied through the socket does not expand the list");
                JToggleButton t = toggle(f);
                assertTrue(t.getText().startsWith("▸"), t.getText());
                assertTrue(t.getIcon() instanceof RecordsFilterBar.Badge, "the badge states the type filter: " + t.getIcon());
                assertEquals("1 of 7", ((RecordsFilterBar.Badge) t.getIcon()).text());
                RecordsFilterBar line = (RecordsFilterBar) field(f.frame, "hiddenLine");
                assertTrue(line.isShowing(), "the hidden line shows under the Records title");
                assertEquals("18 hidden by event types", line.sentence());
            });

            // the person shows it once
            onEdt(() -> toggle(f).doClick());
            settle();
            onEdt(() -> assertTrue(panel(f).isVisible()));
        }
        assertTrue(Files.readString(tmp.resolve("home/.fluxtion-analyser/config")).contains("eventFilterCollapsed=false"),
                "the choice to show it was saved");

        // ...and after a restart it is still shown: a saved choice beats the default
        try (var f = open(tmp, log)) {
            onEdt(() -> {
                assertTrue(panel(f).isVisible(), "shown once, it stays shown after a restart");
                assertTrue(toggle(f).isSelected());
                assertTrue(toggle(f).getText().startsWith("▾"), toggle(f).getText());
            });
        }
    }

    // ---------------------------------------------------------------------------------------------

    private static AsyncOpenInterleavingFrameTest.Frame open(Path tmp, Path log) throws Exception {
        var f = new AsyncOpenInterleavingFrameTest.Frame(tmp);
        f.dialogs.stop();
        onEdt(() -> { f.frame.setSize(1200, 800); f.frame.setVisible(true); });
        assertTrue(f.ex.render("open", Map.of("log", log.toString())).ok());
        settle();
        return f;
    }

    private static EventFilterPanel panel(AsyncOpenInterleavingFrameTest.Frame f) {
        return (EventFilterPanel) field(f.frame, "eventFilterPanel");
    }

    private static JToggleButton toggle(AsyncOpenInterleavingFrameTest.Frame f) {
        return (JToggleButton) field(f.frame, "eventTypesToggle");
    }

    private static String typeWithTotal(AsyncOpenInterleavingFrameTest.Frame f, int total) throws Exception {
        AtomicReference<String> out = new AtomicReference<>();
        onEdt(() -> out.set(((SessionDriver) field(f.frame, "session")).snapshot().recordsView().types().stream()
                .filter(t -> t.total() == total).findFirst().orElseThrow().type()));
        return out.get();
    }

    private static void settle() throws Exception {
        for (int i = 0; i < 5; i++) {
            Thread.sleep(150);
            SwingUtilities.invokeAndWait(() -> { });
        }
    }
}
