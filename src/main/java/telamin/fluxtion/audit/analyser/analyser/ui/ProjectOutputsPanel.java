package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.ProjectOutputsState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** M81 D: render the immutable offers and report the person's scoped click. No discovery or open policy here. */
final class ProjectOutputsPanel extends JPanel {
    private final Consumer<SessionEvents.ProjectOutputOfferAnswered> answered;
    private final JPanel rows = new JPanel();
    private final JScrollPane scroll = new JScrollPane(rows,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    private ProjectOutputsState rendered = ProjectOutputsState.NONE;
    ProjectOutputsPanel(Consumer<SessionEvents.ProjectOutputOfferAnswered> answered) {
        this.answered = answered;
        setLayout(new BorderLayout());
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        add(scroll, BorderLayout.CENTER);
        setName("projectOutputOffers");
        setVisible(false);
    }
    void render(ProjectOutputsState state) {
        if (rendered.equals(state)) return;
        rendered = state;
        rows.removeAll();
        for (var offer : state.offers()) {
            JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 3));
            JLabel text = new JLabel(UiText.inert(offer.text()));
            String files = offer.file().shown() + (offer.graph() == null ? "" : " + " + offer.graph().shown());
            text.setToolTipText(UiText.inert(files + " · stable size/time, not a completeness claim"));
            row.add(text);
            JLabel names = new JLabel(UiText.inert(files));
            names.setForeground(UIManager.getColor("Label.disabledForeground"));
            row.add(names);
            JButton open = new JButton("Open");
            open.setName("projectOutputOpen-" + offer.id());
            open.addActionListener(e -> answered.accept(new SessionEvents.ProjectOutputOfferAnswered(state.watch(), offer.id(), true)));
            row.add(open);
            JButton notNow = new JButton("Not now");
            notNow.setName("projectOutputNotNow-" + offer.id());
            notNow.addActionListener(e -> answered.accept(new SessionEvents.ProjectOutputOfferAnswered(state.watch(), offer.id(), false)));
            row.add(notNow);
            rows.add(row);
        }
        scroll.setPreferredSize(new Dimension(10, Math.min(110, rows.getPreferredSize().height + 3)));
        setVisible(!state.offers().isEmpty());
        revalidate();
        repaint();
    }
}
