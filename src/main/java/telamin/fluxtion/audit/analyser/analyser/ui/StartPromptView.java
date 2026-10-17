package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.StartPageState;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * M74 P2 (spec §5.8, OD-74.19, OD-74.21–74.23): <i>What would you like to do?</i> — the start page's prompt over the
 * catalogue's built-in entries S1–S6 (the configurable list is deferred by the owner until after P3). It RENDERS
 * {@link StartPageState} and decides nothing: where the button goes and whether anything can answer are
 * {@code startPagePrompt}'s. CHOOSING is local and posts
 * nothing (r8 F10): a flow entry becomes the removable chip and gives the button its verb; a prompt entry APPENDS its
 * text to the box. Only the button is the person's act ({@link Host#press}).
 *
 * <p>Every label, tooltip and accessible name that shows derived text is plain ({@code html.disable}, r8 F15). The box shows the whole text, wrapping and growing to six lines, then scrolling — never an ellipsis (r8 F18).
 */
public final class StartPromptView extends JPanel {

    /** What the view asks of its host: the button, the fallbacks, and the words it renders. */
    public interface Host {
        /** The button: the person's act. {@code entryId} {@code S1}…{@code S6} or {@code NONE}. */
        void press(String entryId, String text);

        /** A no-AI fallback button: runs an existing start-page action. {@code entryId} is the entry it is for. */
        void fallback(AskCatalogue.Fallback fallback, String entryId, String text);

        /** The resolver's line for the not-found case: every place searched. */
        String claudeSearched();

        /** A dropped prompt pack's words, by the session's reference (session only), or null. */
        default telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack.Pack pack(long ref) {
            return null;
        }

        /** "← Return to main options". */
        default void returnToMainOptions() { }

        /** "See the format": the user guide's prompt-pack section. */
        default void seePackFormat() { }
    }

    /**
     * One list row: the entry (null for a dropped pack's entry), its label and, for a prompt entry, the text choosing it
     * appends. A pack's entries behave exactly as a built-in prompt entry: choosing fills the box.
     */
    record Row(AskCatalogue.Entry entry, String label, String text) {
        String id() {
            return entry == null ? "NONE" : entry.id();
        }

