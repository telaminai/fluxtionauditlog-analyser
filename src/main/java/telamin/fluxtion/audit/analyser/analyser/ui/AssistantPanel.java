package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantState;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The ONE assistant panel (spec-onboard-assistant-journeys.md §3, OA-1/OA-2; M74 UX, owner 2026-10-17). The in-app
 * assistant (an API key) and Claude Code (the person's own CLI) are two MODES of this panel, not two panels: one
 * transcript ({@link TranscriptView}, the safe Markdown of P2a), one composer, one header of chips with the mode as a
 * single selector, the same tool-line marks, and one place that says what the mode in force cannot do.
 *
 * <p>It RENDERS the session's {@link AssistantState} (in-app), {@code LocalAgentState} (through Claude Code's part,
 * {@link LocalAgentPanel}) and {@link ProfileState} (the profile overlay), and the words those states name in their
 * stores; it REPORTS what the person did — Send, Stop, New chat, the mode, Pop out/Dock, the profile's Apply — as
 * intents. Whether any of that may happen is the session's ({@code assistantLoop}, {@code localAgentSession},
 * {@code assistantProfile}); each mode keeps its own rules (in-app actions and the round limit; Claude Code's posture,
 * permissions and approvals).
 *
 * <p>There is exactly one of these, whichever host shows it: the side tab or the assistant window reparent this same
 * component, so the draft, the transcript, the selection and the scroll position move with it.
 *
 * <p><b>Live and demonstration.</b> The header states the mode in words (§3): <i>Live assistant</i>, or — while a walk
 * with dialogue shows — <i>Simulated conversation</i>, <i>Recorded conversation</i> or <i>Edited recorded
 * conversation</i>. In a demonstration the composer is hidden; there is no way to send from it.
 */
public final class AssistantPanel extends JPanel {

    /** What the person did, reported to the frame, which turns each into a session fact. */
    public interface Intents {
        void send(String draft);

        /**
         * M74 P2: Send with every composed draft the composer still holds ({@code draftBindings}, empty when none) — the
         * node compares each binding with the open log, and a composed draft never carries the live selection's text.
         */
        default void sendBound(String draft, java.util.List<Long> draftBindings) {
            send(draft);
        }

        void cancel();

        void newChat();

        void copyPrompt(String draft);

        void host(boolean docked);

        void configureProvider();

        void connectCliAssistant();

        void showAnalyser();

        void askAboutEvidence();

        /** M74: the person chose which assistant the tab drives; the session holds the mode. */
        default void chooseMode(AssistantMode mode) {
        }

        /** M74 UX: the header's Profile… — the person opens the four cards. */
        default void openProfiles() {
        }

        /** M74 UX: the cards were closed without applying. */
        default void closeProfiles() {
        }

        /** M74 UX: Apply on a card; the details stay in the overlay ({@link #profileDetails()}). */
        default void applyProfile(long request, AssistantProfile profile) {
        }
    }

    /** The mode selector's two entries (owner, 2026-10-17). */
    static final String MODE_CLAUDE_CODE = "Claude Code · your plan";
    static final String MODE_IN_APP = "In-app · API key";

    /** What the in-app mode cannot do, said where Claude Code's limits are said. */
    static final String IN_APP_LIMITS = "In-app cannot read your project folder, set up a project or use your own "
            + "tools — Claude Code can.";
    static final String IN_APP_NO_KEY = "No API key is configured, so nothing is sent from here — choose how the assistant "
            + "works (Profile…), Copy prompt, or Connect a CLI assistant.";
    static final String OFF_LIMITS = "The assistant is off on this machine: no question is sent anywhere. Everything else "
            + "works — choose a profile (Profile…) to turn an assistant on.";

    private final AssistantSurface surface = new AssistantSurface();
    private final AssistantTranscript transcript;
    private Intents intents;
    private LocalAgentPanel localAgent;

    private final JComboBox<String> modeSelector = UiText.plainCells(new JComboBox<>(new String[]{MODE_CLAUDE_CODE,
            MODE_IN_APP}));
    private boolean renderingMode;
    private final JButton profileButton = UiText.button("Profile…");

