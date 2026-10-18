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
import java.awt.Rectangle;
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

        /** M77-2: Set up AI… — the person asked for the wizard. */
        default void openSetUpAi() {
        }
    }

    /**
     * Review M74 usability #8: ONE control for how the assistant works — it names the profile in force and opens the four
     * cards. The mode selector it replaces chose the same thing a second way (the profile includes the mode), and its
     * fixed "your plan" was wrong on API-key billing; the billing chip says which applies.
     */
    static String assistantControlText(AssistantProfile p) {
        return "Assistant: " + (p == null ? AssistantProfile.NONE : p).title() + " ▾";
    }

    /** The control's accessible name: it STARTS with its visible label (WCAG 2.5.3) and says what pressing it does. */
    static String assistantControlName(AssistantProfile p) {
        return "Assistant: " + (p == null ? AssistantProfile.NONE : p).title() + " — choose how the assistant works";
    }

    /** What the in-app mode cannot do, said where Claude Code's limits are said. */
    static final String IN_APP_LIMITS = "In-app cannot read your project folder, set up a project or use your own "
            + "tools — Claude Code can. "
            + telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.IN_APP_SENDS;
    static final String IN_APP_NO_KEY = "No API key is configured, so nothing is sent from here — choose how the assistant "
            + "works (Assistant ▾ above), Copy prompt, or Connect a CLI assistant.";
    static final String OFF_LIMITS = "The assistant is off on this machine: no question is sent anywhere. Everything else "
            + "works — choose a profile (Assistant ▾ above) to turn an assistant on.";

    private final AssistantSurface surface = new AssistantSurface();
    private final AssistantTranscript transcript;
    private Intents intents;
    private LocalAgentPanel localAgent;

    private final JButton profileButton = UiText.button(assistantControlText(AssistantProfile.NONE));
    /** M77-2 (spec-ai-setup-wizard.md §6.6): the empty state's one button, beside the Assistant control. */
    private final JButton setUpAi = UiText.button(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.SET_UP_LINK);
    /** M77-2: nothing is set up — the chips say so instead of "In-app assistant · no API key" (robot #8). */
    private boolean nothingSetUp;

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
    private final JLabel noProviderLine = UiText.label("No provider is configured — nothing is sent from here.");
    /** The composer, the no-provider bar and the demonstration bar: hidden while the profile cards are open. */
    private final JPanel south = new JPanel(new BorderLayout(4, 4));
    /** Owner, 2026-10-18: the cards are open — a focused view: no composer, no header lines but the one status line. */
    private boolean profilesOpen;
    /** The node's one status line when no assistant can answer from here ({@link ProfileState#status()}). */
    private String profileStatus = "";
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
    /**
     * Robot walkthrough #2 (2026-10-18): what the mode cannot do, the session's folder and its notes sit behind one ⓘ
     * disclosure, closed by default — the header keeps one status line. Presentation only; kept for this run.
     */
    private final JButton aboutToggle = UiText.button("ⓘ");
    static final String ABOUT = "About this assistant";
    private boolean aboutOpen;
    private boolean demoShowing;
    private AssistantProfile profile = AssistantProfile.NONE;

    /** What was last rendered, so an unchanged snapshot repaints nothing and never moves the scroll. */
    private AssistantState rendered;
    private List<String> renderedDemo;
    private boolean docked = true;
    private boolean providerConfigured;
    private Supplier<Boolean> providerConfiguredSupplier = () -> false;

    public AssistantPanel(AssistantTranscript transcript) {
        super(new FloorLayout());
        this.transcript = transcript;
        setBorder(UiTheme.pad());
        getAccessibleContext().setAccessibleName("Analyser assistant");

        // ---- the header: the mode selector, the chips, the panel's own actions -------------------------------------
        profileButton.getAccessibleContext().setAccessibleName(assistantControlName(AssistantProfile.NONE));
        profileButton.setToolTipText(UiText.tooltip("How the assistant works: Safe analysis, Full Claude Code, In-app "
                + "assistant or Off"));
        profileButton.addActionListener(e -> { if (intents != null) intents.openProfiles(); });
        mode.setFont(mode.getFont().deriveFont(Font.BOLD));
        mode.getAccessibleContext().setAccessibleName("Assistant mode, in words");
        route.getAccessibleContext().setAccessibleName("Assistant provider");
        basis.getAccessibleContext().setAccessibleName("Workspace this conversation is about");
        status.getAccessibleContext().setAccessibleName("Assistant status");
        limits.getAccessibleContext().setAccessibleName("What this mode cannot do");
        limits.setForeground(UiTheme.mutedForeground());

        JPanel top = new WidthTrackingPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        // robot walkthrough #2 (2026-10-18): one wrapping row, so New chat and Pop out wrap below the Assistant control in
        // a narrow column instead of being clipped off its right edge
        JPanel bar = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));
        bar.add(profileButton);
        setUpAi.getAccessibleContext().setAccessibleName("Set up AI");
        setUpAi.setToolTipText(UiText.tooltip("Choose how AI works with "
                + telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.PRODUCT + ": no AI, your own client, Claude "
                + "inside, or both"));
        setUpAi.addActionListener(e -> { if (intents != null) intents.openSetUpAi(); });
        setUpAi.setVisible(false);
        bar.add(setUpAi);
        bar.add(mode);
        bar.add(surface.newChat);
        bar.add(showAnalyser);
        bar.add(hostButton);
        bar.add(aboutToggle);
        aboutToggle.setToolTipText(UiText.tooltip("What this assistant can and cannot do, where your questions go, and the "
                + "session's folder"));
        aboutToggle.getAccessibleContext().setAccessibleName(ABOUT);
        aboutToggle.addActionListener(e -> {
            aboutOpen = !aboutOpen;
            renderAbout();
        });
        for (JComponent c : new JComponent[]{surface.chips, details, limits}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            top.add(c);
        }
        // the header scrolls inside its share of the panel; the transcript and the composer keep a floor beneath it
        javax.swing.JScrollPane topScroll = new javax.swing.JScrollPane(top,
                javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        topScroll.setBorder(BorderFactory.createEmptyBorder());
        topScroll.getVerticalScrollBar().setUnitIncrement(16);
        topScroll.setViewportBorder(null);
        top.setOpaque(false);
        // the Assistant control, New chat and Pop out stay in sight above the scrolling part
        add(new HeaderPanel(bar, topScroll), BorderLayout.NORTH);

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
        surface.bindingChip.setAlignmentX(LEFT_ALIGNMENT);
        above.add(surface.bindingChip);              // final review #12: which log the drafted questions are about
        composer.add(above, BorderLayout.NORTH);
        composer.add(surface.draftScroll, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new WrapLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(surface.start);
        buttons.add(copyPrompt);
        buttons.add(surface.stop);
        buttons.add(surface.send);
        composer.add(buttons, BorderLayout.SOUTH);
        noProvider.add(noProviderLine);
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
            @Override public void openSetUpAi() { if (intents != null) intents.openSetUpAi(); }
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
        panel.setAboutOpen(aboutOpen);
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
        boolean changed = m != shownMode || demoShowing != this.demoShowing;
        shownMode = m;
        this.demoShowing = demoShowing;
        boolean claude = claudeCodeShown();
        // robot walkthrough #3: the in-app assistant's provider buttons are not Claude Code's
        if (claude) noProvider.setVisible(false);
        // owner, 2026-10-18: ↑ recalls the questions of the assistant the panel shows
        surface.historyMode(claude ? AssistantMode.LOCAL_AGENT : AssistantMode.IN_APP);
        detailsLayout.show(details, (claude ? AssistantMode.LOCAL_AGENT : AssistantMode.IN_APP).name());
        // robot walkthrough #3: Start is not shown while Claude Code runs (the local agent's last render says)
        surface.start.setVisible(claude && (localAgent == null || localAgent.lastState() == null
                || !localAgent.lastState().running()));
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

    JButton profileButton() {
        return profileButton;
    }

    /**
     * For the frame tests: the mode as the profile's Apply chooses it (review M74 usability #8 — there is no second
     * control for it), reported through the same intent.
     */
    void chooseModeForTest(AssistantMode mode) {
        if (intents != null) intents.chooseMode(mode);
    }

    WrapLine limitsLine() {
        return limits;
    }

    /** M77-2: Set up AI, as aiSetup decided it — the chips' "No assistant set up" while nothing is. */
    public void renderSetup(telamin.fluxtion.audit.analyser.analyser.session.AiSetupState s) {
        boolean next = s != null && s.wizardEntry() && s.nothingSetUp();
        // §6.6: the panel's empty state is one line and one button; once something is set up, the door is Assistant ▾
        // ▸ Set up AI… (the header keeps its row: the transcript's floor at 1200×800, P-3)
        setUpAi.setVisible(next);
        if (next == nothingSetUp) return;
        nothingSetUp = next;
        rendered = null;                          // the chips follow on the next render
    }

    JButton setUpAiButton() {
        return setUpAi;
    }

    /** M74 UX: the profile in force and its overlay, as assistantProfile decided them. */
    public void renderProfiles(ProfileState s) {
        ProfileState st = s == null ? ProfileState.NONE : s;
        profile = st.profile();
        profileButton.setText(assistantControlText(profile));
        profileButton.getAccessibleContext().setAccessibleName(assistantControlName(profile));
        profiles.render(st);
        centerLayout.show(center, st.open() ? "PROFILES" : "TRANSCRIPT");
        profilesOpen = st.open();
        profileStatus = st.status();
        renderLimits();
        renderFocus();
    }

    /**
     * Owner, 2026-10-18: choosing a profile is a focused view. While the cards are open the composer and Send, the chips
     * and the mode's lines are hidden — only the Assistant control, the host buttons and the one status line stay. Rendered
     * from {@link ProfileState}; nothing is decided here.
     */
    private void renderFocus() {
        surface.chips.setVisible(!profilesOpen);
        details.setVisible(!profilesOpen);
        south.setVisible(!profilesOpen);
        revalidate();
        repaint();
    }

    /** For the frame tests: the composer's row (Send, Stop, Copy prompt) and its parent. */
    JPanel composerRow() {
        return composer;
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
        surface.accepted(AssistantMode.IN_APP);
    }

    /**
     * M74 P2 (OD-74.14): place a composed draft — APPENDED after whatever the person has written, never over it — with
     * exactly the appended range highlighted, the composer focused and lit with {@code hint}. Nothing is sent.
     */
    public int appendDraft(long draftRef, String text, String hint) {
        return appendDraft(draftRef, text, hint, 0);
    }

    /** Final review #12: with the log generation the node bound the draft to (0: none), for the binding chip. */
    public int appendDraft(long draftRef, String text, String hint, long logGeneration) {
        return surface.appendDraft(AssistantMode.IN_APP, draftRef, text, hint, logGeneration);
    }

    /** Final review #12: the snapshot's open log, rendered on the composer's binding chip. */
    public void renderOpenLog(boolean logOpen, long generation, long openedAtMillis) {
        surface.renderOpenLog(logOpen, generation, openedAtMillis);
    }

    JLabel bindingChip() {
        return surface.bindingChip;
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

        // review M74 usability §6: a live session is named by the Assistant control; only a demonstration says its mode
        mode.setText(inDemo ? demo.mode() : " ");
        mode.setVisible(inDemo);
        mode.getAccessibleContext().setAccessibleDescription(mode.getText());
        // a demonstration has no provider: nothing is sent from it; and where the node's one status line says no assistant
        // can answer here, the route does not say it a second time (owner, 2026-10-18)
        route.setVisible(!inDemo && profileStatus.isBlank());
        basis.setText(inDemo ? demo.walkTitle() : (state.basis().isBlank() ? " " : "About: " + state.basis()));
        status.setText(inDemo ? demo.status() : phaseLine(state));
        composer.setVisible(!inDemo);
        demoBar.setVisible(inDemo);
        profileButton.setEnabled(!inDemo);
        setUpAi.setEnabled(!inDemo);            // §5.1: never during the scripted demo
        boolean claude = claudeCodeShown();
        noProvider.setVisible(!inDemo && !claude && !configured && profile != AssistantProfile.OFF);
        noProviderLine.setVisible(profileStatus.isBlank());   // its buttons stay; the one status line says it (2026-10-18)
        renderFocus();
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
        } else if (!profileStatus.isBlank() && (profilesOpen || !claudeCodeShown())) {
            words = profileStatus;            // owner, 2026-10-18: the ONE line, in the node's words
        } else if (profilesOpen) {
            words = " ";                      // a focused view: the cards say what each profile does
        } else if (claudeCodeShown()) {
            words = localAgent.limitsNow();   // review B2: says when external access is open to a full session's tools
        } else {
            words = Boolean.TRUE.equals(providerConfiguredSupplier.get()) ? IN_APP_LIMITS : IN_APP_NO_KEY;
        }
        limits.setText(words == null ? " " : words);
        boolean statusLine = profile == AssistantProfile.OFF || !profileStatus.isBlank() && (profilesOpen || !claudeCodeShown());
        limits.setVisible(words != null && !words.isBlank() && (statusLine || aboutOpen));
    }

    /** Robot walkthrough #2: open or close the ⓘ disclosure — this panel's limits line and Claude Code's notes. */
    private void renderAbout() {
        aboutToggle.setText(aboutOpen ? "ⓘ ▾" : "ⓘ");
        aboutToggle.getAccessibleContext().setAccessibleDescription(aboutOpen ? "expanded" : "collapsed");
        if (localAgent != null) localAgent.setAboutOpen(aboutOpen);
        renderLimits();
        revalidate();
        repaint();
    }

    boolean aboutOpen() {
        return aboutOpen;
    }

    JButton aboutToggle() {
        return aboutToggle;
    }

    /**
     * Robot walkthrough #2: the header takes what is left after the transcript's and the composer's floors, and scrolls;
     * the transcript is never squeezed below {@link #TRANSCRIPT_FLOOR} by prose above it (at 1200×800 it was given a
     * negative height and Start and Send fell off the panel).
     */
    static final int TRANSCRIPT_FLOOR = 120;

    /** The header inside its scroll pane: as wide as the panel, so its rows wrap instead of running off the right edge. */
    static final class WidthTrackingPanel extends JPanel implements javax.swing.Scrollable {
        @Override public java.awt.Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(16, r.height - 16); }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /** The fixed button row over the scrolling header; its preferred height is both at the width it is laid out at. */
    static final class HeaderPanel extends JPanel {
        private final JComponent bar;
        private final javax.swing.JScrollPane scroll;

        HeaderPanel(JComponent bar, javax.swing.JScrollPane scroll) {
            super(new BorderLayout(0, 2));
            this.bar = bar;
            this.scroll = scroll;
            add(bar, BorderLayout.NORTH);
            add(scroll, BorderLayout.CENTER);
        }

        /** The height wanted at width {@code w}: the row's wrapped height and the whole scrolling header's. */
        int wanted(int w) {
            bar.setSize(w, Math.max(1, bar.getHeight()));
            java.awt.Component v = scroll.getViewport().getView();
            v.setSize(w, Math.max(1, v.getHeight()));
            v.doLayout();
            return bar.getPreferredSize().height + 2 + v.getPreferredSize().height;
        }

        /** The least it takes: the button row, which never scrolls away. */
        int least(int w) {
            bar.setSize(w, Math.max(1, bar.getHeight()));
            return bar.getPreferredSize().height + 2;
        }
    }

    static final class FloorLayout extends BorderLayout {
        FloorLayout() {
            super(4, 4);
        }

        @Override
        public void layoutContainer(java.awt.Container parent) {
            synchronized (parent.getTreeLock()) {
                java.awt.Insets in = parent.getInsets();
                int x = in.left;
                int w = parent.getWidth() - in.left - in.right;
                int h = parent.getHeight() - in.top - in.bottom;
                java.awt.Component north = getLayoutComponent(NORTH);
                java.awt.Component center = getLayoutComponent(CENTER);
                java.awt.Component south = getLayoutComponent(SOUTH);
                int gap = getVgap();
                int southH = south != null && south.isVisible() ? preferredHeight(south, w) : 0;
                southH = Math.min(southH, Math.max(0, h - TRANSCRIPT_FLOOR - 2 * gap));
                int northWanted = north == null || !north.isVisible() ? 0
                        : north instanceof HeaderPanel hp ? hp.wanted(w) : preferredHeight(north, w);
                int northLeast = north instanceof HeaderPanel hp && north.isVisible() ? hp.least(w) : 0;
                int northH = Math.max(northLeast, Math.min(northWanted, h - southH - TRANSCRIPT_FLOOR - 2 * gap));
                int centerH = Math.max(0, h - northH - southH - 2 * gap);
                int y = in.top;
                if (north != null) north.setBounds(x, y, w, northH);
                y += northH + gap;
                if (center != null) center.setBounds(x, y, w, centerH);
                y += centerH + gap;
                if (south != null) south.setBounds(x, y, w, southH);
            }
        }

        /** The preferred height at width {@code w}: wrapping rows and lines report it only once they know their width. */
        private static int preferredHeight(java.awt.Component c, int w) {
            if (c instanceof javax.swing.JScrollPane sp && sp.getViewport().getView() != null) {
                java.awt.Component v = sp.getViewport().getView();
                v.setSize(w, Math.max(1, v.getHeight()));
                v.doLayout();
                return v.getPreferredSize().height;
            }
            c.setSize(w, Math.max(1, c.getHeight()));
            return c.getPreferredSize().height;
        }
    }

    /** The in-app header's chips: who answers, the provider and model, the key, and the action budget. */
    private void renderChips(AssistantState state, boolean configured, boolean inDemo) {
        JPanel chips = surface.chips;
        chips.removeAll();
        if (inDemo) {
            chips.add(chip("Demonstration", true));
            chips.add(chip("nothing is sent", false));
        } else if (nothingSetUp) {
            chips.add(chip(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.CHIP_NOTHING_SET_UP, true));
            if (state.busy()) chips.add(chip("answering", false));
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
