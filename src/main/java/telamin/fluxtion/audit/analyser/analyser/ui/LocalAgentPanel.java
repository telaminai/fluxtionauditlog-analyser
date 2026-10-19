package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * M74 (spec-claude-code-provider.md §5.2, §5.9): the Assistant tab while it drives Claude Code. It RENDERS the session's
 * {@link LocalAgentState} and the words that state names in the bounded store, and it REPORTS what the person did —
 * Start, Stop, Send, New chat, turn on REST — as intents. Whether any of that may happen is {@code localAgentSession}'s
 * decision, and the process belongs to the adapter: this class never starts, stops or reads one.
 *
 * <p>P2: the conversation is drawn by {@link TranscriptView} (roles, safe Markdown, chips, collapsible tool lines,
 * copy), incrementally; the header is compact chips; a draft from the Ask catalogue is APPENDED after the person's own
 * text, the appended range highlighted and the composer lit; Start shows when it is ARMED; and the full posture is cued
 * on the composer, its badge and Send (owner request, 2026-10-17) — every one of these rendered from the snapshot.
 *
 * <p>Copy prompt and Connect a CLI assistant are not here: they exist for external agents.
 */
public final class LocalAgentPanel extends JPanel {

    /** What the person did, reported to the frame, which turns each into a session fact. */
    public interface Intents {
        void start();

        void stop();

        /**
         * Send: the composer's words, and {@code draftBindings} — every composed draft the composer still holds (its
         * appended range not wholly deleted), possibly none. The node compares each binding with the open log.
         */
        void send(String draft, java.util.List<Long> draftBindings);

        void newChat();

        void openSettings();

        /** Review B2: the person chose to close external access while the session of {@code generation} runs. */
        default void closeExternalAccess(long generation) { }

        /** P2b (§5.6): the banner's Review… — the person's click opens the review; a model call never does. */
        default void reviewProposal(String proposalId) { }

        /** P2b: the banner's Not now — every pending item is declined. */
        default void dismissProposal(String proposalId) { }

        /** P2b (§5.6 step 1): <i>Set up a project with Claude…</i> — the person's chooser for the set-up folder. */
        default void setUpProject() { }

        /** Owner, 2026-10-18: the restart offer {@code offer} answered — Restart now ({@code restart}) or Later. */
        default void answerRestart(long offer, boolean restart) { }

        /** Owner, 2026-10-18: the "settings changed — restart to apply" chip was clicked. */
        default void reopenRestartOffer() { }

        /** Owner decision 2026-10-18: the Build-mode offer {@code offer} answered — Switch ({@code switchMode}) or Keep asking. */
        default void answerBuildMode(long offer, boolean switchMode) { }

        /** Robot walkthrough #1: "Ask again for Claude Code" — the composer's drafts, by binding. */
        default void askAgain(java.util.List<Long> draftBindings) { }

        default void keepWaiting(long generation, long ticket) { }

    }

    /**
     * Safe analysis's line under the header (§5.2). Review M74 usability A1/A2: it no longer repeats the limits line, and
     * it states what Safe analysis does rather than pressing toward the riskier profile on every view.
     */
    static final String DEFAULT_POSTURE_NOTE = "Safe analysis — your own skills, hooks and MCP servers are not loaded";
    /** §4.8: an external terminal is invisible to the lock. */
    static final String LOCK_WARNING = "Don't resume this conversation in a terminal while it is open here — the history "
            + "would fork.";
    /** On a Claude plan a dollar figure means nothing to the person (owner, 2026-10-17). */
    static final String PLAN_USAGE = "usage counts against your Claude plan";
    /** OD-74.22: the header line when the session works in the scratch folder for an analyser-owned project. */
    static final String SCRATCH_NOTE = "working in a scratch folder: this project's folder is not one Claude Code is "
            + "started in";

    private final LocalAgentTranscript store;
    private Intents intents;
    /** M74 UX: the one panel's shared transcript, composer and chips — this mode renders into them while it is shown. */
    private final AssistantSurface surface;
    /** Standalone (its own layout, as before the M74 UX step) or a part of the one assistant panel. */
    private final boolean standalone;
    /** This mode is the one the panel shows: only then does it draw the shared transcript, chips and composer. */
    private boolean active = true;

    // they WRAP to the tab's width: a header, a warning or a status that ends in "…" hides its most important part
    private final WrapLine header = new WrapLine(" ");
    private final JPanel chips;
    private final WrapLine posture = new WrapLine(DEFAULT_POSTURE_NOTE);
    private final WrapLine lockWarning = new WrapLine(LOCK_WARNING);

    WrapLine lockWarningLine() {
        return lockWarning;
    }
    private final WrapLine status = new WrapLine(" ");
    private final JPanel activityStall = new JPanel(new BorderLayout(6, 2));
    private final WrapLine activityStallText = new WrapLine(" ");
    private final JButton keepWaiting = UiText.button("Keep waiting");
    private final JButton stallStop = UiText.button("Stop");
    private final JTextArea detail = new JTextArea(2, 40);
    private final TranscriptView view;
    private final JScrollPane viewScroll;
    private final JTextArea draft;
    private final JScrollPane draftScroll;
    private final JLabel fullBadge;
    private final WrapLine composerHint;
    private final JButton start;
    private final JButton stop;
    private final JButton newChat;
    private final JButton send;
    private final JButton settings = new JButton("Settings ▸ Assistant");
    /** P2b (§5.6): <i>Set up a project with Claude…</i>, and the proposal banner (never a modal). */
    private final JButton setUp = UiText.button("Set up a project with Claude…");
    private final JPanel proposalBanner = new JPanel(new BorderLayout(6, 2));
    private final WrapLine proposalText = new WrapLine(" ");
    private final JButton review = UiText.button("Review…");
    private final JButton notNow = UiText.button("Not now");
    /** Review B2: "Close external access while this session runs" — offered when actionListener says so. */
    private final JButton closeExternal = UiText.button(CLOSE_EXTERNAL);
    static final String CLOSE_EXTERNAL = "Close external access while this session runs";
    private telamin.fluxtion.audit.analyser.analyser.session.ListenerState listener =
            telamin.fluxtion.audit.analyser.analyser.session.ListenerState.NONE;
    /**
     * Owner, 2026-10-18 ("update the assistant now? yes / no"): the restart offer, never a modal — rendered from
     * {@link LocalAgentState.Restart}, which {@code localAgentSession} decides; Yes and Later are reported as facts.
     */
    private final JPanel restartBanner = new JPanel(new BorderLayout(6, 2));
    private final WrapLine restartText = new WrapLine(" ");
    private final JButton restartYes = UiText.button(RESTART_YES);
    private final JButton restartLater = UiText.button(RESTART_LATER);
    /** The chip shown after Later: clicking it shows the offer again. */
    private final JButton restartChip = UiText.button(RESTART_CHIP);
    private LocalAgentState.Restart restart = LocalAgentState.Restart.NONE;
    /**
     * Owner decision 2026-10-18: Full Claude Code in {@code default} and Start building pressed — the one-line offer of
     * {@code acceptEdits}, rendered from {@link telamin.fluxtion.audit.analyser.analyser.session.ProfileState}, which
     * {@code aiSetup} decides; Switch and Keep asking are reported as facts.
     */
    private final JPanel buildModeBanner = new JPanel(new BorderLayout(6, 2));
    private final WrapLine buildModeText = new WrapLine(" ");
    private final JButton buildModeSwitch = UiText.button(
            telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.BUILD_MODE_SWITCH);
    private final JButton buildModeKeep = UiText.button(
            telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.BUILD_MODE_KEEP);
    private long buildModeOffer;
    static final String RESTART_YES = "Restart";
    static final String RESTART_LATER = "Later";
    static final String RESTART_CHIP = "settings changed — restart to apply";
    /** Robot walkthrough #1: the one action beside a refusal that the composer holds the other assistant's question. */
    private final JButton askAgain = UiText.button(ASK_AGAIN);
    static final String ASK_AGAIN = "Ask again for Claude Code";
    private final WrapLine diagnostics = new WrapLine(" ");
    private final WrapLine endpoint = new WrapLine(" ");
    /**
     * External utility review (owner-approved 2026-10-17): the operator lines — the CLI version, the endpoint, the PATH,
     * the diagnostics and the last error — sit behind <i>Details</i>, collapsed by default. It opens by itself only when
     * the session's node says they need attention ({@link LocalAgentState.Extra#attention()}); the panel only renders.
     * The person's own choice is kept for this run of the analyser, never saved.
     */
    private final JButton detailsToggle = UiText.button(DETAILS);
    /** M78 D1 (§4.2): the stopped session's banner actions — Restart and Details. */
    private final JPanel bannerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
    private final JButton restartAfterExit = UiText.button(RESTART_AFTER_EXIT);
    static final String RESTART_AFTER_EXIT = "Restart";
    private final JPanel detailsBody = new JPanel();
    private final WrapLine cliLine = new WrapLine(" ");
    private final WrapLine pathLine = new WrapLine(" ");
    /** The person's choice for the disclosure in this run, or null to follow the node's attention. */
    private Boolean detailsChosen;
    private LocalAgentState.Attention detailsAttention = LocalAgentState.Attention.NONE;
    static final String DETAILS = "Details";
    private telamin.fluxtion.audit.analyser.analyser.session.ProposalState proposal =
            telamin.fluxtion.audit.analyser.analyser.session.ProposalState.NONE;