    // SF-2: the route names a configured model, the basis a walk or record, the status a provider's error
    private final JLabel mode = UiText.plain(new JLabel("Live assistant"));
    private final JLabel route = UiText.plain(new JLabel());
    private final JLabel basis = UiText.plain(new JLabel());
    private final JLabel status = UiText.plain(new JLabel(" "));
    /** M74 UX: what the mode in force cannot do — the same place for both modes. */
    private final WrapLine limits = new WrapLine(" ");
    private final JButton copyPrompt = new JButton("Copy prompt");
    private final JButton hostButton = new JButton("Pop out");
    private final JButton showAnalyser = new JButton("Show analyser");
    private final JButton configure = new JButton("Configure provider");
    private final JButton connectCli = new JButton("Connect a CLI assistant");
    private final JButton askAboutEvidence = new JButton("Ask about this evidence");
    private final JPanel composer = new JPanel(new BorderLayout(4, 4));
    private final JPanel noProvider = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private final JPanel demoBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    /** OA-4 (§6.2): Show / Hide conversation is presentation only; the strip still names the journey and its mode. */
    private final JCheckBox showConversation = new JCheckBox("Show conversation");

    /** The mode lines: the in-app assistant's, or Claude Code's own part; one card shows. */
    private final CardLayout detailsLayout = new CardLayout();
    /** As tall as the card SHOWN — a CardLayout alone is as tall as its tallest card, which left a band of blank header. */
    private final JPanel details = new JPanel(detailsLayout) {
        @Override public java.awt.Dimension getPreferredSize() {
            for (java.awt.Component c : getComponents()) {
                if (c.isVisible()) {
                    java.awt.Dimension d = c.getPreferredSize();
                    java.awt.Insets in = getInsets();
                    return new java.awt.Dimension(d.width + in.left + in.right, d.height + in.top + in.bottom);
                }
            }
            return super.getPreferredSize();
        }
    };
    private final JPanel inAppLines = new JPanel();
    /** The transcript, or the profile overlay in its place. */
    private final CardLayout centerLayout = new CardLayout();
    private final JPanel center = new JPanel(centerLayout);
    private final ProfileOverlay profiles = new ProfileOverlay();

    private AssistantState lastState = AssistantState.IDLE;
    private Demo lastDemo;
    private AssistantMode shownMode = AssistantMode.IN_APP;
    private boolean demoShowing;
    private AssistantProfile profile = AssistantProfile.NONE;

    /** What was last rendered, so an unchanged snapshot repaints nothing and never moves the scroll. */
    private AssistantState rendered;
    private List<String> renderedDemo;
    private boolean docked = true;
    private boolean providerConfigured;
    private Supplier<Boolean> providerConfiguredSupplier = () -> false;

