package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** OA-2 (spec §3): the assistant window is restored only within today's usable screens; a removed monitor never strands it. */
class AssistantPlacementTest {

    static final Rectangle LAPTOP = new Rectangle(0, 25, 1440, 875);
    static final Rectangle OWNER = new Rectangle(40, 60, 1200, 800);

    @Test
    @DisplayName("a remembered place on a monitor that is gone falls back beside the analyser, on its screen")
    void aRemovedMonitorDoesNotStrandTheWindow() {
        Rectangle wanted = new Rectangle(3000, 200, 520, 720);          // was on a second monitor
        Rectangle placed = MainFrame.placeWithin(wanted, List.of(LAPTOP), OWNER);
        assertTrue(LAPTOP.contains(placed), "entirely on the remaining screen: " + placed);
        assertEquals(520, placed.width);
    }

    @Test
    @DisplayName("a remembered place still on a screen is kept, and shrunk to fit the usable area")
    void aVisiblePlaceIsKept() {
        Rectangle wanted = new Rectangle(900, 100, 520, 2000);
        Rectangle placed = MainFrame.placeWithin(wanted, List.of(LAPTOP), OWNER);
        assertEquals(900, placed.x);
        assertEquals(LAPTOP.height, placed.height, "shrunk to the usable height");
        assertTrue(LAPTOP.contains(placed), placed.toString());
    }

    @Test
    @DisplayName("with two screens, a place on the second is kept while that monitor exists")
    void aSecondScreenIsUsedWhenPresent() {
        Rectangle second = new Rectangle(1440, 0, 1920, 1080);
        Rectangle wanted = new Rectangle(2000, 200, 520, 720);
        assertEquals(wanted, MainFrame.placeWithin(wanted, List.of(LAPTOP, second), OWNER));
    }

    @Test
    @DisplayName("never placed before: beside the analyser")
    void unsetIsBesideTheAnalyser() {
        Rectangle placed = MainFrame.placeWithin(new Rectangle(-1, -1, 520, 720), List.of(LAPTOP), OWNER);
        assertTrue(LAPTOP.contains(placed), placed.toString());
    }

    @Test
    @DisplayName("OA-2: the overlay lets presses inside the docked assistant through, and keeps every other press")
    void theDockedAssistantsPressesAreItsOwn() {
        SpotlightOverlay overlay = new SpotlightOverlay(() -> { });
        overlay.setSize(1200, 800);
        overlay.setPassThrough(() -> new Rectangle(800, 100, 400, 600));
        assertFalse(overlay.contains(900, 300), "inside the docked assistant: routed to it, not to the overlay");
        assertTrue(overlay.contains(100, 300), "anywhere else: still the overlay's, so an outside click ends a walk");
        overlay.setPassThrough(() -> null);
        assertTrue(overlay.contains(900, 300), "undocked or hidden: no exception");
    }
}
