package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantState;
import telamin.fluxtion.audit.analyser.analyser.walk.ConversationDraft;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

/**
 * OA-3 (spec-onboard-assistant-journeys.md §7): write or capture a walk's dialogue, bind it to steps, and see exactly what
 * leaves the machine before it is saved. A surface over {@link ConversationDraft}, which holds the rules.
 *
 * <p>Nothing is preselected: the completed turns of the current live chat are OFFERED, and join only when the person adds
 * them. The hidden context, the manifest and the system prompt were never visible, so they are never offered.
 */
final class ConversationEditor extends JDialog {

    /** A completed live turn's visible words, offered for capture. */
    record Candidate(String role, String text, boolean truncated) {
        Candidate(String role, String text) {
            this(role, text, false);
        }

        @Override public String toString() {
            String one = text.replace('\n', ' ');
            return ("user".equals(role) ? "You: " : "Assistant: ") + (one.length() > 90 ? one.substring(0, 90) + "…" : one);
        }
    }

    private final WalkSpec walk;
    private final ConversationDraft draft;
    private final BiFunction<WalkSpec.Conversation, List<String>, String> save;
    private final JLabel kind = UiText.plain(new JLabel());
    private final TurnsModel turns = new TurnsModel();
    private final JTable turnsTable = UiText.plainCells(new JTable(turns));   // SF-2: the turns are model output
    private final JPanel bindings = new JPanel(new GridLayout(0, 1, 2, 2));
    private final List<JComboBox<String>> revealCombos = new ArrayList<>();
    /** For tests: the last refusal shown, or null. */
    String lastRefusal;

    ConversationEditor(Window owner, WalkSpec walk, List<Candidate> candidates,
                       BiFunction<WalkSpec.Conversation, List<String>, String> save) {
        this(owner, walk, candidates, null, 0, save);
    }

