package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.core.Background;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.graph.Expr;
import telamin.fluxtion.audit.analyser.analyser.graph.GraphKey;
import telamin.fluxtion.audit.analyser.analyser.graph.Series;
import telamin.fluxtion.audit.analyser.analyser.graph.SeriesExtractor;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Graph a numeric/boolean {@code instanceId.key} from nodeLogs over time (spec §8.7). Keys are
 * discovered from the filtered records; added series honour the shared filter and re-extract when it
 * changes. Series can be added, <b>removed individually</b>, cleared, styled (stairs/line/points),
 * zoomed and exported to CSV. Multiple {@code GraphPanel}s can coexist (see {@code GraphTabs}) for
 * side-by-side comparisons.
 */
public final class GraphPanel extends JPanel {

    private static final int DISCOVER_LIMIT = 2000;

    private final JComboBox<GraphKey> keyCombo = new JComboBox<>();
    private final ChartPanel chart = new ChartPanel();
    private final JComboBox<String> styleCombo = new JComboBox<>(new String[]{"Stairs", "Line", "Points"});
    private final JTextField exprField = new JTextField(26);       // f(x): a formula over instanceId.key
    private final JTextField exprLabelField = new JTextField(10);  // optional display label
    private final JComboBox<String> resolveCombo = new JComboBox<>(new String[]{"locf", "strict"});
    private final List<GraphKey> activeKeys = new ArrayList<>();
    private final List<Derived> activeExprs = new ArrayList<>();   // formula series (spec-graph-artifacts §B)
    private String editingLabel;                                   // formula loaded into the fields by Edit
    private volatile List<String> exprSuggestions = List.of();     // f(x) autocomplete: keys + formula labels
    private ExprCompletion exprCompletion;                          // the f(x) dropdown completion popup
    private List<String> lastKeyDisplays = List.of();              // last discovered keys, for the union above

    // the Series side panel (toggled by "Series…"): a list of every series + add / remove / edit-formula
    private final DefaultListModel<SeriesRow> seriesListModel = new DefaultListModel<>();
    private final JList<SeriesRow> seriesList = new JList<>(seriesListModel);
    private final JToggleButton editSeriesButton = new JToggleButton("Edit series");
    private final JButton addFxButton = new JButton("Add f(x)");
    private final JPanel centerHolder = new JPanel(new BorderLayout());
    private final JSplitPane seriesSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private JComponent seriesPanel;

    // the plot key rendered as a floating Swing overlay (opaque, untruncated) at the plot's top-right,
    // with the "Edit series" toggle stacked above it — right-click a label to remove that series.
    // The overlay is a CHILD of the chart so it repaints with it (e.g. survives panning/zooming).
    private final JPanel legendOverlay = new JPanel();
    private final JPanel legendLabels = new JPanel();

    /** A derived (formula) series: display label, the expr text, and the resolve policy. */
    private record Derived(String label, String exprText, SeriesExtractor.Resolve resolve) { }

    /** One row of the Series list — a raw key or a formula, in plot order (colour matches the plot). */
    private record SeriesRow(String label, boolean formula) { }

    private static final int EXTRACT_DEBOUNCE_MS = 200;    // coalesce rapid dimension/text changes

    private LogStore store;
    private FilterState filter;
    private final Runnable filterListener = this::onFilterChanged;
    private Timer extractDebounce;         // debounces structural (dimension/text) re-extractions
    private int extractGen;                // drops stale off-EDT extraction results

    private String graphName = "";         // logical name (the tab title's 📌 prefix is display-only)
    private String caption = "";           // provenance: an agent's one-line rationale for this graph
    /**
     * Told after any persistable mutation (series, formulas, style, pin, caption, notes, axes) so the
     * owner can sync {@code config.savedGraphs} and persist to the right tier — B-M20-3: without this,
     * graph edits only ever reached disk at export/exit, and never reached an active project profile.
     */
    private Runnable onMutation = () -> { };

    public void setOnMutation(Runnable onMutation) {
        this.onMutation = onMutation == null ? () -> { } : onMutation;
    }

    private void mutated() {
        onMutation.run();
    }
    private final JLabel captionLabel = new JLabel();   // shows the caption under the plot (when set)
    private Long pinnedFrom, pinnedTo;     // non-null → pinned to this window; null → follows the filter
    private final JToggleButton pinButton = new JToggleButton("📌");
    private Runnable onPinChanged = () -> { };   // notifies GraphTabs to refresh the tab's pin indicator
    private java.util.Set<String> lastDims;       // last dimension/text/group used for extraction, to tell a
    private String lastText = "";                 // *structural* change (needs a re-extract) apart from a
    private FilterState.GroupMode lastGroupMode;  // time-only change (just re-windows the cached series)

