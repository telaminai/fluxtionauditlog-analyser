package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Choice;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Posture;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState.Step;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.ui.setup.FlowDiagram;

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
 * <p>Screens: 1 (the four choices), 2a (Claude inside: the two checks and the Safe / Full sub-choice), 2b (your own
 * client, M77-3: which client, external access, the registration and the live check), Other options (the in-app
 * assistant) and the Summary. Today's {@code McpSetupDialog} stays behind 2b's <i>More options…</i> (OD-77.12).
 */
final class AiSetupWindow extends JDialog {

    /** What the person did. The frame turns each into a session fact. */
    interface Host {
        void choose(Choice choice);

        void posture(Posture posture, String fullMode);

        void proceed(Step step, boolean keyEntered);

        default void enableBuild() { proceed(Step.INSIDE, false); }

        void back(Step step);

        void cancel();

        void checkAgain(AiSetupState.CheckKind check);

        void copy(String text);

        void installHelp();

        void suggested(AiSetupState.Suggested suggested);

        /** Where a check found claude, shortened to {@code ~} — by reference; null when unknown. */
        String shownPath(long ref);

        // ---- M77-3: Screen 2b
        default void client(AiSetupState.Client client) { }

        /** The access switch: an ask — actionListener decides (on: the confirmation first). */
        default void access(boolean on) { }

        default void copy(telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.CopyWhat what) { }

        default void register() { }

        default void moreOptions() { }

        /** OD-77.5: the box under No AI. */
        default void alsoTurnOff(boolean on) { }

        default void saveSnippet() { }

        /** The command (or another client's configuration) as the person reads it; "" when there is none. */
        default String commandText(AiSetupState.Client client) { return ""; }

        /** What a client called itself, by reference (OD-77.8); null when unknown. */
        default String clientLabel(long ref) { return null; }

        /** Open a page of the docs site (OD-77.17: the FAQ entry on Claude Code's own usage data). */
        default void openDocs(String page) {
            try {
                java.awt.Desktop.getDesktop().browse(java.net.URI.create(
                        telamin.fluxtion.audit.analyser.analyser.assistant.AskPointers.DOCS_URL + page));
            } catch (Exception ignored) {
                // no browser: the link is a convenience, the FAQ is on the docs site
            }
        }
    }

    static final int DEFAULT_WIDTH = 640;
    static final int DEFAULT_HEIGHT = 560;
    static final int MIN_WIDTH = 520;
    static final int MIN_HEIGHT = 480;
    /** The FAQ link's text lines up with the picture's own margin. */
    private static final int PAD_LINK = 6;

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
    private final JButton primary = UiText.button(AiSetupLines.CONTINUE);
    private final WrapLine line = new WrapLine(" ");
    private final JLabel footer = UiText.label(AiSetupLines.FOOTER);
    /**
     * M77-5's slot under the SELECTED option's title row, above its sentence: <i>Where data goes</i> (§6.1a). It moves
     * with the selection; its contents are kept across renders. Empty, it would have no visible size.
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

    /**
     * M77-3: the slot beside Screen 2b's access switch; M77-4 fills it with M77-5's picture ({@link #accessDiagram}), so
     * the person sees the one-way path the switch opens. Empty, it has no visible size.
     */
    final JPanel accessDiagramSlot = new JPanel(new BorderLayout()) {
        @Override public Dimension getPreferredSize() {
            return getComponentCount() == 0 ? new Dimension(0, 0) : super.getPreferredSize();
        }

        @Override public Dimension getMaximumSize() {
            return getComponentCount() == 0 ? new Dimension(0, 0) : super.getMaximumSize();
        }
    };
    /** Screen 2b: the command is shown collapsed to its first words until the person asks for all of it (view only). */
    private boolean commandExpanded;

