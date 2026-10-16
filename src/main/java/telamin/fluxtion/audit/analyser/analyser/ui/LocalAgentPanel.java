package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;

import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * M74 (spec-claude-code-provider.md §5.2): the Assistant tab while it drives Claude Code. It RENDERS the session's
 * {@link LocalAgentState} and the words that state names in the bounded store, and it REPORTS what the person did —
 * Start, Stop, Send, New chat, turn on REST — as intents. Whether any of that may happen is {@code localAgentSession}'s
 * decision, and the process belongs to the adapter: this class never starts, stops or reads one.
 *
 * <p>Copy prompt and Connect a CLI assistant are not here: they exist for external agents.
 */
public final class LocalAgentPanel extends JPanel {

    /** What the person did, reported to the frame, which turns each into a session fact. */
    public interface Intents {
        void start();

        void stop();

        void send(String draft);

        void newChat();

        void turnOnRest();

        void openSettings();
    }

    /** The posture line of the default posture (§5.2). */
    static final String DEFAULT_POSTURE_NOTE = "analyser assistant — your skills and hooks are not loaded; enable full "
            + "Claude Code for those";
    /** §4.8: an external terminal is invisible to the lock. */
    static final String LOCK_WARNING = "Don't resume this conversation in a terminal while it is open here — the history "
            + "would fork.";
    static final String CONTACTS_ANTHROPIC = "Start contacts Anthropic (sign-in check, MCP connect) before your first Send; "
            + "no record content is sent until you press Send.";

    private final LocalAgentTranscript store;
    private Intents intents;

    private final JLabel header = new JLabel(" ");
    private final JLabel posture = new JLabel(DEFAULT_POSTURE_NOTE);
    private final JLabel lockWarning = new JLabel(LOCK_WARNING);
    private final JLabel status = new JLabel(" ");
    private final JTextArea detail = new JTextArea(2, 40);
    private final JTextPane view = new JTextPane();
    private final JScrollPane viewScroll = new JScrollPane(view);
    private final JTextArea draft = new JTextArea(3, 40);
    private final JButton start = new JButton("Start Claude Code");
    private final JButton stop = new JButton("Stop");
    private final JButton newChat = new JButton("New chat");
    private final JButton send = new JButton("Send");
    private final JButton turnOnRest = new JButton("Turn on the REST transport");
    private final JButton settings = new JButton("Settings ▸ Assistant");

    private LocalAgentState rendered;
    private String renderedHeader;

