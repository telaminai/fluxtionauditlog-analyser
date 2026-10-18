package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollBar;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * M78 (spec-quiet-assistant-panel.md §1, §6): the panel's CHROME, counted as the UX review counted it — every visible
 * label, button and text area outside the transcript and the composer's own text; tooltips excluded. A word is a
 * whitespace-separated token with a letter or a digit in it (✎, ⋯, ◆ and ▾ are not words). A control is a visible
 * button or combo. "Visible" is the review's rule: showing, and at least 4×6 px of it on screen — a component scrolled
 * or clipped out of sight is not read, so it is not counted.
 */
final class ChromeCount {

    /** One counted component: its kind, its words. */
    record Seen(String kind, String text) {
    }

    record Count(int words, int controls, List<Seen> seen) {
        String describe() {
            StringBuilder sb = new StringBuilder(words + " words, " + controls + " controls:");
            for (Seen s : seen) sb.append("\n  ").append(s.kind()).append(" | ").append(s.text().replace('\n', '⏎'));
            return sb.toString();
        }
    }

    private ChromeCount() {
    }

    static Count of(Container panel) {
        List<Seen> seen = new ArrayList<>();
        walk(panel, seen);
        int words = 0;
        int controls = 0;
        for (Seen s : seen) {
            words += words(s.text());
            if (s.kind().equals("button") || s.kind().equals("combo")) controls++;
        }
        return new Count(words, controls, seen);
    }

    static int words(String text) {
        if (text == null) return 0;
        int n = 0;
        for (String t : text.trim().split("\\s+")) {
            if (t.codePoints().anyMatch(Character::isLetterOrDigit)) n++;
        }
        return n;
    }

    private static void walk(Container root, List<Seen> out) {
        for (Component c : root.getComponents()) {
            if (!c.isShowing()) continue;
            if (c instanceof TranscriptView || c instanceof JScrollBar) continue;     // the conversation is not chrome
            boolean shown = !(c instanceof JComponent jc) || visibleEnough(jc.getVisibleRect());
            String kind = null;
            String text = null;
            if (c instanceof AbstractButton b) {
                kind = "button";
                text = b.getText();
            } else if (c instanceof JComboBox<?> box) {
                kind = "combo";
                text = String.valueOf(box.getSelectedItem());
            } else if (c instanceof JLabel l) {
                kind = "label";
                text = l.getText();
            } else if (c instanceof JTextComponent t) {
                if (t.isEditable()) continue;                                         // the composer's own text
                kind = "text";
                text = t.getText();
            }
            if (kind != null && shown) out.add(new Seen(kind, text == null ? "" : text));
            if (c instanceof Container k && !(c instanceof JTextComponent)) walk(k, out);
        }
    }

    private static boolean visibleEnough(Rectangle r) {
        return r.width >= 4 && r.height >= 6;
    }
}
