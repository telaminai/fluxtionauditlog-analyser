package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.awaitLoaded;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.field;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.onEdt;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.render;

/**
 * M69 S3 (spec-spotlight-walks.md §3.7, §3.8; W-A2, W-A3, W-A12) — on a REAL frame: the right-click save menu keeps
 * the spotlight and saves exactly what is lit; the strip's ◀ ▶ step through the walk with real presses; a press
 * outside the strip ends it, and it resumes where it was left; an external view change ends it, while the walk's own
 * view changes do not. Every transition is the session's: this test reads the published snapshot.
 */
class WalkPlaybackFrameTest {

    private static final String GRAPH = "src/test/resources/topology/demo-quote-processor.graphml";
    private static final String LOG = "src/main/resources/demo/demo-quote-series.yaml";

    static WalkPlaybackState walk(AsyncOpenInterleavingFrameTest.Frame f) {
        return ((SessionDriver) field(f.frame, "session")).snapshot().walkPlayback();
    }

    static void await(String what, BooleanSupplier ok) throws Exception {
        long deadline = System.currentTimeMillis() + 8_000;
        AtomicReference<Boolean> done = new AtomicReference<>(false);
        while (System.currentTimeMillis() < deadline) {
            onEdt(() -> done.set(ok.getAsBoolean()));
            if (done.get()) return;
            Thread.sleep(50);
        }
        fail("timed out waiting for: " + what);
    }

    private static void press(SpotlightOverlay overlay, Point at, int button) throws Exception {
        onEdt(() -> {
            long when = System.currentTimeMillis();
            boolean popup = button == MouseEvent.BUTTON3;
            int mask = popup ? MouseEvent.BUTTON3_DOWN_MASK : MouseEvent.BUTTON1_DOWN_MASK;
            overlay.dispatchEvent(new MouseEvent(overlay, MouseEvent.MOUSE_PRESSED, when, mask, at.x, at.y, 1, popup, button));
            overlay.dispatchEvent(new MouseEvent(overlay, MouseEvent.MOUSE_RELEASED, when + 1, 0, at.x, at.y, 1, false, button));
        });
    }

    /** A frame with the demo graph and log open, a walk-sized window, and a name prompt the test answers. */
    static AsyncOpenInterleavingFrameTest.Frame opened(Path tmp) throws Exception {
        var f = new AsyncOpenInterleavingFrameTest.Frame(tmp);
        onEdt(() -> {
            f.frame.setSize(1300, 850);
            f.frame.setVisible(true);
            f.frame.validate();
            f.frame.walkNamePrompt = note -> "tour";
        });
        onEdt(() -> render(f.ex, "open", Map.of("graphml", Path.of(GRAPH).toAbsolutePath().toString())));
        onEdt(() -> render(f.ex, "open", Map.of("log", Path.of(LOG).toAbsolutePath().toString())));
        awaitLoaded(f.ex);
        Thread.sleep(300);
        return f;
    }

    /** Light one target through the ordinary verb, then save it: a new walk, or a step appended to "tour". */
    static void lightAndSave(AsyncOpenInterleavingFrameTest.Frame f, SpotlightOverlay overlay, String target,
                                     String caption, boolean appendToTour) throws Exception {
        onEdt(() -> render(f.ex, "spotlight", Map.of("target", target, "caption", caption)));
        await("the target is lit", overlay::isLit);
        press(overlay, new Point(40, 40), MouseEvent.BUTTON3);
        await("the save menu is shown", () -> f.frame.lastWalkMenu != null && f.frame.lastWalkMenu.isVisible());
        assertTrue(overlay.isLit(), "W-A12: a right-click opens the menu and KEEPS the spotlight");
        onEdt(() -> {
            var menu = f.frame.lastWalkMenu;
            if (!appendToTour) {
                ((javax.swing.JMenuItem) menu.getComponent(0)).doClick();         // Save as new walk…
            } else {
                var addTo = (javax.swing.JMenu) menu.getComponent(1);             // Add to walk ▸ tour
                ((javax.swing.JMenuItem) addTo.getMenuComponent(0)).doClick();
            }
            menu.setVisible(false);
        });
    }

