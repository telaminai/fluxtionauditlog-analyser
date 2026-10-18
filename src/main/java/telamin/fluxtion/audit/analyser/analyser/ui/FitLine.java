package telamin.fluxtion.audit.analyser.analyser.ui;

import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Insets;

/**
 * Robot re-walk #6 (2026-10-18): a line of text in a flowing row that is as wide as its words — and, when the row is
 * narrower than that, as wide as the row, its words wrapping. Unlike {@link WrapLine} (as wide as its parent), it can sit
 * beside other components in a {@link WrapLayout} row, and it never asks for an absurd height before the row has a width.
 */
class FitLine extends WrapLine {

    FitLine(String text) {
        super(text);
    }

    /** Its words on one line, or the row's width when that is less; as tall as the words wrap at that width. */
    @Override
    public Dimension getPreferredSize() {
        Insets in = getInsets();
        FontMetrics fm = getFontMetrics(getFont());
        int natural = 0;
        for (String line : getText().split("\n", -1)) natural = Math.max(natural, fm.stringWidth(line));
        natural += in.left + in.right + 2;
        int w = natural;
        int row = rowWidth();
        if (row > 0) w = Math.min(natural, row);
        javax.swing.text.View root = getUI().getRootView(this);
        root.setSize(Math.max(1, w - in.left - in.right), Short.MAX_VALUE);
        int h = (int) Math.ceil(root.getPreferredSpan(javax.swing.text.View.Y_AXIS)) + in.top + in.bottom;
        return new Dimension(w, h);
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();
    }

    @Override
    public Dimension getMinimumSize() {
        return getPreferredSize();
    }

    /** The width a row of the parent's flow offers one component (0 before the parent is laid out). */
    private int rowWidth() {
        Container p = getParent();
        if (p == null || p.getWidth() <= 0) return 0;
        int gap = p.getLayout() instanceof FlowLayout fl ? fl.getHgap() : 0;
        Insets in = p.getInsets();
        return p.getWidth() - in.left - in.right - 2 * gap;
    }
}
