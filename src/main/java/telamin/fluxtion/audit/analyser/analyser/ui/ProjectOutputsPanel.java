package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.ProjectOutputsState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;
import java.util.HashSet;
import java.util.Set;

/** M81 D: render the immutable offers and report the person's scoped click. No discovery or open policy here. */
final class ProjectOutputsPanel extends JPanel {
    private final Consumer<SessionEvents.ProjectOutputOfferAnswered> answered;
    private final JPanel rows = new JPanel();
    private final JScrollPane scroll = new JScrollPane(rows,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    private JTabbedPane tabs;
    private final Set<Long> read = new HashSet<>();
    private final JLabel empty = new JLabel("No pending messages.");
    private ProjectOutputsState rendered = ProjectOutputsState.NONE;
    ProjectOutputsPanel(Consumer<SessionEvents.ProjectOutputOfferAnswered> answered) {
        this.answered = answered;
        setLayout(new BorderLayout());
        rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        JPanel content = new JPanel(new BorderLayout());
        content.add(rows, BorderLayout.NORTH);
        scroll.setViewportView(content);
        add(scroll, BorderLayout.CENTER);
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        rows.add(empty);
        setName("projectOutputOffers");
    }
    void installTab(JTabbedPane tabs) {
        this.tabs = tabs;
        tabs.addTab("Messages (0)", this);
        tabs.addChangeListener(e -> refreshTab());
        refreshTab();
    }
    private void refreshTab() {
        if (tabs == null) return;
        int index = tabs.indexOfComponent(this);
        if (index < 0) return;
        if (tabs.getSelectedComponent() == this)
            rendered.offers().forEach(offer -> read.add(offer.id()));
        long unread = rendered.offers().stream().filter(offer -> !read.contains(offer.id())).count();
        tabs.setTitleAt(index, UiText.inert("Messages (" + rendered.offers().size() + ")"));
        tabs.setForegroundAt(index, unread > 0 ? UiTheme.attentionForeground() : UIManager.getColor("TabbedPane.foreground"));
        tabs.setToolTipTextAt(index, UiText.inert(rendered.offers().size() + " pending messages; " + unread + " unread"));
        getAccessibleContext().setAccessibleDescription(tabs.getToolTipTextAt(index));
    }
    @Override public void updateUI() {
        super.updateUI();
        refreshTab();
    }
    void render(ProjectOutputsState state) {
        if (rendered.equals(state)) { refreshTab(); return; }
        if (rendered.watch() != state.watch()) read.clear();
        rendered = state;
        read.retainAll(state.offers().stream().map(ProjectOutputsState.Offer::id).toList());
        rows.removeAll();
        if (state.offers().isEmpty()) rows.add(empty);
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
        refreshTab();
        revalidate();
        repaint();
    }
}