    /** M77-5 (§6.1a): the selected option's picture, and (Claude inside / Both) the FAQ link on Claude Code's own data. */
    final FlowDiagram diagram = new FlowDiagram();
    /**
     * M77-4 (§6.1a, §18): the same picture on Screen 2b, beside the access switch — Your own AI client (or Both), drawn
     * from the published state, so its client arrow follows external access as the switch changes it.
     */
    final FlowDiagram accessDiagram = new FlowDiagram();
    final JButton usageLink = link(AiSetupLines.DIAGRAM_USAGE_LINK);
    private final JPanel picture = new JPanel();

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
        picture.setLayout(new BoxLayout(picture, BoxLayout.Y_AXIS));
        picture.setOpaque(false);
        picture.setBorder(BorderFactory.createEmptyBorder(UiTheme.SPACE_XS, 28, UiTheme.SPACE_XS, 0));
        diagram.setAlignmentX(LEFT_ALIGNMENT);
        usageLink.setAlignmentX(LEFT_ALIGNMENT);
        usageLink.setBorder(BorderFactory.createEmptyBorder(0, PAD_LINK, 0, 0));
        usageLink.getAccessibleContext().setAccessibleName(AiSetupLines.DIAGRAM_USAGE_LINK);
        usageLink.addActionListener(e -> host.openDocs(AiSetupLines.DIAGRAM_USAGE_PAGE));
        picture.add(diagram);
        picture.add(usageLink);
        selectedOptionExtra.add(picture, BorderLayout.CENTER);
        accessDiagram.setAlignmentX(LEFT_ALIGNMENT);
        accessDiagramSlot.setBorder(BorderFactory.createEmptyBorder(UiTheme.SPACE_XS, 22, UiTheme.SPACE_XS, 0));
        accessDiagramSlot.add(accessDiagram, BorderLayout.CENTER);
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
        primary.addActionListener(e -> {
            if (state.buildOnboarding()) host.enableBuild();
            else host.proceed(state.step(), inApp.keyEntered());
        });

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
            SwingUtilities.updateComponentTreeUI(picture);
            SwingUtilities.updateComponentTreeUI(accessDiagramSlot);
            usageLink.setBorder(BorderFactory.createEmptyBorder(0, PAD_LINK, 0, 0));
            usageLink.setForeground(UiTheme.accentText());
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
                + next.origin() + "|" + claudeModel + "|" + next.external() + "|" + commandExpanded;
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
            case CLIENT -> clientScreen(next);
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
                JComponent b = AssistantPanel.chip(badge, false);   // a Chip (first-run polish): wraps in a narrow row
                b.setToolTipText(UiText.tooltip(AiSetupLines.FOUND_TOOLTIP));
                top.add(b);
            }
            card.add(top);
            if (s.choice() == c) {
                // M77-5 (§6.1a, OD-77.14): the selected option's picture, between its title row and its sentence; only
                // one picture is on screen, and the sentence stays visible on every card. The picture renders the
                // state; it decides nothing (rule 9).
                FlowDiagram.Option shown = FlowDiagram.Option.of(c);
                diagram.setSnapshot(s, shown);
                usageLink.setVisible(diagram.model().usageLink());
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
        if (s.external().offerAlsoTurnOff()) {
            // OD-77.5: No AI while external access is on — ticked by default (turning off is the safe direction)
            javax.swing.JCheckBox off = UiText.checkBox(AiSetupLines.ALSO_TURN_OFF_ACCESS);
            off.setSelected(s.external().alsoTurnOff());
            off.setBorder(BorderFactory.createEmptyBorder(0, 24, 0, 0));
            off.addActionListener(e -> host.alsoTurnOff(off.isSelected()));
            off.putClientProperty("aiSetup.alsoTurnOff", Boolean.TRUE);
            content.add(off);
        }
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
        if (s.buildOnboarding()) {
            content.add(bold(AiSetupLines.BUILD_CONSENT_TITLE));
            content.add(text(ConfigPanel.FULL_POSTURE_WARNING));
            content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
            content.add(text(AiSetupLines.ACCEPT_EDITS_NOTE));
            content.add(muted(AiSetupLines.SENDS));
            content.add(muted("Nothing starts until you enter a message and press Send."));
            focusFirst = cancel;
            return;
        }
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
            if (AiSetupLines.BUILD_FULL_MODE.equals(s.fullMode())) content.add(indented(AiSetupLines.ACCEPT_EDITS_NOTE));
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
        if (s.inForce() != Choice.CLIENT) {
            content.add(text("• " + AiSetupLines.summaryLine(s.inForce(), p, s.installed(), s.signedIn())));
        }
        if (s.inForce() == Choice.CLIENT || s.inForce() == Choice.BOTH) {
            content.add(text("• " + AiSetupLines.clientSummary(s.external(), hhmm(s.external().live().at()))));
        }
        content.add(text("• " + AiSetupLines.SUMMARY_CHANGE));
        content.add(Box.createVerticalStrut(UiTheme.SPACE_MD));
        if (s.suggested() == AiSetupState.Suggested.BUILD) content.add(text(AiSetupLines.BUILD_NEXT));
        if (s.suggested() == AiSetupState.Suggested.RIGHT_CLICK) {
            content.add(text(AiSetupLines.RIGHT_CLICK_TO_ASK));
        }
        if (s.suggested() == AiSetupState.Suggested.COPY_QUESTION) {
            content.add(text(AiSetupLines.ASK_IN_TERMINAL));
        }
    }

    /**
     * Screen 2b (§6.3): four numbered parts on one scrolling screen — which client, the access switch, the registration
     * and the live check. Every word is the node's ({@link AiSetupLines}); every act is reported through {@link Host}.
     */
    private void clientScreen(AiSetupState s) {
        AiSetupState.External x = s.external();
        if (s.choice() == Choice.BOTH) content.add(muted(AiSetupLines.CLIENT_STEP_BOTH));
        content.add(heading(AiSetupLines.CLIENT_SCREEN_TITLE));
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));

        // 1. Which client?
        content.add(bold(AiSetupLines.PART_WHICH));
        ButtonGroup which = new ButtonGroup();
        for (AiSetupState.Client c : List.of(AiSetupState.Client.CLAUDE_CODE, AiSetupState.Client.CODEX,
                AiSetupState.Client.GENERIC)) {
            String title = switch (c) {
                case CLAUDE_CODE -> AiSetupLines.CLIENT_CLAUDE_CODE;
                case CODEX -> AiSetupLines.CLIENT_CODEX;
                default -> AiSetupLines.CLIENT_GENERIC;
            };
            JRadioButton r = UiText.radio(title);
            r.setSelected(x.client() == c);
            r.putClientProperty("aiSetup.client", c);
            r.addActionListener(e -> host.client(c));
            which.add(r);
            JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, UiTheme.SPACE_SM, 0));
            row.setOpaque(false);
            row.add(r);
            String badge = c == AiSetupState.Client.CLAUDE_CODE && s.claudeFound() ? AiSetupLines.CLAUDE_FOUND
                    : c == AiSetupState.Client.CODEX && s.codexFound() ? AiSetupLines.CODEX_FOUND : null;
            if (badge != null) {
                JComponent b = AssistantPanel.chip(badge, false);
                b.setToolTipText(UiText.tooltip(AiSetupLines.FOUND_TOOLTIP));
                row.add(b);
            }
            content.add(row);
            if (x.client() == c) focusFirst = r;
        }
        if (x.client() == AiSetupState.Client.GENERIC) content.add(indented(AiSetupLines.GENERIC_COVERS_DESKTOP));
        content.add(Box.createVerticalStrut(UiTheme.SPACE_MD));

        // 2. Let your client reach this window.
        content.add(bold(AiSetupLines.PART_REACH));
        JPanel switchRow = new JPanel(new BorderLayout(UiTheme.SPACE_SM, 0));
        switchRow.setOpaque(false);
        javax.swing.JCheckBox access = UiText.checkBox(AiSetupLines.ACCESS_SWITCH);
        boolean on = x.access() != AiSetupState.Access.OFF && x.access() != AiSetupState.Access.CONFIRMING;
        access.setSelected(on);
        access.getAccessibleContext().setAccessibleName(AiSetupLines.ACCESS_SWITCH);
        access.getAccessibleContext().setAccessibleDescription(AiSetupLines.ACCESS_SETTING_NAME + ". "
                + AiSetupLines.ACCESS_LINE);
        access.putClientProperty("aiSetup.access", Boolean.TRUE);
        access.addActionListener(e -> {
            boolean want = access.isSelected();
            access.setSelected(on);                   // the switch follows the setting; the press is only an ask
            host.access(want);
        });
        switchRow.add(access, BorderLayout.WEST);
        content.add(switchRow);
        content.add(indentedMuted(AiSetupLines.ACCESS_SETTING_NAME));
        content.add(indented(AiSetupLines.ACCESS_LINE));
        // the bridge check, here with the switch it needs (usability fix, 2026-10-18: it was behind More options…,
        // below the fold). The press is an ask: aiSetup asks for access first when it is off, then runs the check
        JPanel bridgeRow = row();
        JButton checkBridge = enterPresses(UiText.button(AiSetupLines.CHECK_CONNECTION));
        checkBridge.setToolTipText(UiText.tooltip(AiSetupLines.CHECK_CONNECTION_TIP));
        checkBridge.getAccessibleContext().setAccessibleDescription(AiSetupLines.CHECK_CONNECTION_TIP);
        checkBridge.putClientProperty("aiSetup.bridge", Boolean.TRUE);
        checkBridge.setEnabled(x.bridgeMayCheck() && s.confirming() == AiSetupState.Confirming.NONE);
        checkBridge.addActionListener(e -> host.checkAgain(AiSetupState.CheckKind.BRIDGE));
        bridgeRow.add(checkBridge);
        content.add(bridgeRow);
        String bridgeWords = AiSetupLines.bridgeText(x.bridge());
        if (!bridgeWords.isEmpty()) content.add(statusRow("Connection", bridgeWords));
        else if (!on && x.launcher()) content.add(indentedMuted(AiSetupLines.CHECK_CONNECTION_ASKS));
        // M77-4: the picture of the path the switch opens, with the switch and its words (the picture needs 340 px, so it
        // sits under the switch's lines rather than squeezed to their right at 520–640 px). It renders the state only.
        accessDiagram.setSnapshot(s, s.choice() == Choice.BOTH ? FlowDiagram.Option.BOTH : FlowDiagram.Option.CLIENT);
        accessDiagramSlot.setOpaque(false);
        content.add(accessDiagramSlot);
        String accessState = switch (x.access()) {
            case ON, OTHER_INSTANCE -> AiSetupLines.ACCESS_ON;
            case CONFIRMING -> AiSetupLines.ACCESS_CONFIRMING;
            case CLOSED_FOR_SESSION -> AiSetupLines.ACCESS_CLOSED;
            case OFF -> "";
        };
        if (!accessState.isEmpty()) content.add(indented(accessState));
        if (!on && focusFirst == null) focusFirst = access;
        content.add(Box.createVerticalStrut(UiTheme.SPACE_MD));

        // 3. Register the analyser with your client.
        content.add(bold(AiSetupLines.PART_REGISTER));
        if (x.registration() == AiSetupState.Registration.NO_LAUNCHER) {
            content.add(indented(AiSetupLines.NO_LAUNCHER));
            JPanel actions = row();
            JButton how = enterPresses(UiText.button(AiSetupLines.HOW_TO_INSTALL));
            how.addActionListener(e -> host.installHelp());
            actions.add(how);
            content.add(actions);
        } else if (x.client() == AiSetupState.Client.GENERIC) {
            content.add(indented(AiSetupLines.GENERIC_INTRO));
            content.add(commandBox(host.commandText(x.client()), true));
            JPanel actions = row();
            JButton copy = enterPresses(UiText.button(AiSetupLines.COPY_CONFIGURATION));
            copy.addActionListener(e -> host.copy(telamin.fluxtion.audit.analyser.analyser.session.SessionEvents
                    .CopyWhat.GENERIC_CONFIG));
            JButton save = enterPresses(UiText.button(AiSetupLines.SAVE_SNIPPET));
            save.addActionListener(e -> host.saveSnippet());
            actions.add(copy);
            actions.add(save);
            content.add(actions);
            String reg = AiSetupLines.registrationText(x.registration());
            if (!reg.isEmpty()) content.add(indented(reg));
        } else {
            content.add(indented(AiSetupLines.REGISTER_INTRO));
            String command = host.commandText(x.client());
            content.add(commandBox(commandExpanded ? command : collapsed(command), commandExpanded));
            JPanel shown = row();
            JButton full = enterPresses(UiText.button(commandExpanded ? AiSetupLines.SHOW_LESS
                    : AiSetupLines.SHOW_FULL_COMMAND));
            full.addActionListener(e -> {
                commandExpanded = !commandExpanded;
                renderedKey = "";
                render(state, lastModel, lastProvider, lastInAppModel, lastKeyStored);
            });
            JButton copyCmd = enterPresses(UiText.button(AiSetupLines.COPY));
            copyCmd.addActionListener(e -> host.copy(telamin.fluxtion.audit.analyser.analyser.session.SessionEvents
                    .CopyWhat.REGISTER_COMMAND));
            shown.add(full);
            shown.add(copyCmd);
            content.add(shown);
            // OD-77.10: Register for me… first, behind its confirmation; then I'll run it myself; then the check
            JPanel actions = row();
            boolean present = x.registration() == AiSetupState.Registration.PRESENT;
            JButton register = enterPresses(UiText.button(present ? AiSetupLines.REPLACE_REGISTRATION
                    : AiSetupLines.REGISTER_FOR_ME));
            boolean found = x.client() == AiSetupState.Client.CODEX ? s.codexFound() : s.claudeFound();
            register.setEnabled(found && x.registration() != AiSetupState.Registration.RUNNING);
            register.putClientProperty("aiSetup.register", Boolean.TRUE);
            register.addActionListener(e -> host.register());
            JButton myself = enterPresses(UiText.button(AiSetupLines.RUN_IT_MYSELF));
            myself.addActionListener(e -> host.copy(telamin.fluxtion.audit.analyser.analyser.session.SessionEvents
                    .CopyWhat.REGISTER_COMMAND));
            JButton check = enterPresses(UiText.button(AiSetupLines.CHECK_REGISTRATION));
            check.setEnabled(found && x.registration() != AiSetupState.Registration.RUNNING);
            check.addActionListener(e -> host.checkAgain(AiSetupState.CheckKind.REGISTRATION));
            actions.add(register);
            actions.add(myself);
            actions.add(check);
            content.add(actions);
            String reg = AiSetupLines.registrationText(x.registration());
            if (!reg.isEmpty()) {
                JComponent r = statusRow("Registration", reg);
                content.add(r);
            }
            if (on && focusFirst == null && !x.registered()) focusFirst = register.isEnabled() ? register : myself;
        }
        content.add(Box.createVerticalStrut(UiTheme.SPACE_MD));

        // 4. Can your client see the analyser?
        content.add(bold(AiSetupLines.PART_LIVE));
        AiSetupState.Live live = x.live();
        switch (live.verdict()) {
            case IDLE -> content.add(indentedMuted(x.registration() == AiSetupState.Registration.ABSENT
                    ? AiSetupLines.LIVE_NOT_REGISTERED : AiSetupLines.LIVE_NOT_YET));
            case WAITING, WAITING_LONG -> {
                content.add(indented(AiSetupLines.LIVE_ASK));
                content.add(commandBox(AiSetupLines.LIVE_QUESTION, true));
                JPanel actions = row();
                JButton copyQ = enterPresses(UiText.button(AiSetupLines.COPY_THE_QUESTION));
                copyQ.addActionListener(e -> host.copy(telamin.fluxtion.audit.analyser.analyser.session.SessionEvents
                        .CopyWhat.LIVE_QUESTION));
                actions.add(copyQ);
                content.add(actions);
                content.add(statusRow("Live check", AiSetupLines.LIVE_WAITING));
                if (live.verdict() == AiSetupState.LiveVerdict.WAITING_LONG) {
                    content.add(indentedMuted("• " + AiSetupLines.LIVE_TIP_RESTART));
                    content.add(indentedMuted("• " + AiSetupLines.LIVE_TIP_ACCESS));
                }
            }
            case SEEN -> {
                String words = AiSetupLines.liveSeen(hhmm(live.at()));
                String said = AiSetupLines.selfReported(host.clientLabel(live.labelRef()));
                if (!said.isEmpty()) words += " " + said;
                if (live.fullSessionSeen()) words += " " + AiSetupLines.LIVE_FULL_SESSION;
                content.add(statusRow("Live check", words));
            }
            case OTHER_INSTANCE, ACCESS_OFF -> {
                content.add(statusRow("Live check", live.verdict() == AiSetupState.LiveVerdict.OTHER_INSTANCE
                        ? AiSetupLines.LIVE_OTHER_INSTANCE : AiSetupLines.LIVE_ACCESS_OFF));
                JPanel actions = row();
                JButton again = enterPresses(UiText.button(AiSetupLines.CHECK_AGAIN));
                again.getAccessibleContext().setAccessibleName("Check again: live check");
                again.addActionListener(e -> host.checkAgain(AiSetupState.CheckKind.LIVE));
                actions.add(again);
                content.add(actions);
            }
        }
        content.add(Box.createVerticalStrut(UiTheme.SPACE_SM));
        JButton more = link(AiSetupLines.MORE_OPTIONS);
        enterPresses(more);
        more.addActionListener(e -> host.moreOptions());
        content.add(more);
    }

    /** §6.3: the command collapsed to its first words ("claude mcp add --scope user fluxtion-analyser …"). */
    static String collapsed(String command) {
        if (command == null || command.isBlank()) return "";
        int at = command.indexOf(" -- ");
        return at < 0 ? command : command.substring(0, at) + " …";
    }

    /** "14:02": when the live check saw a client, in this machine's time zone. */
    static String hhmm(long epochMillis) {
        if (epochMillis <= 0) return null;
        return java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(
                java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()));
    }

    /** A command or configuration: selectable, monospaced, wrapping inside its own box (§15: never widens the window). */
    private static JComponent commandBox(String text, boolean wrap) {
        javax.swing.JTextArea a = new javax.swing.JTextArea(UiText.inert(text == null ? "" : text));
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(!wrap);
        java.awt.Font base = javax.swing.UIManager.getFont("TextField.font");
        if (base == null) base = a.getFont();
        a.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, base.getSize()));
        a.setBorder(BorderFactory.createCompoundBorder(javax.swing.UIManager.getBorder("TextField.border"),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        a.getAccessibleContext().setAccessibleName("The exact command");
        JPanel holder = new JPanel(new BorderLayout());
        holder.setOpaque(false);
        holder.setBorder(BorderFactory.createEmptyBorder(UiTheme.SPACE_XS, 28, UiTheme.SPACE_XS, 0));
        holder.add(a, BorderLayout.CENTER);
        return holder;
    }

    /** §6 (review R1-29): Enter on a focused button presses THAT button — Enter on Register for me… never Finishes. */
    private static JButton enterPresses(JButton b) {
        b.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "press-focused");
        b.getActionMap().put("press-focused", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (b.isEnabled()) b.doClick();
            }
        });
        return b;
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
        back.setVisible(!s.buildOnboarding() && (s.step() == Step.INSIDE || s.step() == Step.OTHER || s.step() == Step.CLIENT));
        cancel.setVisible(s.step() != Step.SUMMARY);
        String label = s.buildOnboarding() ? AiSetupLines.ENABLE_BUILD : AiSetupLines.primaryLabel(s);
        primary.setText(label);
        primary.getAccessibleContext().setAccessibleName(label);
        boolean enabled = switch (s.step()) {
            case CHOICE -> s.choice() != Choice.NONE;
            case INSIDE -> s.insideMayProceed() && !s.applying() && s.confirming() == AiSetupState.Confirming.NONE;
            case OTHER, SUMMARY -> !s.applying();
            case CLIENT -> s.clientMayFinish();
        };
        primary.setEnabled(enabled);
        // the Summary's one suggested action is the default; Done closes
        JPanel right = (JPanel) primary.getParent();
        if (suggestedButton != null) right.remove(suggestedButton);
        right.remove(primary);
        right.remove(cancel);
        if (s.buildOnboarding()) {
            right.add(primary);
            right.add(cancel); // Consent: Cancel is the default and the rightmost action.
        } else {
            right.add(cancel);
            right.add(primary);
        }
        suggestedButton = null;
        String suggested = switch (s.suggested()) {
            case BUILD -> AiSetupLines.DESCRIBE_APPLICATION;
            case OPEN_SAMPLE -> AiSetupLines.SUGGEST_SAMPLE;
            case TOUR -> AiSetupLines.SUGGEST_TOUR;
            case COPY_QUESTION -> AiSetupLines.SUGGEST_COPY_QUESTION;
            default -> null;
        };
        if (suggested != null) {
            suggestedButton = UiText.button(suggested);
            suggestedButton.addActionListener(e -> host.suggested(s.suggested()));
            right.add(suggestedButton, 0);
            getRootPane().setDefaultButton(suggestedButton);
            focusFirst = suggestedButton;
        } else {
            getRootPane().setDefaultButton(s.buildOnboarding() ? cancel : primary);
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

    /** M77-3: the slot beside 2b's access switch (empty until M77-4). */
    JPanel accessDiagramSlot() {
        return accessDiagramSlot;
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
            case CLIENT -> s.choice() == Choice.BOTH ? AiSetupLines.CLIENT_STEP_BOTH
                    : "Step 2 · " + AiSetupLines.CLIENT_SCREEN_TITLE;
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

    /** A 2b status line (its glyph is in its words): one focusable, wrapping line whose accessible name is the sentence. */
    private static JComponent statusRow(String check, String words) {
        // the glyph (✓ ✗ ⏳ ?) in its own label, as Screen 2a's rows: an emoji glyph inside wrapping text is measured
        // with the wrong advance and paints over the first letter of the word after it
        String glyph = "";
        String text = words;
        for (String g : List.of("⏳ ", "✓ ", "✗ ", "? ")) {
            if (words.startsWith(g)) {
                glyph = g.trim();
                text = words.substring(g.length());
            }
        }
        JPanel row = new JPanel(new BorderLayout(UiTheme.SPACE_XS, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(0, 28, 0, 0));
        if (!glyph.isEmpty()) {
            JLabel g = UiText.label(glyph);
            g.setVerticalAlignment(JLabel.TOP);
            row.add(g, BorderLayout.WEST);
        }
        JPanel holder = new JPanel(new BorderLayout());   // the line measures itself at ITS width, not the row's
        holder.setOpaque(false);
        holder.add(new WrapLine(text), BorderLayout.CENTER);
        row.add(holder, BorderLayout.CENTER);
        row.setFocusable(true);
        row.getAccessibleContext().setAccessibleName(AiSetupLines.accessible(check, words));
        row.putClientProperty("aiSetup.check", check);
        return row;
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

    private static WrapLine indentedMuted(String s) {
        WrapLine w = indented(s);
        w.setForeground(UiTheme.mutedForeground());
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