    /**
     * M74 P2b (§6): with the Claude Code conversation as the source of candidates — {@code source} names it (a captured
     * turn makes the dialogue v2), and {@code leftOut} is how many of its oldest turns were not offered (the limits).
     */
    ConversationEditor(Window owner, WalkSpec walk, List<Candidate> candidates, WalkSpec.Source source, int leftOut,
                       BiFunction<WalkSpec.Conversation, List<String>, String> save) {
        super(owner, "Conversation for walk '" + walk.displayTitle() + "'", ModalityType.MODELESS);
        this.walk = walk;
        this.draft = new ConversationDraft(walk);
        this.save = save;
        getRootPane().getAccessibleContext().setAccessibleName("Conversation editor");
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(UiTheme.pad());
        setContentPane(root);

        JPanel head = new JPanel(new BorderLayout(6, 2));
        head.add(kind, BorderLayout.NORTH);
        JTextField author = new JTextField(walk.conversation() == null ? "" : walk.conversation().author(), 24);
        author.getAccessibleContext().setAccessibleName("Declared author");
        JPanel authorRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        authorRow.add(new JLabel("Declared author (not verified):"));
        authorRow.add(author);
        head.add(authorRow, BorderLayout.SOUTH);
        root.add(head, BorderLayout.NORTH);

        // the current chat's completed turns, offered — none selected
        DefaultListModel<Candidate> offered = new DefaultListModel<>();
        candidates.forEach(offered::addElement);
        JList<Candidate> offer = UiText.plainCells(new JList<>(offered));
        offer.getAccessibleContext().setAccessibleName("Completed turns of the current chat");
        JButton addCaptured = new JButton("Add selected turns");
        addCaptured.addActionListener(e -> {
            if (source != null && !offer.getSelectedValuesList().isEmpty()) draft.setSource(source);
            for (Candidate c : offer.getSelectedValuesList()) draft.addCaptured(c.role(), c.text(), c.truncated());
            offer.clearSelection();
            refresh();
        });
        JPanel west = new JPanel(new BorderLayout(4, 4));
        String from = source != null ? "From the Claude Code conversation" : "From the current chat";
        String limits = leftOut > 0 ? " — " + leftOut + " earlier turn" + (leftOut == 1 ? " is" : "s are")
                + " not offered (at most 200 turns, 512 KB)" : "";
        west.add(UiText.label(candidates.isEmpty() ? "No completed turns in " + (source != null
                ? "the Claude Code conversation." : "the current chat.")
                : from + " (completed turns; none is added until you choose)" + limits + ":"), BorderLayout.NORTH);
        west.add(new JScrollPane(offer), BorderLayout.CENTER);
        west.add(addCaptured, BorderLayout.SOUTH);
        west.setPreferredSize(new Dimension(320, 300));

        // the draft's turns: written ones can be added; any can be edited (a captured one becomes edited) or removed
        turnsTable.getAccessibleContext().setAccessibleName("Dialogue turns");
        JButton addUser = new JButton("Write a question");
        JButton addAnswer = new JButton("Write an answer");
        JButton remove = new JButton("Remove");
        addUser.addActionListener(e -> write("user"));
        addAnswer.addActionListener(e -> write("assistant"));
        remove.addActionListener(e -> {
            int row = turnsTable.getSelectedRow();
            if (row >= 0) {
                draft.remove(row);
                refresh();
            }
        });
        JPanel turnButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        turnButtons.add(addUser);
        turnButtons.add(addAnswer);
        turnButtons.add(remove);
        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.add(new JLabel("Dialogue (shown beside the real view, never run):"), BorderLayout.NORTH);
        center.add(new JScrollPane(turnsTable), BorderLayout.CENTER);
        center.add(turnButtons, BorderLayout.SOUTH);

        JPanel east = new JPanel(new BorderLayout(4, 4));
        east.add(new JLabel("Each step reveals the dialogue up to:"), BorderLayout.NORTH);
        east.add(new JScrollPane(bindings), BorderLayout.CENTER);
        east.setPreferredSize(new Dimension(300, 300));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, west,
                new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, center, east));
        root.add(split, BorderLayout.CENTER);

        JButton preview = new JButton("Preview what leaves this machine…");
        preview.addActionListener(e -> {
            draft.setAuthor(author.getText());
            JTextArea text = new JTextArea(draft.exportPreview(), 16, 70);
            text.setEditable(false);
            text.setLineWrap(true);
            JOptionPane.showMessageDialog(this, new JScrollPane(text), "What leaves this machine", JOptionPane.INFORMATION_MESSAGE);
        });
        JButton ok = new JButton("Save dialogue");
        ok.addActionListener(e -> {
            draft.setAuthor(author.getText());
            String refused = save.apply(draft.conversation(), draft.through());
            lastRefusal = refused;
            if (refused != null) {
                JOptionPane.showMessageDialog(this, UiText.inert(refused), "Dialogue not saved", JOptionPane.WARNING_MESSAGE);
            } else {
                dispose();
            }
        });
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        south.add(preview);
        south.add(cancel);
        south.add(ok);
        root.add(south, BorderLayout.SOUTH);
        refresh();
        setSize(1100, 560);
        setLocationRelativeTo(owner);
    }

    /** The completed turns of the current chat, with their visible words only. */
    static List<Candidate> candidates(AssistantState state, AssistantTranscript transcript) {
        List<Candidate> out = new ArrayList<>();
        var ok = state.completedTurns();
        for (AssistantState.Entry e : state.visibleEntries()) {
            if (!ok.contains(e.turn())) continue;
            if (AssistantState.USER.equals(e.kind())) out.add(new Candidate("user", transcript.text(e.id())));
            if (AssistantState.ANSWER.equals(e.kind())) {
                String words = AssistantPanel.withoutActionBlocks(transcript.text(e.id()));
                if (!words.isBlank()) out.add(new Candidate("assistant", words));
            }
        }
        return out;
    }

    /**
     * M74 P2b (§6): the Claude Code conversation's candidates — only the person's SENT turns and Claude's rendered,
     * completed answers; never a tool line, a note, a refusal or a compaction summary, and never a trimmed entry (the
     * node no longer holds it). After §6's limits.
     */
    static telamin.fluxtion.audit.analyser.analyser.walk.WalkConversation.Offer localAgentCandidates(
            telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState state,
            telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript store) {
        List<telamin.fluxtion.audit.analyser.analyser.walk.WalkConversation.Candidate> raw = new ArrayList<>();
        for (var e : state.entries()) {
            if (telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState.USER.equals(e.kind())) {
                String t = store.text(e.id());
                if (t != null && !t.isBlank()) raw.add(new telamin.fluxtion.audit.analyser.analyser.walk.WalkConversation.Candidate("user", t, null));
            } else if (telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState.ANSWER.equals(e.kind())
                    && "COMPLETED".equals(e.status())) {
                String t = store.text(e.id());
                if (t != null && !t.isBlank()) raw.add(new telamin.fluxtion.audit.analyser.analyser.walk.WalkConversation.Candidate("assistant", t, null));
            }
        }
        return telamin.fluxtion.audit.analyser.analyser.walk.WalkConversation.limit(raw);
    }

    ConversationDraft draft() {
        return draft;
    }

    private void write(String role) {
        String text = JOptionPane.showInputDialog(this, "user".equals(role) ? "The question:" : "The answer:",
                "user".equals(role) ? "Write a question" : "Write an answer", JOptionPane.PLAIN_MESSAGE);
        if (text != null && !text.isBlank()) {
            draft.addWritten(role, text.strip());
            refresh();
        }
    }

    private void refresh() {
        var c = draft.conversation();
        kind.setText(c == null ? "No dialogue yet." : "Will be saved as: " + c.label() + " (" + c.turns().size() + " turns)");
        turns.fireTableDataChanged();
        bindings.removeAll();
        revealCombos.clear();
        List<String> through = draft.through();
        for (int i = 0; i < walk.steps().size(); i++) {
            JComboBox<String> box = UiText.plainCells(new JComboBox<>());
            box.addItem("(nothing yet)");
            for (int t = 0; t < draft.lines().size(); t++) box.addItem("t" + (t + 1));
            String at = through.get(i);
            box.setSelectedItem(at == null ? "(nothing yet)" : at);
            final int step = i;
            box.addActionListener(e -> draft.reveal(step, box.getSelectedIndex() - 1));
            box.getAccessibleContext().setAccessibleName("Step " + (i + 1) + " reveals up to");
            JPanel row = new JPanel(new BorderLayout(4, 0));
            String caption = walk.steps().get(i).caption();
            row.add(UiText.label("Step " + (i + 1) + (caption.isBlank() ? "" : ": " + (caption.length() > 30
                    ? caption.substring(0, 30) + "…" : caption))), BorderLayout.CENTER);
            row.add(box, BorderLayout.EAST);
            bindings.add(row);
            revealCombos.add(box);
        }
        bindings.revalidate();
        bindings.repaint();
    }

    private final class TurnsModel extends AbstractTableModel {
        @Override public int getRowCount() { return draft.lines().size(); }
        @Override public int getColumnCount() { return 4; }
        @Override public String getColumnName(int c) { return new String[]{"id", "who", "source", "words"}[c]; }
        // §6: a truncated turn is marked in the list
        @Override public boolean isCellEditable(int r, int c) { return c == 3; }
        @Override public Object getValueAt(int r, int c) {
            ConversationDraft.Line l = draft.lines().get(r);
            return switch (c) {
                case 0 -> "t" + (r + 1);
                case 1 -> l.role();
                case 2 -> (l.captured() ? (l.edited() ? "captured, edited" : "captured") : "written")
                        + (l.truncated() ? ", truncated" : "");
                default -> l.text();
            };
        }
        @Override public void setValueAt(Object v, int r, int c) {
            draft.edit(r, String.valueOf(v));
            refresh();
        }
    }
}
