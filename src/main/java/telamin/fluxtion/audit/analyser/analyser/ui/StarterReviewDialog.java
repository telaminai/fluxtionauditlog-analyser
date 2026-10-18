package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Option 2 (owner, 2026-10-18): the ONE combined review after a starter is downloaded and its skills written — what the
 * analyser will put in force when it opens the project. It RENDERS {@code starterProject}'s request (rule 9) and returns
 * the person's answers; every row starts at No — except the Spring design folder's when the request says so (owner,
 * 2026-10-18: it is inside the project just created, and needed to show the design) — and Close opens nothing.
 *
 * <ul>
 *   <li>With the starter's own profile: one row opens the project, naming what its profile declares (source roots, the
 *       processor, runbooks) — a project opens whole, it is never merged.</li>
 *   <li>Without one: M73's offer — each discovered source root and skill a row, the project created with the Yes ones.</li>
 *   <li>Either way, each discovered graph a row (at most one opens), and the skills written for Claude Code listed.</li>
 * </ul>
 */
public final class StarterReviewDialog {

    private StarterReviewDialog() {
    }

    /**
     * What the review shows, all of it the analyser's own observation of the downloaded folder.
     *
     * @param project       the project's folder as {@code …/name}
     * @param profilePresent the starter shipped its own profile
     * @param declaredRoots  the profile's source roots (relative), when it has one
     * @param processor      the profile's selected processor, or null
     * @param runbooks       how many runbooks the profile points at
     * @param roots          discovery's source roots (relative): rows only without a profile
     * @param skills         discovery's skills, {path, name}: rows only without a profile
     * @param graphs         discovery's graphs (relative)
     * @param vendored       the skills written under {@code .claude/skills} for Claude Code
     * @param designRoot     the Spring design's folder (relative) when no source root covers it, else null — its row
     *                       adds it, so the design can open now
     * @param designDefaultYes the design row starts at Yes ({@code starterProject}'s decision, rule 9)
     */
    public record Data(String project, boolean profilePresent, List<String> declaredRoots, String processor,
                       int runbooks, List<String> roots, List<String[]> skills, List<String> graphs,
                       List<String> vendored, String designRoot, boolean designDefaultYes) {
        public Data {
            declaredRoots = List.copyOf(declaredRoots == null ? List.of() : declaredRoots);
            roots = List.copyOf(roots == null ? List.of() : roots);
            skills = List.copyOf(skills == null ? List.of() : skills);
            graphs = List.copyOf(graphs == null ? List.of() : graphs);
            vendored = List.copyOf(vendored == null ? List.of() : vendored);
        }
    }

    /**
     * The answers: open or not, the Yes roots and skills (by their relative identity), the graph chosen or null, and
     * whether the design's folder is added as a source root.
     */
    public record Answer(boolean open, List<String> roots, List<String> skills, String graph, boolean designRoot) {
        public Answer {
            roots = List.copyOf(roots);
            skills = List.copyOf(skills);
        }

        static final Answer CLOSED = new Answer(false, List.of(), List.of(), null, false);
    }

    static final String TITLE = "Open the new project";

    /** Why the design row starts at Yes (owner, 2026-10-18). */
    static final String DESIGN_WHY = "Pre-ticked: inside the project you just created; needed to show the Spring design.";

    /** The content, separable for the tests. */
    static final class Content {
        final JPanel panel = new JPanel(new BorderLayout(8, 8));
        JRadioButton openYes;
        JRadioButton designYes;
        final Map<String, JRadioButton> roots = new LinkedHashMap<>();
        final Map<String, JRadioButton> skills = new LinkedHashMap<>();
        final Map<String, JRadioButton> graphs = new LinkedHashMap<>();
        final JButton apply = UiText.button("Apply chosen");

        Answer answer() {
            if (openYes == null || !openYes.isSelected()) return Answer.CLOSED;
            List<String> r = new ArrayList<>();
            for (var e : roots.entrySet()) if (e.getValue().isSelected()) r.add(e.getKey());
            List<String> s = new ArrayList<>();
            for (var e : skills.entrySet()) if (e.getValue().isSelected()) s.add(e.getKey());
            String g = null;
            for (var e : graphs.entrySet()) {
                if (e.getValue().isSelected()) {
                    g = e.getKey();                 // at most one opens: the first answered Yes
                    break;
                }
            }
            return new Answer(true, r, s, g, designYes != null && designYes.isSelected());
        }
    }

