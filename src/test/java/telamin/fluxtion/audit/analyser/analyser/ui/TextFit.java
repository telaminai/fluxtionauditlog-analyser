package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.ScrollPaneConstants;
import javax.swing.text.BadLocationException;
import javax.swing.text.JTextComponent;
import javax.swing.text.View;
import java.awt.Component;
import java.awt.Container;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Robot re-walk #6 (2026-10-18): what "no clipped text" means, in one place, for the layout tests. Run on the EDT over a
 * laid-out, showing component tree; every visible text the person could need to read is checked:
 *
 * <ul>
 *   <li>a label or button is at least as wide as its text (no "…", no glyphs cut at its right edge);</li>
 *   <li>a wrapping text area is at least as tall as its text at the width it was given (the In-app bullets were given the
 *       height of a wider layout, so each one's last line was drawn under the next);</li>
 *   <li>no text runs past the right edge of what shows it — a parent, or a viewport that cannot scroll sideways (the Full
 *       bullets and the restart note);</li>
 *   <li>text is hidden vertically only inside a viewport that scrolls vertically (scrolling to it is reading it).</li>
 * </ul>
 */
final class TextFit {

    private TextFit() {
    }

    /** Every problem under {@code root}, in words; empty when every visible text fits. */
    static List<String> problems(Component root) {
        List<String> out = new ArrayList<>();
        walk(root, out);
        return out;
    }

    private static void walk(Component c, List<String> out) {
        if (!c.isShowing() || c.getWidth() <= 0 || c.getHeight() <= 0) return;
        check(c, out);
        if (c instanceof Container ct && !(c instanceof JTextComponent)) {
            for (Component child : ct.getComponents()) walk(child, out);
        }
    }

    private static void check(Component c, List<String> out) {
        String text = textOf(c);
        if (text == null || text.isBlank()) return;
        String name = c.getClass().getSimpleName() + " \"" + abbrev(text) + "\"";
        JComponent j = c instanceof JComponent jc ? jc : null;
        if (j == null) return;
        if (c instanceof JLabel || c instanceof AbstractButton) {
            if (!text.startsWith("<html") && c.getWidth() + 1 < c.getPreferredSize().width) {
                out.add(name + " is narrower than its text: " + c.getWidth() + " < " + c.getPreferredSize().width);
            }
        }
        if (c instanceof JTextArea ta && ta.getLineWrap()) {
            int need = wrappedHeight(ta);
            if (need > ta.getHeight() + 1) {
                out.add(name + " is shorter than its wrapped text: " + ta.getHeight() + " < " + need);
            }
        }
        if (c instanceof JTextComponent t && !(c instanceof JTextArea ta2 && !ta2.getLineWrap())) {
            int over = overhang(t);
            if (over > 1) out.add(name + " has text past its right edge by " + over + " px");
        }
        Rectangle vis = j.getVisibleRect();
        // scrolled wholly out of a vertically scrolling viewport: scrolling to it is reading it
        if (vis.isEmpty() && scrollsVertically(c)) return;
        if (vis.width + 1 < c.getWidth() && !scrollsSideways(c)) {
            out.add(name + " is cut at the side: shows " + vis.width + " of " + c.getWidth() + " px");
        }
        if (vis.height + 1 < c.getHeight() && !scrollsVertically(c)) {
            out.add(name + " is cut at the top or bottom: shows " + vis.height + " of " + c.getHeight() + " px");
        }
    }

    /** The height a wrapping text area needs at its own width. */
    static int wrappedHeight(JTextArea ta) {
        Insets in = ta.getInsets();
        int w = ta.getWidth() - in.left - in.right;
        if (w <= 0) return 0;
        View root = ta.getUI().getRootView(ta);
        root.setSize(w, Short.MAX_VALUE);
        return (int) Math.ceil(root.getPreferredSpan(View.Y_AXIS)) + in.top + in.bottom;
    }

    /** How far the furthest character's box reaches past the component's inner right edge (0 when none does). */
    static int overhang(JTextComponent t) {
        int right = t.getWidth() - t.getInsets().right;
        int len = t.getDocument().getLength();
        int worst = 0;
        try {
            for (int i = 0; i < len; i++) {
                var r = t.modelToView2D(i);
                if (r == null) continue;
                String ch = t.getDocument().getText(i, 1);
                if (ch.equals("\n")) continue;
                // the character's own advance, so the box is the glyph's, not a zero-width caret
                var next = t.modelToView2D(i + 1);
                double maxX = next != null && Math.abs(next.getY() - r.getY()) < 1 ? next.getX() : r.getMaxX();
                worst = Math.max(worst, (int) Math.ceil(maxX - right));
            }
        } catch (BadLocationException e) {
            return 0;
        }
        return worst;
    }

    private static boolean scrollsSideways(Component c) {
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            if (p instanceof JViewport v && v.getParent() instanceof JScrollPane sp) {
                if (sp.getHorizontalScrollBarPolicy() != ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                        && sp.getHorizontalScrollBar().isVisible()) return true;
            }
        }
        return false;
    }

    private static boolean scrollsVertically(Component c) {
        for (Container p = c.getParent(); p != null; p = p.getParent()) {
            if (p instanceof JViewport v && v.getParent() instanceof JScrollPane sp
                    && sp.getVerticalScrollBarPolicy() != ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER) return true;
        }
        return false;
    }

    private static String textOf(Component c) {
        if (c instanceof JLabel l) return l.getText();
        if (c instanceof AbstractButton b) return b.getText();
        if (c instanceof JTextComponent t && !t.isEditable()) return t.getText();   // the person's own box is theirs
        return null;
    }

    private static String abbrev(String s) {
        String one = s.replace('\n', ' ').strip();
        return one.length() > 70 ? one.substring(0, 70) + "…" : one;
    }
}