    private LocalAgentState rendered;
    private String renderedHeader;
    /**
     * Robot walkthrough #2 (2026-10-18): the session's folder, its posture note, the fork warning and Set up a project sit
     * behind the one panel's ⓘ disclosure; a standalone panel shows them, as before.
     */
    private boolean aboutOpen;
    private boolean folderKnown;

    public LocalAgentPanel(LocalAgentTranscript store) {
        this(store, new AssistantSurface(), true);
    }

    /** M74 UX: Claude Code's part of the one assistant panel — its own lines; the transcript and composer are shared. */
    LocalAgentPanel(LocalAgentTranscript store, AssistantSurface surface) {
        this(store, surface, false);
    }

    private LocalAgentPanel(LocalAgentTranscript store, AssistantSurface surface, boolean standalone) {
        super(new BorderLayout(4, 4));
        this.store = store;
        this.surface = surface;
        this.standalone = standalone;
        this.aboutOpen = standalone;
        this.chips = surface.chips;
        this.view = surface.view;
        this.viewScroll = surface.viewScroll;
        this.draft = surface.draft;
        this.draftScroll = surface.draftScroll;
        this.fullBadge = surface.fullBadge;
        this.composerHint = surface.composerHint;
        this.start = surface.start;
        this.stop = surface.stop;
        this.newChat = surface.newChat;
        this.send = surface.send;
        // SF-2: the shared Send and badge carry this mode's computed words — plain, as they were when built here
        UiText.plain(send);
        UiText.plain(fullBadge);
        getAccessibleContext().setAccessibleName("Claude Code");
        header.getAccessibleContext().setAccessibleName("Claude Code session folder");
        status.getAccessibleContext().setAccessibleName("Claude Code status");
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setOpaque(false);
        detail.setVisible(false);
        posture.setForeground(UiTheme.mutedForeground());
        lockWarning.setForeground(UiTheme.mutedForeground());
        lockWarning.setVisible(false);                         // L-e: shown once a conversation exists

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel actions = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 2));   // P2b: wraps in a narrow tab
        if (standalone) {
            actions.add(start);
            actions.add(stop);
            actions.add(newChat);
        }
        actions.add(setUp);
        actions.add(settings);
        setUp.setToolTipText(UiText.tooltip("Choose a folder; Claude Code looks and proposes a set-up, you answer each "
                + "item, and the analyser applies the ones you accept"));
        setUp.addActionListener(e -> { if (intents != null) intents.setUpProject(); });
        proposalBanner.setOpaque(true);
        proposalBanner.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        proposalBanner.add(proposalText, BorderLayout.CENTER);
        JPanel bannerButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        bannerButtons.setOpaque(false);
        bannerButtons.add(review);
        bannerButtons.add(notNow);
        proposalBanner.add(bannerButtons, BorderLayout.SOUTH);
        proposalBanner.setVisible(false);
        proposalBanner.getAccessibleContext().setAccessibleName("Claude Code's set-up proposal");
        review.addActionListener(e -> { if (intents != null && proposal.exists()) intents.reviewProposal(proposal.id()); });
        notNow.addActionListener(e -> { if (intents != null && proposal.exists()) intents.dismissProposal(proposal.id()); });
        buildModeBanner.setOpaque(true);
        buildModeBanner.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        buildModeBanner.add(buildModeText, BorderLayout.CENTER);
        JPanel buildModeButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        buildModeButtons.setOpaque(false);
        buildModeButtons.add(buildModeSwitch);
        buildModeButtons.add(buildModeKeep);
        buildModeBanner.add(buildModeButtons, BorderLayout.SOUTH);
        buildModeBanner.setVisible(false);
        buildModeBanner.getAccessibleContext().setAccessibleName("Switch Full Claude Code to edit files without asking");
        buildModeSwitch.addActionListener(e -> {
            if (intents != null && buildModeOffer != 0) intents.answerBuildMode(buildModeOffer, true);
        });
        buildModeKeep.addActionListener(e -> {
            if (intents != null && buildModeOffer != 0) intents.answerBuildMode(buildModeOffer, false);
        });
        restartBanner.setOpaque(true);
        restartBanner.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        restartBanner.add(restartText, BorderLayout.CENTER);
        JPanel restartButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        restartButtons.setOpaque(false);
        restartButtons.add(restartYes);
        restartButtons.add(restartLater);
        restartBanner.add(restartButtons, BorderLayout.SOUTH);
        restartBanner.setVisible(false);
        restartBanner.getAccessibleContext().setAccessibleName("Restart Claude Code to apply the changed settings");
        restartYes.addActionListener(e -> {
            if (intents != null && restart.offered()) intents.answerRestart(restart.offer(), true);
        });
        restartLater.addActionListener(e -> {
            if (intents != null && restart.offered()) intents.answerRestart(restart.offer(), false);
        });
        restartChip.addActionListener(e -> { if (intents != null && restart.deferred()) intents.reopenRestartOffer(); });
        restartChip.getAccessibleContext().setAccessibleName("Settings changed: restart Claude Code to apply them");
        askAgain.setVisible(false);
        askAgain.addActionListener(e -> {
            if (intents != null && rendered != null
                    && LocalAgentState.Guide.ASK_AGAIN.equals(rendered.extra().guide().action())) {
                intents.askAgain(currentBinding());
            }
        });
        closeExternal.setVisible(false);
        closeExternal.setToolTipText(UiText.tooltip("While this session runs, refuse external MCP/REST clients — "
                + "including this session's own tools, which could otherwise reach the analyser through the endpoint "
                + "file. Claude Code keeps its own connection; external access opens again when the session ends."));
        closeExternal.addActionListener(e -> {
            if (intents != null && listener.offerClose()) intents.closeExternalAccess(listener.bridgeFor());
        });
        diagnostics.setForeground(UiTheme.mutedForeground());
        diagnostics.setVisible(false);
        endpoint.setForeground(UiTheme.mutedForeground());
        endpoint.setVisible(false);
        cliLine.setForeground(UiTheme.mutedForeground());
        cliLine.setVisible(false);
        pathLine.setForeground(UiTheme.mutedForeground());
        pathLine.setVisible(false);
        detailsBody.setLayout(new BoxLayout(detailsBody, BoxLayout.Y_AXIS));
        detailsBody.setBorder(BorderFactory.createEmptyBorder(0, 14, 2, 0));
        for (JComponent c : new JComponent[]{cliLine, endpoint, pathLine, diagnostics, detail}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            detailsBody.add(c);
        }
        detailsBody.setVisible(false);
        detailsBody.getAccessibleContext().setAccessibleName("Claude Code details");
        detailsToggle.setVisible(false);
        detailsToggle.addActionListener(e -> {
            detailsChosen = !detailsBody.isVisible();
            showDetails();
        });
        // M78 D1 (§4.2): the stopped session's banner — its words, then [Restart] [Details]
        bannerRow.setOpaque(false);
        bannerRow.add(restartAfterExit);
        bannerRow.add(detailsToggle);
        bannerRow.setVisible(false);
        restartAfterExit.setVisible(false);
        restartAfterExit.setToolTipText(UiText.tooltip("Start Claude Code again; the conversation is resumed"));
        restartAfterExit.getAccessibleContext().setAccessibleName("Restart Claude Code");
        restartAfterExit.addActionListener(e -> onStart());
        activityStall.setOpaque(false);
        activityStall.add(activityStallText, BorderLayout.CENTER);
        JPanel stallActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        stallActions.setOpaque(false);
        stallActions.add(keepWaiting);
        stallActions.add(stallStop);
        activityStall.add(stallActions, BorderLayout.SOUTH);
        activityStall.setVisible(false);
        keepWaiting.addActionListener(e -> {
            if (intents != null && rendered != null) intents.keepWaiting(rendered.generation(), rendered.openTicket());
        });
        stallStop.addActionListener(e -> onStop());
        java.util.List<JComponent> lines = new java.util.ArrayList<>();
        if (standalone) lines.add(chips);
        lines.addAll(java.util.List.of(header, posture, lockWarning, actions, closeExternal, restartBanner,
                buildModeBanner, proposalBanner, status, activityStall, askAgain, bannerRow, detailsBody));
        for (JComponent c : lines) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            top.add(c);
        }
        add(top, standalone ? BorderLayout.NORTH : BorderLayout.CENTER);

        if (standalone) {
            add(viewScroll, BorderLayout.CENTER);
            JPanel composer = new JPanel(new BorderLayout(4, 4));
            JPanel above = new JPanel();
            above.setLayout(new BoxLayout(above, BoxLayout.Y_AXIS));
            fullBadge.setAlignmentX(LEFT_ALIGNMENT);
            composerHint.setAlignmentX(LEFT_ALIGNMENT);
            fullBadge.setFont(fullBadge.getFont().deriveFont(Font.BOLD));
            above.add(fullBadge);
            above.add(composerHint);
            surface.bindingChip.setAlignmentX(LEFT_ALIGNMENT);
            above.add(surface.bindingChip);          // final review #12
            composer.add(above, BorderLayout.NORTH);
            composer.add(draftScroll, BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
            buttons.add(surface.activityRow);
            buttons.add(send);
            composer.add(buttons, BorderLayout.SOUTH);
            add(composer, BorderLayout.SOUTH);
            surface.onStart = this::onStart;
            surface.onStop = this::onStop;
            surface.onNewChat = this::onNewChat;
            surface.onSend = this::onSend;
            surface.historyMode(telamin.fluxtion.audit.analyser.analyser.session.AssistantMode.LOCAL_AGENT);
        }
        settings.addActionListener(e -> { if (intents != null) intents.openSettings(); });
        if (!standalone) {                // robot walkthrough #2: behind the one panel's ⓘ until it is opened
            header.setVisible(false);
            posture.setVisible(false);
            setUp.setVisible(false);
        }
    }

    /** M74 UX: whether this mode is the one the panel shows (only then does it draw the shared parts). */
    void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (!active) surface.activityRow.setVisible(false);
        if (active && rendered != null) {
            LocalAgentState last = rendered;
            rendered = null;
            render(last);
        }
    }

    boolean active() {
        return active;
    }

    /** Robot walkthrough #2: the one panel's ⓘ disclosure opened or closed. */
    void setAboutOpen(boolean open) {
        if (aboutOpen == open) return;
        aboutOpen = open;
        if (rendered != null) showAbout(rendered);
        revalidate();
        repaint();
    }

    private void showAbout(LocalAgentState s) {
        header.setVisible(aboutOpen && folderKnown);
        posture.setVisible(aboutOpen);
        // holistic review L-e: the fork warning only when there IS a conversation a terminal could resume
        lockWarning.setVisible(aboutOpen && s.resumable());
        setUp.setVisible(aboutOpen);
    }

    /**
     * Whether the Details disclosure is open: the person's choice in this run. M78 D1 (§4.2, the error state): Details is
     * the banner's button — the banner says what happened in a few words, and the operator lines open when asked for, so
     * a stopped session stays within its word budget (§6); the node's attention decides that the button is THERE.
     */
    boolean detailsOpen() {
        return detailsChosen != null && detailsChosen;
    }

    JButton detailsToggle() {
        return detailsToggle;
    }

    JPanel detailsBody() {
        return detailsBody;
    }

    /** The disclosure's label: ▸ closed, ▾ open. M78 D1: what needs attention is its tooltip and description. */
    static String detailsLabel(boolean open, LocalAgentState.Attention a) {
        return (open ? "▾ " : "▸ ") + DETAILS;
    }

    /** What needs attention, in words, when the node says something does ("" for nothing). */
    static String attentionWords(LocalAgentState.Attention a) {
        return switch (a == null ? LocalAgentState.Attention.NONE : a) {
            case NONE -> "";
            case WARNING -> "needs attention: Claude Code reported something unexpected";
            case REFUSAL -> "needs attention: Claude Code could not start, or was refused";
            case MISMATCH -> "needs attention: the session did not match how Claude Code was set up";
        };
    }

    private void showDetails() {
        boolean any = cliLine.isVisible() || endpoint.isVisible() || pathLine.isVisible() || diagnostics.isVisible()
                || detail.isVisible();
        // M78 D1 (§4.1 items 17–18): the disclosure is conditional — only while the node says something needs attention;
        // otherwise the same lines are in About this session…
        boolean attention = detailsAttention != LocalAgentState.Attention.NONE;
        boolean open = any && attention && detailsOpen();
        detailsToggle.setVisible(any && attention);
        detailsToggle.setText(detailsLabel(open, detailsAttention));
        detailsToggle.getAccessibleContext().setAccessibleName(DETAILS);
        detailsToggle.getAccessibleContext().setAccessibleDescription((open ? "expanded" : "collapsed")
                + (detailsAttention == LocalAgentState.Attention.NONE ? "" : "; " + attentionWords(detailsAttention)));
        detailsToggle.setToolTipText(UiText.tooltip("The CLI version, where requests go, the PATH, diagnostics and the "
                + "last error" + (attention ? " — " + attentionWords(detailsAttention) : "")));
        bannerRow.setVisible(detailsToggle.isVisible() || restartAfterExit.isVisible());
        detailsBody.setVisible(open);
        revalidate();
        repaint();
    }

    /** The state last rendered (null before the first), for the one panel's limits line. */
    LocalAgentState lastState() {
        return rendered;
    }

    /**
     * Option 2 (owner, 2026-10-18): the analyser's note to Claude Code, in the person's words — About this session… says
     * what the session was told (the provider's identity note, and for Full its starter rule).
     */
    public static final String ABOUT_NOTE_SAFE = "The analyser tells Claude Code it is the assistant inside this app, what "
            + "a project, starter, log, graph and report mean here, and to call analyser_context first.";
    public static final String ABOUT_NOTE_FULL = ABOUT_NOTE_SAFE + " For Full it adds: begin a new application by offering "
            + "one of the analyser's starters, never by searching the web or scaffolding by hand, and create nothing in the "
            + "scratch folder. " + telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FullFolderTrust.DOCS_FETCH;

    /** M74 UX: what Claude Code here cannot do — said in the one panel's limits line, where the in-app limits are said. */
    public static final String SAFE_LIMITS = "Safe analysis reads your evidence; it changes what is open or writes a file only "
            + "after you approve each call. No shell, no edits, no web. "
            + telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.CLAUDE_CODE_SENDS;
    static final String FULL_LIMITS = "Your own tools run under your permission rules: the analyser asks you only when "
            + "Claude Code asks, and cannot confine the rest. "
            + telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.CLAUDE_CODE_SENDS;
    static final String OFF_LIMITS = "Claude Code is not set up on this machine — choose Safe analysis or Full Claude "
            + "Code (the mode chip above), or the in-app assistant.";

    static String limitsText(LocalAgentState s) {
        return limitsText(s, telamin.fluxtion.audit.analyser.analyser.session.ListenerState.NONE);
    }

    /** Review B2: a full session with external access open says its tools can drive the analyser as an external client. */
    public static final String EXTERNAL_OPEN_TO_TOOLS = " External MCP/REST access is open: this session's own tools "
            + "can drive the analyser as an external client, through the endpoint file (~/.fluxtion-analyser/rest-endpoint).";
    public static final String EXTERNAL_CLOSED_FOR_SESSION = " External MCP/REST access is closed while this session "
            + "runs.";

    public static String limitsText(LocalAgentState s, telamin.fluxtion.audit.analyser.analyser.session.ListenerState l) {
        if (s == null) return SAFE_LIMITS;
        if ("SWITCHED_OFF".equals(s.verdict())) return OFF_LIMITS;
        boolean full = s.running() ? s.posture().isFull() : s.fullCue() != LocalAgentState.FullCue.NONE;
        if (!full) return SAFE_LIMITS;
        if (l != null && l.bridgeFor() != 0 && l.fullSession()) {
            if (l.external()) return FULL_LIMITS + EXTERNAL_OPEN_TO_TOOLS;
            if (l.closedForSession()) return FULL_LIMITS + EXTERNAL_CLOSED_FOR_SESSION;
        }
        return FULL_LIMITS;
    }

    /** The limits line for the state and listener last rendered. */
    String limitsNow() {
        return limitsText(rendered, listener);
    }

    /**
     * M78 D1 (spec-quiet-assistant-panel.md §4.3): what <i>About this session…</i> shows for Claude Code — everything the
     * header used to repeat: the assistant, model, posture, CLI, billing and plan or spend, provider; the session's
     * folder and the fork warning; what the posture loads; the limits (with the not-local-only sentence and, for a full
     * session, whether external access is open to its tools); and the operator details. The same words the panel
     * rendered; nothing new is decided here.
     */
    java.util.List<String> aboutLines() {
        java.util.List<String> out = new java.util.ArrayList<>();
        LocalAgentState s = rendered;
        if (s == null) {
            out.add(limitsNow());
            return out;
        }
        LocalAgentTranscript.Header h = shownHeader(s, store);
        out.add(headerLine(h, s));
        out.add(s.toolCallCount() + " tool calls · " + s.turnCount() + " turns");
        if (h != null && h.scratchForProject()) out.add(SCRATCH_NOTE);
        out.add(postureLine(h, s));
        if (s.resumable()) out.add(LOCK_WARNING);
        out.add(limitsNow());
        // Option 2 (owner, 2026-10-18): what the analyser tells Claude Code, said where the limits are said
        boolean full = s.running() ? s.posture().isFull() : s.fullCue() != LocalAgentState.FullCue.NONE;
        out.add(full ? ABOUT_NOTE_FULL : ABOUT_NOTE_SAFE);
        String ep = endpointText(h, s);
        if (ep != null) out.add(ep);
        String diag = diagnosticsText(s, h);
        if (diag != null) out.add(diag);
        String words = s.detailRef() != 0 ? store.text(s.detailRef()) : s.stderrRef() != 0
                ? "Claude Code's last error line: " + store.text(s.stderrRef()) : "";
        if (!words.isBlank()) out.add(words);
        return out;
    }

    /** Review B2/B5: render actionListener's decision — the offer to close external access, and the limits line. */
    public void renderListener(telamin.fluxtion.audit.analyser.analyser.session.ListenerState l) {
        listener = l == null ? telamin.fluxtion.audit.analyser.analyser.session.ListenerState.NONE : l;
        closeExternal.setVisible(listener.offerClose());
    }

    JButton closeExternalButton() {
        return closeExternal;
    }

    /** M78 D1: ⋯ ▸ Settings… while Claude Code is shown. */
    void openSettingsIntent() {
        if (intents != null) intents.openSettings();
    }

    void onStart() {
        if (intents != null) intents.start();
    }

    void onStop() {
        if (intents != null) intents.stop();
    }

    void onNewChat() {
        if (intents != null) intents.newChat();
    }

    /**
     * A theme switch re-renders the conversation (M74 visual check): its colours are set when an entry is drawn, so
     * without this a note kept the previous theme's colour until the next snapshot changed something.
     */
    @Override
    public void updateUI() {
        super.updateUI();
        LocalAgentState last = rendered;
        if (last != null && store != null) {
            rendered = null;
            SwingUtilities.invokeLater(() -> render(last));
        }
    }

    public void setIntents(Intents intents) {
        this.intents = intents;
    }

    /** The transcript's analyser references and where a chip goes: the frame's, reveal-only. */
    public void setTranscriptLinks(TranscriptView.Chips chips, TranscriptView.Navigator navigator) {
        view.setChips(chips);
        view.setNavigator(navigator);
    }

    void onSend() {
        String text = draft.getText().trim();
        if (text.isEmpty() || intents == null) return;
        intents.send(text, currentBinding());   // refused or not, the node says so; the draft stays until it is accepted
    }

    /** The composed draft's binding while any of its appended text remains (deleting the whole draft drops it). */
    java.util.List<Long> currentBinding() {
        return surface.bindings(telamin.fluxtion.audit.analyser.analyser.session.AssistantMode.LOCAL_AGENT);
    }

    /** The node accepted the send: clear the composer. Nothing else clears it (a refused draft stays editable). */
    public void draftAccepted() {
        surface.accepted(telamin.fluxtion.audit.analyser.analyser.session.AssistantMode.LOCAL_AGENT);
    }

    /**
     * Place a composed draft (OD-74.14): APPENDED after whatever the person has written — never over it — with exactly
     * the appended range highlighted, the composer focused and lit with {@code hint}. Nothing is sent.
     *
     * @return the appended range's start
     */
    public int appendDraft(long draftRef, String text, String hint) {
        return appendDraft(draftRef, text, hint, 0);
    }

    /** Final review #12: with the log generation the node bound the draft to (0: none), for the binding chip. */
    public int appendDraft(long draftRef, String text, String hint, long logGeneration) {
        return surface.appendDraft(telamin.fluxtion.audit.analyser.analyser.session.AssistantMode.LOCAL_AGENT, draftRef,
                text, hint, logGeneration);
    }

    JTextArea composerArea() {
        return draft;
    }

    TranscriptView conversationView() {
        return view;
    }

    JButton startButton() {
        return start;
    }

    JButton sendButton() {
        return send;
    }

    JButton stopButton() {
        return stop;
    }

    JLabel fullBadge() {
        return fullBadge;
    }

    WrapLine composerHint() {
        return composerHint;
    }

    JScrollPane draftScroll() {
        return draftScroll;
    }

    JPanel proposalBanner() {
        return proposalBanner;
    }

    WrapLine proposalText() {
        return proposalText;
    }

    JButton reviewButton() {
        return review;
    }

    JButton setUpButton() {
        return setUp;
    }

    WrapLine diagnosticsLine() {
        return diagnostics;
    }

    WrapLine endpointLine() {
        return endpoint;
    }

    /**
     * P2b (§5.6 step 4): the proposal banner, rendered from onboardingProposal's state — shown only while something is
     * the person's to answer; the review dialog opens on the person's click, never on a model call (OD-74.13).
     */
    /** P3 (§8.3 UI): the permission prompt as the session decided it — the status line says Claude Code is waiting. */
    public void renderPermission(telamin.fluxtion.audit.analyser.analyser.session.PermissionState p) {
        // M78 D1 (§4.1 item 16): the 25-word waiting sentence is gone — the header carries the session's chip word (which
        // localAgentPermission's state decides) and the tool line ends "— approve?"; the dialog says the rest
    }

    /**
     * The status line: the node's one instruction (robot walkthrough #3). M78 D1: it is shown only as the conditional
     * banner — {@link LocalAgentState.Guide#banner()} — and the routine phase is the header's chip word.
     */
    static String statusText(LocalAgentState s) {
        LocalAgentState.Guide g = s.extra().guide();
        return g.banner().isEmpty() ? g.instruction() : g.banner();
    }

    /** Owner decision 2026-10-18: the Build-mode offer as {@code aiSetup} decided it — its one line, or nothing. */
    public void renderBuildModeOffer(telamin.fluxtion.audit.analyser.analyser.session.ProfileState p) {
        buildModeOffer = p == null ? 0 : p.buildModeOffer();
        boolean show = buildModeOffer != 0;
        buildModeText.setText(show ? telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.BUILD_MODE_OFFER : " ");
        buildModeBanner.setBackground(UiTheme.accentWash(0.14f));
        buildModeBanner.setVisible(show);
        revalidate();
        repaint();
    }

    /** For the frame test: the Build-mode offer's banner and buttons. */
    JPanel buildModeBanner() {
        return buildModeBanner;
    }

    JButton buildModeSwitchButton() {
        return buildModeSwitch;
    }

    JButton buildModeKeepButton() {
        return buildModeKeep;
    }

    public void renderProposal(telamin.fluxtion.audit.analyser.analyser.session.ProposalState p) {
        proposal = p == null ? telamin.fluxtion.audit.analyser.analyser.session.ProposalState.NONE : p;
        boolean show = proposal.awaitingPerson();
        proposalText.setText(show ? bannerText(proposal) : " ");
        proposalBanner.setBackground(UiTheme.accentWash(0.14f));
        proposalBanner.setVisible(show);
        revalidate();
        repaint();
    }

    /** Owner, 2026-10-18: the restart offer as the node decided it — the prompt, or nothing (the chip is a header chip). */
    private void renderRestart(LocalAgentState s) {
        restart = s.extra().restart();
        String words = restartWords(restart, s.busy() || "STOPPING".equals(s.phase()));
        boolean show = words != null;
        restartText.setText(show ? words : " ");
        restartYes.setVisible(restart.offered());
        restartLater.setVisible(restart.offered());
        restartBanner.setBackground(UiTheme.accentWash(0.14f));
        restartBanner.setVisible(show);
        String detail = restartDetail(restart, s.busy() || "STOPPING".equals(s.phase()));
        restartBanner.getAccessibleContext().setAccessibleDescription(show ? detail : null);
        restartText.setToolTipText(show ? UiText.tooltip(detail) : null);
        restartChip.setToolTipText(restart.deferred() ? UiText.tooltip("Claude Code is running on the settings it was "
                + "started with. Click to restart it now and apply the change: " + restart.changed()) : null);
    }

    /**
     * The prompt's words, or null when there is none (pinned by the restart-offer tests). M78 D1 (§4.1 item 25): a few
     * words on the banner; the whole explanation is {@link #restartDetail} — its tooltip and accessible description.
     */
    static String restartWords(LocalAgentState.Restart r, boolean answering) {
        if (r == null || (!r.offered() && !r.afterTurn())) return null;
        if (r.afterTurn()) return "Restarts when this answer ends" + (r.newChat() ? " (new chat)." : ".");
        return "Settings changed — restart Claude Code now?" + (r.newChat() ? " (new chat)" : "");
    }

    /** The restart offer in full — what changed, what restarting does — for the banner's tooltip. */
    static String restartDetail(LocalAgentState.Restart r, boolean answering) {
        if (r == null || (!r.offered() && !r.afterTurn())) return null;
        if (r.afterTurn()) {
            return "Claude Code restarts with the changed settings when this answer ends"
                    + (r.newChat() ? " — a new chat begins then." : "; the conversation continues.");
        }
        return "Settings changed — restart Claude Code now to apply them? Changed: " + r.changed() + ". "
                + (r.newChat() ? "Restarting begins a new chat: a conversation is never resumed under other settings. "
                : "The conversation continues after the restart. ")
                + (answering ? "Claude is answering: Yes restarts it when this answer ends. " : "")
                + "Until then it keeps running on the previous settings.";
    }

    JPanel restartBanner() {
        return restartBanner;
    }

    WrapLine restartText() {
        return restartText;
    }

    JButton restartYesButton() {
        return restartYes;
    }

    JButton restartLaterButton() {
        return restartLater;
    }

    JButton restartChip() {
        return restartChip;
    }

    /** "Claude Code proposes 4 changes to set up …/medmon — Review…" (§5.6 step 4). */
    static String bannerText(telamin.fluxtion.audit.analyser.analyser.session.ProposalState p) {
        // owner hands-on 2026-10-18b: a starter makes a NEW project in a folder the person chooses — never "set up" the
        // hosted session's own folder, which is what the generic sentence named
        if (starterProposal(p)) {
            return "Claude Code proposes a new project from a starter. Nothing has been created — review it and choose "
                    + "its folder.";
        }
        long pending = p.count(telamin.fluxtion.audit.analyser.analyser.session.ProposalState.ItemState.PENDING);
        long n = pending + ("PENDING".equals(p.create()) ? 1 : 0);
        long shown = p.items().size() - pending;
        String dir = p.setupDirShown() == null ? "this folder" : p.setupDirShown();
        return "Claude Code proposes " + n + " change" + (n == 1 ? "" : "s") + " to set up " + dir
                + (shown > 0 ? ", and " + shown + " that cannot be applied" : "")
                + ". Nothing has been applied — review each one.";
    }

    static boolean starterProposal(telamin.fluxtion.audit.analyser.analyser.session.ProposalState p) {
        return p.items().stream().anyMatch(i -> "starter".equals(i.kind()));
    }

    /** The frame's one-line cue for a waiting proposal, shown above the start page and the workspace. */
    static String cueText(telamin.fluxtion.audit.analyser.analyser.session.ProposalState p) {
        return starterProposal(p) ? "Claude Code proposes a new project from a starter — nothing is created until you review it."
                : "Claude Code proposes a set-up for " + (p.setupDirShown() == null ? "this folder" : p.setupDirShown())
                + " — nothing is applied until you review it.";
    }

    /** Render the session's decision. Unchanged input changes nothing on screen. */
    public void render(LocalAgentState s) {
        // review S5: the header is the session's own — its launch folder, not the project open now — and it is shown
        // only once the session is bound (READY or later), never from a handshake that may yet be refused
        LocalAgentTranscript.Header h = shownHeader(s, store);
        String headerText = headerLine(h, s);
        if (s.equals(rendered) && Objects.equals(headerText, renderedHeader)) return;
        if (active) renderChips(h, s, headerText);
        String folder = h == null ? null : h.folder();
        header.setText(folder == null || folder.isBlank() ? " " : "Folder: " + shortFolder(folder)
                + (h.scratchForProject() ? " — " + SCRATCH_NOTE : ""));
        header.setToolTipText(folder == null ? null : UiText.tooltip(folder));
        folderKnown = folder != null && !folder.isBlank();
        String postureWords = postureLine(h, s);
        int nl = postureWords.indexOf('\n');
        posture.setText(nl < 0 ? postureWords : postureWords.substring(0, nl));
        String path = nl < 0 ? null : postureWords.substring(nl + 1);
        pathLine.setText(path == null ? " " : path);
        pathLine.setVisible(path != null);
        String cli = h == null || h.cliVersion() == null ? null : "Claude Code CLI " + h.cliVersion();
        cliLine.setText(cli == null ? " " : cli);
        cliLine.setVisible(cli != null);
        // holistic review L-e (2026-10-17): the fork warning only when there IS a conversation a terminal could resume —
        // never always-on, where it was noise before the first turn
        showAbout(s);
        String ep = endpointText(h, s);
        endpoint.setText(ep == null ? " " : ep);
        endpoint.setVisible(ep != null);
        String diag = diagnosticsText(s, h);
        diagnostics.setText(diag == null ? " " : diag);
        diagnostics.setVisible(diag != null);
        boolean running = s.running();
        LocalAgentState.Guide guide = s.extra().guide();
        if (active) {
            UiText.plain(surface.activityLine).setText(telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ActivityText
                    .step(guide, store::text));
            surface.activityRow.setVisible(!guide.currentStep().isEmpty());
            // the shared parts are drawn by the mode the panel shows (M74 UX: one composer, one Start/Stop/Send).
            // Robot walkthrough #3: ONE primary action — Send, which starts Claude Code when it is not running, or Stop.
            // Start is secondary, its label fixed, and it is not shown while Claude Code runs (no disabled "Running…")
            // M78 D1 (§4.1 item 39): Start / Resume is in ⋯ in the one panel — Send is the one primary action
            start.setVisible(!running && standalone);
            start.setEnabled(!running);
            start.setText(UiText.inert(startLabel(s)));
            stop.setEnabled(s.busy() || s.starting() || s.pendingDraft() != 0);
            send.setEnabled(!s.busy() && !"STOPPING".equals(s.phase()));
            // M78 D1 (§4.1 item 40): Stop REPLACES Send, in the same slot, while the node's primary action is Stop
            boolean stopSlot = LocalAgentState.Guide.STOP.equals(guide.primary());
            stop.setVisible(stopSlot);
            send.setVisible(!stopSlot);
            emphasise(send, LocalAgentState.Guide.SEND.equals(guide.primary()));
            emphasise(stop, stopSlot);
            // a refusal is the one line: the composer's "Draft — not sent" hint is not shown beside it
            surface.renderHint(telamin.fluxtion.audit.analyser.analyser.session.AssistantMode.LOCAL_AGENT, guide.refusal());
            String newChatName = s.newChatOffered() ? AssistantSurface.NEW_CHAT + " (offered)" : AssistantSurface.NEW_CHAT;
            newChat.setText(UiText.inert(standalone ? newChatName : AssistantSurface.NEW_CHAT_ICON));
            newChat.getAccessibleContext().setAccessibleName(newChatName);
            newChat.setToolTipText(UiText.tooltip(newChatName));
            emphasise(newChat, s.newChatOffered());
            renderFullCue(s);
        }
        settings.setVisible("SWITCHED_OFF".equals(s.verdict()) || "BUDGET_SPENT".equals(s.verdict()));
        activityStallText.setText(guide.stallBanner());
        activityStall.setVisible(guide.stalled());
        renderRestart(s);
        // M78 D1 (§2.3): the line shows only as the node's conditional banner; the routine phase is the chip word
        status.setText(statusText(s));
        status.setVisible(!guide.banner().isEmpty());
        // the banner's Restart: only where the node says the session stopped and starting again is the answer
        restartAfterExit.setVisible(!running && LocalAgentState.Guide.CHIP_STOPPED.equals(guide.chipWord())
                && !settings.isVisible());
        askAgain.setVisible(LocalAgentState.Guide.ASK_AGAIN.equals(guide.action()));
        revalidate();
        String words = s.detailRef() != 0 ? store.text(s.detailRef()) : s.stderrRef() != 0
                ? "Claude Code's last error line: " + store.text(s.stderrRef()) : "";
        detail.setText(words);
        detail.setVisible(!words.isBlank());
        // the node decides whether the details need attention; a NEW attention opens them even if the person closed them
        LocalAgentState.Attention attention = s.extra().attention();
        if (attention != detailsAttention && attention != LocalAgentState.Attention.NONE) detailsChosen = null;
        detailsAttention = attention;
        showDetails();

        if (active) {
            boolean follow = following();
            view.setSpeaker(TranscriptStyle.marker(TranscriptStyle.Role.CLAUDE), "input");
            view.render(items(s));
            if (follow) view.setCaretPosition(view.getDocument().getLength());
            // robot walk (2026-10-18): when a turn ends, the transcript shows the answer's FIRST line, not its end
            String ended = endedAnswer(s);
            if (ended != null && !ended.equals(scrolledTo)) {
                scrolledTo = ended;
                surface.scrollToLastAnswer();
            }
        }
        rendered = s;
        renderedHeader = headerText;
        if (active && headerListener != null) headerListener.run();
    }

    /** The newest answer that has finished (its conversation and ticket), or null while it streams or there is none. */
    static String endedAnswer(LocalAgentState s) {
        for (int i = s.entries().size() - 1; i >= 0; i--) {
            LocalAgentState.Entry e = s.entries().get(i);
            if (!LocalAgentState.ANSWER.equals(e.kind())) continue;
            return "STREAMING".equals(e.status()) ? null : s.conversation() + ":" + e.ticket();
        }
        return null;
    }

    /** The answer last scrolled to its first line. */
    private String scrolledTo;
    /** M78 D1: the one panel's header (its mode control) follows this mode's chip word and full cue. */
    private Runnable headerListener;

    void setHeaderListener(Runnable r) {
        this.headerListener = r;
    }

    /** "↩ Back to the answer": the newest answer, from its first line. */
    public void showLatestAnswer() {
        surface.scrollToLastAnswer();
    }

    /** A theme change: the transcript re-styles (the one full re-render besides New chat and a trim). */
    public void themeChanged() {
        if (!active) return;
        view.restyle();
        if (rendered != null) renderFullCue(rendered);
    }

    /** The published entries as the view's items: words from the store, state from the snapshot. */
    List<TranscriptView.Item> items(LocalAgentState s) {
        List<TranscriptView.Item> out = new ArrayList<>();
        int i = 0;
        for (LocalAgentState.Entry e : s.entries()) {
            String key = s.conversation() + ":" + i++ + ":" + e.kind() + ":" + e.id() + ":" + e.ticket();
            switch (e.kind()) {
                // owner, 2026-10-18: each turn's time is the session's record of it (Entry.atMillis), rendered by the view
                case LocalAgentState.USER -> out.add(new TranscriptView.Item(key, TranscriptStyle.Role.YOU,
                        store.text(e.id()), e.status(), 0, 0, false).at(e.atMillis(), false));
                case LocalAgentState.ANSWER -> {
                    boolean stopped = "CANCELLED".equals(e.status()) || "PROCESS_EXIT".equals(e.status());
                    // M78 D1 (OD-Q.8): the turn's time (and an API key's cost) is the answer's tooltip, not a line
                    String turn = "STREAMING".equals(e.status()) ? null : turnText(s, e.ticket());
                    // M78 D1 (§3): while the node says a permission waits, the streaming answer's open tool line asks
                    boolean asks = "STREAMING".equals(e.status()) && e.ticket() == s.openTicket()
                            && LocalAgentState.Guide.CHIP_NEEDS_YOU.equals(s.extra().guide().chipWord());
                    out.add(new TranscriptView.Item(key, stopped ? TranscriptStyle.Role.STOPPED : TranscriptStyle.Role.CLAUDE,
                            store.text(e.id()), asks ? TranscriptView.AWAITING_APPROVAL : e.status(), e.revision(),
                            e.logGeneration(), "STREAMING".equals(e.status()), turn == null ? null : TURN_TIP + turn)
                            .at(e.atMillis(), false));
                }
                case LocalAgentState.NOTE -> out.add(new TranscriptView.Item(key, TranscriptStyle.Role.NOTE,
                        noteLabel(e.status()) + e.detail(), e.status(), 0, 0, false));
                default -> { }
            }
        }
        return out;
    }

    /**
     * Review M74 usability #4: a note's label in words — never its status code ("[posture changed]" read as the app's
     * own vocabulary leaking). A code with no word here shows no label: the note's own sentence says what happened.
     */
    /** The answer's tooltip: its turn's time, and its cost with an API key. */
    static final String TURN_TIP = "Turn: ";

    static String noteLabel(String code) {
        String w = switch (code == null ? "" : code) {
            case "DENIED" -> "Denied";
            case "TIMED_OUT" -> "Not answered";
            case "WITHDRAWN" -> "Withdrawn";
            case "ABANDONED" -> "Did not run";
            case "COMPACTED" -> "Compacted";
            case "RATE_LIMITED" -> "Usage limit";
            case "AUTH_FAILED" -> "Sign-in";
            case "POSTURE_CHANGED" -> "Settings changed";
            case "BUDGET" -> "Spend limit";
            case "CANCELLED", "STOPPED" -> "Stopped";
            case "EXITED", "PROCESS_EXIT" -> "Claude Code exited";
            case "MCP_SERVER" -> "Your MCP server";
            case "ERROR" -> "Error";
            case "KEPT" -> "Kept";
            default -> "";
        };
        return w.isEmpty() ? "" : w + ": ";
    }

    // ---- the header as chips (§5.9) -----------------------------------------------------------------------------------

    /**
     * M78 D1 (§4.1 items 5–10): the header carries no chips — the CLI, the model, the posture, the billing, the plan and
     * the provider are in About this session… (the posture is the mode control, the plan the PLAN note). The one chip
     * left is conditional: the restart chip after Later.
     */
    private void renderChips(LocalAgentTranscript.Header h, LocalAgentState s, String headerText) {
        chips.removeAll();
        chips.getAccessibleContext().setAccessibleDescription(headerText);
        boolean restartChipShown = s.extra().restart().deferred();
        if (restartChipShown) chips.add(styledChip(restartChip));   // owner, 2026-10-18: after Later
        chips.setVisible(restartChipShown);
        chips.revalidate();
        chips.repaint();
    }

    /** The restart chip: a chip that is a button, in the note colour so it reads as something waiting. */
    private JButton styledChip(JButton b) {
        b.setOpaque(true);
        b.setContentAreaFilled(true);
        b.setFocusPainted(false);
        b.setBackground(UiTheme.accentWash(0.22f));
        b.setForeground(UiTheme.noteForeground());
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.noteForeground(), 1, true),
                BorderFactory.createEmptyBorder(1, 6, 1, 6)));
        Font f = UIManager.getFont("Label.font");
        if (f != null) b.setFont(f.deriveFont(Font.BOLD, f.getSize2D() - 1f));
        b.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return b;
    }

    /** Robot re-walk #6: a chip wraps to the row rather than running off a narrow column's edge. */
    private javax.swing.JComponent chip(String text, boolean strong) {
        return new Chip(text, strong, UiTheme.controlBorder());   // a model name or a folder is not the analyser's words
    }

    /**
     * The API provider as the person should read it, or null for none: the CLI's {@code firstParty} is Anthropic's own
     * API — the default, and internal jargon — so it is not shown (P2b visual fix); another provider is named as given.
     */
    static String providerWords(String apiProvider) {
        if (apiProvider == null || apiProvider.isBlank() || "firstParty".equals(apiProvider)) return null;
        return apiProvider;
    }

    /**
     * §5.2 the endpoint line (P2): what the analyser can observe and no more — only {@code scheme://host[:port]} of the
     * {@code ANTHROPIC_BASE_URL} it passed to the child; in the full posture, that the person's settings may set another.
     */
    static String endpointText(LocalAgentTranscript.Header h, LocalAgentState s) {
        if (h == null) return null;
        String ep = h.endpoint();
        String line = ep == null ? null : "requests go to " + ep;
        if (s.posture().isFull()) {
            String more = "your Claude Code settings may set another endpoint; the analyser does not read them";
            line = line == null ? more : line + " — " + more;
        }
        return line;
    }

    /** §5.2 the diagnostics line (P2), only when a count is non-zero; command names from {@code initialize.commands}. */
    static String diagnosticsText(LocalAgentState s, LocalAgentTranscript.Header h) {
        var x = s.extra();
        if (!x.diagnostics()) return null;
        List<String> parts = new ArrayList<>();
        if (x.unknownEvents() > 0) parts.add(x.unknownEvents() + " unknown stream event" + (x.unknownEvents() == 1 ? "" : "s"));
        if (x.unclassifiedCommands() > 0) {
            List<String> names = h == null ? List.of() : h.unclassified();
            parts.add(x.unclassifiedCommands() + " command" + (x.unclassifiedCommands() == 1 ? "" : "s")
                    + " not classified for the analyser" + (names.isEmpty() ? ""
                    : " (" + String.join(", ", names.stream().limit(5).map(n -> "/" + n).toList())
                    + (names.size() > 5 ? ", …" : "") + ")"));
        }
        if (x.droppedLines() > 0) parts.add(x.droppedLines() + " line" + (x.droppedLines() == 1 ? "" : "s")
                + " over 16 MB dropped");
        return "Diagnostics: " + String.join(" · ", parts);
    }

    /**
     * §5.9 per turn (P2b): a finished answer's time, and its cost only where a dollar figure means something — with an
     * API key (OD-74.24: never on a Claude plan). Null when nothing is known.
     */
    static String turnText(LocalAgentState s, long ticket) {
        LocalAgentState.Turn t = s.extra().turns().get(ticket);
        if (t == null) return null;
        List<String> parts = new ArrayList<>();
        if (t.durationMs() >= 0) parts.add(BigDecimal.valueOf(t.durationMs(), 3).setScale(1, RoundingMode.HALF_UP)
                .toPlainString() + " s");
        boolean apiKey = switch (s.billing()) {
            case API_KEY -> true;
            default -> false;
        };
        if (apiKey && t.costMicros() >= 0) parts.add("US$" + usd(t.costMicros()));
        return parts.isEmpty() ? null : String.join(" · ", parts);
    }

    /** {@code …/<folder name>}; the full path is the line's tooltip (§5.2). */
    static String shortFolder(String folder) {
        String f = folder.endsWith("/") || folder.endsWith("\\") ? folder.substring(0, folder.length() - 1) : folder;
        int cut = Math.max(f.lastIndexOf('/'), f.lastIndexOf('\\'));
        return cut < 0 ? f : "…/" + f.substring(cut + 1);
    }

    // ---- the full-posture cue (owner request, 2026-10-17) -------------------------------------------------------------

    /** The badge's words for the cue, or null when there is none: {@code Full Claude Code · <mode>: …}. */
    static String fullBadgeText(LocalAgentState s) {
        LocalAgentState.FullCue cue = s.fullCue();
        if (cue == LocalAgentState.FullCue.NONE) return null;
        String mode = s.cuePosture().fullMode().cli();
        // review M74 usability A7: the state first, so a narrow tab's ellipsis never eats "running" or "on"
        return "Full Claude Code (" + mode + ")"
                + (cue == LocalAgentState.FullCue.RUNNING ? " — running" : " — on: it runs at the next Start")
                + " · your tools and permissions";
    }

    /** The badge's tooltip: what the cue means. */
    static String fullBadgeTip(LocalAgentState s) {
        boolean strong = s.strongCue();
        return "Full Claude Code is your own Claude Code with your settings, skills, hooks and MCP servers, under your "
                + "permission rules and the mode you chose. The analyser does not confine it to the folder."
                + (strong ? " In this mode fewer actions are asked about." : "");
    }

    /** Send's label (M78 D1, §3): "◆ Send" whenever the full cue is shown — ◆◆ for the modes that ask least. */
    static String sendLabel(LocalAgentState s) {
        if (s.fullCue() == LocalAgentState.FullCue.NONE) return SEND;
        return cueMark(s) + " " + SEND;
    }

    static final String SEND = "Send";
    /** Send's accessible name under the full cue: it says where the question goes. */
    static final String SEND_TO_FULL = "Send to full Claude Code";

    /** The cue's mark: ◆, or ◆◆ for {@code acceptEdits} and {@code auto}; "" with no cue. */
    static String cueMark(LocalAgentState s) {
        if (s == null || s.fullCue() == LocalAgentState.FullCue.NONE) return "";
        return s.strongCue() ? "◆◆" : "◆";
    }

    /**
     * M78 D1 (§3): the full cue is ◆ on the mode control, the accent border on the composer and "◆ Send" — all visible in
     * the narrow dock. The 13-word badge is gone; its sentence is the mode control's tooltip and accessible name.
     */
    private void renderFullCue(LocalAgentState s) {
        String badge = fullBadgeText(s);
        boolean dark = ThemeManager.isDark();
        send.setText(sendLabel(s));
        fullBadge.setVisible(false);
        surface.placeholder(badge != null);
        if (badge == null) {
            surface.cueBorder(null, 0);
            send.setToolTipText(null);
            send.setForeground(null);
            send.getAccessibleContext().setAccessibleName(SEND);
            return;
        }
        Color c = TranscriptStyle.fullPosture(s.strongCue(), dark);
        fullBadge.setText(cueMark(s) + " " + badge);
        fullBadge.getAccessibleContext().setAccessibleName(badge);
        fullBadge.getAccessibleContext().setAccessibleDescription(fullBadgeTip(s));
        surface.cueBorder(c, s.strongCue() ? 3 : 2);
        send.setForeground(c);
        send.getAccessibleContext().setAccessibleName(SEND_TO_FULL);
        send.setToolTipText(UiText.tooltip("Sends to your full Claude Code (" + s.cuePosture().fullMode().cli() + ")"));
    }

    // ---- the words (pinned by LocalAgentHeaderTest) -------------------------------------------------------------------

    /** The header record the tab may show: only once the session is bound and checked (READY, BUSY or STOPPING). */
    static LocalAgentTranscript.Header shownHeader(LocalAgentState s, LocalAgentTranscript store) {
        boolean bound = "READY".equals(s.phase()) || "BUSY".equals(s.phase()) || "STOPPING".equals(s.phase());
        return !bound || s.headerRef() == 0 ? null : store.header(s.headerRef());
    }

    /** §5.2: model, posture, folder, CLI version, subscription type or API-key source, API provider; never an e-mail. */
    static String headerLine(LocalAgentTranscript.Header h, LocalAgentState s) {
        StringBuilder sb = new StringBuilder("Claude Code");
        if (h == null) return sb.append(s.running() ? " · starting" : " · not running").toString();
        String folder = h.folder();
        if (h.model() != null) sb.append(" · ").append(h.model());
        else if (h.configuredModel() != null && !h.configuredModel().isBlank()) {
            // initialize reports no model: say what was asked for until a turn's system/init confirms it
            sb.append(" · model: ").append(h.configuredModel()).append(" (confirmed after the first turn)");
        }
        sb.append(" · ").append(unbroken(s.posture().label()));
        if (h.cliVersion() != null) sb.append(" · CLI ").append(h.cliVersion());
        if (h.billing() != null && !h.billing().isBlank()) sb.append(" · ").append(h.billing());
        String provider = providerWords(h.apiProvider());
        if (provider != null) sb.append(" · ").append(provider);
        String cost = costText(s);
        if (!cost.isEmpty()) sb.append(" · ").append(cost);
        if (folder != null && !folder.isBlank()) sb.append("\nFolder: ").append(folder);     // its own line
        return sb.toString();
    }

    /**
     * The cost, only where it means something (owner, 2026-10-17): with an API key, what was used — of the limit when
     * one is set; on a Claude plan no dollar figure at all, only that usage counts against the plan; with neither known,
     * the limit's figure when a limit is set, else nothing.
     */
    static String costText(LocalAgentState s) {
        String used = (s.costUnderReported() ? "at least " : "") + "US$" + usd(s.spentMicros());
        return switch (s.billing()) {
            case SUBSCRIPTION -> PLAN_USAGE;
            case API_KEY -> s.budgetLimited() ? used + " of US$" + usd(s.budgetMicros()) : used + " used";
            case UNKNOWN -> s.budgetLimited() ? used + " of US$" + usd(s.budgetMicros()) : "";
        };
    }

    /**
     * §5.2: what the posture loads. Default: the analyser assistant's note. Full: the mode, that the person's own
     * settings, skills, hooks and n MCP servers are loaded, and the environment line naming exactly what was appended
     * to the child's PATH (OD-74.11).
     */
    static String postureLine(LocalAgentTranscript.Header h, LocalAgentState s) {
        if (!s.posture().isFull()) return DEFAULT_POSTURE_NOTE;   // the limits line says what it does
        int n = s.personServers();
        String line = unbroken(s.posture().label()) + (n == 0
                ? " — your settings, skills and hooks are loaded, and none of your MCP servers"
                : " — your settings, skills, hooks and " + n + " MCP server" + (n == 1 ? "" : "s") + " are loaded");
        List<String> added = h == null ? List.of() : h.pathAdded();
        return line + "\nPATH: inherited" + (added.isEmpty() ? "" : ", plus " + String.join(", ", added));
    }

    /** A name the wrapped header must not break inside: its spaces become no-break spaces (M74 visual check). */
    static String unbroken(String s) {
        return s == null ? "" : s.replace(' ', '\u00A0');
    }

    /**
     * Start's label, fixed (robot walkthrough #3): what pressing it does — start, or resume the conversation. It is not
     * shown while Claude Code runs, and an armed Start no longer has its own words: Send asks the question.
     */
    static String startLabel(LocalAgentState s) {
        return s.resumable() ? "Resume Claude Code" : "Start Claude Code";
    }

    /** The primary action wears the default button's weight; the other does not. */
    private static void emphasise(JButton b, boolean primary) {
        Font f = b.getFont();
        if (f != null && f.isBold() != primary) b.setFont(f.deriveFont(primary ? Font.BOLD : Font.PLAIN));
    }

    JButton askAgainButton() {
        return askAgain;
    }

    WrapLine instructionLine() {
        return status;
    }

    private static String usd(long micros) {
        return BigDecimal.valueOf(micros, 6).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private boolean following() {
        JScrollBar bar = viewScroll.getVerticalScrollBar();
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 4;
    }
}
