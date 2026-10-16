package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.BorderFactory;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;

/**
 * A read-only line of text that WRAPS to its container's width instead of ending in "…" (M74 visual check). Its
 * preferred height follows the width it is given, so a header or a status line never hides its end at a narrow width.
 */
final class WrapLine extends JTextArea {

    WrapLine(String text) {
        super(text);
        setEditable(false);
        setFocusable(false);
        setLineWrap(true);
        setWrapStyleWord(true);
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder());
        Font f = UIManager.getFont("Label.font");
        if (f != null) setFont(f);
    }

    WrapLine bold() {
        setFont(getFont().deriveFont(Font.BOLD));
        return this;
    }

    /** The height the text needs at the parent's width; the width itself is whatever the layout gives. */
    @Override
    public Dimension getPreferredSize() {
        Container parent = getParent();
        int width = parent == null ? 0 : parent.getWidth() - parent.getInsets().left - parent.getInsets().right;
        if (width > 0 && (getWidth() != width)) setSize(width, Short.MAX_VALUE);
        Dimension d = super.getPreferredSize();
        return width > 0 ? new Dimension(width, d.height) : d;
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
    }
}