    public GraphPanel() {
        super(new BorderLayout(4, 4));   // no border — this is tab content inside the "Graphs" panel

        // A structural change (dimensions/text) needs a re-extract (re-parses the log, off-EDT); a
        // time-only change does NOT — it just re-windows the already-extracted series. Debounce the
        // expensive structural path so typing in the search box (or nudging dimensions) coalesces.
        extractDebounce = new Timer(EXTRACT_DEBOUNCE_MS, e -> {
            extractDebounce.stop();
            refreshKeys();
            reExtract();
        });
        extractDebounce.setRepeats(false);

        keyCombo.setRenderer(graphKeyRenderer());

        // top toolbar: VIEW controls only — series authoring lives in the "Series…" side panel (declutter).
        // The "Series…" toggle itself lives on the plot overlay, above the series key it manages.
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        editSeriesButton.setToolTipText("Show the Series panel — add/remove keys, author and edit formulas");
        editSeriesButton.setFocusable(false);
        editSeriesButton.addActionListener(e -> setSeriesPanelVisible(editSeriesButton.isSelected()));
        row1.add(new JLabel("style:"));
        row1.add(styleCombo);
        JButton zoomIn = new JButton("+");
        JButton zoomOut = new JButton("−");
        JButton fit = new JButton("Fit");
        JButton export = new JButton("Export CSV");
        JButton exportPng = new JButton("Export PNG");
        pinButton.setToolTipText("Pin this graph to a fixed time window (stops it following the shared filter)");
        pinButton.setFocusable(false);
        pinButton.addActionListener(e -> { if (pinButton.isSelected()) pinToCurrentWindow(); else unpin(); });
        row1.add(zoomIn);
        row1.add(zoomOut);
        row1.add(fit);
        row1.add(pinButton);
        row1.add(export);
        row1.add(exportPng);
        add(row1, BorderLayout.NORTH);

        // the plot key as an opaque floating overlay (top-right of the plot), Edit-series toggle on top
        buildLegendOverlay();

        // chart alone by default; toggling "Edit series" swaps in a splitter with the Series panel beside it
        seriesSplit.setResizeWeight(1.0);
        seriesSplit.setContinuousLayout(true);
        centerHolder.add(chart, BorderLayout.CENTER);
        add(centerHolder, BorderLayout.CENTER);

        // provenance caption under the plot — hidden until a rationale is set (e.g. by the graph verb)
        captionLabel.setFont(captionLabel.getFont().deriveFont(java.awt.Font.ITALIC));
        captionLabel.setForeground(UiTheme.mutedForeground());
        captionLabel.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        captionLabel.setVisible(false);
        add(captionLabel, BorderLayout.SOUTH);

        buildSeriesPanel();

        export.addActionListener(e -> exportCsv());
        exportPng.addActionListener(e -> exportPng());
        styleCombo.addActionListener(e -> chart.setStyle(switch (styleCombo.getSelectedIndex()) {
            case 1 -> ChartPanel.Style.LINE;
            case 2 -> ChartPanel.Style.POINTS;
            default -> ChartPanel.Style.STEP;
        }));
        zoomIn.addActionListener(e -> chart.zoomIn());
        zoomOut.addActionListener(e -> chart.zoomOut());
        fit.addActionListener(e -> chart.resetView());
    }

    // ---- Plot key overlay (floating, opaque, untruncated) ---------------------------------------

