package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.ProposalState;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * M74 P2b (spec §5.6 step 5): the review of Claude Code's set-up proposal — opened only by the person's click on the
 * banner. It RENDERS {@link ProposalState}: per item the kind, the path, <b>the analyser's own verdict</b>, and Claude's
 * reason in a separate box labelled <i>"Claude's reason — unverified"</i>. Each applicable item has Yes / No, starting at
 * <b>No</b>; an item marked outside or refused has no Yes. With no project in the set-up folder the first row is the
 * analyser's own <i>Create a project in …</i> (also No), and the items that need a project stay disabled until it is
 * chosen. <i>Apply chosen</i> returns the person's answers; the session applies them.
 */
public final class ProposalReviewDialog {

    private ProposalReviewDialog() {
    }

    /** The person's answers: the item ids answered Yes, and the create row. */
    public record Answer(List<String> yes, boolean createProject) {
    }

    /** The review's content, separable from the dialog for the tests. */
    static final class Content {
        final JPanel panel = new JPanel(new BorderLayout(8, 8));
        final Map<String, JRadioButton> yes = new LinkedHashMap<>();
        final Map<String, JRadioButton> no = new LinkedHashMap<>();
        JRadioButton createYes;
        final JTextArea reason = Fluid.text(" ");
        final JButton apply = UiText.button("Apply chosen");

        Answer answer() {
            List<String> ids = new ArrayList<>();
            for (var e : yes.entrySet()) if (e.getValue().isSelected() && e.getValue().isEnabled()) ids.add(e.getKey());
            return new Answer(ids, createYes != null && createYes.isSelected());
        }
    }

    static boolean needsProject(String kind) {
        return !"logToOpen".equals(kind) && !"graphml".equals(kind);
    }

    static Content content(ProposalState p) {
        Content c = new Content();
        JPanel rows = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = 0;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(2, 4, 2, 4);
        String[] head = {"", "Kind", "Path", "The analyser's check", "Your answer"};
        for (int i = 0; i < head.length; i++) {
            g.gridx = i;
            JLabel h = UiText.label(head[i]);
            h.setFont(h.getFont().deriveFont(Font.BOLD));
            rows.add(h, g);
        }
        List<JRadioButton> needing = new ArrayList<>();
        if ("PENDING".equals(p.create())) {
            g.gridy++;
            g.gridx = 0;
            rows.add(UiText.label("★"), g);
            g.gridx = 1;
            rows.add(UiText.label("create a project"), g);
            g.gridx = 2;
            rows.add(UiText.label(p.setupDir() == null ? "" : LocalAgentPanel.shortFolder(p.setupDir())), g);
            g.gridx = 3;
            rows.add(UiText.label("the analyser's own offer: an empty project here (New project…)"), g);
            g.gridx = 4;
            JRadioButton y = UiText.plain(new JRadioButton("Yes"));
            JRadioButton n = UiText.plain(new JRadioButton("No", true));
            ButtonGroup bg = new ButtonGroup();
            bg.add(y);
            bg.add(n);
            JPanel yn = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            yn.add(y);
            yn.add(n);
            rows.add(yn, g);
            c.createYes = y;
            y.getAccessibleContext().setAccessibleName("Create a project: Yes");
            n.getAccessibleContext().setAccessibleName("Create a project: No");
        }
        int n = 0;
        for (ProposalState.Item i : p.items()) {
            n++;
            g.gridy++;
            g.gridx = 0;
            rows.add(UiText.label(Integer.toString(n)), g);
            g.gridx = 1;
            rows.add(UiText.label(i.kind()), g);
            g.gridx = 2;
            String where = i.path() != null ? i.path() : i.className() != null ? i.className() : "";
            JLabel path = UiText.label(where.length() > 60 ? "…" + where.substring(where.length() - 59) : where);
            path.setToolTipText(UiText.tooltip(where));
            rows.add(path, g);
            g.gridx = 3;
            String check = switch (i.state()) {
                case OUTSIDE -> "outside the set-up folder — cannot be applied";
                case REFUSED -> "refused: " + i.why();
                case PENDING -> i.verdict();
                default -> i.state().name().toLowerCase(java.util.Locale.ROOT) + (i.why().isBlank() ? "" : ": " + i.why());
            };
            rows.add(UiText.label(check), g);
            g.gridx = 4;
            if (i.applicable()) {
                JRadioButton y = UiText.plain(new JRadioButton("Yes"));
                JRadioButton no = UiText.plain(new JRadioButton("No", true));       // every item starts at No
                ButtonGroup bg = new ButtonGroup();
                bg.add(y);
                bg.add(no);
                JPanel yn = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
                yn.add(y);
                yn.add(no);
                rows.add(yn, g);
                y.getAccessibleContext().setAccessibleName("Item " + n + " " + i.kind() + ": Yes");
                no.getAccessibleContext().setAccessibleName("Item " + n + " " + i.kind() + ": No");
                c.yes.put(i.id(), y);
                c.no.put(i.id(), no);
                if (!p.projectExists() && needsProject(i.kind())) needing.add(y);
                final String reason = i.reason() == null ? "" : i.reason();
                y.addActionListener(e -> c.reason.setText(reason));
                no.addActionListener(e -> c.reason.setText(reason));
            } else {
                rows.add(UiText.label("—"), g);               // outside or refused: never a Yes
            }
        }
        // the items that need a project are disabled until the analyser's create row is chosen
        Runnable gate = () -> {
            boolean can = p.projectExists() || (c.createYes != null && c.createYes.isSelected());
            for (JRadioButton y : needing) {
                y.setEnabled(can);
                if (!can && y.isSelected()) {
                    for (var e : c.yes.entrySet()) if (e.getValue() == y) c.no.get(e.getKey()).setSelected(true);
                }
            }
        };
        gate.run();
        if (c.createYes != null) {
            c.createYes.addItemListener(e -> gate.run());
        }

        JPanel reasonBox = new JPanel(new BorderLayout(4, 4));
        JLabel reasonLabel = UiText.label("Claude's reason — unverified (choose an answer to see it)");
        reasonBox.add(reasonLabel, BorderLayout.NORTH);
        c.reason.setRows(3);
        c.reason.setForeground(UiTheme.mutedForeground());
        reasonBox.add(new JScrollPane(c.reason), BorderLayout.CENTER);
        reasonBox.setPreferredSize(new Dimension(520, 90));

        JTextArea lede = Fluid.text("Claude Code proposes these changes. Nothing has been applied. The analyser checked each "
                + "one itself; Claude's reasons are its own words. Every answer starts at No.");
        c.panel.add(lede, BorderLayout.NORTH);
        c.panel.add(new JScrollPane(rows), BorderLayout.CENTER);
        c.panel.add(reasonBox, BorderLayout.SOUTH);
        return c;
    }

    /** Show the review, modal for the person's own click; null when closed without applying. */
    public static Answer show(Window owner, ProposalState p) {
        Content c = content(p);
        JDialog d = new JDialog(owner, "Review Claude Code's set-up proposal", Dialog.ModalityType.APPLICATION_MODAL);
        JButton close = UiText.button("Close");
        Answer[] result = new Answer[1];
        c.apply.addActionListener(e -> {
            result[0] = c.answer();
            d.dispose();
        });
        close.addActionListener(e -> d.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(c.apply);
        buttons.add(close);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(c.panel, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        d.setContentPane(root);
        d.getRootPane().setDefaultButton(close);           // Enter closes: an answer is a click, never a default
        d.pack();
        d.setSize(Math.max(d.getWidth(), 760), Math.min(Math.max(d.getHeight(), 360), 640));
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return result[0];
    }
}
