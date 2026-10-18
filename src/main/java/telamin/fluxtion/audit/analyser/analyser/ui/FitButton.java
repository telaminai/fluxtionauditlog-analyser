package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.JButton;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;

/**
 * Robot re-walk #6 (2026-10-18): a button whose words fit the row it sits in — its full words, or a brief form when the row
 * is narrower than them (the docked assistant at 1200×800 is about 190 px wide, and "Assistant: Safe analysis ▾" or
 * "Apply — In-app assistant" ran off its right edge). Presentation only: what the button does, and what it names, are
 * decided elsewhere; its accessible name is set by its owner and keeps the full words.
 */
final class FitButton extends JButton {
    private String full = "";
    private String brief = "";

    FitButton() {
        UiText.plain(this);                  // SF-2: its words may be computed (a profile's title); never markup
    }

    /** The words with room, and the brief form ("" for none: the full words always). */
    void setWords(String full, String brief) {
        this.full = full == null ? "" : full;
        this.brief = brief == null ? "" : brief;
        setText(wordsFor(rowWidth()));
        revalidate();
    }

    /** The full words — what the button says when there is room. */
    String fullWords() {
        return full;
    }

    private int widthOf(String words) {
        FontMetrics fm = getFontMetrics(getFont());
        String now = getText() == null ? "" : getText();
        return super.getPreferredSize().width - fm.stringWidth(now) + fm.stringWidth(words);
    }

    private String wordsFor(int row) {
        return row > 0 && !brief.isEmpty() && widthOf(full) > row ? brief : full;
    }

    /** The width a row of the parent's flow offers one component (0 before the parent is laid out). */
    private int rowWidth() {
        Container p = getParent();
        if (p == null || p.getWidth() <= 0) return 0;
        int gap = p.getLayout() instanceof FlowLayout fl ? fl.getHgap() : 0;
        return p.getWidth() - p.getInsets().left - p.getInsets().right - 2 * gap;
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        if (full.isEmpty()) return d;
        return new Dimension(widthOf(wordsFor(rowWidth())), d.height);
    }

    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        if (full.isEmpty()) return;
        String words = wordsFor(rowWidth());
        if (!words.equals(getText())) setText(words);      // a text change asks for one more layout, at the same width
    }
}
