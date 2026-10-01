package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.ProjectReopen;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.nio.file.Path;
import java.util.List;

/**
 * "This project has logs and topologies — open any of them?" (O3).
 *
 * <p>Two lists, because a log and a topology are not interchangeable, and each is independently
 * optional: every list carries a <em>Don't open one</em> row, so opening a topology alone, a log
 * alone, both, or neither are all ordinary answers rather than special cases. The detail pane always
 * names BOTH choices in full, since the file name alone is what made two of these ambiguous in the
 * first place and a person deciding needs to see where each one actually is.
 *
 * <p>Nothing here reaches the filesystem or the session: it returns what was chosen.
 */
final class ProjectReopenDialog {

    private ProjectReopenDialog() {
    }

    /** A null in either position means "not this one"; {@code null} overall means Skip. */
    record Choice(String log, String topology) {
        boolean nothing() {
            return log == null && topology == null;
        }
    }

    /** The "Don't open one" row. A sentinel rather than an empty selection: it can be SEEN and clicked. */
    private static final String NONE = "\u0000none";

    static Choice choose(Component owner, String projectLabel, ProjectReopen candidates) {
        JList<String> logs = list(candidates.logs(), "Don't open a log");
        JList<String> graphs = list(candidates.topologies(), "Don't open a topology");

        JTextArea detail = Fluid.text("");
        detail.setBorder(BorderFactory.createEmptyBorder(8, 2, 8, 8));
        detail.setBackground(UiTheme.surface());
        Runnable describe = () -> detail.setText(
                "Log\n" + describe(logs.getSelectedValue())
                        + "\n\nTopology\n" + describe(graphs.getSelectedValue()));
        logs.addListSelectionListener(e -> describe.run());
        graphs.addListSelectionListener(e -> describe.run());
        // Default to the most recent of each -- the common answer -- with "don't" one click away.
        logs.setSelectedIndex(candidates.logs().isEmpty() ? 0 : 1);
        graphs.setSelectedIndex(candidates.topologies().isEmpty() ? 0 : 1);
        describe.run();

        JPanel north = new JPanel();
        north.setLayout(new BoxLayout(north, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Open a log or topology — " + projectLabel);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(title);
        north.add(Box.createVerticalStrut(4));
        JTextArea note = Fluid.text("A project profile holds settings, not what was on screen, so nothing is "
                + "reopened for you. Each list states where its candidates came from. Machine recent files "
                + "are not claimed to belong to this project. Choose either, both or neither.");
        note.setForeground(UiTheme.mutedForeground());
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        north.add(note);

        JPanel lists = new JPanel(new java.awt.GridLayout(1, 2, 10, 0));
        lists.add(titled("Audit logs — " + origin(candidates.logOrigin()), logs));
        lists.add(titled("Topologies — " + origin(candidates.topologyOrigin()), graphs));

        JPanel detailPane = new JPanel(new BorderLayout(0, 8));
        detailPane.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 12));
        detailPane.setBackground(UiTheme.surface());
        JLabel heading = new JLabel("What will open");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 15f));
        detailPane.add(heading, BorderLayout.NORTH);
        JScrollPane explanation = new JScrollPane(detail, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        UiTheme.applySurface(explanation, detail);
        detailPane.add(explanation, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, lists, detailPane);
        split.setBorder(BorderFactory.createLineBorder(UiTheme.surfaceEdge()));
        split.setResizeWeight(0.62);
        split.setDividerLocation(520);
        split.setPreferredSize(new Dimension(880, 380));

        JPanel panel = new JPanel(new BorderLayout(0, 14));
        panel.setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        panel.add(north, BorderLayout.NORTH);
        panel.add(split, BorderLayout.CENTER);

        Object[] options = {"Open", "Not now"};
        int answer = JOptionPane.showOptionDialog(owner, panel, "Open a log or topology",
                JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        if (answer != 0) return null;
        Choice chosen = new Choice(chosenValue(logs), chosenValue(graphs));
        return chosen.nothing() ? null : chosen;
    }

    private static String origin(ProjectReopen.Origin origin) {
        return origin == ProjectReopen.Origin.PROJECT ? "project locations" : "machine recent files";
    }

    private static String chosenValue(JList<String> list) {
        String value = list.getSelectedValue();
        return value == null || NONE.equals(value) ? null : value;
    }

    private static String describe(String value) {
        if (value == null || NONE.equals(value)) return "    Nothing will be opened.";
        return "    " + value;
    }

    private static JList<String> list(List<String> paths, String noneLabel) {
        java.util.List<String> rows = new java.util.ArrayList<>();
        rows.add(NONE);
        rows.addAll(paths);
        JList<String> list = new JList<>(rows.toArray(String[]::new));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(30);
        list.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> component, Object value, int index,
                                                          boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(component, value, index, selected, focus);
                if (NONE.equals(value)) {
                    label.setText(noneLabel);
                    label.setFont(label.getFont().deriveFont(Font.ITALIC));
                } else if (value != null) {
                    Path file = Path.of(String.valueOf(value));
                    label.setText(String.valueOf(file.getFileName()));
                    label.setToolTipText(String.valueOf(value));
                }
                return label;
            }
        });
        return list;
    }

    private static JPanel titled(String heading, JList<String> list) {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        JLabel label = new JLabel(heading);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
        panel.add(label, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createLineBorder(UiTheme.surfaceEdge()));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }
}
