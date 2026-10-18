package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.filter.RecordsViewCounts;
import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Checklist of event dimensions with counts (spec §8.4). Selecting a subset filters the whole app —
 * a row passes if its dimension is among the selected ones (an <b>OR</b>). The list is split into two
 * labelled sections, <b>Event types</b> and <b>Callbacks</b>, purely for readability: a dimension is a
 * callback when the record had one, otherwise the raw event type ({@code eventDimension = callback ??
 * event}). Right-click an item for only-this / add / remove. Stays in sync when the selection changes
 * elsewhere (e.g. a summary-row cross-filter).
 */
public final class EventFilterPanel extends JPanel {

    /**
     * Owner, 2026-10-18: a shown checklist always shows at least this many rows (all of them when there are
     * fewer). It is the panel's minimum size, so the split above the records and the column holding them
     * honour it on a drag; {@link #getMinimumSize()} computes it from the real rows.
     */
    static final int MIN_VISIBLE_ROWS = 3;
    /** Fired when the rows change and with them the height {@link #getMinimumSize()} asks for. */
    static final String FLOOR_PROPERTY = "eventTypesFloor";
    /** m6: the tallest the checklist asks to be by default; a longer list scrolls, and its divider can be dragged. */
    static final int MAX_PREFERRED_HEIGHT = 190;

    private final JPanel checks = new JPanel();
    /**
     * Select all / Select none, stacked BESIDE the list rather than above it (robot walk of P3, 1200x680): above, the
     * row took a fifth of the three-row floor's height out of a column with none to spare; beside, it takes width,
     * which the list's short names leave over.
     */
    private final JPanel top = new JPanel();
    private final JScrollPane scroll = new JScrollPane(checks);
    private final Map<String, JCheckBox> boxes = new LinkedHashMap<>();

    /** Set by the Facts column only at a window too small for every floor: see {@link #yieldTo(int)}. */
    private int yieldTo = Integer.MAX_VALUE;

    private LogIndex index;
    private FilterState filter;
    /** The session's counts the rows were last rendered from, or null: the rows ARE these (no count of its own). */
    private RecordsViewCounts rendered;

