package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review PR #150 R3: the proposal review shows CLAUDE's words for a path and a reason. A bidirectional override in them
 * could make {@code notes/U+202E dm.koobnur} read as another name, and eliding by UTF-16 index could split a surrogate pair or
 * an emoji. The words are shown with hidden characters as marks, and a path is elided between whole grapheme clusters.
 */
class ProposalPathShownTest {

    /** ★ Witness: elide {@code tail} by UTF-16 index, or by code points — the emoji's cluster is split and this fails. */
    @Test
    @DisplayName("★ a path's hidden characters are marks, and the elision never splits an emoji or a mark")
    void tailShowsMarksAndWholeClusters() {
        assertEquals("notes/⟨U+202E⟩dm.koobnur", VisibleMarks.tail("notes/\u202Edm.koobnur", 60));
        assertEquals("short.md", VisibleMarks.tail("short.md", 60));

        String surrogate = "a/".repeat(40) + "\uD83D\uDE00x.md";             // the emoji straddles a UTF-16 cut
        String shown = VisibleMarks.tail(surrogate, 60);
        assertEquals(60, shown.codePointCount(0, shown.length()));
        assertTrue(shown.startsWith("…") && shown.endsWith("\uD83D\uDE00x.md"), shown);
        wholeSurrogates(shown);

        // a cut at 59 code points from the end falls between \u26A0 and its U+FE0F: the whole cluster is dropped instead
        String cluster = "b/".repeat(40) + "\u26A0\uFE0F" + "c".repeat(58);
        String cut = VisibleMarks.tail(cluster, 60);
        assertEquals("…" + "c".repeat(58), cut, "never a lone selector left at the start");
        String kept = VisibleMarks.tail("b/".repeat(40) + "\u26A0\uFE0F" + "c".repeat(57), 60);
        assertEquals("…\u26A0\uFE0F" + "c".repeat(57), kept, "the whole emoji, selector and all, when it fits");

        // a mark is one unit: never "…+202E⟩"
        String mark = VisibleMarks.tail("d/".repeat(40) + "\u202E" + "e".repeat(55), 60);
        assertEquals("…" + "e".repeat(55), mark);
    }

    private static void wholeSurrogates(String shown) {
        for (int i = 0; i < shown.length(); i++) {
            char c = shown.charAt(i);
            if (Character.isHighSurrogate(c)) assertTrue(i + 1 < shown.length() && Character.isLowSurrogate(shown.charAt(i + 1)));
            if (Character.isLowSurrogate(c)) assertTrue(i > 0 && Character.isHighSurrogate(shown.charAt(i - 1)));
        }
    }

    /** ★ Witness: show the reason raw — the override reaches the pane as itself and this fails. */
    @Test
    @DisplayName("★ Claude's reason is shown with hidden characters as marks")
    void theReasonShowsMarks() {
        var c = new ProposalReviewDialog.Content();
        c.showReason(1, "safe \u202Etxt.exe");
        assertEquals("safe ⟨U+202E⟩txt.exe", c.reason.getText());
    }

    /** ★ Static witness: revert the dialog to the raw words or the UTF-16 substring and this fails. */
    @Test
    @DisplayName("★ the review dialog shows the path through VisibleMarks")
    void theDialogUsesTheMarks() throws Exception {
        String src = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ProposalReviewDialog.java"));
        assertTrue(src.contains("UiText.label(VisibleMarks.tail(where, 60))"), "the path is elided through the marks");
        assertTrue(src.contains("UiText.tooltip(VisibleMarks.show(where))"), "and its tooltip shows them too");
        assertFalse(src.contains("where.substring("), "never the UTF-16 cut");
    }
}
