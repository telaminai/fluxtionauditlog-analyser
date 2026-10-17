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
         * Send: the composer's words, and {@code draftBinding} — the composed draft the composer still holds (its
         * appended range not wholly deleted), or 0. The node compares the binding with the open log.
         */
        void send(String draft, long draftBinding);

        void newChat();

        void openSettings();
    }

    /** The posture line of the default posture (§5.2). */
    static final String DEFAULT_POSTURE_NOTE = "analyser assistant — your skills and hooks are not loaded; enable full "
            + "Claude Code for those";
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

    // they WRAP to the tab's width: a header, a warning or a status that ends in "…" hides its most important part
    private final WrapLine header = new WrapLine(" ");
    private final JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 3));
    private final WrapLine posture = new WrapLine(DEFAULT_POSTURE_NOTE);
    private final WrapLine lockWarning = new WrapLine(LOCK_WARNING);
    private final WrapLine status = new WrapLine(" ");
    private final JTextArea detail = new JTextArea(2, 40);
    private final TranscriptView view = new TranscriptView();
    private final JScrollPane viewScroll = new JScrollPane(view);
    private final JTextArea draft = new JTextArea(3, 40);
    private final JScrollPane draftScroll = new JScrollPane(draft);
    private final JLabel fullBadge = UiText.label(" ");
    private final WrapLine composerHint = new WrapLine(" ");
    private final JButton start = new JButton("Start Claude Code");
    private final JButton stop = new JButton("Stop");
    private final JButton newChat = new JButton("New chat");
    private final JButton send = UiText.button("Send");
    private final JButton settings = new JButton("Settings ▸ Assistant");
    private final Border draftBorder;

    private LocalAgentState rendered;
    private String renderedHeader;
    /** The composed draft the composer holds (its appended range), for the binding at Send; 0 when none. */
    private long boundDraft;
    private Object boundHighlight;

    public LocalAgentPanel(LocalAgentTranscript store) {
        super(new BorderLayout(4, 4));
        this.store = store;
        getAccessibleContext().setAccessibleName("Claude Code");
        header.getAccessibleContext().setAccessibleName("Claude Code session folder");
        chips.getAccessibleContext().setAccessibleName("Claude Code session");
        chips.setOpaque(false);
        status.getAccessibleContext().setAccessibleName("Claude Code status");
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setOpaque(false);
        detail.setVisible(false);
        posture.setForeground(UiTheme.mutedForeground());
        lockWarning.setForeground(UiTheme.mutedForeground());

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actions.add(start);
        actions.add(stop);
        actions.add(newChat);
        actions.add(settings);
        for (JComponent c : new JComponent[]{chips, header, posture, lockWarning, actions, status, detail}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            top.add(c);
        }
        add(top, BorderLayout.NORTH);

        add(viewScroll, BorderLayout.CENTER);

        draft.setLineWrap(true);
        draft.setWrapStyleWord(true);
        draft.getAccessibleContext().setAccessibleName("Your message to Claude Code");
        draftBorder = draftScroll.getBorder();
        JPanel composer = new JPanel(new BorderLayout(4, 4));
        JPanel above = new JPanel();
        above.setLayout(new BoxLayout(above, BoxLayout.Y_AXIS));
        fullBadge.setAlignmentX(LEFT_ALIGNMENT);
        composerHint.setAlignmentX(LEFT_ALIGNMENT);
        fullBadge.setVisible(false);
        composerHint.setVisible(false);
        fullBadge.setFont(fullBadge.getFont().deriveFont(Font.BOLD));
        above.add(fullBadge);
        above.add(composerHint);
        composer.add(above, BorderLayout.NORTH);
        composer.add(draftScroll, BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(send);
        composer.add(buttons, BorderLayout.SOUTH);
        add(composer, BorderLayout.SOUTH);

        start.addActionListener(e -> { if (intents != null) intents.start(); });
        stop.addActionListener(e -> { if (intents != null) intents.stop(); });
        newChat.addActionListener(e -> { if (intents != null) intents.newChat(); });
        settings.addActionListener(e -> { if (intents != null) intents.openSettings(); });
        send.addActionListener(e -> onSend());
        draft.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "local-agent-send");
        draft.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK),
                "insert-break");
        draft.getActionMap().put("local-agent-send", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                var requests = draft.getInputMethodRequests();
                if (requests != null && requests.getCommittedTextLength() != draft.getDocument().getLength()) return;
                onSend();
            }
        });
        stop.setEnabled(false);
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

    private void onSend() {
        String text = draft.getText().trim();
        if (text.isEmpty() || intents == null) return;
        intents.send(text, currentBinding());   // refused or not, the node says so; the draft stays until it is accepted
    }

    /** The composed draft's binding while any of its appended text remains (deleting the whole draft drops it). */
    long currentBinding() {
        if (boundDraft == 0 || boundHighlight == null) return 0;
        for (Highlighter.Highlight h : draft.getHighlighter().getHighlights()) {
            if (h == boundHighlight) return h.getEndOffset() > h.getStartOffset() ? boundDraft : 0;
        }
        return 0;
    }

    /** The node accepted the send: clear the composer. Nothing else clears it (a refused draft stays editable). */
    public void draftAccepted() {
        draft.setText("");
        clearDraftMarks();
    }

    /**
     * Place a composed draft (OD-74.14): APPENDED after whatever the person has written — never over it — with exactly
     * the appended range highlighted, the composer focused and lit with {@code hint}. Nothing is sent.
     *
     * @return the appended range's start
     */
    public int appendDraft(long draftRef, String text, String hint) {
        String was = draft.getText();
        String sep = was.isBlank() ? "" : was.endsWith("\n\n") ? "" : was.endsWith("\n") ? "\n" : "\n\n";
        if (was.isBlank()) draft.setText("");
        int from = draft.getDocument().getLength() + sep.length();
        draft.append(sep + text);
        int to = draft.getDocument().getLength();
        clearDraftMarks();
        try {
            boundHighlight = draft.getHighlighter().addHighlight(from, to,
                    new DefaultHighlighter.DefaultHighlightPainter(UiTheme.accentWash(0.28f)));
            boundDraft = draftRef;
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        composerHint.setText(hint == null ? " " : hint);
        composerHint.setVisible(hint != null && !hint.isBlank());
        draft.setCaretPosition(to);
        draft.requestFocusInWindow();
        return from;
    }

    private void clearDraftMarks() {
        if (boundHighlight != null) draft.getHighlighter().removeHighlight(boundHighlight);
        boundHighlight = null;
        boundDraft = 0;
        composerHint.setVisible(false);
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

    /** Render the session's decision. Unchanged input changes nothing on screen. */
    public void render(LocalAgentState s) {
        // review S5: the header is the session's own — its launch folder, not the project open now — and it is shown
        // only once the session is bound (READY or later), never from a handshake that may yet be refused
        LocalAgentTranscript.Header h = shownHeader(s, store);
        String headerText = headerLine(h, s);
        if (s.equals(rendered) && Objects.equals(headerText, renderedHeader)) return;
        renderChips(h, s, headerText);
        String folder = h == null ? null : h.folder();
        header.setText(folder == null || folder.isBlank() ? " " : "Folder: " + shortFolder(folder)
                + (h.scratchForProject() ? " — " + SCRATCH_NOTE : ""));
        header.setToolTipText(folder == null ? null : UiText.tooltip(folder));
        header.setVisible(folder != null && !folder.isBlank());
        posture.setText(postureLine(h, s));
        boolean running = s.running();
        start.setEnabled(!running);
        start.setText(UiText.inert(startLabel(s)));
        stop.setEnabled(s.busy() || s.starting() || s.pendingDraft() != 0);
        send.setEnabled(!s.busy() && !"STOPPING".equals(s.phase()));
        settings.setVisible("SWITCHED_OFF".equals(s.verdict()) || "BUDGET_SPENT".equals(s.verdict()));
        newChat.setText(s.newChatOffered() ? "New chat (offered)" : "New chat");
        status.setText(phaseText(s));
        renderFullCue(s);
        revalidate();
        String words = s.detailRef() != 0 ? store.text(s.detailRef()) : s.stderrRef() != 0
                ? "Claude Code's last error line: " + store.text(s.stderrRef()) : "";
        detail.setText(words);
        detail.setVisible(!words.isBlank());

        boolean follow = following();
        view.render(items(s));
        if (follow) view.setCaretPosition(view.getDocument().getLength());
        rendered = s;
        renderedHeader = headerText;
    }

    /** A theme change: the transcript re-styles (the one full re-render besides New chat and a trim). */
    public void themeChanged() {
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
                }
                case LocalAgentState.NOTE -> out.add(new TranscriptView.Item(key, TranscriptStyle.Role.NOTE,
                        "[" + e.status().toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + "] " + e.detail(),
                        e.status(), 0, 0, false));
                default -> { }
            }
        }
        return out;
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
            if (h.apiProvider() != null) chips.add(chip(h.apiProvider(), false));
        }
        chips.revalidate();
        chips.repaint();
    }

    private JLabel chip(String text, boolean strong) {
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
        return "Full Claude Code (preview) · " + mode + ": your tools and permissions"
                + (cue == LocalAgentState.FullCue.RUNNING ? " — running" : " — on: it runs at the next Start");
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
            draftScroll.setBorder(draftBorder);
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
        draftScroll.setBorder(BorderFactory.createLineBorder(c, s.strongCue() ? 3 : 2));
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
        if (h.apiProvider() != null) sb.append(" · ").append(h.apiProvider());
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
        if (!s.posture().isFull()) return DEFAULT_POSTURE_NOTE;
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