    public EventFilterPanel() {
        super(new BorderLayout());
        getAccessibleContext().setAccessibleName("Event types");
        setBorder(BorderFactory.createEmptyBorder());   // the toggle above names it: no title of its own

        JButton all = new JButton("Select all");
        JButton none = new JButton("Select none");
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 0));
        for (JButton b : new JButton[]{all, none}) {
            b.setAlignmentX(LEFT_ALIGNMENT);
            b.setMaximumSize(new Dimension(Integer.MAX_VALUE, b.getPreferredSize().height));
            top.add(b);
            top.add(Box.createVerticalStrut(4));
        }
        add(top, BorderLayout.EAST);

        checks.setLayout(new BoxLayout(checks, BoxLayout.Y_AXIS));
        add(scroll, BorderLayout.CENTER);

        all.addActionListener(e -> setAll(true));
        none.addActionListener(e -> setAll(false));
    }

    public void bind(LogIndex index, FilterState filter) {
        this.index = index;
        this.filter = filter;
        filter.addListener(this::syncFromState);
        rendered = null;            // the rows arrive with the session's first measurement of this log (render)
        rebuildChecklist();
    }

    /** Unbind and blank (M35.1). rebuildChecklist already empties itself when the index is null. */
    public void clear() {
        this.index = null;
        this.filter = null;
        this.rendered = null;
        rebuildChecklist();
    }

    /**
     * The height that shows {@link #MIN_VISIBLE_ROWS} rows: the border, the Select all / none row, the scroll
     * pane's insets, and the checklist's own components down to the bottom of its third row — so a group
     * header or the divider above a row is counted too. Measured from the components themselves, so it
     * follows the theme and the font size. With no log there are no rows; a stand-in check box of the
     * current font gives the height three would take.
     */
    @Override
    public Dimension getMinimumSize() {
        if (isMinimumSizeSet()) return super.getMinimumSize();
        return new Dimension(0, Math.min(rowsFloor(), yieldTo));
    }

    /**
     * m6: as tall as its rows ask for, up to {@link #MAX_PREFERRED_HEIGHT} (then it scrolls), and never under its
     * three-row floor — instead of a fixed 200px that left a short list in a tall empty box and a long one cramped.
     */
    @Override
    public Dimension getPreferredSize() {
        if (isPreferredSizeSet()) return super.getPreferredSize();
        Dimension content = super.getPreferredSize();
        return new Dimension(content.width, Math.max(rowsFloor(), Math.min(content.height, MAX_PREFERRED_HEIGHT)));
    }

    /**
     * At a window too small for the checklist's three rows AND the records table's, the checklist gives way
     * (it is a filter over the records, and its toggle hides it); the column sets the height it can have.
     * {@link Integer#MAX_VALUE} restores the full floor.
     */
    void yieldTo(int height) { yieldTo = Math.max(0, height); }

    /** The full three-row floor, whatever the column has asked the checklist to yield to. */
    int rowsFloor() {
        java.awt.Insets border = getInsets();
        java.awt.Insets frame = scroll.getInsets();
        int rows = 0;
        int list = 0;
        for (java.awt.Component c : checks.getComponents()) {
            if (rows >= MIN_VISIBLE_ROWS) break;
            if (!(c instanceof JCheckBox) && !(c instanceof JLabel) && !(c instanceof JSeparator)) continue;  // the glue
            list += c.getPreferredSize().height;
            if (c instanceof JCheckBox) rows++;
        }
        if (boxes.isEmpty()) list = MIN_VISIBLE_ROWS * new JCheckBox("Event type").getPreferredSize().height;
        java.awt.Insets checksInsets = checks.getInsets();
        list += checksInsets.top + checksInsets.bottom;
        // a long event name brings a horizontal scroll bar, which takes its height from the rows
        if (scroll.getViewport().getWidth() > 0 && checks.getPreferredSize().width > scroll.getViewport().getWidth()) {
            list += scroll.getHorizontalScrollBar().getPreferredSize().height;
        }
        return border.top + border.bottom + Math.max(top.getPreferredSize().height, frame.top + frame.bottom + list);
    }

    private void rebuildChecklist() {
        int before = rowsFloor();
        fillChecklist();
        firePropertyChange(FLOOR_PROPERTY, before, rowsFloor());
    }

    /**
     * Render the session's records-view counts: the rows, in the order the session measured (highest total first), and
     * each row's count under the OTHER filters — "(3 / 7)" when search, time or flagged-only hide some of a type, a
     * muted row when they hide all of it. The rows are rebuilt only when the types or their order change (a new log, a
     * Follow append that brings a new type); otherwise only the counts move.
     */
    public void render(RecordsViewCounts rv) {
        rendered = index == null || filter == null ? null : rv;
        List<String> keys = rendered == null ? List.of() : rendered.types().stream().map(RecordsViewCounts.TypeCount::type).toList();
        if (!keys.equals(new ArrayList<>(boxes.keySet()))) {
            rebuildChecklist();
        } else {
            labelRows();
        }
    }

    private void fillChecklist() {
        checks.removeAll();
        boxes.clear();
        if (rendered == null) {
            checks.revalidate();
            checks.repaint();
            return;
        }
        List<RecordsViewCounts.TypeCount> events = new ArrayList<>();
        List<RecordsViewCounts.TypeCount> callbacks = new ArrayList<>();
        for (RecordsViewCounts.TypeCount t : rendered.types()) (t.callback() ? callbacks : events).add(t);
        // the first group needs no heading of its own: the toggle above and this panel already say "Event types", and
        // a third copy took a row's height out of the three-row floor (robot walk of P3, 1200x680)
        addSection(null, events);
        if (!events.isEmpty() && !callbacks.isEmpty()) addDivider();
        addSection("Callbacks", callbacks);
        checks.add(Box.createVerticalGlue());
        labelRows();
        syncFromState();
        checks.revalidate();
        checks.repaint();
    }

    /** Each row's label, tooltip and ink from the counts rendered. */
    private void labelRows() {
        if (rendered == null) return;
        for (RecordsViewCounts.TypeCount t : rendered.types()) {
            JCheckBox cb = boxes.get(t.type());
            if (cb == null) continue;
            String name = t.type().isEmpty() ? "(none)" : t.type();
            String text = name + "  " + RecordsViewWords.typeCount(t);
            if (!text.equals(cb.getText())) UiText.plain(cb).setText(text);   // SF-2: the log's own type name
            cb.setForeground(t.shown() == 0 ? UiTheme.mutedForeground() : null);   // null: the theme's own ink
            cb.setToolTipText(UiText.tooltip(t.shown() == t.total()
                    ? t.total() + " records"
                    : t.shown() + " of " + t.total() + " records pass the other filters (search, time range, flagged only)"));
        }
    }

    /**
     * Owner, 2026-08-27: the group headers hold colours computed at build time, which
     * a theme switch does not touch. Recompute them from the new theme.
     */
    public void refreshTheme() {
        for (java.awt.Component c : checks.getComponents()) {
            if (c instanceof JLabel l && l.getFont() != null && l.getFont().isBold()) l.setForeground(UiTheme.mutedForeground());
        }
        checks.revalidate();
        checks.repaint();
    }

    private void addSection(String title, List<RecordsViewCounts.TypeCount> entries) {
        if (entries.isEmpty()) return;
        if (title != null) {
            JLabel hdr = UiText.label(title);
            hdr.setFont(hdr.getFont().deriveFont(Font.BOLD));
            hdr.setForeground(UiTheme.mutedForeground());
            hdr.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
            hdr.setAlignmentX(LEFT_ALIGNMENT);
            checks.add(hdr);
        }
        for (RecordsViewCounts.TypeCount e : entries) {
            String key = e.type();
            // SF-2: an event type is the log's own text
            JCheckBox cb = UiText.checkBox(key.isEmpty() ? "(none)" : key, true);
            cb.setAlignmentX(LEFT_ALIGNMENT);
            cb.addActionListener(ev -> onUserToggle());     // user clicks only (not setSelected)
            cb.setComponentPopupMenu(rowMenu(key));         // right-click → only this / add / remove
            boxes.put(key, cb);
            checks.add(cb);
        }
    }

    private void addDivider() {
        JSeparator sep = new JSeparator();
        sep.setAlignmentX(LEFT_ALIGNMENT);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 8));
        checks.add(sep);
    }

    private JPopupMenu rowMenu(String key) {
        JPopupMenu m = new JPopupMenu();
        JMenuItem only = new JMenuItem("Only this");
        only.addActionListener(a -> filter.setDimensions(Set.of(key)));
        JMenuItem add = new JMenuItem("Add to filter");
        add.addActionListener(a -> { Set<String> s = effectiveDims(); s.add(key); applyDims(s); });
        JMenuItem remove = new JMenuItem("Remove from filter");
        remove.addActionListener(a -> { Set<String> s = effectiveDims(); s.remove(key); applyDims(s); });
        m.add(only);
        m.add(add);
        m.add(remove);
        return m;
    }

    private void onUserToggle() {
        Set<String> checked = new HashSet<>();
        boolean allChecked = true;
        for (Map.Entry<String, JCheckBox> e : boxes.entrySet()) {
            if (e.getValue().isSelected()) checked.add(e.getKey());
            else allChecked = false;
        }
        filter.setDimensions(allChecked ? null : checked);
    }

    private void setAll(boolean selected) {
        for (JCheckBox cb : boxes.values()) cb.setSelected(selected);
        onUserToggle();
    }

    /** The current selection as a concrete set (all dimensions when the filter is "all"). */
    private Set<String> effectiveDims() {
        Set<String> dims = filter.dimensions();
        return dims != null ? new HashSet<>(dims) : new HashSet<>(boxes.keySet());
    }

    /** Apply a dimension set, collapsing "everything selected" back to the null (all) sentinel. */
    private void applyDims(Set<String> s) {
        filter.setDimensions(s.equals(boxes.keySet()) ? null : s);
    }

    /** Reflect the filter's current selection into the checkboxes (no events fired). */
    private void syncFromState() {
        if (filter == null) return;
        Set<String> dims = filter.dimensions();
        for (Map.Entry<String, JCheckBox> e : boxes.entrySet()) {
            boolean on = (dims == null) || dims.contains(e.getKey());
            if (e.getValue().isSelected() != on) e.getValue().setSelected(on);   // setSelected won't fire ActionListener
        }
    }
}
