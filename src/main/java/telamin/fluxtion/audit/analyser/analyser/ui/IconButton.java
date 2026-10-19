package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.JButton;
import java.awt.Dimension;
import java.awt.FontMetrics;

/**
 * M78 D1 (spec-quiet-assistant-panel.md §4.2): a header icon — ✎ or ⋯ — as wide as its glyph and its padding, and at
 * least 24×24 (WCAG 2.5.8). A look and feel's minimum button width (FlatLaf's 72 px) is for words: two icons that wide
 * left the mode control 9 px in the 216 px dock. Its name and tooltip are set by its owner; the glyph is plain text.
 */
final class IconButton extends JButton {

    IconButton(String glyph) {
        super(glyph);
        UiText.plain(this);
        setFocusPainted(false);
        putClientProperty("JButton.buttonType", "toolBarButton");
    }

    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        FontMetrics fm = getFontMetrics(getFont());
        int w = fm.stringWidth(getText() == null ? "" : getText()) + 10;
        int h = Math.max(super.getPreferredSize().height, TranscriptView.TARGET_MIN);
        return new Dimension(Math.max(TranscriptView.TARGET_MIN, w), h);
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }
}
