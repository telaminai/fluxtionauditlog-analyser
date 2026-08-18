package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.topology.ProcessorTopology;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A small collapsible index of the graph, overlaid on the bottom-left of the canvas: pick a node by
 * <b>name</b> rather than by finding it.
 *
 * <p>Hunting for a box is the thing that does not scale. Zoomed out far enough to see a 300-node graph,
 * the labels are gone; zoomed in far enough to read them, most of the graph is off screen. A list is
 * immune to both, and it is also the natural place to start a source navigation — you usually know the
 * name of the node you want to read.
 *
 * <p>Split by kind, because the three answer different questions: <b>Nodes</b> is the application,
 * <b>Events</b> is every way in, <b>Services</b> is the operator surface. Sections with nothing in them
 * are omitted rather than shown empty — most graphs export no services, and a permanently empty heading
 * reads as a broken feature.
 */
public final class TopologyIndex extends JPanel {

    private Consumer<String> onSelect;
    private Consumer<String> onOpenSource;
    private final JPanel body = new JPanel();
    private final JButton collapseAll = new JButton();
    private final List<Section> sections = new ArrayList<>();
    private boolean open = true;
    private boolean syncing;

    public TopologyIndex(Consumer<String> onSelect, Consumer<String> onOpenSource) {
        super(new BorderLayout());
        this.onSelect = onSelect == null ? id -> { } : onSelect;
        this.onOpenSource = onOpenSource == null ? id -> { } : onOpenSource;

        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setOpaque(false);

        collapseAll.setFocusable(false);
        collapseAll.setBorderPainted(false);
        collapseAll.setContentAreaFilled(false);
        collapseAll.addActionListener(e -> setOpen(!open));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.add(collapseAll, BorderLayout.WEST);
        add(header, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);

        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.surfaceEdge()),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));
        applyTheme();
        setOpen(true);
    }

    public void setSelectHandler(Consumer<String> handler) {
        this.onSelect = handler == null ? id -> { } : handler;
    }

    public void setOpenSourceHandler(Consumer<String> handler) {
        this.onOpenSource = handler == null ? id -> { } : handler;
    }

    /** Repaint in the current theme — the overlay sits on the canvas, so it must match it. */
    public void applyTheme() {
        setBackground(UiTheme.surface());
        setOpaque(true);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.surfaceEdge()),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));
        for (Section s : sections) s.applyTheme();
    }

    private void setOpen(boolean nowOpen) {
        this.open = nowOpen;
        body.setVisible(nowOpen);
        updateTitle();
        revalidate();
        repaint();
    }

    private void updateTitle() {
        int total = 0;
        for (Section s : sections) total += s.model.size();
        collapseAll.setText((open ? "▾ " : "▸ ") + "Index  (" + total + ")");
        collapseAll.setForeground(UiTheme.mutedForeground());
        Font base = collapseAll.getFont();
        if (base != null) collapseAll.setFont(base.deriveFont(Font.PLAIN, base.getSize2D() - 1f));
    }

    /** Rebuild from the graph. Pass the FULL graph — the index is how you reach what is filtered out. */
    public void setTopology(ProcessorTopology topology) {
        sections.clear();
        body.removeAll();
        if (topology != null && !topology.isEmpty()) {
            addSection("Nodes", topology, ProcessorTopology.Kind.NODE, ProcessorTopology.Kind.EVENT_HANDLER,
                    ProcessorTopology.Kind.UNKNOWN);
            addSection("Events", topology, ProcessorTopology.Kind.EVENT);
            addSection("Services", topology, ProcessorTopology.Kind.EXPORT_SERVICE);
        }
        updateTitle();
        revalidate();
        repaint();
    }

    private void addSection(String title, ProcessorTopology topology, ProcessorTopology.Kind... kinds) {
        Set<ProcessorTopology.Kind> wanted = Set.of(kinds);
        List<String> ids = new ArrayList<>();
        for (ProcessorTopology.Node node : topology.nodes()) {
            if (wanted.contains(node.kind())) ids.add(node.id());
        }
        if (ids.isEmpty()) return;      // an always-empty heading reads as a broken feature
        // Filed the way a reader scans, not the order the graph happened to emit: alphabetical, with
        // digit runs compared by value so CHILL-2 precedes CHILL-10. On a generated estate (chillers,
        // tills, zones) graph order is arbitrary and lexicographic order is actively misleading.
        ids.sort(NaturalOrder.ID);
        Section section = new Section(title, ids);
        sections.add(section);
        body.add(section);
    }

    /** Reflect the canvas selection in the lists, without echoing it back as a fresh selection. */
    public void setSelection(Collection<String> ids) {
        Set<String> wanted = ids == null ? Set.of() : new LinkedHashSet<>(ids);
        syncing = true;
        try {
            for (Section s : sections) s.select(wanted);
        } finally {
            syncing = false;
        }
    }

    /** One collapsible kind: a heading that toggles, and the names under it. */
    private final class Section extends JPanel {
        private final DefaultListModel<String> model = new DefaultListModel<>();
        private final JList<String> list = new JList<>(model);
        private final JScrollPane scroll = new JScrollPane(list);
        private final JButton heading = new JButton();
        private final String title;
        private boolean expanded = true;

        Section(String title, List<String> ids) {
            super(new BorderLayout());
            this.title = title;
            setOpaque(false);
            setAlignmentX(Component.LEFT_ALIGNMENT);
            ids.forEach(model::addElement);

            list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
            list.setVisibleRowCount(Math.min(8, model.size()));
            list.addListSelectionListener(e -> {
                if (syncing || e.getValueIsAdjusting()) return;
                String id = list.getSelectedValue();
                if (id != null) onSelect.accept(id);
            });
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    // same gesture as the canvas: double-click opens the source
                    if (e.getClickCount() == 2 && list.getSelectedValue() != null) {
                        onOpenSource.accept(list.getSelectedValue());
                    }
                }
            });

            scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(BorderFactory.createEmptyBorder());

            heading.setFocusable(false);
            heading.setBorderPainted(false);
            heading.setContentAreaFilled(false);
            heading.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
            heading.addActionListener(e -> setExpanded(!expanded));

            add(heading, BorderLayout.NORTH);
            add(scroll, BorderLayout.CENTER);
            applyTheme();
            // collapsed to start: expanded, three lists cover half the canvas, and an overlay that
            // obscures what it indexes is worse than one more click
            setExpanded(false);
        }

        void applyTheme() {
            list.setBackground(UiTheme.surface());
            scroll.getViewport().setBackground(UiTheme.surface());
            heading.setForeground(UiTheme.mutedForeground());
            Font base = heading.getFont();
            if (base != null) heading.setFont(base.deriveFont(Font.PLAIN, base.getSize2D() - 1f));
            list.setFont(UiTheme.mono(11));
        }

        void setExpanded(boolean nowExpanded) {
            this.expanded = nowExpanded;
            scroll.setVisible(nowExpanded);
            heading.setText((expanded ? "▾ " : "▸ ") + title + "  (" + model.size() + ")");
            // a fixed ceiling: the overlay must not grow to swallow the canvas it sits on
            int rows = Math.min(8, model.size());
            scroll.setPreferredSize(new Dimension(190, rows * 17 + 4));
            scroll.setMaximumSize(new Dimension(190, rows * 17 + 4));
            revalidate();
            repaint();
        }

        void select(Set<String> wanted) {
            list.clearSelection();
            for (int i = 0; i < model.size(); i++) {
                if (wanted.contains(model.get(i))) list.addSelectionInterval(i, i);
            }
        }
    }

    @Override
    public Dimension getMaximumSize() {
        return getPreferredSize();   // an overlay sizes to its content, never to the space available
    }
}
