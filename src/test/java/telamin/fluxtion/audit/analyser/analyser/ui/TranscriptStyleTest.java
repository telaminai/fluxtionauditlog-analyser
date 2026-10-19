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
        assertEquals(TranscriptStyle.Mark.DENIED, TranscriptStyle.Mark.of("denied by you"));
    }

    @Test
    @DisplayName("the full-posture tokens differ between light and dark, and the strong cue from the standard one")
    void fullPostureTokens() {
        assertNotEquals(TranscriptStyle.fullPosture(false, false), TranscriptStyle.fullPosture(false, true));
        assertNotEquals(TranscriptStyle.fullPosture(true, false), TranscriptStyle.fullPosture(true, true));
        assertNotEquals(TranscriptStyle.fullPosture(true, false), TranscriptStyle.fullPosture(false, false));
    }

    /**
     * ★ Review M74 usability #5 (WCAG 1.3.1, 4.1.2): what is SPOKEN follows the line's own words — "denied by you", "not
     * answered within 120 s" and "not allowed in Safe analysis" are three different decision makers, and the mark's
     * colour is the same for all three. Witness: speak {@code mark.spoken} for every denial (the old "denied by the
     * posture") — the live accessible name below says nothing of who denied, and the first assertion fails.
     */
    @Test
    @DisplayName("★ a denied line's accessible name says who denied it, in the line's own words")
    void theSpokenWordsFollowTheReason() throws Exception {
        assertEquals("denied by you", TranscriptStyle.spoken(TranscriptStyle.Mark.DENIED, "denied by you"));
        assertEquals("denied: not answered within 120 s",
                TranscriptStyle.spoken(TranscriptStyle.Mark.DENIED, "denied: not answered within 120 s"));
        assertEquals("denied: not allowed in Safe analysis",
                TranscriptStyle.spoken(TranscriptStyle.Mark.DENIED, "denied: not allowed in Safe analysis"));
        assertEquals("succeeded", TranscriptStyle.spoken(TranscriptStyle.Mark.OK, "✓"), "a tick is spoken as a word");
        for (TranscriptStyle.Mark m : TranscriptStyle.Mark.values()) {
            assertFalse(m.spoken.contains("posture"), "no internal word is spoken: " + m);
        }

        java.util.List<String> names = TranscriptViewTest.onEdt(() -> {
            TranscriptView v = new TranscriptView();
            v.render(java.util.List.of(TranscriptViewTest.answer("a",
                    "▸ analyser_open {\"log\":\"a\"} — denied by you\n"
                            + "▸ analyser_report {} — denied: not answered within 120 s\n"
                            + "▸ Bash {\"command\":\"ls\"} — denied: not allowed in Safe analysis")));
            java.util.List<String> out = new java.util.ArrayList<>();
            for (java.awt.Component c : components(v)) {
                if (c instanceof javax.swing.JButton b && b.getText() != null && (b.getText().equals("▸")
                        || b.getText().equals("▾"))) out.add(b.getAccessibleContext().getAccessibleName());
            }
            return out;
        });
        assertEquals(3, names.size(), names.toString());
        assertTrue(names.get(0).startsWith("analyser_open — denied by you"), names.get(0));
        assertTrue(names.get(1).startsWith("analyser_report — denied: not answered within 120 s"), names.get(1));
        assertTrue(names.get(2).startsWith("Bash — denied: not allowed in Safe analysis"), names.get(2));
    }

    static java.util.List<java.awt.Component> components(java.awt.Container root) {
        java.util.List<java.awt.Component> out = new java.util.ArrayList<>();
        for (java.awt.Component c : root.getComponents()) {
            out.add(c);
            if (c instanceof java.awt.Container k) out.addAll(components(k));
        }
        return out;
    }
}
