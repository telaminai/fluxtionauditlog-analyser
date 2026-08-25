package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

/**
 * Content that fits the width it is given instead of demanding the width it wants.
 *
 * <p>Swing's default answer to "how wide should this be?" is the widest the content could possibly
 * be, and a {@link JScrollPane} takes that answer literally — it sizes an ordinary view to the view's
 * PREFERRED width and offers a horizontal scrollbar for the difference. For a column of prose that is
 * the wrong answer every time: one long unbroken line silently widens the whole column, everything
 * else is laid out to match, and the reader gets a page that runs off the right-hand edge.
 *
 * <p>The instinct is to fix the TEXT — break the long line, shorten the label. That treats the
 * symptom, breaks at whatever width the author happened to have, and re-breaks wrongly the moment
 * anyone resizes the window. The primitives here fix the sizing instead, and are shared rather than
 * reimplemented per panel because both surfaces that need them got them wrong independently
 * (the M36 start page, and the M33 Reports tab).
 */
public final class Fluid {

    private Fluid() {
    }

    /**
     * A panel that takes all the width it is offered and only the height it currently needs.
     *
     * <p>A vertical {@code BoxLayout} grants a child everything up to its MAXIMUM height, and the
     * default maximum is unbounded — so a flexible child absorbs all the spare space. That is what
     * turns a two-line callout into a tinted block half the pane tall. Pinning the maximum to a
     * CONSTANT is the usual fix and is wrong wherever the height depends on the width, which for
     * wrapped text is always; pinning it to the CURRENT preferred height keeps both properties.
     */
    public static class Panel extends JPanel {
        public Panel() {
            this(new BorderLayout());
        }

        public Panel(LayoutManager lm) {
            super(lm);
            trackWidth(this);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }

    /**
     * Text that wraps to the width it is GIVEN and is exactly as tall as that wrap needs.
     *
     * <p>Use this anywhere a sentence could be long. A {@code JLabel} — including the
     * {@code "<html>…"} form — does not wrap: it lays out on one line and reports that line's full
     * width as its preferred size, which is precisely how a single note widens an entire column. The
     * {@code <html><body style='width:640px'>} workaround wraps but at a guessed pixel count, so it
     * clips mid-word as soon as the pane is narrower than the guess.
     */
    public static JTextArea text(String s) {
        JTextArea t = new JTextArea(s) {
            @Override
            public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }

            @Override
            public Dimension getMinimumSize() {
                // never let a long word veto a narrow pane: the wrap handles it
                return new Dimension(0, getPreferredSize().height);
            }
        };
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setFocusable(false);
        t.setOpaque(false);
        t.setBorder(null);
        t.setFont(UIManager.getFont("Label.font"));
        t.setAlignmentX(Component.LEFT_ALIGNMENT);
        trackWidth(t);
        return t;
    }

    /**
     * Wrap {@code content} for a {@link JScrollPane} so it scrolls VERTICALLY only, reflowing to the
     * viewport's width rather than scrolling sideways.
     *
     * <p>This is the half that cannot be skipped. Without it a scroll pane sizes the view to the
     * view's own preferred width, so wrapped children are laid out against a width far larger than
     * the pane — and if the horizontal scrollbar is also disabled, the overflow is not merely present
     * but invisible, which looks exactly like content being cut off.
     */
    public static JPanel column(JComponent content) {
        return new Column(content);
    }

    private static final class Column extends JPanel implements Scrollable {
        Column(JComponent content) {
            super(new BorderLayout());
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder());
            add(content, BorderLayout.NORTH);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL ? r.height : r.width;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;      // reflow instead of scrolling sideways
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;     // but do scroll down when the content genuinely does not fit
        }
    }

    /**
     * Re-ask the parent for space when the WIDTH changes.
     *
     * <p>Wrapped content laid out at one width and then widened is otherwise laid out correctly and
     * clipped to the height it needed before — the height is a function of the width, and nothing in
     * Swing knows that but us. Height-only changes are ignored, since those are the result of this
     * very call and would loop.
     */
    static void trackWidth(JComponent c) {
        c.addComponentListener(new ComponentAdapter() {
            private int lastWidth = -1;

            @Override
            public void componentResized(ComponentEvent e) {
                if (c.getWidth() == lastWidth) return;
                lastWidth = c.getWidth();
                c.revalidate();
            }
        });
    }
}
