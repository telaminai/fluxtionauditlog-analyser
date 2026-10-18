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

    /**
     * The person's answers: the item ids answered Yes, the create row, and (scorecard R5) EVERY row's answer — Yes or No,
     * by item id, {@code create} for the create row — so each is posted as its own fact.
     */
    public record Answer(List<String> yes, boolean createProject, Map<String, Boolean> rows) {
        public Answer(List<String> yes, boolean createProject) {
            this(yes, createProject, Map.of());
        }

        public Answer {
            rows = Map.copyOf(rows == null ? Map.of() : rows);
        }
    }

    /** The reason box's label: Claude's words, never verified by the analyser. */
    static final String REASON_LABEL = "Claude's reason — unverified";

    /** The review's content, separable from the dialog for the tests. */
    static final class Content {
        final JPanel panel = new JPanel(new BorderLayout(8, 8));
        final Map<String, JRadioButton> yes = new LinkedHashMap<>();
        final Map<String, JRadioButton> no = new LinkedHashMap<>();
        JRadioButton createYes;
        final JTextArea reason = Fluid.text(" ");
        /** The row whose reason shows, or 0; its label names the row and stays "Claude's reason — unverified". */
        int reasonRow;
        final JLabel reasonLabel = UiText.label(REASON_LABEL + " (point at or select a row)");

        void showReason(int row, String text) {
            reasonRow = row;
            reasonLabel.setText(REASON_LABEL + " — item " + row);
            // review PR #150 R3: Claude's words, hidden characters shown as marks
            reason.setText(text == null || text.isBlank() ? "(Claude gave no reason)" : VisibleMarks.show(text));
            reason.setCaretPosition(0);
        }
        final JButton apply = UiText.button("Apply chosen");

        Answer answer() {
            List<String> ids = new ArrayList<>();
            Map<String, Boolean> rows = new LinkedHashMap<>();
            if (createYes != null) rows.put("create", createYes.isSelected());
            for (var e : yes.entrySet()) {
                boolean y = e.getValue().isSelected() && e.getValue().isEnabled();
                if (y) ids.add(e.getKey());
                rows.put(e.getKey(), y);
            }
            return new Answer(ids, createYes != null && createYes.isSelected(), rows);
        }
    }

    /** Review M74 usability #16 (R1): an item's kind in plain words — never the proposal's code identifier. */
    static String kindWords(String kind) {
        return switch (kind == null ? "" : kind) {
            case "sourceRoot" -> "Source folder";
            case "processor" -> "Processor";
            case "graphml" -> "Graph file";
            case "runbook" -> "Runbook";
            case "logToOpen" -> "Log to open";
            default -> kind == null || kind.isBlank() ? "Item" : kind;
        };
    }

    /** Review M74 usability #16 (R1): the analyser's verdict in words — the code stays in the record, not on screen. */
    static String verdictWords(String verdict) {
        String v = verdict == null ? "" : verdict.strip();
        java.util.regex.Matcher m;
        if ((m = java.util.regex.Pattern.compile("DIRECTORY \\((\\d+) \\.java files\\)").matcher(v)).matches()) {
            int n = Integer.parseInt(m.group(1));
            return "found: a folder with " + n + " Java file" + (n == 1 ? "" : "s");
        }
        if ((m = java.util.regex.Pattern.compile("READABLE \\((\\d+) bytes, not opened\\)").matcher(v)).matches()) {
            return "found: " + m.group(1) + " bytes, not opened yet";
        }
        return switch (v) {
            case "RESOLVES (pairing UNKNOWN)", "RESOLVES" -> "found in the source; whether it matches the log is not known yet";
            case "READABLE (fit UNKNOWN until opened)" -> "found; whether it fits is known only once it is opened";
            case "MARKDOWN" -> "found: a Markdown file";
            case "SOURCE_FILE (not in the source roots yet)" -> "a Java file, not in the source folders yet";
            case "NOT_IN_SOURCES" -> "not found in the source folders";
            case "NOT_FOUND" -> "not found";
            case "OUTSIDE" -> "outside the set-up folder";
            case "NOT_A_DIRECTORY" -> "not a folder";
            case "NOT_GRAPHML" -> "not a GraphML file";
            case "NOT_MARKDOWN" -> "not a Markdown file";
            case "NOT_A_FILE" -> "not a file";
            case "NOT_JAVA" -> "not a Java file";
            case "WRONG_TYPE" -> "the wrong kind of file";
            case "UNKNOWN", "" -> "not known";
            default -> v;
        };
    }

    /** The review's columns (final review #13: the answer second, after the number). */
    static final int COL_ANSWER = 1, COL_WHAT = 2, COL_PATH = 3, COL_CHECK = 4;

    /** The analyser's check, wrapped in its own column (about 34 characters wide) — never widening the row. */
    static JTextArea check(String words) {
        JTextArea t = new JTextArea(words, 0, 34);
        t.setLineWrap(true);
        t.setWrapStyleWord(true);
        t.setEditable(false);
        t.setFocusable(false);
        t.setOpaque(false);
        t.setBorder(null);
        t.setFont(UIManager.getFont("Label.font"));
        return t;
    }

    static boolean needsProject(String kind) {
        return !"logToOpen".equals(kind) && !"graphml".equals(kind);
    }

    /** {@code words} resolves Claude's words by reference (the transcript store): the summary, a path, a reason (R6). */
    static Content content(java.util.function.LongFunction<String> words, ProposalState p) {
        Content c = new Content();
        JPanel rows = new JPanel(new GridBagLayout());
        GridBagConstraints g = new GridBagConstraints();
        g.gridy = 0;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(2, 4, 2, 4);
        // final review #13 (2026-10-17): the ANSWER column comes straight after the number, so the Yes / No controls are
        // in view at any width — at the default 760 px they were the last column, scrolled off to the right; the
        // analyser's check wraps in its own column and the path is elided, never the answers
        String[] head = {"", "Your answer", "What", "Path", "The analyser's check"};
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
            g.gridx = COL_WHAT;
            rows.add(UiText.label("create a project"), g);
            g.gridx = COL_PATH;
            rows.add(UiText.label(p.setupDirShown() == null ? "" : p.setupDirShown()), g);
            g.gridx = COL_CHECK;
            rows.add(check("the analyser's own offer: an empty project here (New project…)"), g);
            g.gridx = COL_ANSWER;
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
            g.gridx = COL_WHAT;
            rows.add(UiText.label(kindWords(i.kind())), g);
            g.gridx = COL_PATH;
            // review PR #150 R3: Claude's words, so hidden characters are marks (a bidi override cannot reverse a name),
            // elided from the left between whole grapheme clusters
            String where = i.pathRef() != 0 ? words.apply(i.pathRef()) : i.classNameRef() != 0
                    ? words.apply(i.classNameRef()) : "";
            JLabel path = UiText.label(VisibleMarks.tail(where, 60));
            path.setToolTipText(UiText.tooltip(VisibleMarks.show(where)));
            rows.add(path, g);
            g.gridx = COL_CHECK;
            String check = switch (i.state()) {
                case OUTSIDE -> "outside the set-up folder — cannot be applied";
                case REFUSED -> "refused: " + verdictWords(i.why());
                case PENDING -> verdictWords(i.verdict());
                default -> i.state().name().toLowerCase(java.util.Locale.ROOT) + (i.why().isBlank() ? "" : ": "
                        + verdictWords(i.why()));
            };
            rows.add(check(check), g);
            g.gridx = COL_ANSWER;
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
                y.getAccessibleContext().setAccessibleName("Item " + n + ", " + kindWords(i.kind()) + ": Yes");
                no.getAccessibleContext().setAccessibleName("Item " + n + ", " + kindWords(i.kind()) + ": No");
                c.yes.put(i.id(), y);
                c.no.put(i.id(), no);
                if (!p.projectExists() && needsProject(i.kind())) needing.add(y);
                final String reason = i.reasonRef() == 0 ? "" : words.apply(i.reasonRef());
                // review M74 usability #16 (R2): the reason is input TO the decision — shown when the row is pointed
                // at or focused, before any answer, and the first row's from the start
                final int row = n;
                Runnable show = () -> c.showReason(row, reason);
                for (JComponent target : new JComponent[]{y, no, path}) {
                    target.addFocusListener(new java.awt.event.FocusAdapter() {
                        @Override public void focusGained(java.awt.event.FocusEvent e) { show.run(); }
                    });
                    target.addMouseListener(new java.awt.event.MouseAdapter() {
                        @Override public void mouseEntered(java.awt.event.MouseEvent e) { show.run(); }
                    });
                }
                y.addActionListener(e -> show.run());
                no.addActionListener(e -> show.run());
                if (c.reasonRow == 0) show.run();
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
        reasonBox.add(c.reasonLabel, BorderLayout.NORTH);
        c.reason.setRows(3);
        c.reason.setForeground(UiTheme.mutedForeground());
        reasonBox.add(new JScrollPane(c.reason), BorderLayout.CENTER);
        reasonBox.setPreferredSize(new Dimension(520, 90));

        JTextArea lede = Fluid.text("Claude Code proposes these changes. Nothing has been applied. The analyser checked each "
                + "one itself; Claude's reasons are its own words. Every answer starts at No.");
        c.panel.add(lede, BorderLayout.NORTH);
        JScrollPane rowScroll = new JScrollPane(rows);
        // every row in view where the screen allows (at most 10 items and the create row), and no wider than the
        // dialog's default width, so the answers — the second column — are never scrolled out of view
        Dimension want = rows.getPreferredSize();
        rowScroll.setPreferredSize(new Dimension(Math.min(want.width + 24, DEFAULT_WIDTH - 24),
                Math.min(want.height + 12, 440)));
        c.panel.add(rowScroll, BorderLayout.CENTER);
        c.panel.add(reasonBox, BorderLayout.SOUTH);
        return c;
    }

    /** The review's default (and least) width. */
    static final int DEFAULT_WIDTH = 760;

    /** Show the review, modal for the person's own click; null when closed without applying. */
    public static Answer show(Window owner, ProposalState p, java.util.function.LongFunction<String> words) {
        Answer[] result = new Answer[1];
        JDialog d = dialog(owner, p, words, true, result);
        d.setVisible(true);
        return result[0];
    }

    /** The review's dialog, built and not shown ({@code modal} false for a frame test or a capture). */
    static JDialog dialog(Window owner, ProposalState p, java.util.function.LongFunction<String> words, boolean modal,
                          Answer[] result) {
        Content c = content(words, p);
        JDialog d = new JDialog(owner, "Review Claude Code's set-up proposal", modal ? Dialog.ModalityType.APPLICATION_MODAL
                : Dialog.ModalityType.MODELESS);
        JButton close = UiText.button("Close");
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
        d.setSize(Math.max(d.getWidth(), DEFAULT_WIDTH), Math.min(Math.max(d.getHeight(), 360), 640));
        d.setMinimumSize(new Dimension(DEFAULT_WIDTH, 360));
        d.setLocationRelativeTo(owner);
        return d;
    }
}