        boolean flow() {
            return entry != null && entry.kind() == AskCatalogue.Kind.FLOW;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Host host;
    private final DefaultListModel<Row> model = new DefaultListModel<>();
    private final JList<Row> list = UiText.plainCells(new JList<>(model));
    private final JPanel chipRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    private final JLabel chip = UiText.label(" ");
    private final JButton removeChip = UiText.button("×");
    private final JTextArea does = Fluid.text(" ");
    private final JTextArea box = new JTextArea(2, 40) {
        @Override public Dimension getPreferredScrollableViewportSize() {
            // grows with its text up to six lines, then scrolls; the width is the layout's
            Dimension d = super.getPreferredSize();
            int line = getFontMetrics(getFont()).getHeight();
            return new Dimension(super.getPreferredScrollableViewportSize().width,
                    Math.max(2 * line + 4, Math.min(6 * line + 4, d.height)));
        }
    };
    private final JScrollPane boxScroll = new JScrollPane(box, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private final JLabel fullBadge = UiText.label(" ");
    private final JButton action = UiText.button(AskCatalogue.text("start.send"));
    private final JTextArea note = Fluid.text(" ");
    /** A dropped pack's title and description, the way back, and a refused drop's one cause. */
    private final JLabel packTitle = UiText.label(" ");
    private final JTextArea packDescription = Fluid.text(" ");
    private final JButton returnButton = UiText.button("← Return to main options");
    private final JTextArea packRefusal = Fluid.text(" ");
    private final JButton seeFormat = UiText.button("See the format");
    private final JPanel packHeader = new JPanel();
    private final JPanel refusalRow = new JPanel(new BorderLayout(8, 0));
    private final JPanel fallbacks = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
    private final JTextArea notFound = Fluid.text(" ");
    private final Border boxBorder;
    private final List<Runnable> recolour = new ArrayList<>();

    private StartPageState state = StartPageState.IDLE;
    private LocalAgentState agent = LocalAgentState.IDLE;
    private Row chosen;
    private List<Row> renderedRows = List.of();
    private String renderedFallbackKey;

    public StartPromptView(Host host) {
        this.host = host;
        setOpaque(false);
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        getAccessibleContext().setAccessibleName(AskCatalogue.text("start.title"));

        list.setVisibleRowCount(6);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.getAccessibleContext().setAccessibleName(AskCatalogue.text("start.title"));
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int i = list.locationToIndex(e.getPoint());
                if (i >= 0 && list.getCellBounds(i, i).contains(e.getPoint())) choose(i);
            }
        });
        list.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "start-choose");
        list.getActionMap().put("start-choose", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (list.getSelectedIndex() >= 0) choose(list.getSelectedIndex());
            }
        });
        // Alt+1 … Alt+9 choose the first nine (no mnemonic past 9), wherever the focus is in the view; a typed digit
        // in the box is a digit (r8 F19)
        for (int n = 1; n <= 9; n++) {
            final int index = n - 1;
            String key = "start-choose-" + n;
            getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_0 + n, InputEvent.ALT_DOWN_MASK), key);
            getActionMap().put(key, new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    if (index < model.size()) {
                        list.setSelectedIndex(index);
                        choose(index);
                    }
                }
            });
        }
        packHeader.setOpaque(false);
        packHeader.setLayout(new BoxLayout(packHeader, BoxLayout.Y_AXIS));
        packHeader.setAlignmentX(LEFT_ALIGNMENT);
        packTitle.setFont(packTitle.getFont().deriveFont(Font.BOLD));
        packTitle.setAlignmentX(LEFT_ALIGNMENT);
        packDescription.setAlignmentX(LEFT_ALIGNMENT);
        returnButton.setAlignmentX(LEFT_ALIGNMENT);
        returnButton.setMnemonic(KeyEvent.VK_R);
        returnButton.getAccessibleContext().setAccessibleName("Return to main options");
        returnButton.setToolTipText(UiText.tooltip("Back to the analyser's own list (Alt+R)"));
        returnButton.addActionListener(e -> host.returnToMainOptions());
        packHeader.add(returnButton);
        packHeader.add(packTitle);
        packHeader.add(packDescription);
        packHeader.setVisible(false);
        refusalRow.setOpaque(false);
        refusalRow.setAlignmentX(LEFT_ALIGNMENT);
        refusalRow.add(packRefusal, BorderLayout.CENTER);
        seeFormat.setToolTipText(UiText.tooltip("The prompt-pack format, in the user guide"));
        seeFormat.addActionListener(e -> host.seePackFormat());
        JPanel formatHolder = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        formatHolder.setOpaque(false);
        formatHolder.add(seeFormat);
        refusalRow.add(formatHolder, BorderLayout.EAST);
        refusalRow.setVisible(false);
        recolour.add(() -> {
            packDescription.setForeground(UiTheme.mutedForeground());
            packRefusal.setForeground(UiTheme.warnForeground());
        });
        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setAlignmentX(LEFT_ALIGNMENT);
        listScroll.setBorder(BorderFactory.createEmptyBorder());
        listScroll.setOpaque(false);
        listScroll.getViewport().setOpaque(false);
        list.setOpaque(false);

        chipRow.setOpaque(false);
        chipRow.setAlignmentX(LEFT_ALIGNMENT);
        chip.setOpaque(true);
        chip.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        removeChip.setToolTipText(UiText.tooltip(AskCatalogue.text("start.chip.remove")));
        removeChip.getAccessibleContext().setAccessibleName(AskCatalogue.text("start.chip.remove"));
        removeChip.setMargin(new Insets(0, 4, 0, 4));
        removeChip.addActionListener(e -> unchoose());
        chipRow.add(chip);
        chipRow.add(removeChip);
        chipRow.setVisible(false);
        does.setAlignmentX(LEFT_ALIGNMENT);
        does.setVisible(false);

        box.setLineWrap(true);
        box.setWrapStyleWord(true);
        box.getAccessibleContext().setAccessibleName(AskCatalogue.text("start.box"));
        boxScroll.setAlignmentX(LEFT_ALIGNMENT);
        boxBorder = boxScroll.getBorder();
        box.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { boxChanged(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { boxChanged(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        fullBadge.setAlignmentX(LEFT_ALIGNMENT);
        fullBadge.setVisible(false);
        fullBadge.setFont(fullBadge.getFont().deriveFont(Font.BOLD));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.add(action);
        action.addActionListener(e -> press());
        fallbacks.setOpaque(false);
        fallbacks.setAlignmentX(LEFT_ALIGNMENT);
        fallbacks.setVisible(false);
        for (JTextArea t : new JTextArea[]{note, notFound}) {
            t.setAlignmentX(LEFT_ALIGNMENT);
            t.setVisible(false);
            recolour.add(() -> t.setForeground(UiTheme.mutedForeground()));
        }

        add(packHeader);
        add(listScroll);
        add(refusalRow);
        add(Box.createVerticalStrut(6));
        add(chipRow);
        add(does);
        add(Box.createVerticalStrut(4));
        add(fullBadge);
        add(boxScroll);
        add(Box.createVerticalStrut(6));
        add(actions);
        add(note);
        add(notFound);
        add(fallbacks);
        recolour.add(() -> {
            chip.setBackground(UiTheme.accentWash(0.22f));
            chip.setForeground(UiTheme.accentText());
        });
        recolour.forEach(Runnable::run);
    }

    // ---- rendering ----------------------------------------------------------------------------------------------------

    /** Render the node's state (and the local agent's, for the full-posture cue on the box). */
    public void render(StartPageState s, LocalAgentState agent) {
        this.state = s == null ? StartPageState.IDLE : s;
        this.agent = agent == null ? LocalAgentState.IDLE : agent;
        var pack = state.packRef() == 0 ? null : host.pack(state.packRef());
        List<Row> rows = pack == null ? rows() : packRows(pack);
        packHeader.setVisible(pack != null);
        if (pack != null) {
            packTitle.setText(pack.title());
            packTitle.getAccessibleContext().setAccessibleName("Prompt pack: " + pack.title());
            packDescription.setText(pack.description());
            packDescription.setVisible(!pack.description().isBlank());
        }
        packRefusal.setText(state.packRefusal() == null ? " "
                : "This prompt pack was not used: " + state.packRefusalWords() + ".");
        refusalRow.setVisible(state.packRefusal() != null);
        if (!rows.equals(renderedRows)) {
            renderedRows = rows;
            String chosenId = chosen == null ? null : chosen.id();
            model.clear();
            for (Row r : rows) model.addElement(r);
            chosen = null;
            for (Row r : rows) if (r.id().equals(chosenId)) chosen = r;
            renderChip();
        }
        note.setText(state.note());
        note.setVisible(!state.note().isBlank());
        renderAction();
        renderFallbacks();
        renderFullCue();
        revalidate();
        repaint();
    }

    /** The built-in entries S1–S6, from the registry. */
    private static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (AskCatalogue.Entry e : AskCatalogue.startPageBuiltIns()) {
            String text = e.kind() == AskCatalogue.Kind.PROMPT ? composeBuiltInPrompt(e.id()) : null;
            out.add(new Row(e, AskCatalogue.question(e), text));
        }
        return out;
    }

    /** A dropped pack's entries, as prompt rows: choosing one appends its text to the box. */
    private static List<Row> packRows(telamin.fluxtion.audit.analyser.analyser.assistant.PromptPack.Pack pack) {
        List<Row> out = new ArrayList<>();
        for (var e : pack.entries()) out.add(new Row(null, e.label(), e.text()));
        return out;
    }

    private static String composeBuiltInPrompt(String id) {
        AskCatalogue.Draft d = AskCatalogue.composeStart(id, AskCatalogue.StartFacts.none(""));
        return d == null ? null : d.text();
    }

    /** "Prompt 3 of 6: Open an evidence bundle someone sent me" (r8 F19). */
    static String accessibleName(int n, int total, String label) {
        return AskCatalogue.text("start.accessible", java.util.Map.of("n", Integer.toString(n), "total",
                Integer.toString(total), "label", label));
    }

    private void renderChip() {
        if (chosen == null) {
            chipRow.setVisible(false);
            does.setVisible(false);
            return;
        }
        chip.setText(chosen.label());
        chip.getAccessibleContext().setAccessibleName(chosen.label());
        does.setText(AskCatalogue.text(chosen.id() + ".does"));
        chipRow.setVisible(true);
        does.setVisible(true);
    }

    private void renderAction() {
        String label;
        if (chosen != null) {
            label = AskCatalogue.text(chosen.id() + ".verb");
        } else {
            label = switch (state.route()) {
                case SWITCH_TO_CLAUDE_CODE -> AskCatalogue.text("start.askClaude");
                case SWITCH_TO_IN_APP -> AskCatalogue.text("start.askInApp");
                default -> state.route().toClaudeCode() ? LocalAgentPanel.sendLabel(agent) : AskCatalogue.text("start.send");
            };
        }
        action.setText(label);
        action.getAccessibleContext().setAccessibleName(label);
        action.setEnabled(chosen != null || !box.getText().isBlank());
    }

    private void boxChanged() {
        renderAction();

    }

    /** Which fallback buttons show: in the no-AI column, the chosen entry's (or the last pressed one's); S4 always offers its dialog. */
    private void renderFallbacks() {
        String entry = chosen != null ? chosen.id() : state.fallbackEntry() != null ? state.fallbackEntry() : "S6";
        List<AskCatalogue.Fallback> shown = new ArrayList<>();
        if (!state.route().canAnswer()) {
            if (entry.equals("NONE")) shown.add(AskCatalogue.Fallback.COPY_PROMPT);
            else AskCatalogue.byId(entry).ifPresent(e -> shown.addAll(e.fallbacks()));
        } else if ("S4".equals(entry) && chosen != null) {
            shown.add(AskCatalogue.Fallback.TEMPLATE);            // beside the draft: the existing dialog (§5.8 S4)
        }
        String fkey = entry + shown + state.route() + state.claudeCodeNotFound();
        notFound.setText(AskCatalogue.text("start.claudeNotFound", java.util.Map.of("searched",
                host.claudeSearched() == null ? "" : host.claudeSearched())));
        notFound.setVisible(state.claudeCodeNotFound() && !state.route().canAnswer());
        if (fkey.equals(renderedFallbackKey)) return;
        renderedFallbackKey = fkey;
        fallbacks.removeAll();
        if (!state.route().canAnswer()) {
            JLabel why = UiText.label(AskCatalogue.text("start.noAi"));
            why.setForeground(UiTheme.mutedForeground());
            fallbacks.add(why);
        }
        for (AskCatalogue.Fallback f : shown) {
            String key = "fallback." + switch (f) {
                case ASSISTANT_SETTINGS -> "assistantSettings";
                case CONNECT -> "connect";
                case COPY_PROMPT -> "copyPrompt";
                case NEW_PROJECT -> "newProject";
                case BUNDLE_GUIDE -> "bundleGuide";
                case TEMPLATE -> "template";
                case USER_GUIDE -> "userGuide";
            };
            JButton b = UiText.button(AskCatalogue.text(key));
            b.setToolTipText(UiText.tooltip(AskCatalogue.text(key + ".tip")));   // a menu path, only as a tooltip (r8 F7)
            b.getAccessibleContext().setAccessibleName(AskCatalogue.text(key));
            b.putClientProperty("start.fallback", f);
            final String forEntry = entry;
            b.addActionListener(e -> host.fallback(f, forEntry, box.getText().strip()));
            fallbacks.add(b);
        }
        if (state.claudeCodeNotFound() && !state.route().canAnswer()) {
            JButton install = UiText.button(AskCatalogue.text("start.install"));
            install.setToolTipText(UiText.tooltip("https://docs.claude.com/en/docs/claude-code/setup"));
            install.addActionListener(e -> host.fallback(AskCatalogue.Fallback.CONNECT, "INSTALL", ""));
            fallbacks.add(install);
        }
        fallbacks.setVisible(fallbacks.getComponentCount() > 0);
    }

    private void renderFullCue() {
        boolean toClaude = state.route().toClaudeCode();
        String badge = toClaude ? LocalAgentPanel.fullBadgeText(agent) : null;
        if (badge == null) {
            fullBadge.setVisible(false);
            boxScroll.setBorder(boxBorder);
            return;
        }
        Color c = TranscriptStyle.fullPosture(agent.strongCue(), ThemeManager.isDark());
        fullBadge.setText((agent.strongCue() ? "◆◆ " : "◆ ") + badge);
        fullBadge.setForeground(c);
        fullBadge.setToolTipText(UiText.tooltip(LocalAgentPanel.fullBadgeTip(agent)));
        fullBadge.getAccessibleContext().setAccessibleName(badge);
        fullBadge.setVisible(true);
        boxScroll.setBorder(BorderFactory.createLineBorder(c, agent.strongCue() ? 3 : 2));
    }

    // ---- choosing (local: posts nothing) and the button ---------------------------------------------------------------

    /** Choose row {@code i}: a flow entry becomes the chip; a prompt entry's text is APPENDED to the box. */
    void choose(int i) {
        if (i < 0 || i >= model.size()) return;
        Row r = model.get(i);
        if (r.flow()) {
            chosen = r;
            renderChip();
        } else if (r.text() != null) {
            String was = box.getText();
            String sep = was.isBlank() ? "" : was.endsWith("\n") ? "\n" : "\n\n";
            box.append(sep + r.text());                 // never replacing what the person typed
            box.setCaretPosition(box.getDocument().getLength());
        }
        renderAction();
        renderFallbacks();
        revalidate();
        repaint();
    }

    void unchoose() {
        chosen = null;
        renderChip();
        renderAction();
        renderFallbacks();
        revalidate();
    }

    private void press() {
        String text = box.getText().strip();
        if (chosen != null) {
            host.press(chosen.id(), text);
        } else if (!text.isEmpty()) {
            host.press("NONE", text);
        }
    }

    /** After the button was answered: the box keeps its words until the person clears them. */
    public void clearAfterSend() {
        box.setText("");
        unchoose();
    }

    @Override
    public void updateUI() {
        super.updateUI();
        if (recolour != null) SwingUtilities.invokeLater(() -> recolour.forEach(Runnable::run));
    }

    // ---- for the tests ------------------------------------------------------------------------------------------------

    JList<Row> promptList() {
        return list;
    }

    JTextArea box() {
        return box;
    }

    JScrollPane boxScroll() {
        return boxScroll;
    }

    JButton actionButton() {
        return action;
    }

    JLabel chipLabel() {
        return chip;
    }

    JLabel fullBadge() {
        return fullBadge;
    }

    JButton returnButton() {
        return returnButton;
    }

    JLabel packTitle() {
        return packTitle;
    }

    JTextArea packRefusal() {
        return packRefusal;
    }

    JPanel fallbacksPanel() {
        return fallbacks;
    }

    Row chosen() {
        return chosen;
    }

    /** The renderer: each row's label, its number and origin, plain (r8 F15), with its accessible name (r8 F19). */
    private final class Renderer extends DefaultListCellRenderer {
        Renderer() {
            UiText.plain(this);
        }

        @Override
        public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus) {
            super.getListCellRendererComponent(l, "", index, selected, focus);
            UiText.plain(this);
            Row r = (Row) value;
            String number = index < 9 ? (index + 1) + ".  " : "     ";
            setText(number + r.label());
            setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
            setToolTipText(UiText.tooltip(r.text() != null ? r.text() : AskCatalogue.text(r.id() + ".does")));   // plain
            getAccessibleContext().setAccessibleName(accessibleName(index + 1, l.getModel().getSize(), r.label()));
            if (!selected) setForeground(UiTheme.accentText());
            setOpaque(selected);
            return this;
        }
    }

    /** For a host that needs to know the box's words (Copy prompt). */
    public String boxText() {
        return box.getText();
    }
}
