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
class WrapLine extends JTextArea {

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
        stillCaret();
        // other words need other heights: a new width is measured again (robot re-walk #6)
        getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { reflowed.clear(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { reflowed.clear(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
    }

    /**
     * Robot walkthrough #2 (2026-10-18): a line never moves its caret when its text changes, so it never scrolls a
     * scrolling ancestor (the assistant's header) to itself — the header's top row stayed out of sight.
     */
    private void stillCaret() {
        if (getCaret() instanceof javax.swing.text.DefaultCaret c) c.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
    }

    @Override
    public void updateUI() {
        super.updateUI();
        stillCaret();
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

    /**
     * Robot re-walk #6 (2026-10-18): the In-app bullets overlapped and the Full bullets were cut. A BoxLayout caches its
     * children's heights; the line's height follows its width, so when the width changes (a scroll bar appears, a card is
     * chosen, the panel moves to its own window) the cached height belongs to another width and the last lines are drawn
     * under the next line. Given a new width at which it needs another height, the line asks its ancestors to lay out
     * again — once per width, so a layout that cannot give the height never loops.
     */
    @Override
    public void setBounds(int x, int y, int width, int height) {
        super.setBounds(x, y, width, height);
        reflow(width, height);
    }

    private final java.util.Set<Integer> reflowed = new java.util.HashSet<>();

    private void reflow(int width, int height) {
        if (width <= 0 || !reflowed.add(width)) return;
        java.awt.Insets in = getInsets();
        javax.swing.text.View root = getUI().getRootView(this);
        root.setSize(width - in.left - in.right, Short.MAX_VALUE);
        int need = (int) Math.ceil(root.getPreferredSpan(javax.swing.text.View.Y_AXIS)) + in.top + in.bottom;
        if (need != height) javax.swing.SwingUtilities.invokeLater(this::revalidate);
    }

    @Override
    public void setFont(Font f) {
        if (reflowed != null) reflowed.clear();
        super.setFont(f);
    }
}