    @Test
    @DisplayName("W-A12: right-click saves exactly what is lit, keeping the spotlight; the menu's closing press is swallowed")
    void theSaveMenuSavesWhatIsLit(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            lightAndSave(f, overlay, "topology:node:priceListener", "every price arrives here first", false);
            AppConfig config = (AppConfig) field(f.frame, "config");
            WalkSpec tour = config.walks.stream().filter(w -> w.name().equals("tour")).findFirst().orElseThrow();
            assertEquals(1, tour.steps().size());
            WalkSpec.Target t = tour.steps().get(0).targets().get(0);
            assertEquals("topology:node:priceListener", t.target());
            assertEquals("every price arrives here first", t.caption());
            assertEquals("graph", t.basis().kind());
            assertFalse(t.basis().digest().isBlank(), "a file graph has a digest, and the walk bound itself to it");
            assertEquals(WalkSpec.AUTHOR_PERSON, tour.author(), "saved by a person, and it says so — never 'you'");
            assertNotNull(tour.steps().get(0).view().filter(), "the view's filter is stated, complete");

            // the press that closes the menu carries the menu's close time: it must not put the spotlight out
            onEdt(() -> render(f.ex, "spotlight", Map.of("target", "tab:topology")));
            await("lit again", overlay::isLit);
            onEdt(() -> overlay.swallowPressesUntil(Long.MAX_VALUE - 1));
            press(overlay, new Point(40, 40), MouseEvent.BUTTON1);
            onEdt(() -> assertTrue(overlay.isLit(), "the menu's own dismissal wins, and the spotlight stays"));
            onEdt(() -> overlay.swallowPressesUntil(Long.MIN_VALUE));
        }
    }

    @Test
    @DisplayName("W-A2/W-A3: ◀ ▶ step for real; an outside press ends the walk; it resumes; an external view change ends it")
    void theStripStepsAndEnds(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless());
        try (var f = opened(tmp)) {
            SpotlightOverlay overlay = (SpotlightOverlay) field(f.frame, "spotlight");
            lightAndSave(f, overlay, "topology:node:priceListener", "step one", false);
            lightAndSave(f, overlay, "topology:node:quotePublisher", "step two", true);
            onEdt(() -> render(f.ex, "spotlight", Map.of("clear", true)));

            onEdt(() -> assertNull(f.frame.playWalk("tour", 0, "test")));
            await("step 1 shown", () -> walk(f).showing() && walk(f).step() == 0 && "SHOWN".equals(walk(f).phase()));
            await("step 1 lit", () -> overlay.lit().stream().anyMatch(l -> l.target().equals("topology:node:priceListener")));
            assertNotNull(overlay.strip(), "the strip is shown while a walk shows");

            AtomicReference<Point> next = new AtomicReference<>();
            onEdt(() -> next.set(overlay.stripControlCentre(SpotlightOverlay.StripControl.NEXT)));
            press(overlay, next.get(), MouseEvent.BUTTON1);
            await("step 2 shown", () -> walk(f).step() == 1 && "SHOWN".equals(walk(f).phase()));
            await("step 2 lit", () -> overlay.lit().stream().anyMatch(l -> l.target().equals("topology:node:quotePublisher")));
            assertTrue(walk(f).showing(), "W-A3: the walk's own view change and relight did not end it");

            AtomicReference<Point> back = new AtomicReference<>();
            onEdt(() -> back.set(overlay.stripControlCentre(SpotlightOverlay.StripControl.BACK)));
            press(overlay, back.get(), MouseEvent.BUTTON1);
            await("back on step 1", () -> walk(f).step() == 0 && "SHOWN".equals(walk(f).phase()));

            press(overlay, new Point(30, 30), MouseEvent.BUTTON1);                 // outside the strip
            await("an outside press ends the walk", () -> !walk(f).showing());
            onEdt(() -> assertNull(overlay.strip(), "and the strip goes with it"));

            onEdt(() -> assertNull(f.frame.playWalk("tour", -1, "resume")));
            await("resumed where it was left", () -> walk(f).showing() && walk(f).step() == 0);

            onEdt(() -> render(f.ex, "filter", Map.of("text", "zzz")));            // a view change from OUTSIDE the walk
            await("an external view change ends the walk", () -> !walk(f).showing());
        }
    }
}
