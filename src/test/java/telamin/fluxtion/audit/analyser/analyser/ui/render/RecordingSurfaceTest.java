package telamin.fluxtion.audit.analyser.analyser.ui.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.*;

/** PR #53 review: the recorder's own fidelity — a test built on it is only as good as what it records. */
class RecordingSurfaceTest {

    @Test
    @DisplayName("A horizontal line crossing text overlaps it — a 1px line has area")
    void aLineCrossingTextOverlapsIt() {
        var s = new RecordingSurface();
        s.drawString("label", 10, 20);
        s.drawLine(0, 15, 100, 15);
        var text = s.texts().get(0);
        var line = s.lines().get(0);
        assertTrue(line.bounds().intersects(text.bounds()),
                "zero-area bounds never intersect anything, so an overlap assertion against a line passed vacuously");
    }

    @Test
    @DisplayName("Each mark records the clip in force when it was drawn")
    void eachMarkKnowsItsClip() {
        var s = new RecordingSurface();
        s.drawLine(0, 0, 5, 0);
        s.clip(1, 2, 3, 4);
        s.drawLine(0, 1, 5, 1);
        s.clearClip();
        assertNull(s.clipOf(s.lines().get(0)));
        assertEquals(new Rectangle(1, 2, 3, 4), s.clipOf(s.lines().get(1)));
        assertNull(s.clip(), "after a render clears its clip, clip() is null — which is why clipOf exists");
    }
}
