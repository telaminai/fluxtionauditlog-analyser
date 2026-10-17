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
    }

    /**
     * Safe analysis's line under the header (§5.2). Review M74 usability A1/A2: it no longer repeats the limits line, and
     * it states what Safe analysis does rather than pressing toward the riskier profile on every view.
     */
    static final String DEFAULT_POSTURE_NOTE = "Safe analysis — your own skills, hooks and MCP servers are not loaded";
    /** §4.8: an external terminal is invisible to the lock. */
    static final String LOCK_WARNING = "Don't resume this conversation in a terminal while it is open here — the history "
            + "would fork.";
    static final String CONTACTS_ANTHROPIC = "Start contacts Anthropic (sign-in check, MCP connect) before your first Send; "
            + "no record content is sent until you press Send. In full Claude Code, Start also runs your and the folder's "
            + "hooks and connects your MCP servers.";
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
    private final WrapLine diagnostics = new WrapLine(" ");
    private final WrapLine endpoint = new WrapLine(" ");
    private telamin.fluxtion.audit.analyser.analyser.session.ProposalState proposal =
            telamin.fluxtion.audit.analyser.analyser.session.ProposalState.NONE;

    private LocalAgentState rendered;
    private String renderedHeader;

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
        java.util.List<JComponent> lines = new java.util.ArrayList<>();
        if (standalone) lines.add(chips);
        lines.addAll(java.util.List.of(header, posture, endpoint, diagnostics, lockWarning, actions, closeExternal,
                proposalBanner, status, detail));
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
            buttons.add(send);
            composer.add(buttons, BorderLayout.SOUTH);
            add(composer, BorderLayout.SOUTH);
            surface.onStart = this::onStart;
            surface.onStop = this::onStop;
            surface.onNewChat = this::onNewChat;
            surface.onSend = this::onSend;
        }
        settings.addActionListener(e -> { if (intents != null) intents.openSettings(); });
    }

    /** M74 UX: whether this mode is the one the panel shows (only then does it draw the shared parts). */
    void setActive(boolean active) {
        if (this.active == active) return;
        this.active = active;
        if (active && rendered != null) {
            LocalAgentState last = rendered;
            rendered = null;
            render(last);
        }
    }

    boolean active() {
        return active;
    }

    /** The state last rendered (null before the first), for the one panel's limits line. */
    LocalAgentState lastState() {
        return rendered;
    }

    /** M74 UX: what Claude Code here cannot do — said in the one panel's limits line, where the in-app limits are said. */
    public static final String SAFE_LIMITS = "Safe analysis reads your evidence; it changes what is open or writes a file only "
            + "after you approve each call. No shell, no edits, no web.";
    static final String FULL_LIMITS = "Your own tools run under your permission rules: the analyser asks you only when "
            + "Claude Code asks, and cannot confine the rest.";
    static final String OFF_LIMITS = "Claude Code is not set up on this machine — choose Safe analysis or Full Claude "
            + "Code (Assistant ▾ above), or the in-app assistant.";

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

    /** Review B2/B5: render actionListener's decision — the offer to close external access, and the limits line. */
    public void renderListener(telamin.fluxtion.audit.analyser.analyser.session.ListenerState l) {
        listener = l == null ? telamin.fluxtion.audit.analyser.analyser.session.ListenerState.NONE : l;
        closeExternal.setVisible(listener.offerClose());
    }

    JButton closeExternalButton() {
        return closeExternal;
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
        surface.accepted();
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
        boolean was = waitingForApproval;
        waitingForApproval = p != null && p.waiting();
        if (was != waitingForApproval && rendered != null) status.setText(statusText(rendered, waitingForApproval));
    }

    /** The status line: the phase, or, while a permission dialog is open, that Claude Code is waiting for the person. */
    static String statusText(LocalAgentState s, boolean waiting) {
        return waiting ? WAITING_TEXT : phaseText(s);
    }

    static final String WAITING_TEXT = "Claude Code is waiting for your answer — the analyser's dialog shows what it asks "
            + "to run; Deny is the default, and Stop ends the turn";

    private boolean waitingForApproval;

    public void renderProposal(telamin.fluxtion.audit.analyser.analyser.session.ProposalState p) {
        proposal = p == null ? telamin.fluxtion.audit.analyser.analyser.session.ProposalState.NONE : p;
        boolean show = proposal.awaitingPerson();
        proposalText.setText(show ? bannerText(proposal) : " ");
        proposalBanner.setBackground(UiTheme.accentWash(0.14f));
        proposalBanner.setVisible(show);
        revalidate();
        repaint();
    }

    /** "Claude Code proposes 4 changes to set up …/medmon — Review…" (§5.6 step 4). */
    static String bannerText(telamin.fluxtion.audit.analyser.analyser.session.ProposalState p) {
        long pending = p.count(telamin.fluxtion.audit.analyser.analyser.session.ProposalState.ItemState.PENDING);
        long n = pending + ("PENDING".equals(p.create()) ? 1 : 0);
        long shown = p.items().size() - pending;
        String dir = p.setupDirShown() == null ? "this folder" : p.setupDirShown();
        return "Claude Code proposes " + n + " change" + (n == 1 ? "" : "s") + " to set up " + dir
                + (shown > 0 ? ", and " + shown + " that cannot be applied" : "")
                + ". Nothing has been applied — review each one.";
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
        header.setVisible(folder != null && !folder.isBlank());
        posture.setText(postureLine(h, s));
        // holistic review L-e (2026-10-17): the fork warning only when there IS a conversation a terminal could resume —
        // never always-on, where it was noise before the first turn
        lockWarning.setVisible(s.resumable());
        String ep = endpointText(h, s);
        endpoint.setText(ep == null ? " " : ep);
        endpoint.setVisible(ep != null);
        String diag = diagnosticsText(s, h);
        diagnostics.setText(diag == null ? " " : diag);
        diagnostics.setVisible(diag != null);
        boolean running = s.running();
        if (active) {
            // the shared parts are drawn by the mode the panel shows (M74 UX: one composer, one Start/Stop/Send)
            start.setEnabled(!running);
            start.setText(UiText.inert(startLabel(s)));
            stop.setEnabled(s.busy() || s.starting() || s.pendingDraft() != 0);
            send.setEnabled(!s.busy() && !"STOPPING".equals(s.phase()));
            newChat.setText(s.newChatOffered() ? "New chat (offered)" : "New chat");
            renderFullCue(s);
        }
        settings.setVisible("SWITCHED_OFF".equals(s.verdict()) || "BUDGET_SPENT".equals(s.verdict()));
        status.setText(statusText(s, waitingForApproval));
        revalidate();
        String words = s.detailRef() != 0 ? store.text(s.detailRef()) : s.stderrRef() != 0
                ? "Claude Code's last error line: " + store.text(s.stderrRef()) : "";
        detail.setText(words);
        detail.setVisible(!words.isBlank());

        if (active) {
            boolean follow = following();
            view.setSpeaker(TranscriptStyle.marker(TranscriptStyle.Role.CLAUDE), "input");
            view.render(items(s));
            if (follow) view.setCaretPosition(view.getDocument().getLength());
        }
        rendered = s;
        renderedHeader = headerText;
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
                case LocalAgentState.USER -> out.add(new TranscriptView.Item(key, TranscriptStyle.Role.YOU,
                        store.text(e.id()), e.status(), 0, 0, false));
                case LocalAgentState.ANSWER -> {
                    boolean stopped = "CANCELLED".equals(e.status()) || "PROCESS_EXIT".equals(e.status());
                    out.add(new TranscriptView.Item(key, stopped ? TranscriptStyle.Role.STOPPED : TranscriptStyle.Role.CLAUDE,
                            store.text(e.id()), e.status(), e.revision(), e.logGeneration(), "STREAMING".equals(e.status())));
                    String turn = "STREAMING".equals(e.status()) ? null : turnText(s, e.ticket());
                    if (turn != null) out.add(new TranscriptView.Item(key + ":turn", TranscriptStyle.Role.NOTE,
                            "Turn: " + turn, "TURN", 0, 0, false));
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
    static String noteLabel(String code) {
        String w = switch (code == null ? "" : code) {
            case "DENIED" -> "Denied";
            case "TIMED_OUT" -> "Not answered";
            case "APPROVED" -> "Approved";
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

    private void renderChips(LocalAgentTranscript.Header h, LocalAgentState s, String headerText) {
        chips.removeAll();
        chips.getAccessibleContext().setAccessibleDescription(headerText);
        chips.add(chip("Claude Code", true));
        if (h == null) {
            chips.add(chip(s.running() ? "starting" : "not running", false));
        } else {
            if (h.model() != null) chips.add(chip(h.model(), false));
            else if (h.configuredModel() != null && !h.configuredModel().isBlank()) {
                chips.add(chip("model: " + h.configuredModel() + " (confirmed after the first turn)", false));
            }
            chips.add(chip(s.posture().label(), s.posture().isFull()));
            if (h.billing() != null && !h.billing().isBlank()) chips.add(chip(h.billing(), false));
            String cost = costText(s);
            if (!cost.isEmpty()) chips.add(chip(cost, false));
            if (h.cliVersion() != null) chips.add(chip("CLI " + h.cliVersion(), false));
            // P2b visual fix: "firstParty" is the CLI's own word for Anthropic's API — internal jargon, not shown as a chip
            String provider = providerWords(h.apiProvider());
            if (provider != null) chips.add(chip(provider, false));
        }
        chips.revalidate();
        chips.repaint();
    }

    private JLabel chip(String text, boolean strong) {
        JLabel l = UiText.label(text);                 // a model name or a folder is not the analyser's words
        l.setOpaque(true);
        l.setBackground(UiTheme.accentWash(strong ? 0.22f : 0.10f));
        l.setForeground(strong ? UiTheme.accentText() : UIManager.getColor("Label.foreground"));
        l.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(UiTheme.controlBorder(), 1, true),
                BorderFactory.createEmptyBorder(1, 6, 1, 6)));
        Font f = UIManager.getFont("Label.font");
        if (f != null) l.setFont(f.deriveFont(strong ? Font.BOLD : Font.PLAIN, f.getSize2D() - 1f));
        return l;
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

    /** Send's label: it names the full posture whenever the cue is shown. */
    static String sendLabel(LocalAgentState s) {
        return s.fullCue() == LocalAgentState.FullCue.NONE ? "Send" : "Send to full Claude Code";
    }

    private void renderFullCue(LocalAgentState s) {
        String badge = fullBadgeText(s);
        boolean dark = ThemeManager.isDark();
        send.setText(sendLabel(s));
        if (badge == null) {
            fullBadge.setVisible(false);
            surface.cueBorder(null, 0);
            send.setToolTipText(null);
            return;
        }
        Color c = TranscriptStyle.fullPosture(s.strongCue(), dark);
        fullBadge.setText((s.strongCue() ? "◆◆ " : "◆ ") + badge);
        fullBadge.setForeground(c);
        fullBadge.setToolTipText(UiText.tooltip(fullBadgeTip(s)));
        fullBadge.getAccessibleContext().setAccessibleName(badge);
        fullBadge.getAccessibleContext().setAccessibleDescription(fullBadgeTip(s));
        fullBadge.setVisible(true);
        surface.cueBorder(c, s.strongCue() ? 3 : 2);
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

    /** The Start button says what pressing it would do, that it is ARMED, or that there is nothing to press. */
    static String startLabel(LocalAgentState s) {
        if (s.starting()) return "Starting…";
        if (s.running()) return "Running…";
        if (s.armedWhy() != null) return AskCatalogue.text(s.armedWhy());
        return s.resumable() ? "Resume Claude Code" : "Start Claude Code";
    }

    static String phaseText(LocalAgentState s) {
        String phase = switch (s.phase()) {
            case "SPAWNING", "HANDSHAKE" -> "Starting Claude Code…";
            case "BINDING" -> s.bindingElapsedMs() >= 2_000 ? "connecting to the analyser bridge…" : "Starting Claude Code…";
            case "READY" -> s.pendingDraft() != 0 ? "Sending your message…" : "Ready";
            case "BUSY" -> "Claude is answering…";
            case "STOPPING" -> "Stopping the turn…";
            default -> s.armedWhy() != null ? "Not running — your question is placed; press Start, then Send. "
                    + CONTACTS_ANTHROPIC : "Not running. " + CONTACTS_ANTHROPIC;
        };
        // a Send refused as busy already says what is happening: one sentence, not the phase and then the refusal
        if ("BUSY".equals(s.verdict()) && s.busy()) return s.reason();
        String why = s.reason().isBlank() ? "" : " — " + s.reason();
        return phase + why;
    }

    private static String usd(long micros) {
        return BigDecimal.valueOf(micros, 6).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private boolean following() {
        JScrollBar bar = viewScroll.getVerticalScrollBar();
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 4;
    }
}
