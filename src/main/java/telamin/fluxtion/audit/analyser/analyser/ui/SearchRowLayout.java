package telamin.fluxtion.audit.analyser.analyser.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;

/**
 * The Records row: {@code Search:}, the search box, and the controls (Event types, Columns, Clear history). On one line
 * while the box keeps at least {@link #MIN_FIELD} pixels; on a narrower Facts column the controls drop to a second
 * line, right-aligned, instead of being laid over the label and the box (robot walk of P3, 900x680: the Event types
 * button covered "Search:" and the box vanished).
 *
 * <p>Components are added in order: the label, the field, the controls panel.
 */
final class SearchRowLayout implements LayoutManager {

    /** The narrowest the search box may be before the controls move to their own line. */
    static final int MIN_FIELD = 100;

    private final int hgap;
    private final int vgap;

    SearchRowLayout(int hgap, int vgap) {
        this.hgap = hgap;
        this.vgap = vgap;
    }

    @Override public void addLayoutComponent(String name, Component comp) { }

    @Override public void removeLayoutComponent(Component comp) { }

    /** Whether, at {@code width}, the controls need a line of their own. */
    boolean wraps(Container parent, int width) {
        if (parent.getComponentCount() < 3) return false;
        Insets in = parent.getInsets();
        int room = width - in.left - in.right;
        int label = parent.getComponent(0).getPreferredSize().width;
        int controls = parent.getComponent(2).getPreferredSize().width;
        return label + hgap + MIN_FIELD + hgap + controls > room;
    }

    private static int width(Container parent) {
        if (parent.getWidth() > 0) return parent.getWidth();
        Container up = parent.getParent();
        return up == null || up.getWidth() == 0 ? Integer.MAX_VALUE : up.getWidth();
    }

    @Override
    public Dimension preferredLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        if (parent.getComponentCount() < 3) return new Dimension(in.left + in.right, in.top + in.bottom);
        Dimension label = parent.getComponent(0).getPreferredSize();
        Dimension field = parent.getComponent(1).getPreferredSize();
        Dimension controls = parent.getComponent(2).getPreferredSize();
        int first = Math.max(label.height, field.height);
        int w = label.width + hgap + Math.max(MIN_FIELD, field.width) + hgap + controls.width;
        int h = wraps(parent, width(parent)) ? first + vgap + controls.height : Math.max(first, controls.height);
        return new Dimension(in.left + in.right + w, in.top + in.bottom + h);
    }

    @Override
    public Dimension minimumLayoutSize(Container parent) {
        Insets in = parent.getInsets();
        if (parent.getComponentCount() < 3) return new Dimension(in.left + in.right, in.top + in.bottom);
        Dimension pref = preferredLayoutSize(parent);
        return new Dimension(in.left + in.right + parent.getComponent(0).getPreferredSize().width + hgap + 40, pref.height);
    }

    @Override
    public void layoutContainer(Container parent) {
        if (parent.getComponentCount() < 3) return;
        Insets in = parent.getInsets();
        int x = in.left;
        int y = in.top;
        int room = parent.getWidth() - in.left - in.right;
        Component label = parent.getComponent(0);
        Component field = parent.getComponent(1);
        Component controls = parent.getComponent(2);
        Dimension lp = label.getPreferredSize();
        Dimension fp = field.getPreferredSize();
        Dimension cp = controls.getPreferredSize();
        boolean wrap = wraps(parent, parent.getWidth());
        int first = wrap ? Math.max(lp.height, fp.height) : Math.max(Math.max(lp.height, fp.height), cp.height);
        int fieldWidth = wrap ? room - lp.width - hgap : room - lp.width - hgap - hgap - cp.width;
        label.setBounds(x, y + (first - lp.height) / 2, lp.width, lp.height);
        field.setBounds(x + lp.width + hgap, y + (first - fp.height) / 2, Math.max(0, fieldWidth), fp.height);
        if (wrap) {
            int cw = Math.min(cp.width, room);
            controls.setBounds(x + room - cw, y + first + vgap, cw, cp.height);
        } else {
            controls.setBounds(x + room - cp.width, y + (first - cp.height) / 2, cp.width, cp.height);
        }
    }
}
