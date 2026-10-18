package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Posture;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * M77-2 (spec-ai-setup-wizard.md §5, §6, §15): the <i>Set up AI</i> window — modeless, one instance, owned by the main
 * window. It RENDERS {@link AiSetupState} and reports the person's acts through {@link Host}; it decides nothing (rule 9,
 * {@code OneDispatchModelTest}). Every sentence is {@link AiSetupLines}'.
 *
 * <p>Screens: 1 (the four choices), 2a (Claude inside: the two checks and the Safe / Full sub-choice), Other options
 * (the in-app assistant) and the Summary. Screen 2b (your own client) is M77-3; until then <i>Your own client</i> and
 * <i>Both</i> open today's {@code McpSetupDialog} (review R1-11).
 */
final class AiSetupWindow extends JDialog {

    /** What the person did. The frame turns each into a session fact. */
    interface Host {
        void choose(Choice choice);

        void posture(Posture posture, String fullMode);

        void proceed(Step step, boolean keyEntered);

        void back(Step step);

        void cancel();

        void checkAgain(AiSetupState.CheckKind check);

        void copy(String text);

        void installHelp();

        void suggested(AiSetupState.Suggested suggested);

        /** Where a check found claude, shortened to {@code ~} — by reference; null when unknown. */
        String shownPath(long ref);
    }

    static final int DEFAULT_WIDTH = 640;
    static final int DEFAULT_HEIGHT = 560;
    static final int MIN_WIDTH = 520;
    static final int MIN_HEIGHT = 480;

    /** The first three sentences of the Full warning, by reference (§6.2). */
    static List<String> fullWarningBullets() {
        String[] s = ConfigPanel.FULL_POSTURE_WARNING.replace("\n\n", " ").split("(?<=\\.) ");
        List<String> out = new ArrayList<>();
        for (int i = 0; i < Math.min(3, s.length); i++) out.add(s[i].trim());
        return out;
    }

