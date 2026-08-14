package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.source.SourceNavigation;
import telamin.fluxtion.audit.analyser.analyser.source.SourceNavigation.NodeRef;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Read-only, colourised view of the selected record(s) incl. the full {@code nodeLogs} block
 * (spec §8.3). Navigation (spec §9):
 * <ul>
 *   <li><b>click a node-log line</b> → open that node's source, scrolled to the method it ran
 *       (the line's first key, e.g. {@code orderVenueConnected});</li>
 *   <li><b>right-click</b> → a menu of the record's nodes to open.</li>
 * </ul>
 * Navigation is delegated to an opener supplied by the frame: {@code (instanceId, method)}.
 */
public final class DetailPanel extends JPanel {

    private static final String HINT = "  (select a record in the table above to see its nodeLogs)";

    private final WrapTextPane text = new WrapTextPane(false);
    private final JScrollPane scroll = new JScrollPane(text);
    private final YamlHighlighter highlighter = new YamlHighlighter();
    private final Map<String, String> methodByInstance = new LinkedHashMap<>();  // instanceId -> driving method
    private final javax.swing.JLabel selectionInfo = new javax.swing.JLabel(" ");
    private String shownText = "";
    private final List<LogRecord> shownRecords = new java.util.ArrayList<>();   // records currently displayed
    private final List<Integer> recordStarts = new java.util.ArrayList<>();     // their start offsets in shownText
    private BiConsumer<String, String> nodeSourceOpener = (id, method) -> { };
    private java.util.function.Consumer<LogRecord> eventHandlerOpener = r -> { };  // click the event → EP handler
    private Runnable explainAction = () -> { };
    private GraphTargets graphTargets;   // right-click "Add to graph" wiring (null → menu items absent)

    /** The graph tabs, as seen from the right-click "Add <instanceId.key> to graph" menu. */
    public interface GraphTargets {
        /** Logical name of the currently selected graph, or null. */
        String currentName();
        /** Logical names of all open graphs, in tab order. */
        List<String> names();
        /** Add the series to the named graph (null → current, unknown → new named graph). */
        void addSeries(String graphName, String instanceId, String key);
    }

    public DetailPanel() {
        super(new BorderLayout());
        text.setEditable(false);
        text.setFont(new Font("Monospaced", Font.PLAIN, 12));
        text.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JCheckBox wrap = new JCheckBox("Wrap", false);
        wrap.addActionListener(e -> setWrap(wrap.isSelected()));
        JButton explain = new JButton("Explain with LLM");
        explain.setToolTipText("Send the selected record(s) to the LLM assistant for a plain-English explanation");
        explain.addActionListener(e -> explainAction.run());
        JButton copy = new JButton("Copy");
        copy.setToolTipText("Copy the shown record(s) to the clipboard");
        copy.addActionListener(e -> copyToClipboard());
        bar.add(explain);
        bar.add(copy);
        bar.add(wrap);
        bar.add(selectionInfo);
        add(bar, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        setWrap(false);
        highlighter.render(text.getStyledDocument(), HINT);
        // let the split freely resize this panel (don't let the text pane's content dictate a large min)
        setMinimumSize(new java.awt.Dimension(100, 60));
        setBorder(UiTheme.section("Record detail"));
        setToolTipText("nodeLogs for the selected record — click a node line to open its source");

        MouseAdapter ma = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { maybePopup(e); }
            @Override public void mouseReleased(MouseEvent e) { maybePopup(e); }
            @Override public void mouseClicked(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1 && !e.isPopupTrigger()) navigateAtClick(e);
            }
        };
        text.addMouseListener(ma);
    }

    /** Supplies the action used to open a node's source: {@code (instanceId, method)}. */
    public void setInstanceSourceOpener(BiConsumer<String, String> opener) {
        this.nodeSourceOpener = opener == null ? (id, m) -> { } : opener;
    }

    /** Supplies the action run when the event / eventToString line is clicked: open the EP handler. */
    public void setEventHandlerOpener(java.util.function.Consumer<LogRecord> opener) {
        this.eventHandlerOpener = opener == null ? r -> { } : opener;
    }

    /** Action run by the "Explain with LLM" button. */
    public void setExplainAction(Runnable action) {
        this.explainAction = action == null ? () -> { } : action;
    }

    /** Wires the right-click "Add to graph" menu to the graph tabs (null disables it). */
    public void setGraphTargets(GraphTargets targets) {
        this.graphTargets = targets;
    }

    /** Shows how many records are selected and how they feed the LLM. */
    public void setSelectionInfo(int count) {
        if (count <= 1) {
            selectionInfo.setText(count == 1 ? "  1 record" : " ");
        } else {
            selectionInfo.setText("  " + count + " records selected — last shown here; all sent to the LLM");
        }
    }

    public void showRecords(List<LogRecord> records) {
        methodByInstance.clear();
        shownRecords.clear();
        recordStarts.clear();
        if (records == null || records.isEmpty()) {
            shownText = "";
            highlighter.render(text.getStyledDocument(), "");
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < records.size(); i++) {
            if (i > 0) sb.append("\n---\n");
            LogRecord r = records.get(i);
            recordStarts.add(sb.length());
            shownRecords.add(r);
            sb.append(r.rawText());
            for (NodeLog nl : r.nodeLogs()) {
                // the method that ran = the record's callback, else the node's first logged key
                String method = r.callback();
                if (method == null && !nl.entries().isEmpty()) method = nl.entries().get(0).key();
                methodByInstance.putIfAbsent(nl.instanceId(), method);
            }
        }
        shownText = sb.toString();
        highlighter.render(text.getStyledDocument(), shownText);
        text.setCaretPosition(0);
    }

    public void clear() {
        methodByInstance.clear();
        shownRecords.clear();
        recordStarts.clear();
        shownText = "";
        setSelectionInfo(0);
        highlighter.render(text.getStyledDocument(), HINT);
    }

    /** Re-colour the current content (e.g. after a theme change). */
    public void refresh() {
        highlighter.render(text.getStyledDocument(), shownText);
    }

    private void copyToClipboard() {
        String content = shownText.isEmpty() ? "" : shownText;
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new java.awt.datatransfer.StringSelection(content), null);
    }

    private void navigateAtClick(MouseEvent e) {
        if (shownText.isEmpty()) return;
        int offset = text.viewToModel2D(e.getPoint());
        String line = SourceNavigation.lineAt(shownText, offset);
        NodeRef ref = SourceNavigation.parseNodeLogLine(line);
        if (ref != null) {
            String method = ref.methodKey() != null ? ref.methodKey() : methodByInstance.get(ref.instanceId());
            nodeSourceOpener.accept(ref.instanceId(), method);
            return;
        }
        // not a node-log line — clicking the event / eventToString opens the processor's handler for it
        String t = line.strip();
        if (t.startsWith("event:") || t.startsWith("eventToString:")) {
            LogRecord rec = recordAt(offset);
            if (rec != null) eventHandlerOpener.accept(rec);
        }
    }

    /** The displayed record whose block contains {@code offset} (the last one starting at/before it). */
    private LogRecord recordAt(int offset) {
        LogRecord found = null;
        for (int i = 0; i < recordStarts.size(); i++) {
            if (recordStarts.get(i) <= offset) found = shownRecords.get(i);
            else break;
        }
        return found;
    }

    /** Toggle line-wrap: swap the view behaviour, sync the scrollbar policy, and rebuild the views. */
    private void setWrap(boolean wrap) {
        text.setWrap(wrap);
        scroll.setHorizontalScrollBarPolicy(wrap
                ? JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
                : JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        highlighter.render(text.getStyledDocument(), shownText);   // rebuild views under the new mode
        text.revalidate();
        scroll.revalidate();
        scroll.repaint();
    }

    private void maybePopup(MouseEvent e) {
        if (!e.isPopupTrigger()) return;
        JPopupMenu menu = new JPopupMenu();

        // "Add to graph" — forgiving: an exact-key click gets the full target chooser; clicking anywhere
        // else on a node line offers every graphable key of that node (source is never required to graph)
        if (graphTargets != null) {
            List<String[]> pairs = graphKeysAt(e.getPoint());
            if (pairs.size() == 1) {
                menu.add(graphSubmenu(pairs.get(0)[0], pairs.get(0)[1]));   // pick the target graph
            } else if (!pairs.isEmpty()) {
                javax.swing.JMenu addTo = new javax.swing.JMenu("Add to graph");
                for (String[] pair : pairs) {
                    JMenuItem it = new JMenuItem(pair[0] + "." + pair[1]);
                    it.addActionListener(a -> graphTargets.addSeries(null, pair[0], pair[1]));   // → current graph
                    addTo.add(it);
                }
                menu.add(addTo);
            }
        }

        if (!methodByInstance.isEmpty()) {
            if (menu.getComponentCount() > 0) menu.addSeparator();
            JMenuItem header = new JMenuItem("Open node source…");
            header.setEnabled(false);
            menu.add(header);
            for (Map.Entry<String, String> entry : methodByInstance.entrySet()) {
                JMenuItem item = new JMenuItem(entry.getKey());
                item.addActionListener(a -> nodeSourceOpener.accept(entry.getKey(), entry.getValue()));
                menu.add(item);
            }
        }
        if (menu.getComponentCount() > 0) menu.show(text, e.getX(), e.getY());
    }

    /**
     * The {@code {instanceId, key}} pairs to offer graphing for at {@code point}: the exact key when the
     * click lands on one, otherwise every graphable key of the node line under the cursor (from the parsed
     * record — so it needs no source and works even when the click misses a key token).
     */
    private List<String[]> graphKeysAt(java.awt.Point point) {
        String[] exact = attributeAt(point);
        if (exact != null) return List.<String[]>of(exact);
        if (shownText.isEmpty()) return List.of();
        int offset = text.viewToModel2D(point);
        if (offset < 0 || offset > shownText.length()) return List.of();
        NodeRef ref = SourceNavigation.parseNodeLogLine(SourceNavigation.lineAt(shownText, offset));
        if (ref == null) return List.of();
        LogRecord rec = recordAt(offset);
        if (rec == null) return List.of();
        java.util.LinkedHashSet<String> keys = new java.util.LinkedHashSet<>();
        for (NodeLog nl : rec.nodeLogs()) {
            if (nl.instanceId().equals(ref.instanceId())) {
                for (var kv : nl.entries()) if (kv.key() != null) keys.add(kv.key());
            }
        }
        List<String[]> out = new java.util.ArrayList<>();
        for (String k : keys) out.add(new String[]{ref.instanceId(), k});
        return out;
    }

    /** The "Add instanceId.key to graph" submenu with current / named / new-graph targets. */
    private javax.swing.JMenu graphSubmenu(String instanceId, String key) {
        String display = instanceId + "." + key;
        javax.swing.JMenu addTo = new javax.swing.JMenu("Add " + display + " to graph");
        String current = graphTargets.currentName();
        JMenuItem cur = new JMenuItem(current == null ? "Current graph" : "Current graph (" + current + ")");
        cur.addActionListener(a -> graphTargets.addSeries(null, instanceId, key));
        addTo.add(cur);
        List<String> names = graphTargets.names();
        if (!names.isEmpty()) {
            addTo.addSeparator();
            for (String n : names) {
                JMenuItem item = new JMenuItem(n);
                item.addActionListener(a -> graphTargets.addSeries(n, instanceId, key));
                addTo.add(item);
            }
        }
        addTo.addSeparator();
        JMenuItem fresh = new JMenuItem("New graph…");
        fresh.addActionListener(a -> {
            String name = javax.swing.JOptionPane.showInputDialog(this, "Graph name:", display);
            if (name != null && !name.isBlank()) graphTargets.addSeries(name.trim(), instanceId, key);
        });
        addTo.add(fresh);
        return addTo;
    }

    /** The {@code {instanceId, key}} attribute under the point, or null when it isn't on a key token. */
    private String[] attributeAt(java.awt.Point p) {
        if (shownText.isEmpty()) return null;
        int offset = text.viewToModel2D(p);
        if (offset < 0 || offset > shownText.length()) return null;
        String line = SourceNavigation.lineAt(shownText, offset);
        NodeRef ref = SourceNavigation.parseNodeLogLine(line);
        if (ref == null) return null;
        int lineStart = shownText.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int col = Math.min(offset - lineStart, line.length());
        if (col < 0) return null;
        int s = col;
        while (s > 0 && isIdentChar(line.charAt(s - 1))) s--;
        int end = col;
        while (end < line.length() && isIdentChar(line.charAt(end))) end++;
        if (s >= end) return null;
        String token = line.substring(s, end);
        int after = end;
        while (after < line.length() && line.charAt(after) == ' ') after++;
        // a key token is immediately followed by ':'; the instanceId itself doesn't count
        if (after >= line.length() || line.charAt(after) != ':' || token.equals(ref.instanceId())) return null;
        return new String[]{ref.instanceId(), token};
    }

    private static boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
