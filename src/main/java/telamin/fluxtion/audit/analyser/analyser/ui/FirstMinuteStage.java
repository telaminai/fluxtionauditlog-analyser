package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.firstminute.FirstMinuteLines;
import telamin.fluxtion.audit.analyser.analyser.session.FirstMinuteState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.node.WalkPlayback;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** A view of the published stage/card. Buttons report intents only; no file open, play or stage decision lives here. */
final class FirstMinuteStage extends JPanel {
    private final JSplitPane upper = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final JSplitPane lower = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    private final JSplitPane body = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upper, lower);
    private final JPanel card = new JPanel(new BorderLayout(8, 4));
    private final JLabel title = UiText.label(FirstMinuteLines.TITLE);
    private final JLabel callout = UiText.label(FirstMinuteLines.CALLOUT);
    private JComponent recordsComponent, detailComponent;
    private Dimension recordsMinimum, detailMinimum;
    private boolean recordsMinimumSet, detailMinimumSet;
    private TopologyPanel topologyPanel;
    private java.util.List<JButton> doorButtons = java.util.List.of();
    /** Remeasure what is lit after a refit: a spotlight measured before it points where the boxes used to be. */
    private Runnable refitted = () -> { };
    void onRefit(Runnable then) { refitted = then == null ? () -> { } : then; }
    private final JButton close = UiText.button("✕");

    FirstMinuteStage(Consumer<Object> intent) {
        super(new BorderLayout(6, 6));
        setName("first-minute-stage");
        setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        add(title, BorderLayout.NORTH);
        add(body, BorderLayout.CENTER);
        upper.setResizeWeight(WalkPlayback.TOPOLOGY_SHARE);
        lower.setResizeWeight(WalkPlayback.TOPOLOGY_SHARE);
        body.setResizeWeight(0.68);
        upper.setContinuousLayout(true); lower.setContinuousLayout(true); body.setContinuousLayout(true);
        // Owner, 2026-10-27 ("the buttons on the bottom look weird"): one row, every door button the same size, the
        // Sample project link beside Open audit log rather than stacked under it, and Close a small ✕, not a square.
        card.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        callout.setFont(callout.getFont().deriveFont(java.awt.Font.BOLD));
        card.add(callout, BorderLayout.NORTH);
        JButton tour = doorButton(FirstMinuteLines.GUIDED_TOUR, "learn", intent);
        JButton open = doorButton(FirstMinuteLines.OPEN_LOG, "investigate", intent);
        JButton sample = doorButton(FirstMinuteLines.SAMPLE, "sample", intent);
        JButton template = doorButton(FirstMinuteLines.TEMPLATE, "build", intent);
        doorButtons = java.util.List.of(tour, open, sample, template);
        Dimension same = new Dimension(0, 0);
        for (JButton b : doorButtons) {
            Dimension d = b.getPreferredSize();
            same = new Dimension(Math.max(same.width, d.width), Math.max(same.height, d.height));
        }
        for (JButton b : doorButtons) { b.setPreferredSize(same); b.setMinimumSize(same); b.setMaximumSize(same); }
        JPanel doors = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        doors.add(group(FirstMinuteLines.LEARN, tour));
        doors.add(group(FirstMinuteLines.INVESTIGATE, open, sample));
        doors.add(group(FirstMinuteLines.BUILD, template));
        card.add(doors, BorderLayout.CENTER);
        close.setToolTipText(FirstMinuteLines.CLOSE);
        close.getAccessibleContext().setAccessibleName(FirstMinuteLines.CLOSE);
        close.addActionListener(e -> intent.accept(new SessionEvents.FirstMinuteCardClosed()));
        JPanel closeBox = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 2));
        closeBox.add(close);
        card.add(closeBox, BorderLayout.EAST);
        add(card, BorderLayout.SOUTH);
        card.setVisible(false);
    }

    private static JButton doorButton(String action, String id, Consumer<Object> intent) {
        JButton button = UiText.button(action);
        button.addActionListener(e -> intent.accept(new SessionEvents.FirstMinuteDoorChosen(id)));
        return button;
    }

    private static JPanel group(String name, JButton... buttons) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JLabel label = UiText.label(name);
        label.setForeground(UiTheme.mutedForeground());
        panel.add(label);
        for (JButton b : buttons) panel.add(b);
        return panel;
    }

    /** The doors and Close, for the layout witness. */
    java.util.List<JButton> doorButtons() { return doorButtons; }
    JButton closeButton() { return close; }

    void attach(JComponent topology, JComponent design, JComponent records, JComponent detail) {
        topologyPanel = topology instanceof TopologyPanel panel ? panel : null;
        recordsComponent = records; detailComponent = detail;
        recordsMinimumSet = records.isMinimumSizeSet(); detailMinimumSet = detail.isMinimumSizeSet();
        recordsMinimum = records.getMinimumSize(); detailMinimum = detail.getMinimumSize();
        int stripHeight = recordsMinimum.height + (records instanceof LogTablePanel table
                ? (WalkPlayback.RECORD_STRIP_ROWS - LogTablePanel.MIN_VISIBLE_ROWS) * table.table().getRowHeight() : 0);
        upper.setLeftComponent(topology); upper.setRightComponent(design);
        lower.setLeftComponent(records); lower.setRightComponent(detail);
        records.setMinimumSize(new Dimension(200, stripHeight));
        detail.setMinimumSize(new Dimension(180, 120));
        upper.setMinimumSize(new Dimension(200, 150));
        lower.setMinimumSize(new Dimension(200, stripHeight));
        if (topologyPanel != null) topologyPanel.foldIndex();
        SwingUtilities.invokeLater(() -> {
            upper.setDividerLocation(WalkPlayback.TOPOLOGY_SHARE);
            lower.setDividerLocation(WalkPlayback.TOPOLOGY_SHARE);
            body.setDividerLocation(Math.max(150, body.getHeight() - Math.max(190, stripHeight)));
            // Fit once the stage has its final size: a fit made in the old side tab leaves the diamond tiny.
            SwingUtilities.invokeLater(() -> { validate(); if (topologyPanel != null) topologyPanel.fit(); refitted.run(); });
        });
    }

    void detach() {
        upper.setLeftComponent(null); upper.setRightComponent(null);
        lower.setLeftComponent(null); lower.setRightComponent(null);
        if (recordsComponent != null) recordsComponent.setMinimumSize(recordsMinimumSet ? recordsMinimum : null);
        if (detailComponent != null) detailComponent.setMinimumSize(detailMinimumSet ? detailMinimum : null);
    }

    void render(FirstMinuteState state) {
        title.setVisible(state.bundled());
        boolean doors = "DOORS".equals(state.phase());
        boolean resized = card.isVisible() != doors;
        card.setVisible(doors);
        callout.setText(UiText.inert(state.callout()));
        revalidate(); repaint();
        // The card changes the viewport, not session state. Refit after layout so the graph
        // previously fitted into the taller canvas is not clipped by its new bounds.
        if (resized && topologyPanel != null) SwingUtilities.invokeLater(() -> {
            validate(); topologyPanel.fit(); refitted.run();
        });
    }
}
