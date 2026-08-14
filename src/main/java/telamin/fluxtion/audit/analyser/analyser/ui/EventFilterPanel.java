package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
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
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Checklist of event dimensions with counts (spec §8.4). Selecting a subset filters the whole app —
 * a row passes if its dimension is among the selected ones (an <b>OR</b>). The list is split into two
 * labelled sections, <b>Event types</b> and <b>Callbacks</b>, purely for readability: a dimension is a
 * callback when the record had one, otherwise the raw event type ({@code eventDimension = callback ??
 * event}). Right-click an item for only-this / add / remove. Stays in sync when the selection changes
 * elsewhere (e.g. a summary-row cross-filter).
 */
public final class EventFilterPanel extends JPanel {

    private final JPanel checks = new JPanel();
    private final Map<String, JCheckBox> boxes = new LinkedHashMap<>();

    private LogIndex index;
    private FilterState filter;

    public EventFilterPanel() {
        super(new BorderLayout());
        setBorder(UiTheme.section("Event types"));

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        JButton all = new JButton("Select all");
        JButton none = new JButton("Select none");
        top.add(all);
        top.add(none);
        add(top, BorderLayout.NORTH);

        checks.setLayout(new BoxLayout(checks, BoxLayout.Y_AXIS));
        add(new JScrollPane(checks), BorderLayout.CENTER);

        all.addActionListener(e -> setAll(true));
        none.addActionListener(e -> setAll(false));
    }

    public void bind(LogIndex index, FilterState filter) {
        this.index = index;
        this.filter = filter;
        filter.addListener(this::syncFromState);
        rebuildChecklist();
    }

    private void rebuildChecklist() {
        checks.removeAll();
        boxes.clear();
        if (index == null || filter == null) {
            checks.revalidate();
            checks.repaint();
            return;
        }
        Map<String, Integer> counts = counts();
        Set<String> callbackDims = callbackDimensions();
        List<Map.Entry<String, Integer>> events = new ArrayList<>();
        List<Map.Entry<String, Integer>> callbacks = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            (callbackDims.contains(e.getKey()) ? callbacks : events).add(e);
        }
        addSection("Event types", events);
        if (!events.isEmpty() && !callbacks.isEmpty()) addDivider();
        addSection("Callbacks", callbacks);
        checks.add(Box.createVerticalGlue());
        syncFromState();
        checks.revalidate();
        checks.repaint();
    }

    private void addSection(String title, List<Map.Entry<String, Integer>> entries) {
        if (entries.isEmpty()) return;
        JLabel hdr = new JLabel(title);
        hdr.setFont(hdr.getFont().deriveFont(Font.BOLD));
        hdr.setForeground(UiTheme.mutedForeground());
        hdr.setBorder(BorderFactory.createEmptyBorder(4, 2, 2, 2));
        hdr.setAlignmentX(LEFT_ALIGNMENT);
        checks.add(hdr);
        for (Map.Entry<String, Integer> e : entries) {
            String key = e.getKey();
            JCheckBox cb = new JCheckBox((key.isEmpty() ? "(none)" : key) + "  (" + e.getValue() + ")", true);
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

    /** Counts per event dimension (callback or event type), highest first. */
    private Map<String, Integer> counts() {
        Map<String, Integer> raw = new LinkedHashMap<>();
        for (int row = 0; row < index.size(); row++) {
            String key = FilterState.groupKey(index, row, FilterState.GroupMode.DIMENSION);
            raw.merge(key, 1, Integer::sum);
        }
        Map<String, Integer> sorted = new TreeMap<>((a, b) -> {
            int byCount = Integer.compare(raw.get(b), raw.get(a));
            return byCount != 0 ? byCount : a.compareTo(b);
        });
        sorted.putAll(raw);
        return sorted;
    }

    /** The dimensions that came from a callback (vs a raw event type) — for the section split. */
    private Set<String> callbackDimensions() {
        Set<String> cb = new HashSet<>();
        for (int row = 0; row < index.size(); row++) {
            if (index.callback(row) != null) {
                String d = index.dimension(row);
                cb.add(d == null ? "" : d);
            }
        }
        return cb;
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
