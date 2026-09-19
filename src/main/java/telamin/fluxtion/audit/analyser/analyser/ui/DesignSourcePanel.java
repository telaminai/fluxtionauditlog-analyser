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
    record Anchor(String bean, int line, String label) { @Override public String toString() { return label + " · " + line; } }
    DesignSourcePanel() {
        super(new BorderLayout());
        text.setEditable(false); text.setFont(UiTheme.mono(12));
        status.setEditable(false); status.setLineWrap(true); status.setWrapStyleWord(true); status.setRows(3);
        status.setBackground(UIManager.getColor("Panel.background"));
        JPanel top = new JPanel(new BorderLayout()); top.add(status); top.add(follow, BorderLayout.EAST); add(top, BorderLayout.NORTH);
        var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(beans), new JScrollPane(text));
        split.setDividerLocation(210); split.setResizeWeight(0.22); add(split);
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
            SwingUtilities.invokeLater(() -> scroll(view.line()));
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