    public LocalAgentPanel(LocalAgentTranscript store) {
        super(new BorderLayout(4, 4));
        this.store = store;
        getAccessibleContext().setAccessibleName("Claude Code");
        header.setFont(header.getFont().deriveFont(Font.BOLD));
        header.getAccessibleContext().setAccessibleName("Claude Code session");
        status.getAccessibleContext().setAccessibleName("Claude Code status");
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setOpaque(false);
        detail.setVisible(false);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        actions.add(start);
        actions.add(stop);
        actions.add(newChat);
        actions.add(turnOnRest);
        actions.add(settings);
        for (JComponent c : new JComponent[]{header, posture, lockWarning, actions, status, detail}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            top.add(c);
        }
        add(top, BorderLayout.NORTH);

        view.setEditable(false);
        view.getAccessibleContext().setAccessibleName("Claude Code conversation");
        add(viewScroll, BorderLayout.CENTER);

        draft.setLineWrap(true);
        draft.setWrapStyleWord(true);
        draft.getAccessibleContext().setAccessibleName("Your message to Claude Code");
        JPanel composer = new JPanel(new BorderLayout(4, 4));
        composer.add(new JScrollPane(draft), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(send);
        composer.add(buttons, BorderLayout.SOUTH);
        add(composer, BorderLayout.SOUTH);

        start.addActionListener(e -> { if (intents != null) intents.start(); });
        stop.addActionListener(e -> { if (intents != null) intents.stop(); });
        newChat.addActionListener(e -> { if (intents != null) intents.newChat(); });
        turnOnRest.addActionListener(e -> { if (intents != null) intents.turnOnRest(); });
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
        turnOnRest.setVisible(false);
        stop.setEnabled(false);
    }

    public void setIntents(Intents intents) {
        this.intents = intents;
    }

    private void onSend() {
        String text = draft.getText().trim();
        if (text.isEmpty() || intents == null) return;
        intents.send(text);      // refused or not, the node says so; the draft stays until it is accepted
    }

    /** The node accepted the send: clear the composer. Nothing else clears it (a refused draft stays editable). */
    public void draftAccepted() {
        draft.setText("");
    }

    /** Put words in the composer without sending, never over what the person is writing. */
    public void primeDraft(String text) {
        if (draft.getText().isBlank()) draft.setText(text);
        draft.requestFocusInWindow();
    }

    JTextArea composerArea() {
        return draft;
    }

    JTextPane conversationView() {
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

    /** Render the session's decision. Unchanged input changes nothing on screen. */
    public void render(LocalAgentState s) {
        // review S5: the header is the session's own — its launch folder, not the project open now — and it is shown
        // only once the session is bound (READY or later), never from a handshake that may yet be refused
        String headerText = headerLine(shownHeader(s, store), s);
        if (s.equals(rendered) && Objects.equals(headerText, renderedHeader)) return;
        header.setText(headerText);
        boolean running = s.running();
        start.setEnabled(!running);
        start.setText(s.resumable() ? "Start Claude Code (resume)" : "Start Claude Code");
        stop.setEnabled(s.busy() || s.starting() || s.pendingDraft() != 0);
        send.setEnabled(!s.busy() && !"STOPPING".equals(s.phase()));
        turnOnRest.setVisible("REST_OFF".equals(s.verdict()));
        settings.setVisible("SWITCHED_OFF".equals(s.verdict()) || "BUDGET_SPENT".equals(s.verdict()));
        newChat.setText(s.newChatOffered() ? "New chat (offered)" : "New chat");
        status.setText(phaseText(s));
        String words = s.detailRef() != 0 ? store.text(s.detailRef()) : s.stderrRef() != 0
                ? "Claude Code's last error line: " + store.text(s.stderrRef()) : "";
        detail.setText(words);
        detail.setVisible(!words.isBlank());

        boolean follow = following();
        StyledDocument doc = view.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            for (LocalAgentState.Entry e : s.entries()) renderEntry(doc, e);
        } catch (BadLocationException ex) {
            throw new IllegalStateException(ex);
        }
        if (follow) view.setCaretPosition(doc.getLength());
        rendered = s;
        renderedHeader = headerText;
    }

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
        if (h != null && h.model() != null) sb.append(" · ").append(h.model());
        sb.append(" · analyser assistant (default posture)");
        if (folder != null && !folder.isBlank()) sb.append(" · ").append(folder);
        if (h != null && h.cliVersion() != null) sb.append(" · CLI ").append(h.cliVersion());
        if (h != null && h.billing() != null && !h.billing().isBlank()) sb.append(" · ").append(h.billing());
        if (h != null && h.apiProvider() != null) sb.append(" · ").append(h.apiProvider());
        if (s.spentMicros() > 0 || s.budgetMicros() > 0) {
            sb.append(" · ").append(s.costUnderReported() ? "at least " : "").append("US$").append(usd(s.spentMicros()))
                    .append(" of US$").append(usd(s.budgetMicros()));
        }
        return sb.toString();
    }

    static String phaseText(LocalAgentState s) {
        String phase = switch (s.phase()) {
            case "SPAWNING", "HANDSHAKE" -> "Starting Claude Code…";
            case "BINDING" -> s.bindingElapsedMs() >= 2_000 ? "connecting to the analyser bridge…" : "Starting Claude Code…";
            case "READY" -> s.pendingDraft() != 0 ? "Sending your message…" : "Ready";
            case "BUSY" -> "Claude is answering…";
            case "STOPPING" -> "Stopping the turn…";
            default -> "Not running. " + CONTACTS_ANTHROPIC;
        };
        String why = s.reason().isBlank() ? "" : " — " + s.reason();
        return phase + why;
    }

    private static String usd(long micros) {
        return BigDecimal.valueOf(micros, 6).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private void renderEntry(StyledDocument doc, LocalAgentState.Entry e) throws BadLocationException {
        switch (e.kind()) {
            case LocalAgentState.USER -> {
                doc.insertString(doc.getLength(), "You\n", bold());
                doc.insertString(doc.getLength(), store.text(e.id()) + "\n\n", plain());
            }
            case LocalAgentState.ANSWER -> {
                doc.insertString(doc.getLength(), "Claude Code" + ("STREAMING".equals(e.status()) ? " (answering…)" : "")
                        + " — the model's words, not a verified result\n", bold());
                doc.insertString(doc.getLength(), store.text(e.id()) + "\n\n", plain());
            }
            case LocalAgentState.NOTE -> doc.insertString(doc.getLength(), "[" + e.status().toLowerCase(java.util.Locale.ROOT)
                    .replace('_', ' ') + "] " + e.detail() + "\n\n", warning());
            default -> { }
        }
    }

    private boolean following() {
        JScrollBar bar = viewScroll.getVerticalScrollBar();
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 4;
    }

    private static SimpleAttributeSet plain() {
        return new SimpleAttributeSet();
    }

    private static SimpleAttributeSet bold() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setBold(a, true);
        return a;
    }

    private static SimpleAttributeSet warning() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setBold(a, true);
        StyleConstants.setForeground(a, new Color(0xB0, 0x5A, 0x00));
        return a;
    }
}
