package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2 (spec §5.9, §10): the transcript's styles meet their contrast in BOTH themes, computed here; and colour is never
 * the only signal — with colour removed, every role and every mark is still told apart by its words.
 */
class TranscriptStyleTest {

    /** ★ Witness: give the muted note text the background colour's neighbour (#C8CDD3 on light) — 4.5:1 fails. */
    @Test
    @DisplayName("★ body text 4.5:1 and marks 3:1 against their background, for every role, in light and dark")
    void contrast() {
        for (boolean dark : new boolean[]{false, true}) {
            for (TranscriptStyle.Role role : TranscriptStyle.Role.values()) {
                double c = TranscriptStyle.contrast(TranscriptStyle.foreground(role, dark), TranscriptStyle.background(role, dark));
                assertTrue(c >= 4.5, role + (dark ? " dark " : " light ") + c);
            }
            for (TranscriptStyle.Mark m : TranscriptStyle.Mark.values()) {
                double c = TranscriptStyle.contrast(TranscriptStyle.mark(m, dark), TranscriptStyle.palette(dark).background());
                assertTrue(c >= 3.0, m + (dark ? " dark " : " light ") + c);
            }
            var p = TranscriptStyle.palette(dark);
            for (var pair : new java.awt.Color[][]{{p.link(), p.background()}, {p.codeText(), p.codeBackground()},
                    {p.chipText(), p.chipBackground()}, {p.youText(), p.youBand()}}) {
                assertTrue(TranscriptStyle.contrast(pair[0], pair[1]) >= 4.5, (dark ? "dark " : "light ") + pair[0]);
            }
            for (boolean strong : new boolean[]{false, true}) {
                assertTrue(TranscriptStyle.contrast(TranscriptStyle.fullPosture(strong, dark), p.background()) >= 3.0,
                        "the full-posture cue is visible: " + strong + " " + dark);
            }
        }
    }

    /** ★ Witness: a mark rendered by colour only (two marks with the same words) — the distinct-words assertion fails. */
    @Test
    @DisplayName("★ every mark has its words and its screen-reader words; every role its marker; all distinct")
    void neverColourAlone() {
        Set<String> words = new HashSet<>();
        Set<String> spoken = new HashSet<>();
        for (TranscriptStyle.Mark m : TranscriptStyle.Mark.values()) {
            assertFalse(m.words.isBlank(), m.name());
            assertFalse(m.spoken.isBlank(), m.name());
            assertTrue(words.add(m.words), "distinct words: " + m);
            assertTrue(spoken.add(m.spoken), "distinct spoken words: " + m);
        }
        assertEquals("You", TranscriptStyle.marker(TranscriptStyle.Role.YOU));
        assertEquals("Claude Code", TranscriptStyle.marker(TranscriptStyle.Role.CLAUDE));
        assertEquals(TranscriptStyle.Mark.OK, TranscriptStyle.Mark.of("✓"));
        assertEquals(TranscriptStyle.Mark.REFUSED, TranscriptStyle.Mark.of("refused"));
        assertEquals(TranscriptStyle.Mark.DENIED, TranscriptStyle.Mark.of("denied by the posture"));
    }

    @Test
    @DisplayName("the full-posture tokens differ between light and dark, and the strong cue from the standard one")
    void fullPostureTokens() {
        assertNotEquals(TranscriptStyle.fullPosture(false, false), TranscriptStyle.fullPosture(false, true));
        assertNotEquals(TranscriptStyle.fullPosture(true, false), TranscriptStyle.fullPosture(true, true));
        assertNotEquals(TranscriptStyle.fullPosture(true, false), TranscriptStyle.fullPosture(false, false));
    }
}
