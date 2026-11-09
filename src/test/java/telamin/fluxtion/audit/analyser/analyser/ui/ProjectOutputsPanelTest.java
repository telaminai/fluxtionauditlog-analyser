package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.ProjectOutputsState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ProjectOutputsPanelTest {
    private static ProjectOutputsState state(long watch, long... ids) {
        var file = new ProjectOutputsState.File(ProjectOutputsState.Kind.GRAPHML, 1, "graph", "Processor.graphml",
                new ProjectOutputsState.Revision(20, 10, "file"));
        var offers = new ArrayList<ProjectOutputsState.Offer>();
        for (long id : ids) offers.add(new ProjectOutputsState.Offer(id, file, null));
        return new ProjectOutputsState(watch, 1, true, offers);
    }

    @Test void countsPendingMessagesWithoutStealingFocusAndClearsUnreadWhenViewed() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var tabs = new JTabbedPane();
            var topology = new JPanel();
            tabs.addTab("Topology", topology);
            var panel = new ProjectOutputsPanel(answer -> fail("viewing must not accept an offer"));
            panel.installTab(tabs);
            assertEquals("Messages (0)", tabs.getTitleAt(1));
            panel.render(state(1, 2, 3));
            assertSame(topology, tabs.getSelectedComponent());
            assertEquals("Messages (2)", tabs.getTitleAt(1));
            assertEquals(UiTheme.attentionForeground(), tabs.getForegroundAt(1));
            assertEquals("2 pending messages; 2 unread", tabs.getToolTipTextAt(1));
            tabs.setSelectedComponent(panel);
            assertEquals("Messages (2)", tabs.getTitleAt(1), "viewing is not dismissing");
            assertEquals("2 pending messages; 0 unread", tabs.getToolTipTextAt(1));
            assertNotEquals(UiTheme.attentionForeground(), tabs.getForegroundAt(1));
            tabs.setSelectedComponent(topology);
            panel.render(state(1, 2, 3));
            assertEquals("2 pending messages; 0 unread", tabs.getToolTipTextAt(1));
            panel.render(state(1, 2, 3, 4));
            assertEquals("3 pending messages; 1 unread", tabs.getToolTipTextAt(1));
            panel.render(state(1));
            assertEquals("Messages (0)", tabs.getTitleAt(1));
            tabs.setSelectedComponent(panel);
            assertTrue(panel.isVisible(), "empty messages remain an available tab");
        });
    }

    @Test void projectSwitchResetsUnreadAndActionsKeepTheirWatchAndOfferIdentity() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var answered = new ArrayList<SessionEvents.ProjectOutputOfferAnswered>();
            var tabs = new JTabbedPane();
            var panel = new ProjectOutputsPanel(answered::add);
            tabs.addTab("Topology", new JPanel());
            panel.installTab(tabs);
            panel.render(state(1, 2));
            tabs.setSelectedComponent(panel);
            tabs.setSelectedIndex(0);
            panel.render(state(9, 2));
            assertEquals("1 pending messages; 1 unread", tabs.getToolTipTextAt(1));
            button(panel, "projectOutputOpen-2").doClick();
            button(panel, "projectOutputNotNow-2").doClick();
            assertEquals(List.of(new SessionEvents.ProjectOutputOfferAnswered(9, 2, true),
                    new SessionEvents.ProjectOutputOfferAnswered(9, 2, false)), answered);
        });
    }

    @Test void bulkButtonsCaptureTheVisibleMessagesAndDisableWhenEmpty() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var answers = new ArrayList<SessionEvents.ProjectOutputOfferAnswered>();
            var panel = new ProjectOutputsPanel(answers::add);
            JButton accept = button(panel, "projectOutputAcceptAll");
            JButton clear = button(panel, "projectOutputClearAll");
            assertFalse(accept.isEnabled()); assertFalse(clear.isEnabled());
            panel.render(state(7, 2, 3));
            accept.doClick(); clear.doClick();
            assertEquals(List.of(new SessionEvents.ProjectOutputOfferAnswered(7, 0, true, List.of(2L, 3L)),
                    new SessionEvents.ProjectOutputOfferAnswered(7, 0, false, List.of(2L, 3L))), answers);
            panel.render(state(7));
            assertFalse(accept.isEnabled()); assertFalse(clear.isEnabled());
        });
    }

    private static JButton button(Container root, String name) {
        for (Component c : root.getComponents()) {
            if (c instanceof JButton b && name.equals(b.getName())) return b;
            if (c instanceof Container nested) {
                JButton found = button(nested, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
