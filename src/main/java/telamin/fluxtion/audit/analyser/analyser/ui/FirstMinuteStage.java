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
        card.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        card.add(callout, BorderLayout.NORTH);
        JPanel doors = new JPanel(new GridLayout(1, 3, 8, 0));
        doors.add(door(FirstMinuteLines.LEARN, FirstMinuteLines.GUIDED_TOUR, "learn", intent));
        JPanel investigate = door(FirstMinuteLines.INVESTIGATE, FirstMinuteLines.OPEN_LOG, "investigate", intent);
        JButton sample = UiText.button(FirstMinuteLines.SAMPLE);
        sample.addActionListener(e -> intent.accept(new SessionEvents.FirstMinuteDoorChosen("sample")));
        investigate.add(sample, BorderLayout.SOUTH);
        doors.add(investigate);
        doors.add(door(FirstMinuteLines.BUILD, FirstMinuteLines.TEMPLATE, "build", intent));
        card.add(doors, BorderLayout.CENTER);
        JButton close = UiText.button(FirstMinuteLines.CLOSE);
        close.addActionListener(e -> intent.accept(new SessionEvents.FirstMinuteCardClosed()));
        card.add(close, BorderLayout.EAST);
        add(card, BorderLayout.SOUTH);
        card.setVisible(false);
    }

    private static JPanel door(String name, String action, String id, Consumer<Object> intent) {
        JPanel panel = new JPanel(new BorderLayout(2, 2));
        panel.add(UiText.label(name), BorderLayout.NORTH);
        JButton button = UiText.button(action);
        button.addActionListener(e -> intent.accept(new SessionEvents.FirstMinuteDoorChosen(id)));
        panel.add(button, BorderLayout.CENTER);
        return panel;
    }

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
        SwingUtilities.invokeLater(() -> {
            upper.setDividerLocation(WalkPlayback.TOPOLOGY_SHARE);
            lower.setDividerLocation(WalkPlayback.TOPOLOGY_SHARE);
            body.setDividerLocation(Math.max(150, body.getHeight() - Math.max(190, stripHeight)));
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
            validate(); topologyPanel.fit();
        });
    }
}