    static Content content(Data d) {
        Content c = new Content();
        Box list = Box.createVerticalBox();
        c.openYes = yesNo(list, d.profilePresent() ? "Open the project " + d.project() + " with its own settings"
                : "Create the project " + d.project() + " and open it", "Open the project");
        if (d.profilePresent()) {
            list.add(muted("Its profile declares: source root" + (d.declaredRoots().size() == 1 ? " " : "s ")
                    + (d.declaredRoots().isEmpty() ? "(none)" : String.join(", ", d.declaredRoots()))
                    + (d.processor() == null ? "" : " · processor " + d.processor())
                    + (d.runbooks() == 0 ? "" : " · " + d.runbooks() + " runbook" + (d.runbooks() == 1 ? "" : "s"))
                    + ". A project opens whole: its settings replace the ones in force."));
        } else {
            heading(list, "Source roots");
            for (String r : d.roots()) c.roots.put(r, yesNo(list, r, "Source root " + r));
            if (d.roots().isEmpty()) list.add(muted("(none found)"));
            heading(list, "Skills as runbook pointers");
            for (String[] s : d.skills()) c.skills.put(s[0], yesNo(list, s[1] + " — " + s[0], "Skill " + s[1]));
            if (d.skills().isEmpty()) list.add(muted("(none found)"));
        }
        if (d.designRoot() != null) {
            heading(list, "The Spring design");
            c.designYes = yesNo(list, "Add " + d.designRoot() + " as a source root, so the design opens now",
                    "Design folder " + d.designRoot(), d.designDefaultYes());
            if (d.designDefaultYes()) list.add(muted(DESIGN_WHY));
        }
        heading(list, "Topology (at most one opens now)");
        for (String g : d.graphs()) {
            JRadioButton y = yesNo(list, g, "Graph " + g);
            c.graphs.put(g, y);
        }
        if (d.graphs().isEmpty()) {
            list.add(muted("None yet — a starter's graph is written by its first build. The analyser offers it then."));
        }
        heading(list, "Skills written for Claude Code (.claude/skills)");
        list.add(muted(d.vendored().isEmpty() ? "(none written)" : String.join(", ", d.vendored())));
        // the project-dependent rows follow the open row
        Runnable gate = () -> {
            boolean on = c.openYes.isSelected();
            for (var m : List.of(c.roots, c.skills, c.graphs)) for (JRadioButton b : m.values()) b.setEnabled(on);
            if (c.designYes != null) c.designYes.setEnabled(on);
        };
        gate.run();
        c.openYes.addItemListener(e -> gate.run());
        JTextArea lede = Fluid.text("The starter is unpacked and its skills are written. Nothing has been built or run. "
                + "Choose what the analyser puts in force; every answer starts at No"
                + (c.designYes != null && d.designDefaultYes() ? ", except the design folder's." : "."));
        c.panel.add(lede, BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(640, Math.min(list.getPreferredSize().height + 16, 420)));
        c.panel.add(scroll, BorderLayout.CENTER);
        return c;
    }

    private static void heading(Box list, String text) {
        JLabel h = UiText.label(text);
        h.setFont(h.getFont().deriveFont(Font.BOLD));
        h.setBorder(BorderFactory.createEmptyBorder(8, 0, 2, 0));
        list.add(h);
    }

    private static JComponent muted(String text) {
        JTextArea t = Fluid.text(text);
        t.setForeground(UiTheme.mutedForeground());
        return t;
    }

    /** A row with Yes / No, starting at No; returns its Yes. The words are the folder's own (plain, never markup). */
    private static JRadioButton yesNo(Box list, String words, String accessible) {
        return yesNo(list, words, accessible, false);
    }

    /** A row with Yes / No, starting at Yes only when {@code yes}; returns its Yes. */
    private static JRadioButton yesNo(Box list, String words, String accessible, boolean yes) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        JRadioButton y = UiText.plain(new JRadioButton("Yes", yes));
        JRadioButton n = UiText.plain(new JRadioButton("No", !yes));
        ButtonGroup bg = new ButtonGroup();
        bg.add(y);
        bg.add(n);
        y.getAccessibleContext().setAccessibleName(accessible + ": Yes");
        n.getAccessibleContext().setAccessibleName(accessible + ": No");
        row.add(y);
        row.add(n);
        row.add(UiText.label(words));
        row.setAlignmentX(0f);
        list.add(row);
        return y;
    }

    /** Show the review, modal; Close (or the window's close) opens nothing. */
    public static Answer show(Window owner, Data d) {
        Answer[] result = {Answer.CLOSED};
        JDialog dialog = dialog(owner, d, true, result);
        dialog.setVisible(true);
        return result[0];
    }

    static JDialog dialog(Window owner, Data d, boolean modal, Answer[] result) {
        Content c = content(d);
        JDialog dialog = new JDialog(owner, TITLE, modal ? Dialog.ModalityType.APPLICATION_MODAL
                : Dialog.ModalityType.MODELESS);
        JButton close = UiText.button("Close");
        c.apply.addActionListener(e -> {
            result[0] = c.answer();
            dialog.dispose();
        });
        close.addActionListener(e -> dialog.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(c.apply);
        buttons.add(close);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        root.add(c.panel, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        dialog.setContentPane(root);
        dialog.getRootPane().setDefaultButton(close);     // Enter closes: an answer is a click
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 700), Math.min(Math.max(dialog.getHeight(), 360), 620));
        dialog.setLocationRelativeTo(owner);
        return dialog;
    }
}
