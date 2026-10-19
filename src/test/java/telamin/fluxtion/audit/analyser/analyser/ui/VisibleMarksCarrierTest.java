package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Review PR #150 R2: what is seen is what is copied — so a variation selector or an invisible filler in Claude's words is
 * a mark too, except the one U+FE0F an emoji is drawn with.
 */
class VisibleMarksCarrierTest {

    /** ★ Witness: drop the carrier clause from VisibleMarks.hiddenAt — the selectors stay invisible and this fails. */
    @Test
    @DisplayName("★ selectors and fillers are marks; the emoji's own U+FE0F is not")
    void carriersAreMarks() {
        assertEquals("run⟨U+E0162⟩⟨U+FE01⟩", VisibleMarks.show("run\uDB40\uDD62\uFE01"));
        assertEquals("a⟨U+3164⟩b", VisibleMarks.show("a\u3164b"));
        assertEquals("\u26A0\uFE0F ok", VisibleMarks.show("\u26A0\uFE0F ok"));
        assertEquals("\u26A0⟨U+FE0F⟩⟨U+FE0F⟩", VisibleMarks.show("\u26A0\uFE0F\uFE0F"), "a run is marked whole");
        assertEquals(2, VisibleMarks.count("run\uDB40\uDD62\uFE01\u26A0\uFE0F"));
    }
}
