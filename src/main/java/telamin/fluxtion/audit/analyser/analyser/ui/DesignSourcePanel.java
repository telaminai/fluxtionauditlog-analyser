package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.design.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/** Read-only source projection. Buttons emit navigation requests; they never mutate the design. */
final class DesignSourcePanel extends JPanel {
    final WrapTextPane text = new WrapTextPane(false);
    private final JTextArea status = new JTextArea("Open a design with open {design: path}.");
    private final DefaultListModel<Anchor> index = new DefaultListModel<>();
    private final JList<Anchor> beans = new JList<>(index);
    private final JCheckBox follow = new JCheckBox("Follow design", true);
    private DesignDocument document;
    private String file;
    private Consumer<String> showNode = id -> { }, showRecords = id -> { };
    private Consumer<Map<String, Object>> navigate = p -> { };
    private boolean rendering;
    private long navigation;
    private Runnable viewportChanged = () -> { };
    void onViewportChanged(Runnable listener) { viewportChanged = listener; }
    record Anchor(String bean, int line, String label) { @Override public String toString() { return label + " · " + line; } }
    static final int BEAN_LIST_WIDTH = 210;
    /** The bean list takes at most 30% of the pane (and never more than its usual 210 px); the XML keeps the rest. */
    static int beanListWidth(int splitWidth) { return Math.max(0, Math.min(BEAN_LIST_WIDTH, (int) (splitWidth * 0.3))); }
    private boolean dividerDragged;
    /** Follows the pane's width until the divider is dragged; after that it is only capped, never widened. */
    private void fitBeanList(JSplitPane split) {
        int width = split.getWidth(), fit = beanListWidth(width);
        if (width <= 0 || (dividerDragged && split.getDividerLocation() <= fit) || split.getDividerLocation() == fit) return;
        split.setDividerLocation(fit);
    }
    DesignSourcePanel() {
        super(new BorderLayout());
        text.setEditable(false); text.setFont(UiTheme.mono(12));
        status.setEditable(false); status.setLineWrap(true); status.setWrapStyleWord(true); status.setRows(3);
        status.setBackground(UIManager.getColor("Panel.background"));
        // The status (file path + note) wraps; in a narrow pane an unbounded area took the whole height and left the
        // XML a negative one. It keeps its three rows and scrolls past them.
        JScrollPane statusScroll = new JScrollPane(status, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        statusScroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel top = new JPanel(new BorderLayout()); top.add(statusScroll); top.add(follow, BorderLayout.EAST); add(top, BorderLayout.NORTH);
        JScrollPane sourceScroll = new JScrollPane(text);
        sourceScroll.getViewport().addChangeListener(e -> viewportChanged.run());
        // At the default 1200 px window this panel is ~170-210 px wide, and a fixed 210 px bean list left the XML
        // no width: no line was visible, so every design bean/line spotlight was refused. The list yields, and is
        // fitted inside the split's own layout — a divider moved from a resize listener was not laid out.
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(beans), sourceScroll) {
            @Override public void doLayout() { fitBeanList(this); super.doLayout(); }
        };
        split.setDividerLocation(BEAN_LIST_WIDTH); split.setResizeWeight(0.22); add(split);
        // Only a press on the divider is a drag: the split also moves it on every resize (its resize weight).
        if (split.getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI ui) {
            ui.getDivider().addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mousePressed(java.awt.event.MouseEvent e) { dividerDragged = true; }
            });
        }
        JPanel links = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton node = new JButton("Show node"), records = new JButton("Show records");
        links.add(node); links.add(records); links.add(new JLabel("Matched by name · relationship to this run unverified")); add(links, BorderLayout.SOUTH);
        node.addActionListener(e -> { String id = selectedBean(); if (id != null) showNode.accept(id); });
        records.addActionListener(e -> { String id = selectedBean(); if (id != null) showRecords.accept(id); });
        beans.addListSelectionListener(e -> {
            if (!rendering && !e.getValueIsAdjusting() && beans.getSelectedValue() != null && document != null) {
                Anchor a = beans.getSelectedValue();
                navigate.accept(a.bean == null ? Map.of("file", document.file(), "line", a.line)
                        : Map.of("file", document.file(), "bean", a.bean));
            }
        });
        text.addCaretListener(e -> { node.setEnabled(selectedBean() != null); records.setEnabled(selectedBean() != null); });
        node.setEnabled(false); records.setEnabled(false);
    }
    void callbacks(Consumer<Map<String, Object>> navigate, Consumer<String> node, Consumer<String> records) { this.navigate=navigate; showNode=node; showRecords=records; }
    boolean following() { return follow.isSelected(); }
    DesignDocument document() { return document; }
    void render(DesignWorkspace.View view, String note) {
        document = view.document();
        file = view.file();
        rendering = true;
        try {
            new XmlHighlighter().render(text.getStyledDocument(), view.text());
            index.clear();
            if (document != null) {
                for (String id : document.beanIds()) for (var bean : document.beans(id)) index.addElement(new Anchor(id, bean.line(), id));
                for (String property : List.of("nodeBeans", "eventHandlers", "serviceRegistrations")) for (var entry : document.entries(property))
                    index.addElement(new Anchor(null, entry.line(), property + ": " + (entry.attr("bean").isEmpty() ? entry.value().trim() : entry.attr("bean"))));
            }
            status.setText(view.file() + "\n" + note);
            long ticket = ++navigation;
            SwingUtilities.invokeLater(() -> { if (ticket == navigation) scroll(view.line()); });
        } finally { rendering = false; }
    }
    void note(String note) { status.setText((file == null ? "" : file + "\n") + note); }
    String file() { return file; }
    void refresh() {
        int caret = text.getCaretPosition();
        new XmlHighlighter().render(text.getStyledDocument(), text.getText());
        text.setCaretPosition(Math.min(caret, text.getDocument().getLength()));
    }
    void setWrap(boolean on) { text.setWrap(on); refresh(); }
    void clear() { document=null; file=null; text.setText(""); index.clear(); status.setText("No session design open."); }
    /** Settle this reveal now; a previous render must not scroll it away after the echo. */
    void revealLine(int line) {
        navigation++;
        validate();
        scroll(line);
    }
    void scroll(int line) {
        int off = DesignDocument.offset(text.getText(), line, 1);
        text.setCaretPosition(off);
        try { var r = text.modelToView2D(off); if (r != null) text.scrollRectToVisible(r.getBounds()); }
        catch (javax.swing.text.BadLocationException ignored) { }
    }
    Optional<Rectangle> lineBounds(int line) {
        if (!text.isShowing() || file == null || line < 1 || line > 1 + text.getText().chars().filter(c -> c == '\n').count()) return Optional.empty();
        try {
            var r = text.modelToView2D(DesignDocument.offset(text.getText(), line, 1));
            if (r == null) return Optional.empty();
            Rectangle at = r.getBounds(); at.width = Math.max(20, text.getVisibleRect().width);
            at.x = text.getVisibleRect().x;
            if (at.isEmpty() || !text.getVisibleRect().contains(at)) return Optional.empty();
            return Optional.of(SwingUtilities.convertRectangle(text, at, this));
        } catch (javax.swing.text.BadLocationException e) { return Optional.empty(); }
    }
    private String selectedBean() {
        if (document == null) return null;
        int offset = text.getCaretPosition();
        int line = DesignDocument.lineAt(text.getText(), offset);
        return document.elements().stream().filter(e -> e.name().equals("bean") && !e.attr("id").isEmpty()
                        && (e.start() <= offset || e.line() == line) && e.end() >= offset && document.beans(e.attr("id")).size() == 1)
                .min(Comparator.comparingInt(e -> e.end() - e.start())).map(e -> e.attr("id")).orElse(null);
    }
}
