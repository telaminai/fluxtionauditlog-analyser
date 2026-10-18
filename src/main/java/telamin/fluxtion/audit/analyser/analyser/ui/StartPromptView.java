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
 * catalogue's built-in entries S1–S8 (the configurable list is deferred by the owner until after P3). It RENDERS
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

        /**
         * Robot re-walk #3: one of the "No log is open" line's actions for held press {@code request} — the person's act;
         * the session decides what follows.
         */
        default void answerNoLog(long request, NoLogAction choice) { }

        /** "See the format": the user guide's prompt-pack section. */
        default void seePackFormat() { }

        /**
         * P2b: an example entry's own button — the person's act. Nothing was fetched on the drop or on choosing; the
         * session decides each step from here (confirm, fetch, verify, unpack, the project offer).
         */
        default void downloadExample(int entryIndex) { }
    }

    /** Robot re-walk #3: the "No log is open" line's actions, as the page reports them (the frame makes them facts). */
    public enum NoLogAction { OPEN_SAMPLE, OPEN_LOG, ASK_ANYWAY }

    /** P2b: the download badge an example entry shows in the list and on its chip. */
    static final String DOWNLOAD_BADGE = "⤓";

    /**
     * One list row: the entry (null for a dropped pack's entry), its label and, for a prompt entry, the text choosing it
     * appends. A pack's entries behave exactly as a built-in prompt entry: choosing fills the box.
     */
    record Row(AskCatalogue.Entry entry, String label, String text,
               telamin.fluxtion.audit.analyser.analyser.assistant.ExampleSource example, int packIndex) {
        Row(AskCatalogue.Entry entry, String label, String text) {
            this(entry, label, text, null, -1);
        }

        String id() {
            return entry == null ? (example != null ? "EXAMPLE" : "NONE") : entry.id();
        }

        boolean flow() {
            return entry != null && entry.kind() == AskCatalogue.Kind.FLOW;
        }

        /** P2b: a downloadable example — chosen like a flow (a chip), pressed only by its own button. */
        boolean isExample() {
            return example != null;
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
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            UiTheme.paintPlaceholder(this, g, PLACEHOLDER);         // review #9: it reads as a box to type in
        }

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
    /** Review M74 usability S3/#15: the box's keys, said under it — the composer's binding, the same in both. */
    private final JLabel keyHint = UiText.label(KEY_HINT);
    static final String KEY_HINT = "Enter sends · Shift+Enter starts a new line";
    static final String PLACEHOLDER = "Ask about a log, or choose an entry above";
    private final JTextArea note = Fluid.text(" ");
    /** P2b: an example download's phase and the node's words (exampleDownload decides them). */
    private final JTextArea exampleNote = Fluid.text(" ");
    private telamin.fluxtion.audit.analyser.analyser.session.ExampleDownloadState example =
            telamin.fluxtion.audit.analyser.analyser.session.ExampleDownloadState.IDLE;
    /** A dropped pack's title and description, the way back, and a refused drop's one cause. */
    private final JLabel packTitle = UiText.label(" ");
    private final JTextArea packDescription = Fluid.text(" ");
    /** External utility review (2026-10-17): the gentle, non-blocking note of what the reader normalised. */
    private final JTextArea packNormalised = Fluid.text(" ");
    private final JButton returnButton = UiText.button("← Return to main options");
    private final JTextArea packRefusal = Fluid.text(" ");
    private final JButton seeFormat = UiText.button("See the format");
    private final JPanel packHeader = new JPanel();
    private final JPanel refusalRow = new JPanel(new BorderLayout(8, 0));
    private final JPanel fallbacks = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
    /** Robot re-walk #3: "No log is open — the answer will be general" and its three actions, while a press is held. */
    private final JPanel noLog = new JPanel(new WrapLayout(FlowLayout.LEFT, 6, 4));
    private final JLabel noLogLine = UiText.label(telamin.fluxtion.audit.analyser.analyser.session.node.StartPageNoLogHint.HINT);
    private final JButton noLogSample = UiText.button(NO_LOG_SAMPLE);
    private final JButton noLogOpen = UiText.button(NO_LOG_OPEN);
    private final JButton noLogAnyway = UiText.button(NO_LOG_ANYWAY);
    static final String NO_LOG_SAMPLE = "Open the sample";
    static final String NO_LOG_OPEN = "Open a log…";
    static final String NO_LOG_ANYWAY = "Ask anyway";
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
        HeldEnter.install();                     // final review #7: the composer asks whether this page's Enter is held
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
        // Alt+1 … Alt+9 (⌘1 … ⌘9 on macOS) choose the first nine (no shortcut past 9), wherever the focus is in the
        // window while the page shows; a typed digit in the box is a digit (r8 F19). Review M74 usability #18: on macOS
        // ⌥+digit TYPES a character (⌥1 is ¡) into the focused box as well as choosing, so the Mac takes ⌘.
        for (int n = 1; n <= 9; n++) {
            final int index = n - 1;
            String key = "start-choose-" + n;
            getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_0 + n, chooserMask()), key);
            getActionMap().put(key, new AbstractAction() {
                // final review #8: a window binding fires wherever the focus is — but not into another text field
                @Override public boolean isEnabled() {
                    return shortcutsApply();
                }

                @Override public void actionPerformed(ActionEvent e) {
                    if (index < model.size()) {          // Swing gives a window binding only to a showing view
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
        packNormalised.setAlignmentX(LEFT_ALIGNMENT);
        packNormalised.getAccessibleContext().setAccessibleName("What the analyser changed to read this pack");
        packNormalised.setVisible(false);
        returnButton.setAlignmentX(LEFT_ALIGNMENT);
        // review M74 usability #18: FlatLaf binds a mnemonic to Ctrl+⌥ on macOS, so "(Alt+R)" was wrong there — the
        // shortcut is bound explicitly, with the platform's modifier, and the tooltip names the one that works
        getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, chooserMask()), "start-return");
        getActionMap().put("start-return", new AbstractAction() {
            @Override public boolean isEnabled() {
                return shortcutsApply();                // final review #8
            }

            @Override public void actionPerformed(ActionEvent e) {
                if (packHeader.isVisible()) returnButton.doClick();
            }
        });
        returnButton.getAccessibleContext().setAccessibleName("Return to main options");
        returnButton.setToolTipText(UiText.tooltip("Back to the analyser's own list (" + shortcutText("R") + ")"));
        returnButton.addActionListener(e -> host.returnToMainOptions());
        packHeader.add(returnButton);
        packHeader.add(packTitle);
        packHeader.add(packDescription);
        packHeader.add(packNormalised);
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
            packNormalised.setForeground(UiTheme.mutedForeground());
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
        TranscriptView.atLeastTarget(removeChip);               // review §4.3: 24×24 (WCAG 2.5.8)
        removeChip.addActionListener(e -> unchoose());
        chipRow.add(chip);
        chipRow.add(removeChip);
        chipRow.setVisible(false);
        does.setAlignmentX(LEFT_ALIGNMENT);
        does.setVisible(false);

        box.setLineWrap(true);
        box.setWrapStyleWord(true);
        box.getAccessibleContext().setAccessibleName(AskCatalogue.text("start.box"));
        box.getAccessibleContext().setAccessibleDescription(KEY_HINT);
        box.putClientProperty("JTextField.placeholderText", PLACEHOLDER);
        // review M74 usability S3/#15: the composer's keys — Enter sends, Shift+Enter is a newline; while an input
        // method is composing, Enter belongs to it
        box.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "start-send");
        box.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK),
                "insert-break");
        box.getActionMap().put("start-send", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (composing()) return;
                if (action.isEnabled()) press();
            }
        });
        box.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) { renderFullCue(); }
            @Override public void focusLost(java.awt.event.FocusEvent e) { renderFullCue(); }
        });
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

        // review M74 usability #9: the page's main action is its strongest control — the accented primary, as tall as
        // the box (32 px or more), at the box's right edge; the keys are said under the box
        JPanel actions = new JPanel(new BorderLayout(UiTheme.SPACE_SM, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(LEFT_ALIGNMENT);
        actions.add(boxScroll, BorderLayout.CENTER);
        JPanel actionHolder = new JPanel(new BorderLayout());
        actionHolder.setOpaque(false);
        actionHolder.add(action, BorderLayout.NORTH);
        actions.add(actionHolder, BorderLayout.EAST);
        action.addActionListener(e -> press());
        action.putClientProperty("JButton.buttonType", null);
        keyHint.setAlignmentX(LEFT_ALIGNMENT);
        recolour.add(() -> keyHint.setForeground(UiTheme.mutedForeground()));
        fallbacks.setOpaque(false);
        fallbacks.setAlignmentX(LEFT_ALIGNMENT);
        fallbacks.setVisible(false);
        for (JTextArea t : new JTextArea[]{note, notFound, exampleNote}) {
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
        add(actions);
        add(Box.createVerticalStrut(UiTheme.SPACE_XS));
        add(keyHint);
        add(Box.createVerticalStrut(UiTheme.SPACE_XS));
        noLog.setOpaque(false);
        noLog.setAlignmentX(LEFT_ALIGNMENT);
        noLog.getAccessibleContext().setAccessibleName("No log is open");
        noLogLine.setFont(noLogLine.getFont().deriveFont(Font.BOLD));
        noLog.add(noLogLine);
        for (JButton b : new JButton[]{noLogSample, noLogOpen, noLogAnyway}) noLog.add(b);
        noLogSample.addActionListener(e -> answerNoLog(
                NoLogAction.OPEN_SAMPLE));
        noLogOpen.addActionListener(e -> answerNoLog(
                NoLogAction.OPEN_LOG));
        noLogAnyway.addActionListener(e -> answerNoLog(
                NoLogAction.ASK_ANYWAY));
        noLog.setVisible(false);
        recolour.add(() -> noLogLine.setForeground(UiTheme.noteForeground()));
        add(noLog);
        add(note);
        add(exampleNote);
        add(notFound);
        add(fallbacks);
        recolour.add(() -> {
            chip.setBackground(UiTheme.accentWash(0.22f));
            chip.setForeground(UiTheme.accentText());
            box.setBackground(UiTheme.surface());                // review #9: the box reads as a field on the band
            renderPrimary();
            renderFullCue();
        });
        recolour.forEach(Runnable::run);
    }

    // ---- rendering ----------------------------------------------------------------------------------------------------

    /** P2b: render an example download's state, as exampleDownload decided it. */
    public void renderExample(telamin.fluxtion.audit.analyser.analyser.session.ExampleDownloadState e) {
        example = e == null ? telamin.fluxtion.audit.analyser.analyser.session.ExampleDownloadState.IDLE : e;
        exampleNote.setText(example.note().isBlank() ? " " : example.note());
        exampleNote.setVisible(!example.note().isBlank());
        renderAction();
        revalidate();
        repaint();
    }

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
            String fixed = pack.normalisedWords();
            packNormalised.setText(fixed.isEmpty() ? " " : fixed);
            packNormalised.setVisible(!fixed.isEmpty());
        }
        packRefusal.setText(state.packRefusal() == null ? " "
                : "This prompt pack was not used: " + state.packRefusalWords() + ".");
        refusalRow.setVisible(state.packRefusal() != null);
        if (!rows.equals(renderedRows)) {
            renderedRows = rows;
            // every entry in view, never a scroll bar over the eighth (at most nine: Alt+1–9)
            list.setVisibleRowCount(Math.max(1, Math.min(9, rows.size())));
            String chosenId = chosen == null ? null : chosen.id();
            model.clear();
            for (Row r : rows) model.addElement(r);
            chosen = null;
            for (Row r : rows) if (r.id().equals(chosenId)) chosen = r;
            renderChip();
        }
        note.setText(state.note());
        note.setVisible(!state.note().isBlank());
        // robot re-walk #3: the held press's one line — nothing is placed or sent until one of its actions is pressed
        noLog.setVisible(state.noLogHeld() != 0);
        for (JButton b : new JButton[]{noLogSample, noLogOpen}) b.setEnabled(!state.noLogOpening());
        renderAction();
        renderFallbacks();
        renderFullCue();
        revalidate();
        repaint();
    }

    /** The built-in entries, in the catalogue's start order. */
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
        int i = 0;
        for (var e : pack.entries()) {
            // P2b: an example entry shows its download badge; choosing it fetches nothing
            out.add(e.isExample() ? new Row(null, DOWNLOAD_BADGE + " " + e.label(), null, e.example(), i)
                    : new Row(null, e.label(), e.text(), null, i));
            i++;
        }
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
        does.setText(chosen.isExample() ? exampleDoes(chosen.example()) : AskCatalogue.text(chosen.id() + ".does"));
        chipRow.setVisible(true);
        does.setVisible(true);
    }

    private void renderAction() {
        String label;
        if (chosen != null && chosen.isExample()) {
            label = AskCatalogue.text("example.verb");
            action.setText(label);
            action.getAccessibleContext().setAccessibleName(label);
            action.setEnabled(!example.busy());                     // one download at a time (the node refuses too)
            renderKeyHint(label);
            return;
        }
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
        renderKeyHint(chosen == null ? null : label);
        renderPrimary();
    }

    /**
     * Final review #7: with an entry chosen, Enter runs THAT entry — the hint under the box (and what a screen reader
     * hears for the box) says so, by the button's own words; with none, the composer's keys.
     */
    private void renderKeyHint(String chosenVerb) {
        String hint = keyHintFor(chosenVerb);
        keyHint.setText(hint);
        box.getAccessibleContext().setAccessibleDescription(hint);
    }

    static String keyHintFor(String chosenVerb) {
        return chosenVerb == null || chosenVerb.isBlank() ? KEY_HINT
                : "Enter: " + chosenVerb + " · Shift+Enter starts a new line";
    }

    /**
     * The main action's look: accent-filled while it can be pressed, the theme's own button while it cannot (an accent
     * fill with disabled text would read neither as enabled nor as disabled); at least 32 px and as tall as the box.
     */
    private void renderPrimary() {
        boolean on = action.isEnabled();
        action.setBackground(on ? UiTheme.accent() : null);
        action.setForeground(on ? UiTheme.onAccent() : null);
        Font f = action.getFont();
        if (f != null && f.isBold() != on) action.setFont(f.deriveFont(on ? Font.BOLD : Font.PLAIN));
        action.setPreferredSize(null);
        Dimension d = action.getPreferredSize();
        int h = Math.max(UiTheme.TARGET_MIN_HEIGHT, boxScroll.getPreferredSize().height);
        action.setPreferredSize(new Dimension(Math.max(d.width, 88), Math.min(h, 3 * UiTheme.TARGET_MIN_HEIGHT)));
    }

    /** Who has the keyboard focus now (replaceable by the tests: a headless run has no focus owner). */
    java.util.function.Supplier<java.awt.Component> focusOwner =
            () -> java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();

    /**
     * Final review #8: the page's window shortcuts (⌘/Alt+1…9, ⌘/Alt+R) apply unless the focus is in a text component
     * other than the start box — there the keys belong to that field (a disabled action leaves the key unconsumed).
     */
    boolean shortcutsApply() {
        java.awt.Component f = focusOwner.get();
        return !(f instanceof javax.swing.text.JTextComponent) || f == box;
    }

    /** An input method is mid-composition: the committed text is shorter than the document. */
    private boolean composing() {
        var requests = box.getInputMethodRequests();
        return requests != null && requests.getCommittedTextLength() != box.getDocument().getLength();
    }

    static boolean mac() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
    }

    /** The modifier of the page's own shortcuts: ⌘ on macOS (⌥ types characters there), Alt elsewhere. */
    static int chooserMask() {
        return mac() ? InputEvent.META_DOWN_MASK : InputEvent.ALT_DOWN_MASK;
    }

    /** How a shortcut is written on this platform: "⌘1" on macOS, "Alt+1" elsewhere. */
    static String shortcutText(String key) {
        return (mac() ? "⌘" : "Alt+") + key;
    }

    private void boxChanged() {
        renderAction();

    }

    /** The bundle key of a fallback button's words: {@code fallback.<name>} (and {@code .tip} for its tooltip). */
    static String fallbackKey(AskCatalogue.Fallback f) {
        return "fallback." + switch (f) {
            case ASSISTANT_SETTINGS -> "assistantSettings";
            case CONNECT -> "connect";
            case COPY_PROMPT -> "copyPrompt";
            case NEW_PROJECT -> "newProject";
            case BUNDLE_GUIDE -> "bundleGuide";
            case TEMPLATE -> "template";
            case USER_GUIDE -> "userGuide";
            case GUIDED_TOUR -> "guidedTour";
            case SAMPLE_PROJECT -> "sampleProject";
        };
    }

    /**
     * M77-2 (spec-ai-setup-wizard.md §6.0, T15): the start page's AI block or line says what can answer, so the
     * fallbacks' "No AI assistant can answer here yet" label is not said a second time beside it.
     */
    private boolean aiLineSaysIt;

    void setAiLineSaysIt(boolean on) {
        if (on == aiLineSaysIt) return;
        aiLineSaysIt = on;
        renderFallbacks();
    }

    /** Which fallback buttons show: in the no-AI column, the chosen entry's (or the last pressed one's); S4 and S7 always offer the template dialog. */
    private void renderFallbacks() {
        String entry = chosen != null ? chosen.id() : state.fallbackEntry() != null ? state.fallbackEntry() : "S6";
        List<AskCatalogue.Fallback> shown = new ArrayList<>();
        if (!state.route().canAnswer()) {
            if (entry.equals("NONE")) shown.add(AskCatalogue.Fallback.COPY_PROMPT);
            else AskCatalogue.byId(entry).ifPresent(e -> shown.addAll(e.fallbacks()));
        } else if (("S4".equals(entry) || "S7".equals(entry)) && chosen != null) {
            shown.add(AskCatalogue.Fallback.TEMPLATE);            // beside the draft: the existing dialog (§5.8 S4)
        }
        String fkey = entry + shown + state.route() + state.claudeCodeNotFound() + aiLineSaysIt;
        notFound.setText(AskCatalogue.text("start.claudeNotFound", java.util.Map.of("searched",
                host.claudeSearched() == null ? "" : host.claudeSearched())));
        notFound.setVisible(state.claudeCodeNotFound() && !state.route().canAnswer());
        if (fkey.equals(renderedFallbackKey)) return;
        renderedFallbackKey = fkey;
        fallbacks.removeAll();
        boolean offerInstall = state.claudeCodeNotFound() && !state.route().canAnswer();
        // review M74 usability S6: the label says what the buttons after it are — so with none, there is no label
        if (!state.route().canAnswer() && (!shown.isEmpty() || offerInstall) && !aiLineSaysIt) {
            JLabel why = UiText.label(AskCatalogue.text("start.noAi"));
            why.setForeground(UiTheme.mutedForeground());
            fallbacks.add(why);
        }
        for (AskCatalogue.Fallback f : shown) {
            String key = fallbackKey(f);
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
            // review #9: a field's edge at 3:1 on the band, the accent while it has the focus
            boxScroll.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(box.isFocusOwner()
                    ? UiTheme.accent() : UiTheme.controlBorder(), box.isFocusOwner() ? 2 : 1),
                    BorderFactory.createEmptyBorder(box.isFocusOwner() ? 2 : 3, 4, box.isFocusOwner() ? 2 : 3, 4)));
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
        if (r.flow() || r.isExample()) {
            chosen = r;
            renderChip();
        } else if (r.text() != null) {
            String was = box.getText();
            // review M74 usability S4: a second click on the same entry does not append its text again
            if (was.stripTrailing().endsWith(r.text().strip())) {
                box.requestFocusInWindow();
                renderAction();
                return;
            }
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
        if (chosen != null && chosen.isExample()) {
            host.downloadExample(chosen.packIndex());          // the entry's own button; nothing was fetched before
        } else if (chosen != null) {
            host.press(chosen.id(), text);
        } else if (!text.isEmpty()) {
            host.press("NONE", text);
        }
    }

    private void answerNoLog(NoLogAction choice) {
        if (state.noLogHeld() != 0) host.answerNoLog(state.noLogHeld(), choice);
    }

    /** For the tests: the "No log is open" line and its actions. */
    JPanel noLogRow() {
        return noLog;
    }

    JButton noLogButton(NoLogAction choice) {
        return switch (choice) {
            case OPEN_SAMPLE -> noLogSample;
            case OPEN_LOG -> noLogOpen;
            case ASK_ANYWAY -> noLogAnyway;
        };
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

    JLabel keyHintLabel() {
        return keyHint;
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

    JTextArea packNormalised() {
        return packNormalised;
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
            setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));      // review §4.3: rows at least 24 px tall
            String key = index < 9 ? " (" + shortcutText(Integer.toString(index + 1)) + ")" : "";
            setToolTipText(UiText.tooltip((r.isExample() ? exampleDoes(r.example())
                    : r.text() != null ? r.text() : AskCatalogue.text(r.id() + ".does")) + key));   // plain
            getAccessibleContext().setAccessibleName(accessibleName(index + 1, l.getModel().getSize(), r.label()));
            if (!selected) setForeground(UiTheme.accentText());
            setOpaque(selected);
            return this;
        }
    }

    /** What choosing an example says: its publisher, marked third-party unless Telamin's, and that nothing is fetched yet. */
    static String exampleDoes(telamin.fluxtion.audit.analyser.analyser.assistant.ExampleSource x) {
        return AskCatalogue.text("example.does", java.util.Map.of("publisher", x.publisher(),
                "party", x.thirdParty() ? AskCatalogue.text("example.thirdParty") : AskCatalogue.text("example.telamin")));
    }

    JTextArea exampleNote() {
        return exampleNote;
    }

    /** For a host that needs to know the box's words (Copy prompt). */
    public String boxText() {
        return box.getText();
    }
}