    private final Host host;
    private final JPanel content = new JPanel();
    private final JScrollPane scroll;
    private final JButton back = UiText.button("Back");
    private final JButton cancel = UiText.button("Cancel");
    private final JButton primary = UiText.button("Continue");
    private final WrapLine line = new WrapLine(" ");
    private final JLabel footer = UiText.label(AiSetupLines.FOOTER);
    /**
     * M77-5's slot: an empty container under the SELECTED option's title row, above its sentence, for the diagram PR to
     * fill. Empty, it has no visible size. It moves with the selection; its contents are kept across renders.
     */
    final JPanel selectedOptionExtra = new JPanel(new BorderLayout()) {
        @Override public Dimension getPreferredSize() {
            return getComponentCount() == 0 ? new Dimension(0, 0) : super.getPreferredSize();
        }

        @Override public Dimension getMaximumSize() {
            return getComponentCount() == 0 ? new Dimension(0, 0)
                    : new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height);
        }
    };

    /** The in-app details keep what the person typed across renders. */
    final InAppDetails inApp = new InAppDetails();
    private AiSetupState state = AiSetupState.IDLE;
    private String renderedKey = "";
    private Step shownStep;
    private Component focusFirst;

    AiSetupWindow(Window owner, Host host) {
        super(owner, AiSetupLines.TITLE, ModalityType.MODELESS);
        this.host = host;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                host.cancel();                       // the window's close is Cancel (Done on the Summary)
            }
        });
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(UiTheme.SPACE_LG, UiTheme.SPACE_LG, UiTheme.SPACE_MD,
                UiTheme.SPACE_LG));
        JPanel holder = new AssistantPanel.WidthTrackingPanel();
        holder.setLayout(new BorderLayout());
        holder.add(content, BorderLayout.NORTH);
        scroll = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED, ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JPanel south = new JPanel(new BorderLayout(UiTheme.SPACE_SM, UiTheme.SPACE_XS));
        south.setBorder(BorderFactory.createEmptyBorder(UiTheme.SPACE_SM, UiTheme.SPACE_LG, UiTheme.SPACE_MD,
                UiTheme.SPACE_LG));
        line.getAccessibleContext().setAccessibleName("Set up AI status");
        JPanel lines = new JPanel();
        lines.setLayout(new BoxLayout(lines, BoxLayout.Y_AXIS));
        line.setAlignmentX(LEFT_ALIGNMENT);
        footer.setAlignmentX(LEFT_ALIGNMENT);
        footer.setForeground(UiTheme.mutedForeground());
        lines.add(line);
        lines.add(footer);
        south.add(lines, BorderLayout.NORTH);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.add(back);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.SPACE_SM, 0));
        right.add(cancel);
        right.add(primary);
        south.add(left, BorderLayout.WEST);
        south.add(right, BorderLayout.EAST);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(scroll, BorderLayout.CENTER);
        getContentPane().add(south, BorderLayout.SOUTH);

        back.addActionListener(e -> host.back(state.step()));
        cancel.addActionListener(e -> host.cancel());
        primary.addActionListener(e -> host.proceed(state.step(), inApp.keyEntered()));

        JRootPane root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "setup-cancel");
        root.getActionMap().put("setup-cancel", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { host.cancel(); }
        });
        // §15 (review R1-29): Back is Cmd+[ on macOS and Alt+Left elsewhere
        KeyStroke backKey = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("mac")
                ? KeyStroke.getKeyStroke(KeyEvent.VK_OPEN_BRACKET, InputEvent.META_DOWN_MASK)
                : KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.ALT_DOWN_MASK);
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(backKey, "setup-back");
        root.getActionMap().put("setup-back", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { if (back.isVisible() && back.isEnabled()) back.doClick(); }
        });
        // a wrapping line measures itself at its parent's width, so a new width needs a second pass (the P-1 lesson)
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent e) { relayout(); }
        });
        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setSize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        if (owner != null) setLocationRelativeTo(owner);
    }

    private String lastModel;
    private String lastProvider;
    private String lastInAppModel;
    private boolean lastKeyStored;
    /** A theme switch redraws the screen in the new theme's colours (the muted lines are coloured when built). */
    private final java.beans.PropertyChangeListener themeListener = e -> {
        if (!"lookAndFeel".equals(e.getPropertyName())) return;
        SwingUtilities.invokeLater(() -> {
            for (JComponent c : List.of(inApp.provider, inApp.model, inApp.apiKey, inApp.keyNote)) {
                SwingUtilities.updateComponentTreeUI(c);
            }
            footer.setForeground(UiTheme.mutedForeground());
            renderedKey = "";
            render(state, lastModel, lastProvider, lastInAppModel, lastKeyStored);
        });
    };

    @Override
    public void addNotify() {
        super.addNotify();
        javax.swing.UIManager.addPropertyChangeListener(themeListener);
    }

    @Override
    public void removeNotify() {
        javax.swing.UIManager.removePropertyChangeListener(themeListener);
        super.removeNotify();
    }

    /** Render the session's decision. Unchanged input changes nothing on screen. */
    void render(AiSetupState s, String claudeModel, String provider, String model, boolean keyStored) {
        lastModel = claudeModel;
        lastProvider = provider;
        lastInAppModel = model;
        lastKeyStored = keyStored;
        AiSetupState next = s == null ? AiSetupState.IDLE : s;
        if (next.step() == Step.OTHER && shownStep != Step.OTHER) inApp.load(provider, model, keyStored);
        state = next;
        String key = next.step() + "|" + next.choice() + "|" + next.inForce() + "|" + next.posture() + "|"
                + next.fullMode() + "|" + next.installed() + "|" + next.signedIn() + "|" + next.confirming() + "|"
                + next.applying() + "|" + next.claudeFound() + "|" + next.codexFound() + "|" + next.suggested() + "|"
                + next.origin() + "|" + claudeModel;
        boolean stepChanged = next.step() != shownStep;
        shownStep = next.step();
        line.setText(next.line().isBlank() ? " " : next.line());
        line.setVisible(!next.line().isBlank());
        if (key.equals(renderedKey)) return;
        renderedKey = key;
        content.removeAll();
        focusFirst = null;
        selectedOptionExtra.setOpaque(false);
        switch (next.step()) {
            case CHOICE -> choiceScreen(next);
            case INSIDE -> insideScreen(next, claudeModel);
            case OTHER -> otherScreen(next);
            case SUMMARY -> summaryScreen(next);
            case CLIENT -> content.add(text(AiSetupLines.CLIENT_INTERIM));
        }
        for (Component c : content.getComponents()) ((JComponent) c).setAlignmentX(LEFT_ALIGNMENT);
        buttons(next);
        // the confirmation is application-modal; the wizard says so by being inert behind it
        boolean live = next.confirming() == AiSetupState.Confirming.NONE && !next.applying();
        setInert(content, !live);
        back.setEnabled(live);
        cancel.setEnabled(next.confirming() == AiSetupState.Confirming.NONE);
        relayout();
        getAccessibleContext().setAccessibleDescription(stepIndicator(next));
        if (stepChanged) {
            Component f = focusFirst != null ? focusFirst : primary;
            SwingUtilities.invokeLater(f::requestFocusInWindow);
            scroll.getVerticalScrollBar().setValue(0);
        }
    }

    // ---- the screens --------------------------------------------------------------------------------------------------

    private void choiceScreen(AiSetupState s) {
        content.add(heading(AiSetupLines.QUESTION));
        content.add(Box.createVerticalStrut(UiTheme.SPACE_MD));
        ButtonGroup group = new ButtonGroup();
        for (Choice c : List.of(Choice.NO_AI, Choice.CLIENT, Choice.INSIDE, Choice.BOTH)) {
            String title = switch (c) {
                case NO_AI -> AiSetupLines.NO_AI_TITLE;
                case CLIENT -> AiSetupLines.CLIENT_TITLE;
                case INSIDE -> AiSetupLines.INSIDE_TITLE;
                default -> AiSetupLines.BOTH_TITLE;
            };
            JRadioButton r = UiText.radio(title);
            r.setFont(r.getFont().deriveFont(Font.BOLD));
            r.setSelected(s.choice() == c);
            r.getAccessibleContext().setAccessibleName(title);
            r.getAccessibleContext().setAccessibleDescription(AiSetupLines.sentenceFor(c));
            r.addActionListener(e -> host.choose(c));
            r.putClientProperty("aiSetup.choice", c);
            group.add(r);
            if (s.choice() == c) focusFirst = r;
            JPanel card = new JPanel();
            card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
            card.setOpaque(false);
            JPanel top = new JPanel(new WrapLayout(FlowLayout.LEFT, UiTheme.SPACE_SM, 0));
            top.setOpaque(false);
            top.add(r);
            String badge = badge(c, s);
            if (badge != null) {
                JLabel b = AssistantPanel.chip(badge, false);
                b.setToolTipText(UiText.tooltip(AiSetupLines.FOUND_TOOLTIP));
                top.add(b);
            }
            card.add(top);
            if (s.choice() == c) {
                // M77-5's slot (owner, 2026-10-18): the selected option's data-flow diagram goes here, between its
                // title row and its sentence; empty, it takes no space. The sentence stays visible on every card.
                selectedOptionExtra.setAlignmentX(LEFT_ALIGNMENT);
                card.add(selectedOptionExtra);
            }
            WrapLine sentence = new WrapLine(AiSetupLines.sentenceFor(c));
            sentence.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 0));
            card.add(sentence);
            if (c == Choice.CLIENT && (s.inForce() == Choice.INSIDE || s.inForce() == Choice.BOTH)) {
                WrapLine warn = new WrapLine(AiSetupLines.CLIENT_TURNS_OFF_INSIDE);
                warn.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 0));
                warn.setForeground(UiTheme.attentionForeground());
                card.add(warn);
            }
            for (Component k : card.getComponents()) ((JComponent) k).setAlignmentX(LEFT_ALIGNMENT);
            content.add(card);
            content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        }
        if (focusFirst == null && group.getElements().hasMoreElements()) focusFirst = group.getElements().nextElement();
        JButton other = link(AiSetupLines.OTHER_OPTIONS_LINK);
        other.addActionListener(e -> {
            host.choose(Choice.IN_APP);
            host.proceed(Step.CHOICE, false);
        });
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        content.add(other);
    }

    private static String badge(Choice c, AiSetupState s) {
        return switch (c) {
            case CLIENT -> s.claudeFound() ? AiSetupLines.CLAUDE_FOUND : s.codexFound() ? AiSetupLines.CODEX_FOUND : null;
            case INSIDE, BOTH -> s.claudeFound() ? AiSetupLines.CLAUDE_FOUND : AiSetupLines.CLAUDE_NOT_FOUND;
            default -> null;
        };
    }

    private void insideScreen(AiSetupState s, String claudeModel) {
        if (s.choice() == Choice.BOTH) content.add(muted(AiSetupLines.INSIDE_STEP_BOTH));
        content.add(heading(AiSetupLines.INSIDE_TITLE));
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        content.add(text(AiSetupLines.CHECKS_INTRO));
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));

        // Installed
        AiSetupState.Installed iv = s.installed().verdict();
        String installedWords = AiSetupLines.installedText(s.installed());
        String path = iv == AiSetupState.Installed.FOUND || iv == AiSetupState.Installed.DID_NOT_RUN
                ? host.shownPath(s.installed().pathRef()) : null;
        if (path != null && iv == AiSetupState.Installed.FOUND) installedWords += " " + path;
        if (path != null && iv == AiSetupState.Installed.DID_NOT_RUN) {
            installedWords = "Claude Code was found at " + path + " but did not answer --version. Try running it once in "
                    + "a terminal.";
        }
        JComponent installedRow = checkRow("Installed", glyph(iv), installedWords);
        content.add(installedRow);
        if (iv == AiSetupState.Installed.NOT_FOUND || iv == AiSetupState.Installed.DID_NOT_RUN) {
            JPanel actions = row();
            JButton again = UiText.button(AiSetupLines.CHECK_AGAIN);
            again.getAccessibleContext().setAccessibleName("Check again: installed");
            again.addActionListener(e -> host.checkAgain(AiSetupState.CheckKind.INSTALLED));
            actions.add(again);
            if (iv == AiSetupState.Installed.NOT_FOUND) {
                JButton how = UiText.button(AiSetupLines.HOW_TO_INSTALL);
                how.addActionListener(e -> host.installHelp());
                actions.add(how);
            }
            content.add(actions);
            focusFirst = again;
        }
        // Signed in (only after Installed passed)
        AiSetupState.SignedIn sv = s.signedIn().verdict();
        if (iv == AiSetupState.Installed.FOUND) {
            String words = AiSetupLines.signedInText(s.signedIn());
            content.add(checkRow("Signed in", glyph(sv), words));
            if (sv == AiSetupState.SignedIn.NOT_SIGNED_IN || sv == AiSetupState.SignedIn.UNKNOWN) {
                JPanel actions = row();
                JButton again = UiText.button(AiSetupLines.CHECK_AGAIN);
                again.getAccessibleContext().setAccessibleName("Check again: signed in");
                again.addActionListener(e -> host.checkAgain(AiSetupState.CheckKind.SIGNED_IN));
                actions.add(again);
                if (sv == AiSetupState.SignedIn.NOT_SIGNED_IN) {
                    JButton copy = UiText.button(AiSetupLines.COPY_COMMAND);
                    copy.addActionListener(e -> host.copy(AiSetupLines.LOGIN_COMMAND));
                    actions.add(copy);
                }
                content.add(actions);
                if (sv == AiSetupState.SignedIn.NOT_SIGNED_IN) content.add(muted(AiSetupLines.NOT_SIGNED_IN_CONTINUE));
            }
            if (sv == AiSetupState.SignedIn.RUNNING) content.add(muted(AiSetupLines.STILL_CHECKING));
        }
        content.add(Box.createVerticalStrut(UiTheme.SPACE_MD));

        // the sub-question
        content.add(bold(AiSetupLines.POSTURE_QUESTION));
        ButtonGroup g = new ButtonGroup();
        JRadioButton safe = UiText.radio(AiSetupLines.SAFE_TITLE);
        JRadioButton full = UiText.radio(AiSetupLines.FULL_TITLE);
        g.add(safe);
        g.add(full);
        safe.setSelected(s.posture() == Posture.SAFE);
        full.setSelected(s.posture() == Posture.FULL);
        safe.getAccessibleContext().setAccessibleDescription(AiSetupLines.SAFE_LINE_1 + " " + AiSetupLines.SAFE_LINE_2);
        full.getAccessibleContext().setAccessibleDescription(AiSetupLines.FULL_LINE);
        safe.addActionListener(e -> host.posture(Posture.SAFE, s.fullMode()));
        full.addActionListener(e -> host.posture(Posture.FULL, s.fullMode()));
        content.add(safe);
        content.add(indented(AiSetupLines.SAFE_LINE_1));
        content.add(indented(AiSetupLines.SAFE_LINE_2));
        content.add(full);
        content.add(indented(AiSetupLines.FULL_LINE));
        if (s.posture() == Posture.FULL) {
            for (String b : fullWarningBullets()) {
                WrapLine w = new WrapLine("• " + b);
                w.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 0));
                w.setForeground(UiTheme.attentionForeground());
                content.add(w);
            }
            JComboBox<String> mode = UiText.plainCells(new JComboBox<>(
                    telamin.fluxtion.audit.analyser.analyser.config.ConfigStore.FULL_MODES.toArray(new String[0])));
            mode.setSelectedItem(s.fullMode());
            mode.getAccessibleContext().setAccessibleName("Full Claude Code mode");
            mode.addActionListener(e -> host.posture(Posture.FULL, String.valueOf(mode.getSelectedItem())));
            JComponent modeRow = InAppDetails.field("Mode:", mode);
            ((JComponent) modeRow).setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 0));
            content.add(modeRow);
        }
        if (claudeModel != null && !claudeModel.isBlank()) content.add(muted("Model: " + claudeModel
                + " (change it in Settings ▸ Assistant)"));
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        content.add(muted(AiSetupLines.SENDS));
        content.add(muted(AiSetupLines.FOLDER_NOTE));
        if (focusFirst == null) focusFirst = s.posture() == Posture.FULL ? full : safe;
    }

    private void otherScreen(AiSetupState s) {
        content.add(heading(AiSetupLines.OTHER_TITLE));
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        if (s.claudeFound()) content.add(muted(AiSetupLines.CLAUDE_NEEDS_NO_KEY));
        content.add(inApp.rows());
        content.add(muted(AiSetupLines.KEY_NOTE));
        content.add(muted(AiSetupLines.IN_APP_SENDS));
        focusFirst = inApp.apiKey;
    }

    private void summaryScreen(AiSetupState s) {
        content.add(heading(AiSetupLines.SUMMARY_TITLE));
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        AssistantProfile p = switch (s.inForce()) {
            case INSIDE, BOTH -> s.posture() == Posture.FULL ? AssistantProfile.FULL : AssistantProfile.SAFE;
            case IN_APP -> AssistantProfile.IN_APP;
            case NO_AI -> AssistantProfile.OFF;
            default -> AssistantProfile.NONE;
        };
        content.add(text("• " + AiSetupLines.summaryLine(s.inForce(), p, s.installed(), s.signedIn())));
        content.add(text("• " + AiSetupLines.SUMMARY_CHANGE));
        content.add(Box.createVerticalStrut(UiTheme.SPACE_MD));
        if (s.suggested() == AiSetupState.Suggested.RIGHT_CLICK) {
            content.add(text(AiSetupLines.RIGHT_CLICK_TO_ASK));
        }
    }

    /**
     * Lay the screen out twice: the first pass gives every panel its width, the second lets each wrapping line take the
     * height its words need at that width — so no sentence is cut to one line.
     */
    void relayout() {
        for (int pass = 0; pass < 2; pass++) {
            invalidateAll(getContentPane());
            getContentPane().validate();
        }
        repaint();
    }

    private static void invalidateAll(java.awt.Container c) {
        c.invalidate();
        for (Component k : c.getComponents()) if (k instanceof java.awt.Container sub) invalidateAll(sub);
    }

    // ---- the buttons ------------------------------------------------------------------------------------------------

    private JButton suggestedButton;

    private void buttons(AiSetupState s) {
        back.setVisible(s.step() == Step.INSIDE || s.step() == Step.OTHER);
        cancel.setVisible(s.step() != Step.SUMMARY);
        String label = switch (s.step()) {
            case CHOICE -> "Continue";
            case INSIDE -> s.posture() == Posture.FULL ? AiSetupLines.CONFIRM_FULL_BUTTON
                    : s.choice() == Choice.BOTH ? "Continue" : "Finish";
            case OTHER -> "Finish";
            case SUMMARY -> AiSetupLines.DONE;
            case CLIENT -> "Finish";
        };
        primary.setText(label);
        primary.getAccessibleContext().setAccessibleName(label);
        boolean enabled = switch (s.step()) {
            case CHOICE -> s.choice() != Choice.NONE;
            case INSIDE -> s.insideMayProceed() && !s.applying() && s.confirming() == AiSetupState.Confirming.NONE;
            case OTHER, SUMMARY -> !s.applying();
            case CLIENT -> false;
        };
        primary.setEnabled(enabled);
        // the Summary's one suggested action is the default; Done closes
        JPanel right = (JPanel) primary.getParent();
        if (suggestedButton != null) right.remove(suggestedButton);
        suggestedButton = null;
        String suggested = switch (s.suggested()) {
            case OPEN_SAMPLE -> AiSetupLines.SUGGEST_SAMPLE;
            case TOUR -> AiSetupLines.SUGGEST_TOUR;
            default -> null;
        };
        if (suggested != null) {
            suggestedButton = UiText.button(suggested);
            suggestedButton.addActionListener(e -> host.suggested(s.suggested()));
            right.add(suggestedButton, 0);
            getRootPane().setDefaultButton(suggestedButton);
            focusFirst = suggestedButton;
        } else {
            getRootPane().setDefaultButton(primary);
        }
        right.revalidate();
    }

    /** For the tests and the robot: the window's buttons. */
    JButton primaryButton() {
        return primary;
    }

    JButton backButton() {
        return back;
    }

    JButton cancelButton() {
        return cancel;
    }

    JButton suggestedButton() {
        return suggestedButton;
    }

    /** M77-5: the selected option's slot (empty in M77-2). */
    JPanel selectedOptionExtra() {
        return selectedOptionExtra;
    }

    JPanel contentPanel() {
        return content;
    }

    /** The radio for {@code c} on Screen 1, or null. */
    JRadioButton choiceRadio(Choice c) {
        return find(content, c);
    }

    private static JRadioButton find(java.awt.Container root, Choice c) {
        for (Component k : root.getComponents()) {
            if (k instanceof JRadioButton r && c == r.getClientProperty("aiSetup.choice")) return r;
            if (k instanceof java.awt.Container sub) {
                JRadioButton f = find(sub, c);
                if (f != null) return f;
            }
        }
        return null;
    }

    AssistantProfiles.Details details() {
        return inApp.details();
    }

    // ---- small pieces ---------------------------------------------------------------------------------------------

    static String stepIndicator(AiSetupState s) {
        return switch (s.step()) {
            case CHOICE -> "Step 1 · " + AiSetupLines.QUESTION;
            case INSIDE -> s.choice() == Choice.BOTH ? AiSetupLines.INSIDE_STEP_BOTH : "Step 2 · " + AiSetupLines.INSIDE_TITLE;
            case OTHER -> "Step 2 · " + AiSetupLines.OTHER_TITLE;
            case SUMMARY -> AiSetupLines.SUMMARY_TITLE;
            case CLIENT -> AiSetupLines.CLIENT_TITLE;
        };
    }

    /** ✓ ✗ ⏳ ? — a glyph AND the words beside it, never colour alone (§15). */
    static String glyph(AiSetupState.Installed v) {
        return switch (v) {
            case UNCHECKED -> "·";
            case RUNNING -> "⏳";
            case FOUND -> "✓";
            case NOT_FOUND, DID_NOT_RUN -> "✗";
        };
    }

    static String glyph(AiSetupState.SignedIn v) {
        return switch (v) {
            case UNCHECKED -> "·";
            case RUNNING -> "⏳";
            case SIGNED_IN, KEY -> "✓";
            case NOT_SIGNED_IN -> "✗";
            case UNKNOWN -> "?";
        };
    }

    /** One check: glyph and words in one focusable row whose accessible name is the whole sentence (§15). */
    private static JComponent checkRow(String check, String glyph, String words) {
        JPanel r = new JPanel(new BorderLayout(UiTheme.SPACE_SM, 0));
        r.setOpaque(false);
        JLabel g = UiText.label(glyph);
        g.setFont(g.getFont().deriveFont(Font.BOLD, g.getFont().getSize2D() + 2f));
        g.setVerticalAlignment(JLabel.TOP);
        WrapLine w = new WrapLine(check + ": " + (words.isBlank() ? "not checked yet" : words));
        r.add(g, BorderLayout.WEST);
        r.add(w, BorderLayout.CENTER);
        r.setFocusable(true);
        r.getAccessibleContext().setAccessibleName(AiSetupLines.accessible(check, words));
        r.putClientProperty("aiSetup.check", check);
        return r;
    }

    private static JPanel row() {
        JPanel p = new JPanel(new WrapLayout(FlowLayout.LEFT, UiTheme.SPACE_SM, UiTheme.SPACE_XS));
        p.setOpaque(false);
        return p;
    }

    private static WrapLine heading(String s) {
        WrapLine w = new WrapLine(s).bold();
        w.setFont(w.getFont().deriveFont(w.getFont().getSize2D() + 3f));
        return w;
    }

    private static WrapLine bold(String s) {
        return new WrapLine(s).bold();
    }

    private static WrapLine text(String s) {
        return new WrapLine(s);
    }

    private static WrapLine muted(String s) {
        WrapLine w = new WrapLine(s);
        w.setForeground(UiTheme.mutedForeground());
        return w;
    }

    private static WrapLine indented(String s) {
        WrapLine w = new WrapLine(s);
        w.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 0));
        return w;
    }

    private static JButton link(String s) {
        JButton b = UiText.button(s);
        b.setBorderPainted(false);
        b.setContentAreaFilled(false);
        b.setForeground(UiTheme.accentText());
        b.setHorizontalAlignment(JButton.LEFT);
        return b;
    }

    private static void setInert(java.awt.Container c, boolean inert) {
        for (Component k : c.getComponents()) {
            if (k instanceof javax.swing.AbstractButton || k instanceof JComboBox<?>
                    || k instanceof javax.swing.text.JTextComponent t && t.isEditable()) {
                k.setEnabled(!inert);
            }
            if (k instanceof java.awt.Container sub) setInert(sub, inert);
        }
    }

    /** Where claude was found, shortened to {@code ~} under the home folder. */
    static String shorten(Path p, Path home) {
        if (p == null) return null;
        if (home != null && p.startsWith(home)) return "~/" + home.relativize(p);
        return p.toString();
    }
}