    /** Build the top-right overlay: the "Edit series" toggle stacked above the (right-aligned) series key. */
    private void buildLegendOverlay() {
        legendOverlay.setLayout(new BoxLayout(legendOverlay, BoxLayout.Y_AXIS));
        legendOverlay.setOpaque(true);
        legendOverlay.setBackground(UIManager.getColor("Panel.background"));
        java.awt.Color border = UIManager.getColor("Component.borderColor");
        legendOverlay.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border != null ? border : java.awt.Color.GRAY),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)));

        editSeriesButton.setAlignmentX(Component.RIGHT_ALIGNMENT);
        legendLabels.setLayout(new BoxLayout(legendLabels, BoxLayout.Y_AXIS));
        legendLabels.setOpaque(false);
        legendLabels.setAlignmentX(Component.RIGHT_ALIGNMENT);
        legendOverlay.add(editSeriesButton);
        legendOverlay.add(Box.createVerticalStrut(6));
        legendOverlay.add(legendLabels);

        // parent the overlay ON the chart so a chart repaint (pan/zoom drag) repaints it too
        chart.setLayout(null);
        chart.add(legendOverlay);
        chart.addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { positionLegendOverlay(); }
        });
    }

    /** Float the overlay at the chart's top-right corner, clamped to the chart bounds. */
    private void positionLegendOverlay() {
        if (chart.getWidth() == 0) return;
        java.awt.Dimension pref = legendOverlay.getPreferredSize();
        int x = Math.max(0, chart.getWidth() - pref.width - 14);
        int hgt = Math.min(pref.height, Math.max(0, chart.getHeight() - 24));
        legendOverlay.setBounds(x, 10, pref.width, hgt);
        legendOverlay.revalidate();
    }

    /** Rebuild the overlay's series rows (raw keys then formulas, in plot-colour order). */
    private void rebuildLegendLabels() {
        legendLabels.removeAll();
        int idx = 0;
        for (GraphKey k : activeKeys) legendLabels.add(legendRow(k.display(), idx++));
        for (Derived d : activeExprs) legendLabels.add(legendRow(d.label(), idx++));
        legendLabels.revalidate();
        legendLabels.repaint();
        positionLegendOverlay();
    }

    /** One overlay row: a plot-colour swatch + the full label, with a right-click "Remove". */
    private JComponent legendRow(String label, int paletteIndex) {
        JLabel l = new JLabel(label, swatch(ChartPanel.paletteColor(paletteIndex)), SwingConstants.LEFT);
        l.setIconTextGap(6);
        l.setAlignmentX(Component.RIGHT_ALIGNMENT);
        l.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, 2));
        l.setToolTipText("Right-click to remove this series");
        JPopupMenu menu = new JPopupMenu();
        JMenuItem rm = new JMenuItem("Remove");
        rm.addActionListener(e -> removeSeriesByLabel(label));
        menu.add(rm);
        l.setComponentPopupMenu(menu);
        return l;
    }

    // ---- Series side panel (add/remove keys, author/edit formulas) ------------------------------

    private void buildSeriesPanel() {
        seriesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        seriesList.setCellRenderer(new SeriesRowRenderer());
        seriesList.addListSelectionListener(e -> { if (!e.getValueIsAdjusting()) onSeriesSelected(); });
        JScrollPane listScroll = new JScrollPane(seriesList);
        listScroll.setBorder(UiTheme.section("Series"));

        JButton remove = new JButton("Remove");
        remove.setToolTipText("Remove the selected series");
        remove.addActionListener(e -> removeSelectedSeries());
        JButton clear = new JButton("Clear all");
        clear.addActionListener(e -> {
            activeKeys.clear();
            activeExprs.clear();
            clearFormulaFields();
            chart.clear();
            seriesChanged();
        });
        JPanel listButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        listButtons.add(remove);
        listButtons.add(clear);

        // add a raw key
        JButton add = new JButton("Add");
        add.addActionListener(e -> {
            if (keyCombo.getSelectedItem() instanceof GraphKey k && !activeKeys.contains(k)) {
                activeKeys.add(k);
                reExtract();
            }
        });
        JButton pick = new JButton("Pick…");
        pick.addActionListener(e -> pickKeys());
        JPanel addKeyRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        addKeyRow.add(new JLabel("Add key:"));
        addKeyRow.add(keyCombo);
        addKeyRow.add(add);
        addKeyRow.add(pick);

        // author / edit a formula (migrated here from the toolbar)
        exprField.setToolTipText("A formula over instanceId.key values, e.g. askMakerOrder.price - bidMakerOrder.price");
        resolveCombo.setToolTipText("locf = carry each ref's last value (cross-node formulas); strict = same-record only");
        exprCompletion = new ExprCompletion(exprField);   // dropdown of matching keys/labels as you type
        addFxButton.addActionListener(e -> addFormulaFromUi());
        JButton newFx = new JButton("New");
        newFx.setToolTipText("Clear the formula fields (stop editing)");
        newFx.addActionListener(e -> clearFormulaFields());

        JPanel fx = new JPanel(new java.awt.GridBagLayout());
        fx.setBorder(UiTheme.section("Formula f(x)"));
        java.awt.GridBagConstraints c = new java.awt.GridBagConstraints();
        c.insets = new java.awt.Insets(2, 3, 2, 3);
        c.anchor = java.awt.GridBagConstraints.LINE_START;
        c.gridx = 0; c.gridy = 0; fx.add(new JLabel("f(x):"), c);
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1; c.fill = java.awt.GridBagConstraints.HORIZONTAL; fx.add(exprField, c);
        c.gridwidth = 1; c.weightx = 0; c.fill = java.awt.GridBagConstraints.NONE;
        c.gridx = 0; c.gridy = 1; fx.add(new JLabel("label:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = java.awt.GridBagConstraints.HORIZONTAL; fx.add(exprLabelField, c);
        c.gridx = 2; c.weightx = 0; c.fill = java.awt.GridBagConstraints.NONE; fx.add(resolveCombo, c);
        JPanel fxButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        fxButtons.add(addFxButton);
        fxButtons.add(newFx);
        c.gridx = 0; c.gridy = 2; c.gridwidth = 3; c.anchor = java.awt.GridBagConstraints.LINE_START; fx.add(fxButtons, c);

        JPanel bottom = new JPanel(new BorderLayout(4, 4));
        bottom.add(addKeyRow, BorderLayout.NORTH);
        bottom.add(fx, BorderLayout.CENTER);

        JPanel panel = new JPanel(new BorderLayout(4, 4));
        JPanel listWrap = new JPanel(new BorderLayout());
        listWrap.add(listScroll, BorderLayout.CENTER);
        listWrap.add(listButtons, BorderLayout.SOUTH);
        panel.add(listWrap, BorderLayout.CENTER);
        panel.add(bottom, BorderLayout.SOUTH);
        panel.setPreferredSize(new java.awt.Dimension(330, 0));
        seriesPanel = panel;
    }

    private void setSeriesPanelVisible(boolean show) {
        legendLabels.setVisible(!show);   // the Series panel already lists every series — hide the plot key
        positionLegendOverlay();
        if (show) {
            centerHolder.remove(chart);
            seriesSplit.setLeftComponent(chart);
            seriesSplit.setRightComponent(seriesPanel);
            centerHolder.add(seriesSplit, BorderLayout.CENTER);
            centerHolder.revalidate();
            centerHolder.repaint();
            SwingUtilities.invokeLater(() -> seriesSplit.setDividerLocation(Math.max(200, getWidth() - 350)));
        } else {
            centerHolder.remove(seriesSplit);
            seriesSplit.setRightComponent(null);   // release the panel so it can be re-added later
            centerHolder.add(chart, BorderLayout.CENTER);
            centerHolder.revalidate();
            centerHolder.repaint();
        }
    }

    private void pickKeys() {
        List<GraphKey> all = new ArrayList<>();
        for (int i = 0; i < keyCombo.getItemCount(); i++) all.add(keyCombo.getItemAt(i));
        boolean added = false;
        for (GraphKey k : SeriesPickerDialog.pick(this, all)) {
            if (!activeKeys.contains(k)) { activeKeys.add(k); added = true; }
        }
        if (added) {
            reExtract();
            mutated();
        }
    }

    /** Selecting a formula row loads it into the f(x) fields for editing. */
    private void onSeriesSelected() {
        SeriesRow row = seriesList.getSelectedValue();
        if (row == null || !row.formula()) return;
        for (Derived d : activeExprs) {
            if (d.label().equals(row.label())) {
                exprField.setText(d.exprText());
                exprLabelField.setText(d.label());
                resolveCombo.setSelectedIndex(d.resolve() == SeriesExtractor.Resolve.STRICT ? 1 : 0);
                editingLabel = d.label();
                addFxButton.setText("Update");
                return;
            }
        }
    }

    private void clearFormulaFields() {
        editingLabel = null;
        exprField.setText("");
        exprLabelField.setText("");
        addFxButton.setText("Add f(x)");
    }

    private void removeSelectedSeries() {
        SeriesRow row = seriesList.getSelectedValue();
        if (row == null) return;
        if (row.formula()) {
            activeExprs.removeIf(d -> d.label().equals(row.label()));
            if (row.label().equals(editingLabel)) clearFormulaFields();
        } else {
            activeKeys.removeIf(k -> k.display().equals(row.label()));
        }
        reExtract();
    }

    /** Rebuild the Series list (raw keys then formulas, matching plot colour order) + f(x) suggestions. */
    private void seriesChanged() {
        String selected = seriesList.getSelectedValue() == null ? null : seriesList.getSelectedValue().label();
        seriesListModel.clear();
        for (GraphKey k : activeKeys) seriesListModel.addElement(new SeriesRow(k.display(), false));
        for (Derived d : activeExprs) seriesListModel.addElement(new SeriesRow(d.label(), true));
        if (selected != null) {
            for (int i = 0; i < seriesListModel.size(); i++) {
                if (seriesListModel.get(i).label().equals(selected)) { seriesList.setSelectedIndex(i); break; }
            }
        }
        rebuildLegendLabels();
        rebuildExprSuggestions();
    }

    private static javax.swing.Icon swatch(java.awt.Color color) {
        return new javax.swing.Icon() {
            @Override public int getIconWidth() { return 11; }
            @Override public int getIconHeight() { return 11; }
            @Override public void paintIcon(Component comp, java.awt.Graphics g, int x, int y) {
                g.setColor(color);
                g.fillRect(x, y + 1, 10, 10);
            }
        };
    }

    /** Series list cell: a colour swatch matching the plot, the label, and an ƒ marker for formulas. */
    private final class SeriesRowRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean sel, boolean foc) {
            super.getListCellRendererComponent(list, value, index, sel, foc);
            if (value instanceof SeriesRow r) {
                setText(r.formula() ? r.label() + "   ƒ(x)" : r.label());
                setIcon(swatch(ChartPanel.paletteColor(index)));
            }
            return this;
        }
    }

    public void bind(LogStore store, FilterState filter) {
        this.store = store;
        this.filter = filter;
        this.activeKeys.clear();
        this.activeExprs.clear();
        clearFormulaFields();
        seriesChanged();
        this.pinnedFrom = this.pinnedTo = null;   // a fresh log starts following the filter
        pinButton.setSelected(false);
        chart.clear();
        // seed the change-detection baseline so the first filter event is classified correctly
        this.lastDims = filter.dimensions() == null ? null : new java.util.HashSet<>(filter.dimensions());
        this.lastText = filter.text();
        this.lastGroupMode = filter.groupMode();
        filter.addListener(filterListener);
        refreshKeys();
    }

    /** Detach from the shared filter (call when the panel/tab is closed). */
    public void unbind() {
        extractDebounce.stop();
        if (filter != null) filter.removeListener(filterListener);
    }

    private static final char SPEC_SEP = '';

    /** The active series encoded for persistence ({@code instanceId<SEP>key}). */
    public List<String> seriesSpecs() {
        List<String> out = new ArrayList<>();
        for (GraphKey k : activeKeys) out.add(k.instanceId() + SPEC_SEP + k.key());
        return out;
    }

    /** Restore series from encoded specs (used when reopening a saved profile). */
    public void addSpecs(List<String> specs) {
        boolean added = false;
        for (String s : specs) {
            int i = s.indexOf(SPEC_SEP);
            if (i < 0) continue;
            GraphKey k = new GraphKey(s.substring(0, i), s.substring(i + 1));
            if (!activeKeys.contains(k)) { activeKeys.add(k); added = true; }
        }
        if (added) reExtract();
    }

    /** Forward plot clicks (the UTC time under the cursor) to a handler — e.g. scroll the table there. */
    public void setOnTimeClick(java.util.function.LongConsumer handler) {
        chart.setOnPlotClick(handler);
    }

    // ---- pinned range (spec-graph-artifacts §A) -------------------------------------------------

    public String graphName() { return graphName; }
    public void setGraphName(String name) { this.graphName = name == null ? "" : name; }

    /**
     * The explanation block and pinned notes drawn on the plot.
     *
     * <p>Distinct from {@link #setCaption} on purpose: the caption is a one-line provenance stamp under
     * the chart, and this is the reader-facing write-up drawn <b>on</b> it, so it survives an exported
     * PNG. A rationale that lives only in the app is lost exactly when the picture is shared.
     */
    public void setNotes(telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes notes) {
        chart.setNotes(notes);
        mutated();
    }

    /** Told when the user pins or edits a note on the chart itself, so the caller can persist it. */
    public void onNotesChanged(Runnable listener) {
        chart.onNotesChanged(listener);
    }

    public telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes notes() {
        return chart.notes();
    }

    /**
     * Mark the moment under diagnosis with a dashed rule and a label; {@code null} clears it.
     *
     * <p>Transient by construction — it is not a note and is never saved with the graph. A plot beside a
     * finding shows a trend but says nothing about <em>which</em> point of it the finding is about; this
     * is what joins the two.
     */
    public void setRecordMarker(Long atMillis, String label) {
        chart.setRecordMarker(atMillis, label);
    }

    /** Which series are measured against the right-hand scale. */
    public void setAxes(telamin.fluxtion.audit.analyser.analyser.graph.AxisAssignment axes) {
        chart.setAxes(axes);
        mutated();
    }

    public telamin.fluxtion.audit.analyser.analyser.graph.AxisAssignment axes() {
        return chart.axes();
    }

    /** The provenance caption (agent rationale), or "" when none. */
    public String caption() { return caption; }

    /** Set the provenance caption shown under the plot; blank/null hides it. */
    public void setCaption(String note) {
        this.caption = note == null ? "" : note;
        captionLabel.setText(caption.isBlank() ? "" : "ⓘ " + caption);
        captionLabel.setToolTipText(caption.isBlank() ? null : caption);
        captionLabel.setVisible(!caption.isBlank());
        mutated();
    }

    public boolean isPinned() { return pinnedFrom != null || pinnedTo != null; }
    public Long pinnedFrom() { return pinnedFrom; }
    public Long pinnedTo() { return pinnedTo; }

    /** Notify the owner (GraphTabs) when the pin state changes, so it can refresh the tab's 📌 indicator. */
    public void setOnPinChanged(Runnable r) { this.onPinChanged = r == null ? () -> { } : r; }

    /** Pin to a fixed window (null,null = unpin/follow); re-windows the chart and syncs the toggle + tab. */
    public void pin(Long from, Long to) {
        this.pinnedFrom = from;
        this.pinnedTo = to;
        pinButton.setSelected(isPinned());
        applyWindow();
        onPinChanged.run();
        mutated();
    }

    public void unpin() {
        pin(null, null);
    }

    /** Capture the current filter window (or the effective data bounds when unbounded) and pin to it. */
    public void pinToCurrentWindow() {
        Long from = filter == null ? null : filter.fromMillis();
        Long to = filter == null ? null : filter.toMillis();
        if (from == null && to == null) {            // unbounded filter → pin real data bounds, not nulls
            long[] b = chart.dataBounds();
            if (b != null) { from = b[0]; to = b[1]; }
        }
        if (from == null && to == null) { pinButton.setSelected(false); return; }   // nothing to pin
        pin(from, to);
    }

    /** Window the chart to the pinned range if pinned, else to the shared filter's range. */
    private void applyWindow() {
        if (isPinned()) chart.setViewWindow(pinnedFrom, pinnedTo);
        else if (filter != null) chart.setViewWindow(filter.fromMillis(), filter.toMillis());
    }

    /** Add resolved series (assistant {@code graph} action); dedup + redraw. Call on the EDT. */
    public void addKeys(List<GraphKey> keys) {
        boolean added = false;
        for (GraphKey k : keys) {
            if (!activeKeys.contains(k)) { activeKeys.add(k); added = true; }
        }
        if (added) reExtract();
    }

    /** Set the plot style by name ({@code step|line|points}); reflects in the combo. Call on the EDT. */
    public void setStyleByName(String style) {
        if (style == null) return;
        int idx = switch (style.toLowerCase()) {
            case "line" -> 1;
            case "points" -> 2;
            default -> 0;   // step
        };
        styleCombo.setSelectedIndex(idx);   // fires the listener → chart.setStyle
        mutated();
    }

    private void onFilterChanged() {
        if (filter == null) return;
        java.util.Set<String> dims = filter.dimensions();
        boolean structural = !java.util.Objects.equals(lastDims, dims)
                || !java.util.Objects.equals(lastText, filter.text())
                || lastGroupMode != filter.groupMode();
        if (structural) {
            lastDims = dims == null ? null : new java.util.HashSet<>(dims);
            lastText = filter.text();
            lastGroupMode = filter.groupMode();
            extractDebounce.restart();      // re-parse (off-EDT), coalesced
        } else if (!isPinned()) {
            // time-only change → just window the cached series (cheap); a PINNED graph ignores it and
            // holds its fixed window (evidence that survives the investigation moving on)
            chart.setViewWindow(filter.fromMillis(), filter.toMillis());
        }
    }


    private void refreshKeys() {
        if (store == null || filter == null) return;
        final LogStore s = store;
        final FilterState f = filter;
        Background.run(
                () -> SeriesExtractor.discover(s, f, DISCOVER_LIMIT),
                keys -> {
                    keyCombo.setModel(new DefaultComboBoxModel<>(keys.toArray(new GraphKey[0])));
                    List<String> displays = new ArrayList<>();
                    for (GraphKey k : keys) displays.add(k.display());
                    lastKeyDisplays = displays;
                    rebuildExprSuggestions();
                },
                err -> { /* best-effort */ });
    }

    /**
     * Re-extract the active series <b>across all time</b> (off-EDT), then window to the current time range.
     * Only called on a structural change (dimensions/text/keys) — a time-only change re-windows instead.
     * A generation counter drops results from a superseded extraction.
     */
    private void reExtract() {
        seriesChanged();   // keep the Series list in step with any add/remove of keys or formulas
        if (store == null || filter == null || (activeKeys.isEmpty() && activeExprs.isEmpty())) {
            chart.clear();
            return;
        }
        final LogStore s = store;
        final FilterState f = filter;
        final List<GraphKey> keys = new ArrayList<>(activeKeys);
        final List<Derived> exprs = new ArrayList<>(activeExprs);
        final int gen = ++extractGen;
        Background.run(
                () -> {
                    List<Series> out = new ArrayList<>();
                    for (GraphKey k : keys) out.add(SeriesExtractor.extract(s, f, k, true));   // acrossAllTime
                    java.util.Set<String> knownDisplays = new java.util.HashSet<>(lastKeyDisplays);
                    for (Derived d : exprs) {
                        try {
                            // expand references to other formulas' labels, then parse by STRUCTURE (refs
                            // split on the first dot) — no discovery gate, so a ref resolves wherever it fires
                            String text = expandFormulaRefs(d.exprText(), otherFormulas(exprs, d.label()), knownDisplays);
                            out.add(SeriesExtractor.extractExpr(s, f, Expr.parse(text), d.label(), true, d.resolve()));
                        } catch (RuntimeException ignore) {
                            // leave a syntactically-bad formula out of the plot
                        }
                    }
                    return out;
                },
                out -> {
                    if (gen != extractGen) return;   // a newer extraction superseded this one
                    chart.setSeries(out);
                    applyWindow();                   // pinned range if pinned, else the filter's window
                },
                err -> { /* best-effort */ });
    }

    /** Validate the f(x) field's syntax then add it — a ref that doesn't exist just plots empty. */
    private void addFormulaFromUi() {
        if (store == null) return;
        String exprText = exprField.getText().trim();
        if (exprText.isEmpty()) return;
        String label = exprLabelField.getText().isBlank() ? exprText : exprLabelField.getText().trim();
        SeriesExtractor.Resolve resolve = resolveCombo.getSelectedIndex() == 1
                ? SeriesExtractor.Resolve.STRICT : SeriesExtractor.Resolve.LOCF;
        String replacesLabel = editingLabel;   // set by Edit — the entry to replace on success
        java.util.Map<String, String> others = otherFormulas(activeExprs, replacesLabel != null ? replacesLabel : label);
        try {
            // expand references to other formulas, then syntax-check (refs split on the first dot;
            // a ref that doesn't exist just plots empty)
            Expr.parse(expandFormulaRefs(exprText, others, new java.util.HashSet<>(lastKeyDisplays)));
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(), "Formula error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (replacesLabel != null && !replacesLabel.equals(label)) {
            activeExprs.removeIf(d -> d.label().equals(replacesLabel));   // edited + renamed
        }
        addExpr(label, exprText, resolve);
        clearFormulaFields();
    }

    /** Add (or replace by label) a derived formula series and redraw. Call on the EDT. */
    public void addExpr(String label, String exprText, SeriesExtractor.Resolve resolve) {
        activeExprs.removeIf(d -> d.label().equals(label));
        activeExprs.add(new Derived(label, exprText, resolve == null ? SeriesExtractor.Resolve.LOCF : resolve));
        reExtract();   // refreshes the Series list too
        mutated();
    }

    /** Remove a series (raw or derived) by its display label — the overlay's right-click "Remove". */
    private void removeSeriesByLabel(String label) {
        boolean removed = activeKeys.removeIf(k -> k.display().equals(label));
        boolean formulaRemoved = activeExprs.removeIf(d -> d.label().equals(label));
        if (removed || formulaRemoved) {
            reExtract();
            mutated();
        }
    }

    // ---- f(x) autocomplete suggestions (keys ∪ formula labels) ----------------------------------

    /** Suggestions = discovered keys ∪ formula labels (labels that can't lex as a ref are backticked). */
    private void rebuildExprSuggestions() {
        List<String> out = new ArrayList<>(lastKeyDisplays);
        for (Derived d : activeExprs) {
            out.add(isPlainRefToken(d.label()) ? d.label() : "`" + d.label() + "`");
        }
        exprSuggestions = out;
    }

    /** Mirrors the Expr lexer's dotted-identifier grammar — such a label can be referenced bare. */
    private static boolean isPlainRefToken(String s) {
        return s != null && s.matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*");
    }

    /**
     * Expand references to other formulas' labels (bare tokens or {@code `backticked`}) into the
     * referenced expression, parenthesised, so a formula can build on another. Repeated passes cover
     * chains; the pass cap breaks reference cycles, whose leftover label then fails Expr.parse with
     * the usual actionable unknown-key error. Labels that collide with a real key are not expanded
     * (the key wins).
     */
    static String expandFormulaRefs(String exprText, java.util.Map<String, String> labelToExpr,
                                    java.util.Set<String> knownKeyDisplays) {
        if (labelToExpr.isEmpty()) return exprText;
        List<String> labels = new ArrayList<>(labelToExpr.keySet());
        labels.sort((a, b) -> b.length() - a.length());   // longest first: no partial shadowing
        String cur = exprText;
        for (int pass = 0; pass < 8; pass++) {
            String before = cur;
            for (String label : labels) {
                if (knownKeyDisplays.contains(label)) continue;   // a real key wins over a formula label
                String replacement = "(" + labelToExpr.get(label) + ")";
                cur = cur.replace("`" + label + "`", replacement);
                if (isPlainRefToken(label)) {
                    cur = cur.replaceAll(
                            "(?<![A-Za-z0-9_.`])" + java.util.regex.Pattern.quote(label) + "(?![A-Za-z0-9_.`])",
                            java.util.regex.Matcher.quoteReplacement(replacement));
                }
            }
            if (cur.equals(before)) break;
        }
        return cur;
    }

    /** The label→expr map for expanding {@code target}'s references (its own label excluded). */
    private static java.util.Map<String, String> otherFormulas(List<Derived> all, String targetLabel) {
        java.util.Map<String, String> m = new java.util.LinkedHashMap<>();
        for (Derived d : all) if (!d.label().equals(targetLabel)) m.put(d.label(), d.exprText());
        return m;
    }

    private static boolean isTokenChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '.';
    }

    /**
     * A completion dropdown for the f(x) field: as you type a ref token (a dotted identifier, or the text
     * inside an unterminated {@code `…`}), a popup lists matching keys and formula labels. ↓/↑ move, Enter
     * or Tab or a click accept, Esc dismisses. Enter with no popup submits the formula. Non-focusable so
     * the field keeps focus and caret while the list is up.
     */
    private final class ExprCompletion {
        private final JTextField field;
        private final DefaultListModel<String> model = new DefaultListModel<>();
        private final JList<String> list = new JList<>(model);
        private Popup popup;
        private boolean showing;
        private int tokenStart;      // doc offset where the current token begins (after any opening `)
        private boolean tokenTicked; // the token is inside backticks

        ExprCompletion(JTextField field) {
            this.field = field;
            list.setFocusable(false);
            list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            list.setFont(field.getFont());
            list.setVisibleRowCount(8);
            list.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                    int i = list.locationToIndex(e.getPoint());
                    if (i >= 0) { list.setSelectedIndex(i); accept(); }
                }
            });

            field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
                @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refreshLater(); }
                @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refreshLater(); }
                @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
            });
            field.addFocusListener(new java.awt.event.FocusAdapter() {
                @Override public void focusLost(java.awt.event.FocusEvent e) { hide(); }
            });

            // key handling takes precedence over the field's default bindings while the popup is up
            InputMap im = field.getInputMap(JComponent.WHEN_FOCUSED);
            ActionMap am = field.getActionMap();
            bind(im, am, "exprDown", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), this::onDown);
            bind(im, am, "exprUp", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), this::onUp);
            bind(im, am, "exprAccept", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), this::onEnter);
            bind(im, am, "exprTab", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_TAB, 0), this::onTab);
            bind(im, am, "exprEsc", KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), this::onEsc);
        }

        private void bind(InputMap im, ActionMap am, String name, KeyStroke ks, Runnable action) {
            im.put(ks, name);
            am.put(name, new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { action.run(); }
            });
        }

        private void refreshLater() {
            SwingUtilities.invokeLater(this::refresh);
        }

        /** Recompute the token under the caret and the matching suggestions; show or hide the popup. */
        private void refresh() {
            if (!field.hasFocus()) { hide(); return; }   // ignore programmatic setText (e.g. loading a formula)
            String text = field.getText();
            int caret = field.getCaretPosition();
            boolean inTick = 0 != countTicks(text, caret) % 2;
            int start;
            if (inTick) {
                start = text.lastIndexOf('`', caret - 1) + 1;
            } else {
                start = caret;
                while (start > 0 && isTokenChar(text.charAt(start - 1))) start--;
            }
            String token = text.substring(start, caret);
            if (token.isEmpty() && !inTick) { hide(); return; }

            String tokenLower = token.toLowerCase();
            model.clear();
            for (String cand : exprSuggestions) {
                boolean ticked = cand.startsWith("`");
                String plain = ticked ? cand.substring(1, cand.length() - 1) : cand;
                if (plain.toLowerCase().startsWith(tokenLower) && !plain.equalsIgnoreCase(token)) {
                    model.addElement(plain);
                }
                if (model.size() >= 200) break;
            }
            if (model.isEmpty()) { hide(); return; }
            this.tokenStart = start;
            this.tokenTicked = inTick;
            list.setSelectedIndex(0);
            show();
        }

        private void show() {
            hide();
            try {
                java.awt.Rectangle r = field.modelToView2D(tokenStart).getBounds();
                java.awt.Point p = new java.awt.Point(r.x, r.y + r.height + 1);
                SwingUtilities.convertPointToScreen(p, field);
                JScrollPane sp = new JScrollPane(list);
                int rows = Math.min(model.size(), 8);
                int rowH = Math.max(16, list.getFontMetrics(list.getFont()).getHeight() + 2);
                sp.setPreferredSize(new java.awt.Dimension(Math.max(180, field.getWidth() / 2), rows * rowH + 4));
                popup = PopupFactory.getSharedInstance().getPopup(field, sp, p.x, p.y);
                popup.show();
                showing = true;
            } catch (javax.swing.text.BadLocationException ignore) {
                showing = false;
            }
        }

        private void hide() {
            if (popup != null) { popup.hide(); popup = null; }
            showing = false;
        }

        private void accept() {
            String chosen = list.getSelectedValue();
            if (chosen == null) { hide(); return; }
            int caret = field.getCaretPosition();
            String insert = tokenTicked ? chosen + "`" : chosen;   // opening ` is already in the text
            try {
                javax.swing.text.Document doc = field.getDocument();
                doc.remove(tokenStart, caret - tokenStart);
                doc.insertString(tokenStart, insert, null);
            } catch (javax.swing.text.BadLocationException ignore) {
                // fall through — nothing inserted
            }
            hide();
        }

        // ---- keystrokes (only act when the popup is up; otherwise defer to normal field behaviour) ----

        private void onDown() {
            if (showing) { move(1); } else { refresh(); }
        }

        private void onUp() {
            if (showing) move(-1);
        }

        private void onEnter() {
            if (showing) accept();
            else addFormulaFromUi();   // no popup → Enter submits the formula (was the field's action)
        }

        private void onTab() {
            if (showing) accept();
            else field.transferFocus();
        }

        private void onEsc() {
            hide();
        }

        private void move(int delta) {
            int n = model.size();
            if (n == 0) return;
            int i = (list.getSelectedIndex() + delta + n) % n;
            list.setSelectedIndex(i);
            list.ensureIndexIsVisible(i);
        }

        private int countTicks(String s, int end) {
            int c = 0;
            for (int i = 0; i < end && i < s.length(); i++) if (s.charAt(i) == '`') c++;
            return c;
        }
    }

    /** Quote a CSV field if it contains a comma or quote (formula labels can). */
    private static String csv(String v) {
        if (v == null) return "";
        return (v.contains(",") || v.contains("\"")) ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
    }

    /**
     * Every label currently on this graph: raw keys as {@code instanceId.key} plus formula labels — the
     * names {@code rightAxis} / note {@code series} refer to. Used for echo warnings (M26.4).
     */
    public java.util.Set<String> plottedLabels() {
        java.util.Set<String> out = new java.util.LinkedHashSet<>();
        for (GraphKey k : activeKeys) out.add(k.display());
        for (Derived d : activeExprs) out.add(d.label());
        return out;
    }

    /** The derived formulas, for persistence. */
    public List<GraphSpec.ExprSpec> exprSpecs() {
        List<GraphSpec.ExprSpec> out = new ArrayList<>();
        for (Derived d : activeExprs) out.add(new GraphSpec.ExprSpec(d.label(), d.exprText(), d.resolve().name()));
        return out;
    }

    private void exportCsv() {
        if (store == null || (activeKeys.isEmpty() && activeExprs.isEmpty())) return;
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("series.csv"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        Path out = fc.getSelectedFile().toPath();
        final LogStore s = store;
        final FilterState f = filter;
        final List<GraphKey> keys = new ArrayList<>(activeKeys);
        final List<Derived> exprs = new ArrayList<>(activeExprs);
        Background.run(
                () -> {
                    List<Series> all = new ArrayList<>();
                    for (GraphKey k : keys) all.add(SeriesExtractor.extract(s, f, k, true));
                    java.util.Set<String> knownDisplays = new java.util.HashSet<>(lastKeyDisplays);
                    for (Derived d : exprs) {
                        try {
                            String text = expandFormulaRefs(d.exprText(), otherFormulas(exprs, d.label()), knownDisplays);
                            all.add(SeriesExtractor.extractExpr(s, f, Expr.parse(text), d.label(), true, d.resolve()));
                        } catch (RuntimeException ignore) { /* skip bad formula */ }
                    }
                    StringBuilder sb = new StringBuilder("series,logTime,utc,value\n");
                    for (Series ser : all) {
                        for (int i = 0; i < ser.size(); i++) {
                            sb.append(csv(ser.label())).append(',')
                                    .append(ser.x(i)).append(',')
                                    .append(TimeFormat.utc(ser.x(i))).append(',')
                                    .append(ser.y(i)).append('\n');
                        }
                    }
                    try {
                        Files.writeString(out, sb.toString());
                    } catch (IOException ex) {
                        throw new UncheckedIOException(ex);
                    }
                    return out;
                },
                path -> JOptionPane.showMessageDialog(this, "Exported to " + path),
                err -> JOptionPane.showMessageDialog(this, "Export failed: " + err.getMessage(),
                        "Export", JOptionPane.ERROR_MESSAGE));
    }

    private void exportPng() {
        JFileChooser fc = new JFileChooser();
        fc.setSelectedFile(new java.io.File("graph.png"));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        java.io.File file = fc.getSelectedFile();
        try {
            String fmt = file.getName().toLowerCase().endsWith(".jpg") || file.getName().toLowerCase().endsWith(".jpeg")
                    ? "jpg" : "png";
            javax.imageio.ImageIO.write(chart.toImage(), fmt, file);
            JOptionPane.showMessageDialog(this, "Saved " + file);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Export failed: " + ex.getMessage(), "Export", JOptionPane.ERROR_MESSAGE);
        }
    }

    private static DefaultListCellRenderer graphKeyRenderer() {
        return new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof GraphKey k) setText(k.display());
                return this;
            }
        };
    }
}
