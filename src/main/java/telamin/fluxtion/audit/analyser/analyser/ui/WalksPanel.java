package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * M69 S4 (spec-spotlight-walks.md §3.10) — the Reports tab's list of spotlight walks: Play, Play from step N, Rename,
 * Delete and Restore deleted… Every button is a {@code walk} operation through {@link WalkVerb}, the path the
 * assistant's verb takes, so the tab and the verb cannot disagree about what a delete or a play does. The panel
 * decides nothing about playback; it renders the walks and says what the session reported.
 */
final class WalksPanel extends JPanel {

    private final Supplier<List<WalkSpec>> walks;
    private final Function<Map<String, Object>, ActionResult> walk;
    private final Supplier<List<String>> restorable;
    private final DefaultListModel<String> names = new DefaultListModel<>();
    private final JList<String> list = new JList<>(names);
    private final JTextArea detail = new JTextArea();

    /** Asks for a step number (1-based), or null. Replaceable so a test can answer it. */
    Function<WalkSpec, Integer> stepPrompt = w -> {
        Object typed = JOptionPane.showInputDialog(this, "Play \"" + w.displayTitle() + "\" from step (1–" + w.steps().size() + "):",
                "Play from step", JOptionPane.PLAIN_MESSAGE, null, null, "1");
        if (typed == null) return null;
        try {
            return Integer.parseInt(typed.toString().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    };
    /** Asks for a new name, or null. */
    Function<WalkSpec, String> renamePrompt = w -> {
        Object typed = JOptionPane.showInputDialog(this, "Rename the walk \"" + w.name() + "\" to:", "Rename walk",
                JOptionPane.PLAIN_MESSAGE, null, null, w.name());
        return typed == null ? null : typed.toString();
    };
    /** Confirms a delete. */
    Function<WalkSpec, Boolean> confirmDelete = w -> JOptionPane.showConfirmDialog(this, deleteWarning(w), "Delete walk",
            JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    /** Chooses a deleted walk to restore, or null. */
    Function<List<String>, String> restoreChooser = names -> {
        Object choice = JOptionPane.showInputDialog(this, "Restore which walk?", "Restore deleted walk",
                JOptionPane.QUESTION_MESSAGE, null, names.toArray(), names.get(0));
        return choice == null ? null : choice.toString();
    };
    /** Says why something was refused. */
    java.util.function.Consumer<String> warn = message -> JOptionPane.showMessageDialog(this, message, "Spotlight walk",
            JOptionPane.WARNING_MESSAGE);

    final JButton play = new JButton("Play");
    final JButton playFrom = new JButton("Play from step…");
    final JButton rename = new JButton("Rename…");
    final JButton delete = new JButton("Delete…");
    final JButton restore = new JButton("Restore deleted…");

    WalksPanel(Supplier<List<WalkSpec>> walks, Function<Map<String, Object>, ActionResult> walk,
               Supplier<List<String>> restorable) {
        super(new BorderLayout());
        this.walks = walks;
        this.walk = walk;
        this.restorable = restorable;
        JPanel bar = new JPanel(new GridLayout(0, 1, 0, 4));
        play.addActionListener(e -> withSelected(w -> run(Map.of("name", w.name(), "play", true))));
        playFrom.addActionListener(e -> withSelected(w -> {
            Integer step = stepPrompt.apply(w);
            if (step != null) run(Map.of("name", w.name(), "play", true, "step", step));
        }));
        rename.addActionListener(e -> withSelected(w -> {
            String to = renamePrompt.apply(w);
            if (to != null && run(Map.of("name", w.name(), "rename", to))) select(to.trim());
        }));
        delete.addActionListener(e -> withSelected(w -> {
            if (Boolean.TRUE.equals(confirmDelete.apply(w))) run(Map.of("name", w.name(), "delete", true));
        }));
        restore.setToolTipText("Bring back a walk deleted from this project on this machine");
        restore.addActionListener(e -> {
            List<String> gone = restorable.get();
            if (gone.isEmpty()) {
                warn.accept("No deleted walks to restore in this project.");
                return;
            }
            String name = restoreChooser.apply(gone);
            if (name != null && run(Map.of("restore", name))) select(name);
        });
        for (JButton b : List.of(play, playFrom, rename, delete, restore)) bar.add(b);
        add(bar, BorderLayout.NORTH);
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) renderSelected();
        });
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), new JScrollPane(detail));
        split.setDividerLocation(180);
        add(split, BorderLayout.CENTER);
    }

    static String deleteWarning(WalkSpec w) {
        int n = w.steps().size();
        return "Delete the walk \"" + w.name() + "\"?\n\nIts " + n + " step" + (n == 1 ? "" : "s")
                + " move to the recently-deleted list. The log, the charts and the graph are NOT touched.\n\n"
                + "It can be brought back with Restore deleted… — the last "
                + telamin.fluxtion.audit.analyser.analyser.config.WalkBin.CAPACITY + " deletions are kept on this machine.";
    }

    /** Rebuild the list from the store, keeping the selection where it survives. */
    void refresh() {
        String selected = list.getSelectedValue();
        names.clear();
        for (WalkSpec w : walks.get()) names.addElement(w.name());
        if (selected != null && names.contains(selected)) list.setSelectedValue(selected, true);
        else if (!names.isEmpty()) list.setSelectedIndex(0);
        else renderSelected();
        boolean any = !names.isEmpty();
        play.setEnabled(any);
        playFrom.setEnabled(any);
        rename.setEnabled(any);
        delete.setEnabled(any);
    }

    void select(String name) {
        if (names.contains(name)) list.setSelectedValue(name, true);
    }

    String selectedName() {
        return list.getSelectedValue();
    }

    String detailText() {
        return detail.getText();
    }

    /** The showing walk changed: the detail line that says so is re-rendered from the published state. */
    private WalkPlaybackState showing = WalkPlaybackState.IDLE;

    void render(WalkPlaybackState state) {
        WalkPlaybackState s = state == null ? WalkPlaybackState.IDLE : state;
        if (s.showing() == showing.showing() && java.util.Objects.equals(s.walk(), showing.walk()) && s.step() == showing.step()) {
            showing = s;
            return;
        }
        showing = s;
        renderSelected();
    }

    private void renderSelected() {
        WalkSpec w = selected();
        if (w == null) {
            detail.setText(names.isEmpty()
                    ? "No spotlight walks yet.\n\nLight something (or ask the assistant to), right-click the spotlight "
                    + "and choose Save as new walk…"
                    : "");
            return;
        }
        StringBuilder out = new StringBuilder(w.displayTitle()).append('\n');
        out.append(w.steps().size()).append(" step").append(w.steps().size() == 1 ? "" : "s").append(" · ")
                .append(w.authorLabel());
        if (w.updatedAt() != null && !w.updatedAt().isBlank()) out.append(" · saved ").append(w.updatedAt());
        out.append('\n');
        if (showing.showing() && w.name().equals(showing.walk())) {
            out.append("Showing now: step ").append(showing.step() + 1).append(" of ").append(showing.count()).append('\n');
        }
        for (int i = 0; i < w.steps().size(); i++) {
            WalkSpec.Step s = w.steps().get(i);
            out.append('\n').append(i + 1).append(". ");
            out.append(s.caption().isBlank() ? "(no caption)" : s.caption());
            for (WalkSpec.Target t : s.targets()) {
                out.append("\n    ").append(t.target());
                if (!t.caption().isBlank()) out.append(" — ").append(t.caption());
            }
        }
        detail.setText(out.toString());
        detail.setCaretPosition(0);
    }

    private WalkSpec selected() {
        String name = list.getSelectedValue();
        if (name == null) return null;
        for (WalkSpec w : walks.get()) if (w.name().equals(name)) return w;
        return null;
    }

    private void withSelected(java.util.function.Consumer<WalkSpec> action) {
        WalkSpec w = selected();
        if (w != null) action.accept(w);
    }

    /** One walk operation; a refusal is said, never swallowed. Returns whether it succeeded. */
    private boolean run(Map<String, Object> params) {
        ActionResult r = walk.apply(params);
        if (!r.ok()) {
            warn.accept(r.error());
            return false;
        }
        refresh();
        return true;
    }
}
