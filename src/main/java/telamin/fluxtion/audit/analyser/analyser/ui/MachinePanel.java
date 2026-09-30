package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.List;
import java.util.Map;

/**
 * What THIS MACHINE remembers, under the Context column and beneath the project it qualifies.
 *
 * <p>The three columns say what the session is made of: context (what the system is), facts (what it
 * did), canvas (what you make of it). There is a fourth party with no column — the machine tier: your
 * recent lists, the focus you were last on, where a bundle's source lives on this laptop, the working
 * copies unpacked here. None of it travels, none of it is evidence, and until now none of it was
 * visible either — which is exactly where a run of confusions lived: an anchor written against the
 * wrong bundle, ten recent entries that were all throwaway unpacks, a project that came up empty
 * because the settings were loaded but never applied (2026-09-30).
 *
 * <p>So it sits under Context, indented by meaning rather than by pixels: it qualifies the project
 * above it — "and on this machine, that project means…" — without claiming to be part of it. Nothing
 * here is written to a profile, and the panel says so, because the whole point of the boundary is
 * that a colleague opening the repository does not inherit any of it.
 *
 * <p>A rendering of state that already exists, like {@link ProjectPanel}: it reads the same
 * {@code context} payload an agent gets, so the two cannot drift.
 */
final class MachinePanel extends JPanel {

    private final JPanel body = new JPanel();

    /** Asked to clear the working copies not in use; the frame performs it and re-renders. */
    private Runnable clearCopies = () -> { };

    void onClearWorkingCopies(Runnable action) {
        this.clearCopies = action == null ? () -> { } : action;
    }

    MachinePanel() {
        super(new BorderLayout());
        setBorder(UiTheme.section("Private settings"));
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(UiTheme.pad());
        JScrollPane scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        setMinimumSize(new Dimension(120, 60));
        render(null, null, null);
    }

    /**
     * @param context     the {@code context} payload, or null when it could not be read
     * @param lastFocus   the focus this project was last left on here, or null
     * @param workingCopies how many bundle working copies this machine holds
     */
    @SuppressWarnings("unchecked")
    void render(Map<String, Object> context, String lastFocus, Integer workingCopies) {
        body.removeAll();
        note("Yours, on this machine. Never written to a profile, never shared.");

        Map<String, Object> bundles = context == null ? null : (Map<String, Object>) context.get("bundles");
        List<Map<String, Object>> recent = bundles == null ? null
                : (List<Map<String, Object>>) bundles.get("recent");

        row("Focus last used here", lastFocus == null || lastFocus.isBlank() ? "none" : lastFocus);
        row("Evidence bundles opened", recent == null ? "none" : String.valueOf(recent.size()));
        row("Working copies unpacked", workingCopies == null ? "—" : String.valueOf(workingCopies));
        if (bundles != null && bundles.get("workingCopies") != null) {
            row("Unpacked into", String.valueOf(bundles.get("workingCopies")));
        }
        // #85: the count was a number a person could see and not act on. A working copy is a
        // throwaway -- but only the app knows which one is open, so the app is what must offer it.
        if (workingCopies != null && workingCopies > 0) {
            JButton clear = new JButton("Clear unused copies");
            clear.setToolTipText("Remove the unpacked working copies, except the one open now. "
                    + "The bundles themselves are untouched.");
            clear.setAlignmentX(LEFT_ALIGNMENT);
            clear.addActionListener(e -> clearCopies.run());
            body.add(javax.swing.Box.createVerticalStrut(6));
            body.add(clear);
        }

        body.revalidate();
        body.repaint();
    }

    private void note(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.ITALIC, label.getFont().getSize() - 1f));
        label.setForeground(UiTheme.mutedForeground());
        label.setAlignmentX(LEFT_ALIGNMENT);
        body.add(label);
        body.add(javax.swing.Box.createVerticalStrut(6));
    }

    private void row(String name, String value) {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        JLabel label = new JLabel(name);
        label.setForeground(UiTheme.mutedForeground());
        JLabel text = new JLabel(ProjectModel.abbreviate(value));
        text.setToolTipText(value);
        row.add(label, BorderLayout.WEST);
        row.add(text, BorderLayout.EAST);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
        body.add(row);
        body.add(javax.swing.Box.createVerticalStrut(3));
    }
}
