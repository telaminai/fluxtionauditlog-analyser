package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextArea;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * M69 S4 (spec-spotlight-walks.md §3.10) — the Reports tab's list of spotlight walks: Play, Play selected step, Rename,
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
    private final DefaultListModel<String> stepLabels = new DefaultListModel<>();
    private final JList<String> steps = new JList<>(stepLabels);
    private final JTextArea detail = new JTextArea();
    private final JSplitPane split;
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
    final JButton playFrom = new JButton("Play selected step");
    final JMenuItem rename = new JMenuItem("Rename…");
    final JMenuItem delete = new JMenuItem("Delete…");
    final JMenuItem restore = new JMenuItem("Restore deleted…");
    /** OA-3: write or capture the selected walk's dialogue (the frame opens the editor). */
    final JMenuItem conversation = new JMenuItem("Conversation…");
    java.util.function.Consumer<WalkSpec> onConversation = w -> { };

    WalksPanel(Supplier<List<WalkSpec>> walks, Function<Map<String, Object>, ActionResult> walk,
               Supplier<List<String>> restorable) {
        super(new BorderLayout());
        this.walks = walks;
        this.walk = walk;
        this.restorable = restorable;
        JPanel bar = new JPanel(new BorderLayout(0, 2));
        JPanel mainActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
        play.addActionListener(e -> withSelected(w -> run(Map.of("name", w.name(), "play", true))));
        playFrom.addActionListener(e -> withSelected(w -> {
            int step = steps.getSelectedIndex();
            if (step >= 0) run(Map.of("name", w.name(), "play", true, "step", step + 1));
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
        conversation.setToolTipText("Write, or capture from the current chat, dialogue shown beside this walk's steps");
        conversation.addActionListener(e -> withSelected(w -> onConversation.accept(w)));
        JPopupMenu actions = new JPopupMenu();
        actions.add(conversation);
        actions.addSeparator();
        actions.add(rename);
        actions.add(delete);
        actions.addSeparator();
        actions.add(restore);
        JButton more = new JButton("More ▾");
        more.setToolTipText("Rename, delete or restore spotlight walks");
        more.setComponentPopupMenu(actions);
        more.addActionListener(e -> actions.show(more, 0, more.getHeight()));
        mainActions.add(play);
        mainActions.add(more);
        bar.add(mainActions, BorderLayout.NORTH);
        JPanel stepAction = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        stepAction.add(playFrom);
        bar.add(stepAction, BorderLayout.SOUTH);
        add(bar, BorderLayout.NORTH);
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setRows(6);
        detail.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        steps.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        steps.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && !updatingSteps) {
                renderStepDetail();
                updateActions();
            }
        });
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) renderSelected();
        });
        JPanel walkDetail = new JPanel(new BorderLayout());
        walkDetail.add(new JScrollPane(detail), BorderLayout.NORTH);
        walkDetail.add(new JScrollPane(steps), BorderLayout.CENTER);
        split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), walkDetail);
        split.setDividerLocation(180);
        split.setResizeWeight(0.32);
        add(split, BorderLayout.CENTER);
        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                int orientation = getWidth() < 360 ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT;
                if (split.getOrientation() != orientation) {
                    split.setOrientation(orientation);
                    javax.swing.SwingUtilities.invokeLater(() -> split.setDividerLocation(
                            orientation == JSplitPane.VERTICAL_SPLIT ? 90 : Math.min(180, getWidth() / 3)));
                }
            }
        });
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
        updateActions();
    }

    void select(String name) {
        if (names.contains(name)) list.setSelectedValue(name, true);
    }

    String selectedName() {
        return list.getSelectedValue();
    }

    void selectStep(int oneBased) {
        steps.setSelectedIndex(oneBased - 1);
    }

    String detailText() {
        return detail.getText();
    }

    /** The showing walk changed: the detail line that says so is re-rendered from the published state. */
    private WalkPlaybackState showing = WalkPlaybackState.IDLE;
    private String renderedWalk;
    private boolean updatingSteps;

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
            renderedWalk = null;
            stepLabels.clear();
            detail.setText(names.isEmpty()
                    ? "No spotlight walks yet.\n\nLight something (or ask the assistant to), right-click the spotlight "
                    + "and choose Save as new walk…"
                    : "");
            updateActions();
            return;
        }
        int selectedStep = w.name().equals(renderedWalk) ? steps.getSelectedIndex() : 0;
        renderedWalk = w.name();
        updatingSteps = true;
        stepLabels.clear();
        for (int i = 0; i < w.steps().size(); i++) {
            WalkSpec.Step s = w.steps().get(i);
            boolean current = showing.showing() && w.name().equals(showing.walk()) && showing.step() == i;
            stepLabels.addElement((current ? "▶ " : "    ") + (i + 1) + ". "
                    + (s.caption().isBlank() ? "(no caption)" : s.caption()));
        }
        if (selectedStep >= 0 && selectedStep < stepLabels.size()) steps.setSelectedIndex(selectedStep);
        updatingSteps = false;
        renderStepDetail();
        updateActions();
    }

    private void renderStepDetail() {
        WalkSpec w = selected();
        if (w == null) return;
        StringBuilder out = new StringBuilder(w.displayTitle()).append('\n');
        out.append(w.steps().size()).append(" step").append(w.steps().size() == 1 ? "" : "s").append(" · ")
                .append(w.authorLabel());
        if (w.updatedAt() != null && !w.updatedAt().isBlank()) out.append(" · saved ").append(w.updatedAt());
        if (showing.showing() && w.name().equals(showing.walk())) {
            out.append("\nShowing now: step ").append(showing.step() + 1).append(" of ").append(showing.count());
        }
        int index = steps.getSelectedIndex();
        if (index >= 0 && index < w.steps().size()) {
            WalkSpec.Step step = w.steps().get(index);
            out.append("\n\nSelected step ").append(index + 1).append(": ")
                    .append(step.caption().isBlank() ? "(no caption)" : step.caption());
            for (WalkSpec.Target target : step.targets()) {
                out.append("\n").append(target.target());
                if (!target.caption().isBlank()) out.append(" — ").append(target.caption());
            }
        }
        detail.setText(out.toString());
        detail.setCaretPosition(0);
    }

    private void updateActions() {
        boolean selected = selected() != null;
        play.setEnabled(selected);
        playFrom.setEnabled(selected && steps.getSelectedIndex() >= 0);
        rename.setEnabled(selected);
        conversation.setEnabled(selected);
        delete.setEnabled(selected);
        restore.setEnabled(!restorable.get().isEmpty());
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