    public AssistantPanel(AssistantTranscript transcript) {
        super(new BorderLayout(4, 4));
        this.transcript = transcript;
        setBorder(UiTheme.pad());
        getAccessibleContext().setAccessibleName("Analyser assistant");

        // ---- the header: the mode selector, the chips, the panel's own actions -------------------------------------
        modeSelector.getAccessibleContext().setAccessibleName("Assistant mode");
        modeSelector.setToolTipText(UiText.tooltip("Which assistant this panel drives: your own Claude Code on your Claude "
                + "plan, or the analyser's in-app assistant with an API key"));
        modeSelector.addActionListener(e -> {
            if (renderingMode || intents == null) return;
            intents.chooseMode(modeSelector.getSelectedIndex() == 0 ? AssistantMode.LOCAL_AGENT : AssistantMode.IN_APP);
        });
        profileButton.getAccessibleContext().setAccessibleName("Choose how the assistant works");
        profileButton.setToolTipText(UiText.tooltip("Safe analysis, Full Claude Code, In-app assistant or Off"));
        profileButton.addActionListener(e -> { if (intents != null) intents.openProfiles(); });
        mode.setFont(mode.getFont().deriveFont(Font.BOLD));
        mode.getAccessibleContext().setAccessibleName("Assistant mode, in words");
        route.getAccessibleContext().setAccessibleName("Assistant provider");
        basis.getAccessibleContext().setAccessibleName("Workspace this conversation is about");
        status.getAccessibleContext().setAccessibleName("Assistant status");
        limits.getAccessibleContext().setAccessibleName("What this mode cannot do");
        limits.setForeground(UiTheme.mutedForeground());

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel bar = new JPanel(new BorderLayout(6, 0));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        left.add(modeSelector);
        left.add(mode);
        bar.add(left, BorderLayout.WEST);
        JPanel hostActions = new JPanel(new WrapLayout(FlowLayout.RIGHT, 4, 0));
        hostActions.add(profileButton);
        hostActions.add(surface.newChat);
        hostActions.add(showAnalyser);
        hostActions.add(hostButton);
        bar.add(hostActions, BorderLayout.CENTER);
        for (JComponent c : new JComponent[]{bar, surface.chips, details, limits}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            top.add(c);
        }
        add(top, BorderLayout.NORTH);

        inAppLines.setLayout(new BoxLayout(inAppLines, BoxLayout.Y_AXIS));
        for (JComponent c : new JComponent[]{route, basis, status}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            inAppLines.add(c);
        }
        details.add(inAppLines, AssistantMode.IN_APP.name());

        // ---- the one transcript, or the profile cards in its place ---------------------------------------------------
        surface.view.setSpeaker("Assistant", "result");
        center.add(surface.viewScroll, "TRANSCRIPT");
        center.add(profiles, "PROFILES");
        add(center, BorderLayout.CENTER);

        // ---- the one composer --------------------------------------------------------------------------------------
        JPanel above = new JPanel();
        above.setLayout(new BoxLayout(above, BoxLayout.Y_AXIS));
        surface.fullBadge.setAlignmentX(LEFT_ALIGNMENT);
        surface.composerHint.setAlignmentX(LEFT_ALIGNMENT);
        surface.fullBadge.setFont(surface.fullBadge.getFont().deriveFont(Font.BOLD));
        surface.composerHint.setFont(surface.composerHint.getFont().deriveFont(Font.BOLD));
        above.add(surface.fullBadge);
        above.add(surface.composerHint);
        composer.add(above, BorderLayout.NORTH);
        composer.add(surface.draftScroll, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new WrapLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(surface.start);
        buttons.add(copyPrompt);
        buttons.add(surface.stop);
        buttons.add(surface.send);
        composer.add(buttons, BorderLayout.SOUTH);
        noProvider.add(UiText.label("No provider is configured — nothing is sent from here."));
        noProvider.add(configure);
        noProvider.add(connectCli);
        demoBar.add(askAboutEvidence);
        JButton demoCli = new JButton("Connect your CLI assistant");
        demoCli.addActionListener(e -> { if (intents != null) intents.connectCliAssistant(); });
        demoBar.add(demoCli);
        showConversation.setSelected(true);
        showConversation.getAccessibleContext().setAccessibleName("Show the demonstration's conversation");
        showConversation.addActionListener(e -> { rendered = null; render(lastState, lastDemo); });
        demoBar.add(showConversation);
        demoBar.add(new JLabel("Not live: nothing here is sent anywhere."));
        JPanel south = new JPanel(new BorderLayout(4, 4));
        south.add(noProvider, BorderLayout.NORTH);
        south.add(composer, BorderLayout.CENTER);
        south.add(demoBar, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
        demoBar.setVisible(false);
        showAnalyser.setVisible(false);
        surface.start.setVisible(false);

        // the shared buttons do what the MODE IN FORCE does with them; the session decides whether they may
        surface.onSend = this::dispatchSend;
        surface.onStop = this::dispatchStop;
        surface.onNewChat = this::dispatchNewChat;
        surface.onStart = () -> { if (localAgent != null) localAgent.onStart(); };
        copyPrompt.addActionListener(e -> { if (intents != null) intents.copyPrompt(surface.draft.getText().trim()); });
        hostButton.addActionListener(e -> { if (intents != null) intents.host(!docked); });
        showAnalyser.addActionListener(e -> { if (intents != null) intents.showAnalyser(); });
        configure.addActionListener(e -> { if (intents != null) intents.configureProvider(); });
        connectCli.addActionListener(e -> { if (intents != null) intents.connectCliAssistant(); });
        askAboutEvidence.addActionListener(e -> { if (intents != null) intents.askAboutEvidence(); });
        profiles.setHost(new ProfileOverlay.Host() {
            @Override public void apply(long request, AssistantProfile p) { if (intents != null) intents.applyProfile(request, p); }
            @Override public void close() { if (intents != null) intents.closeProfiles(); }
            @Override public void openSettings() { if (intents != null) intents.configureProvider(); }
        });
        renderMode(AssistantMode.IN_APP, false);
    }

    public void setIntents(Intents intents) {
        this.intents = intents;
    }

    /** M74 UX: the shared transcript, composer and chips, for Claude Code's part of this panel. */
    AssistantSurface surface() {
        return surface;
    }

    /** M74 UX: the profile overlay's details for the card being applied — read by the performer of the node's effect. */
    public AssistantProfiles.Details profileDetails() {
        return profiles.details();
    }

    /** M74 UX: what the cards start from when they open (the frame reads this machine's settings). */
    public void setProfileDefaults(Supplier<ProfileOverlay.Defaults> defaults) {
        profiles.setDefaults(defaults);
    }

    ProfileOverlay profileOverlay() {
        return profiles;
    }

    /** M74: Claude Code's part — its own lines here; it draws the shared transcript and composer while it is shown. */
    public void setLocalAgentPanel(LocalAgentPanel panel) {
        this.localAgent = panel;
        details.add(panel, AssistantMode.LOCAL_AGENT.name());
        panel.setActive(shownMode == AssistantMode.LOCAL_AGENT && !demoShowing);
    }

    private boolean claudeCodeShown() {
        return shownMode == AssistantMode.LOCAL_AGENT && !demoShowing && localAgent != null;
    }

    private void dispatchSend() {
        if (claudeCodeShown()) localAgent.onSend();
        else onInAppSend();
    }

    private void dispatchStop() {
        if (claudeCodeShown()) localAgent.onStop();
        else if (intents != null) intents.cancel();
    }

    private void dispatchNewChat() {
        if (claudeCodeShown()) localAgent.onNewChat();
        else if (intents != null) intents.newChat();
    }

    /** M74: render the session's mode — the selector, the mode's lines and who draws the shared parts. Deciding nothing. */
    public void renderMode(AssistantMode mode) {
        renderMode(mode, false);
    }

    /**
     * M74 P2: render the mode; while a walk with a conversation shows, the panel shows that conversation whichever mode
     * is in force (S1's demo plays the same in every mode, §5.8) — the walk's own strip, its label and its handoff.
     */
    public void renderMode(AssistantMode mode, boolean demoShowing) {
        AssistantMode m = mode == null ? AssistantMode.IN_APP : mode;
        renderingMode = true;
        try {
            modeSelector.setSelectedIndex(m == AssistantMode.LOCAL_AGENT ? 0 : 1);
        } finally {
            renderingMode = false;
        }
        boolean changed = m != shownMode || demoShowing != this.demoShowing;
        shownMode = m;
        this.demoShowing = demoShowing;
        boolean claude = claudeCodeShown();
        detailsLayout.show(details, (claude ? AssistantMode.LOCAL_AGENT : AssistantMode.IN_APP).name());
        surface.start.setVisible(claude);
        copyPrompt.setVisible(!claude);
        if (localAgent != null) localAgent.setActive(claude);
        if (changed && !claude) {
            surface.fullBadge.setVisible(false);          // the full-posture cue is Claude Code's alone
            surface.cueBorder(null, 0);
            surface.send.setText("Send");
            surface.send.setToolTipText(null);
            surface.newChat.setText("New chat");
            rendered = null;
            render(lastState, lastDemo);
        }
        renderLimits();
    }

    /** The selector, for the tests: the person's choice of mode is a selection here. */
    JComboBox<String> modeSelector() {
        return modeSelector;
    }

    JButton profileButton() {
        return profileButton;
    }

    WrapLine limitsLine() {
        return limits;
    }

    /** M74 UX: the profile in force and its overlay, as assistantProfile decided them. */
    public void renderProfiles(ProfileState s) {
        ProfileState st = s == null ? ProfileState.NONE : s;
        profile = st.profile();
        profileButton.setText("Profile: " + profile.title() + "…");
        profiles.render(st);
        centerLayout.show(center, st.open() ? "PROFILES" : "TRANSCRIPT");
        renderLimits();
    }

    /** Whether a usable provider is configured, read when rendering (it is configuration, not a session decision). */
    public void setProviderConfigured(Supplier<Boolean> configured) {
        this.providerConfiguredSupplier = configured == null ? () -> false : configured;
    }

    /** The route line: the configured provider and model, or that none is configured (§3). */
    public void setRoute(String text) {
        route.setText(text == null ? "" : text);
    }

    private void onInAppSend() {
        String text = surface.draft.getText().trim();
        if (text.isEmpty() || intents == null || !surface.send.isEnabled()) return;
        intents.sendBound(text, currentBinding());
    }

    /** The node accepted the send: the composer is cleared for the next question. Nothing else clears the draft. */
    public void draftAccepted() {
        surface.accepted();
    }

    /**
     * M74 P2 (OD-74.14): place a composed draft — APPENDED after whatever the person has written, never over it — with
     * exactly the appended range highlighted, the composer focused and lit with {@code hint}. Nothing is sent.
     */
    public int appendDraft(long draftRef, String text, String hint) {
        return surface.appendDraft(AssistantMode.IN_APP, draftRef, text, hint);
    }

    /** The composed draft's binding while any of its appended text remains. */
    java.util.List<Long> currentBinding() {
        return surface.bindings(AssistantMode.IN_APP);
    }

    /** Focus the composer (the journey handoff's fresh thread), changing nothing in it. */
    public void focusComposer() {
        surface.draft.requestFocusInWindow();
    }

    WrapLine composerHint() {
        return surface.composerHint;
    }

    public String draftText() {
        return surface.draft.getText();
    }

    /** Where the conversation is scrolled to, so a move between hosts can put it back. */
    public java.awt.Point scrollPosition() {
        return surface.viewScroll.getViewport().getViewPosition();
    }

    /** Put the conversation back where it was (after a move between hosts has laid it out again). */
    public void restoreScrollPosition(java.awt.Point p) {
        if (p == null) return;
        SwingUtilities.invokeLater(() -> surface.viewScroll.getViewport().setViewPosition(p));
    }

    /** For tests and the host: the one composer. */
    JTextArea composerArea() {
        return surface.draft;
    }

    TranscriptView conversationView() {
        return surface.view;
    }

    JButton sendButton() {
        return surface.send;
    }

    /** The one Stop: in-app it cancels the turn; under Claude Code it interrupts it. */
    JButton cancelButton() {
        return surface.stop;
    }

    JButton hostButton() {
        return hostButton;
    }

    JButton askAboutEvidenceButton() {
        return askAboutEvidence;
    }

    // ---- rendering ----------------------------------------------------------------------------------------------------

    /**
     * Render the in-app assistant from the session's decision. {@code demo} is null in live mode; otherwise the
     * demonstration's header and its already-rendered lines (OA-4). Unchanged input changes nothing on screen. While
     * Claude Code is the mode shown, the shared transcript and composer are its: only the in-app lines are kept.
     */
    public void render(AssistantState state, Demo demo) {
        lastState = state;
        lastDemo = demo;
        boolean configured = Boolean.TRUE.equals(providerConfiguredSupplier.get());
        List<String> demoLines = demo == null ? null : demo.lines();
        boolean inDemo = demo != null;
        if (inDemo != demoShowing) {
            renderMode(shownMode, inDemo);                 // a walk with dialogue starts or ends: who draws changes
            return;
        }
        if (state.equals(rendered) && java.util.Objects.equals(demoLines, renderedDemo) && configured == providerConfigured
                && docked == state.docked()) {
            return;
        }
        providerConfigured = configured;
        docked = state.docked();
        hostButton.setText(docked ? "Pop out" : "Dock");
        hostButton.getAccessibleContext().setAccessibleName(docked ? "Pop out the assistant" : "Dock the assistant");
        showAnalyser.setVisible(!docked);

        mode.setText(inDemo ? demo.mode() : "Live assistant");
        mode.getAccessibleContext().setAccessibleDescription(mode.getText());
        route.setVisible(!inDemo);                    // a demonstration has no provider: nothing is sent from it
        basis.setText(inDemo ? demo.walkTitle() : (state.basis().isBlank() ? " " : "About: " + state.basis()));
        status.setText(inDemo ? demo.status() : phaseLine(state));
        composer.setVisible(!inDemo);
        demoBar.setVisible(inDemo);
        modeSelector.setEnabled(!inDemo);
        boolean claude = claudeCodeShown();
        noProvider.setVisible(!inDemo && !claude && !configured && profile != AssistantProfile.OFF);
        renderLimits();
        if (claude) {                                     // Claude Code draws the shared parts while it is shown
            rendered = state;
            renderedDemo = demoLines;
            return;
        }
        surface.newChat.setEnabled(!inDemo);
        // with nothing chosen Send stays pressable: the first question opens the profile cards (assistantProfile)
        surface.send.setEnabled(!inDemo && (configured || profile == AssistantProfile.NONE) && !state.busy()
                && !state.frozen());
        surface.stop.setEnabled(!inDemo && state.busy());
        copyPrompt.setEnabled(!inDemo && !state.busy());
        renderChips(state, configured, inDemo);

        boolean follow = following();
        java.awt.Point at = surface.viewScroll.getViewport().getViewPosition();
        surface.view.setSpeaker(inDemo ? demo.mode() : "Assistant", "result");
        surface.view.render(inDemo ? demoItems(demo) : items(state));
        // new content scrolls only when the person was already following the bottom (§3)
        if (follow) {
            surface.view.setCaretPosition(surface.view.getDocument().getLength());
        } else {
            SwingUtilities.invokeLater(() -> surface.viewScroll.getViewport().setViewPosition(at));
        }
        rendered = state;
        renderedDemo = demoLines;
    }

    /** M74 UX: what the mode in force cannot do — one line, in the same place for both modes. */
    private void renderLimits() {
        String words;
        if (profile == AssistantProfile.OFF) {
            words = OFF_LIMITS;
        } else if (demoShowing) {
            words = " ";
        } else if (claudeCodeShown()) {
            words = LocalAgentPanel.limitsText(localAgent.lastState());
        } else {
            words = Boolean.TRUE.equals(providerConfiguredSupplier.get()) ? IN_APP_LIMITS : IN_APP_NO_KEY;
        }
        limits.setText(words == null ? " " : words);
        limits.setVisible(words != null && !words.isBlank());
    }

    /** The in-app header's chips: who answers, the provider and model, the key, and the action budget. */
    private void renderChips(AssistantState state, boolean configured, boolean inDemo) {
        JPanel chips = surface.chips;
        chips.removeAll();
        if (inDemo) {
            chips.add(chip("Demonstration", true));
            chips.add(chip("nothing is sent", false));
        } else {
            chips.add(chip("In-app assistant", true));
            String r = route.getText();
            if (r.startsWith("via ")) {
                int dash = r.indexOf(" — ");
                chips.add(chip(r.substring(4, dash > 0 ? dash : r.length()), false));
            }
            chips.add(chip(configured ? "API key" : "no API key", false));
            if (state.busy()) chips.add(chip("answering", false));
        }
        chips.revalidate();
        chips.repaint();
    }

    /** A header chip, in the same look as Claude Code's (§5.9). */
    static JLabel chip(String text, boolean strong) {
        JLabel l = UiText.label(text);                 // a model name or a folder is not the analyser's words
        l.setOpaque(true);
        l.setBackground(UiTheme.accentWash(strong ? 0.22f : 0.10f));
        l.setForeground(strong ? UiTheme.accentText() : UIManager.getColor("Label.foreground"));
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.surfaceEdge(), 1, true),
                BorderFactory.createEmptyBorder(1, 6, 1, 6)));
        Font f = UIManager.getFont("Label.font");
        if (f != null) l.setFont(f.deriveFont(strong ? Font.BOLD : Font.PLAIN, f.getSize2D() - 1f));
        return l;
    }

    /** A demonstration's projection, built by the frame from walkPlayback's frozen definition (OA-4). */
    public record Demo(String mode, String walkTitle, String status, List<String> lines) {
        public Demo {
            lines = List.copyOf(lines);
        }
    }

    /** The demonstration as transcript items: its lines as written, plain (never Markdown), or the hidden note. */
    List<TranscriptView.Item> demoItems(Demo demo) {
        List<TranscriptView.Item> out = new ArrayList<>();
        if (!showConversation.isSelected()) {
            out.add(new TranscriptView.Item("demo:hidden", TranscriptStyle.Role.NOTE,
                    "(conversation hidden — the walk's strip still steps it)", "", 0, 0, false));
            return out;
        }
        int i = 0;
        for (String line : demo.lines()) {
            out.add(new TranscriptView.Item("demo:" + i++ + ":" + line.hashCode(), TranscriptStyle.Role.PLAIN, line, "", 0, 0,
                    false));
        }
        return out;
    }

    /**
     * The in-app conversation as transcript items: the person's turns as typed, the answers as safe Markdown (their
     * fenced action blocks shown as the tool lines that follow), each action as a tool line with the same marks Claude
     * Code's carry, and the node's notes.
     */
    List<TranscriptView.Item> items(AssistantState state) {
        List<TranscriptView.Item> out = new ArrayList<>();
        int i = 0;
        for (AssistantState.Entry e : state.visibleEntries()) {
            String key = state.conversation() + ":" + i++ + ":" + e.kind() + ":" + e.id();
            switch (e.kind()) {
                case AssistantState.USER -> out.add(new TranscriptView.Item(key, TranscriptStyle.Role.YOU,
                        transcript.text(e.id()), "", 0, 0, false));
                case AssistantState.ANSWER -> out.add(new TranscriptView.Item(key, TranscriptStyle.Role.CLAUDE,
                        withoutActionBlocks(transcript.text(e.id())), e.status(), 0, 0, false));
                case AssistantState.ACTION -> out.add(new TranscriptView.Item(key + ":" + e.status(),
                        TranscriptStyle.Role.TOOL, toolLine(e, e.result() == 0 ? null : transcript.text(e.result())),
                        e.status(), 0, 0, false));
                case AssistantState.NOTE -> out.add(new TranscriptView.Item(key, TranscriptStyle.Role.NOTE,
                        "[" + e.status().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + "] " + e.detail(),
                        e.status(), 0, 0, false));
                default -> { }
            }
        }
        return out;
    }

    /**
     * An in-app action as a tool line: {@code ▸ analyser_read <its result> — ✓}, the same marks as Claude Code's lines
     * ({@code ✓}, {@code refused}, {@code denied}); the result — what the model was given — is shown on expanding it, as
     * data, at most 4,000 characters here (the whole result went to the model).
     */
    static String toolLine(AssistantState.Entry e, String result) {
        String verb = e.verb().isBlank() ? "action" : e.verb();
        String outcome = switch (e.status()) {
            case "OK" -> "✓";
            case "REFUSED" -> "refused" + (e.detail().isBlank() ? "" : ": " + e.detail());
            case "NOT_RUN" -> "denied: not run" + (e.detail().isBlank() ? "" : " (" + e.detail() + ")");
            default -> "";
        };
        String shown = result == null ? "" : result.length() > 4000 ? result.substring(0, 4000) + " … (" + result.length()
                + " characters; the whole result was given to the model)" : result;
        shown = shown.replace('\n', ' ');
        return "▸ " + telamin.fluxtion.audit.analyser.analyser.mcp.McpTools.PREFIX + verb
                + (shown.isBlank() ? "" : " " + shown) + (outcome.isEmpty() ? "" : " — " + outcome);
    }

    private boolean following() {
        JScrollBar bar = surface.viewScroll.getVerticalScrollBar();
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 4;
    }

    /** A theme change: the one transcript re-styles. */
    public void themeChanged() {
        surface.view.restyle();
        limits.setForeground(UiTheme.mutedForeground());
        if (localAgent != null) localAgent.themeChanged();
    }

    static String phaseLine(AssistantState s) {
        String phase = switch (s.phase()) {
            case "PREPARING" -> "Requesting — preparing the question";
            case "REQUESTING" -> "Requesting — waiting for the provider (round " + s.round() + ")";
            case "RUNNING_ACTION" -> "Running action " + s.actionsRun() + (s.runningVerb() == null ? "" : " (" + s.runningVerb() + ")")
                    + " — waiting for its result";
            case "COMPLETE" -> "Complete";
            case "CANCELLED" -> "Cancelled";
            case "SUPERSEDED" -> "Superseded";
            case "LIMIT_REACHED" -> "Limit reached";
            case "FAILED" -> "Failed";
            default -> "Ready";
        };
        String why = s.reason().isBlank() ? "" : " — " + s.reason();
        return phase + why;
    }

    /** An answer's fenced action blocks are shown as the action entries that follow it, not as prose. */
    static String withoutActionBlocks(String reply) {
        return reply.replaceAll("(?s)```analyser-action\\s.*?```", "[an action was requested — its result follows]").strip();
    }

    /** Unused while docked: the host shows the panel itself. Kept for the popout host's title. */
    public static final String WINDOW_TITLE = "Analyser assistant";

    /** For the frame: route intents from a Consumer-based caller (tests). */
    public static Intents intents(Consumer<String> send, Runnable cancel) {
        return new Intents() {
            @Override public void send(String d) { send.accept(d); }
            @Override public void cancel() { cancel.run(); }
            @Override public void newChat() { }
            @Override public void copyPrompt(String d) { }
            @Override public void host(boolean docked) { }
            @Override public void configureProvider() { }
            @Override public void connectCliAssistant() { }
            @Override public void showAnalyser() { }
            @Override public void askAboutEvidence() { }
        };
    }
}
