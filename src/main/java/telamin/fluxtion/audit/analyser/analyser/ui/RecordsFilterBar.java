package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.filter.RecordsViewCounts;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.Consumer;

/**
 * What the active filters hide, as a row of real components — a label per filter and a button that undoes it. Two
 * placements render the same {@link RecordsViewCounts}:
 * <ul>
 *   <li>{@link Mode#HIDDEN_LINE} under the Records title, while some records are hidden and some shown:
 *       {@code 18 hidden by event types [Show all] · 9 hidden by search "hedging" [×]};</li>
 *   <li>{@link Mode#EMPTY} over an empty table: {@code No records: event types exclude all 21 [Select all] · search
 *       "hedging" excludes 9 [Clear]}. It replaces a message that was painted at a fixed spot in a fixed grey: below
 *       contrast, under the scroll bar at 1000px, and invisible to a screen reader.</li>
 * </ul>
 * It renders what the session measured and decides nothing: an undo is a request to change the filter, and the counts
 * shown after it are the next snapshot's.
 */
final class RecordsFilterBar extends JPanel {

    enum Mode { HIDDEN_LINE, EMPTY }

    private final Mode mode;
    private final Consumer<RecordsViewWords.Kind> undo;
    private String sentence = "";

    RecordsFilterBar(Mode mode, Consumer<RecordsViewWords.Kind> undo) {
        super(new FlowLayout(FlowLayout.LEFT, 4, 0));
        this.mode = mode;
        this.undo = undo;
        setOpaque(false);
        setBorder(mode == Mode.EMPTY ? BorderFactory.createEmptyBorder(8, 8, 8, 8) : BorderFactory.createEmptyBorder(0, 0, 4, 0));
        getAccessibleContext().setAccessibleName(mode == Mode.EMPTY ? "Records table is empty" : "Hidden records");
        render(null, null);
    }

    /** Render {@code rv} (null: no log) and its resolved search {@code text}. Visible only when there is something to say. */
    void render(RecordsViewCounts rv, String text) {
        removeAll();
        boolean show = mode == Mode.EMPTY ? rv == null || rv.total() == 0 || rv.allHidden() : rv != null && rv.anyHidden() && !rv.allHidden();
        sentence = !show ? "" : mode == Mode.EMPTY ? RecordsViewWords.emptySentence(rv, text) : RecordsViewWords.hiddenSentence(rv, text);
        if (show) {
            if (mode == Mode.EMPTY) add(muted(rv == null || rv.total() == 0 ? sentence : "No records:"));
            boolean first = true;
            for (RecordsViewWords.Reason r : RecordsViewWords.reasons(rv, text)) {
                if (!first) add(muted("·"));
                first = false;
                add(muted(mode == Mode.EMPTY ? r.empty() : r.hidden()));
                JButton b = UiText.button(mode == Mode.EMPTY ? r.emptyUndo() : r.hiddenUndo());
                b.putClientProperty("JButton.buttonType", "borderless");
                b.setToolTipText(UiText.tooltip(r.undoName()));
                b.getAccessibleContext().setAccessibleName(r.undoName());
                b.setMargin(new java.awt.Insets(0, 4, 0, 4));
                b.addActionListener(e -> undo.accept(r.kind()));
                add(b);
            }
            if (mode == Mode.EMPTY && rv != null && rv.total() > 0 && RecordsViewWords.reasons(rv, text).isEmpty()) {
                add(muted("the filter hides every record"));
            }
        }
        getAccessibleContext().setAccessibleDescription(sentence);
        setToolTipText(sentence.isEmpty() ? null : UiText.tooltip(sentence));
        setVisible(show);
        revalidate();
        repaint();
    }

    /** The whole of what this placement says, as one sentence; "" while it is hidden. */
    String sentence() {
        return sentence;
    }

    private static JLabel muted(String text) {
        JLabel l = UiText.label(text);    // SF-2: a search term is the person's (or an assistant's) text
        l.setForeground(UiTheme.mutedForeground());
        return l;
    }

    /** Recompute the theme-derived ink after a theme switch (it is set explicitly, so a LAF update keeps the old one). */
    void refreshTheme() {
        for (Component c : getComponents()) if (c instanceof JLabel l) l.setForeground(UiTheme.mutedForeground());
    }

    /**
     * The Event types toggle's badge: the selection count ("1 of 7") on an accent pill, so a type filter is visible
     * whether the checklist is shown or not.
     */
    static final class Badge implements Icon {
        private final String text;
        private final Font font;
        private final int width;
        private final int height;

        Badge(String text, Font font) {
            this.text = text;
            this.font = font.deriveFont(Font.BOLD, font.getSize2D() - 1f);
            java.awt.FontMetrics fm = new JLabel().getFontMetrics(this.font);
            this.width = fm.stringWidth(text) + 12;
            this.height = fm.getHeight() + 2;
        }

        String text() { return text; }

        @Override public int getIconWidth() { return width; }

        @Override public int getIconHeight() { return height; }

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(UiTheme.accent());
                int h = getIconHeight();
                g2.fillRoundRect(x, y, getIconWidth(), h, h, h);
                g2.setColor(UiTheme.onAccent());
                g2.setFont(font);
                var fm = c.getFontMetrics(font);
                g2.drawString(text, x + 6, y + (h - fm.getHeight()) / 2 + fm.getAscent());
            } finally {
                g2.dispose();
            }
        }
    }
}
