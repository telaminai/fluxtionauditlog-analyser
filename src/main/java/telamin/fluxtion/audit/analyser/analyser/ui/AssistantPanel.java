package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantState;

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
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The onboard assistant's one surface (spec-onboard-assistant-journeys.md §3, OA-1/OA-2). It RENDERS the session's
 * {@link AssistantState} and the transcript text that state names, and it REPORTS what the person did — Send, Cancel,
 * New chat, Pop out/Dock — as intents. Whether any of that may happen is the {@code assistantLoop} node's decision.
 *
 * <p>There is exactly one of these, whichever host shows it: the side tab or the assistant window reparent this same
 * component, so the draft, the transcript, the selection and the scroll position move with it, and there is never a
 * second composer that could send a second request.
 *
 * <p><b>Live and demonstration.</b> The header states the mode in words (§3): <i>Live assistant</i>, or — while a walk
 * with dialogue shows — <i>Simulated conversation</i>, <i>Recorded conversation</i> or <i>Edited recorded
 * conversation</i>. In a demonstration the composer is hidden; there is no way to send from it.
 */
public final class AssistantPanel extends JPanel {

    /** What the person did, reported to the frame, which turns each into a session fact. */
    public interface Intents {
        void send(String draft);

        void cancel();

        void newChat();

        void copyPrompt(String draft);

        void host(boolean docked);

        void configureProvider();

        void connectCliAssistant();

        void showAnalyser();

        void askAboutEvidence();
    }

    private final AssistantTranscript transcript;
    private Intents intents;

    private final JLabel mode = new JLabel("Live assistant");
    private final JLabel route = new JLabel();
    private final JLabel basis = new JLabel();
    private final JLabel status = new JLabel(" ");
    private final JTextPane view = new JTextPane();
    private final JScrollPane viewScroll = new JScrollPane(view);
    private final JTextArea draft = new JTextArea(3, 40);
    private final JButton send = new JButton("Send");
    private final JButton cancel = new JButton("Cancel");
    private final JButton copyPrompt = new JButton("Copy prompt");
    private final JButton newChat = new JButton("New chat");
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
    private AssistantState lastState = AssistantState.IDLE;
    private Demo lastDemo;

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

        JPanel header = new JPanel(new BorderLayout(6, 2));
        mode.setFont(mode.getFont().deriveFont(Font.BOLD));
        mode.getAccessibleContext().setAccessibleName("Assistant mode");
        route.getAccessibleContext().setAccessibleName("Assistant provider");
        basis.getAccessibleContext().setAccessibleName("Workspace this conversation is about");
        status.getAccessibleContext().setAccessibleName("Assistant status");
        JPanel titles = new JPanel(new BorderLayout(6, 0));
        titles.add(mode, BorderLayout.WEST);
        titles.add(route, BorderLayout.CENTER);
        JPanel hostActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        hostActions.add(newChat);
        hostActions.add(showAnalyser);
        hostActions.add(hostButton);
        titles.add(hostActions, BorderLayout.EAST);
        header.add(titles, BorderLayout.NORTH);
        header.add(basis, BorderLayout.CENTER);
        header.add(status, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);

        view.setEditable(false);
        view.getAccessibleContext().setAccessibleName("Conversation");
        add(viewScroll, BorderLayout.CENTER);

        draft.setLineWrap(true);
        draft.setWrapStyleWord(true);
        draft.getAccessibleContext().setAccessibleName("Your question");
        composer.add(new JScrollPane(draft), BorderLayout.CENTER);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        buttons.add(copyPrompt);
        buttons.add(cancel);
        buttons.add(send);
        composer.add(buttons, BorderLayout.SOUTH);
        noProvider.add(new JLabel("No provider is configured — nothing is sent from here."));
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

        send.addActionListener(e -> onSend());
        cancel.addActionListener(e -> { if (intents != null) intents.cancel(); });
        newChat.addActionListener(e -> { if (intents != null) intents.newChat(); });
        copyPrompt.addActionListener(e -> { if (intents != null) intents.copyPrompt(draft.getText().trim()); });
        hostButton.addActionListener(e -> { if (intents != null) intents.host(!docked); });
        showAnalyser.addActionListener(e -> { if (intents != null) intents.showAnalyser(); });
        configure.addActionListener(e -> { if (intents != null) intents.configureProvider(); });
        connectCli.addActionListener(e -> { if (intents != null) intents.connectCliAssistant(); });
        askAboutEvidence.addActionListener(e -> { if (intents != null) intents.askAboutEvidence(); });
        cancel.setEnabled(false);

        // Enter sends; Shift+Enter is a newline. While an input method is composing, Enter belongs to it (§3).
        draft.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "assistant-send");
        draft.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK),
                "insert-break");
        draft.getActionMap().put("assistant-send", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (composing()) return;
                onSend();
            }
        });
    }

    public void setIntents(Intents intents) {
        this.intents = intents;
    }

    /** Whether a usable provider is configured, read when rendering (it is configuration, not a session decision). */
    public void setProviderConfigured(Supplier<Boolean> configured) {
        this.providerConfiguredSupplier = configured == null ? () -> false : configured;
    }

    /** The route line: the configured provider and model, or that none is configured (§3). */
    public void setRoute(String text) {
        route.setText(text == null ? "" : text);
    }

    /** An input method is mid-composition: the committed text is shorter than the document. */
    private boolean composing() {
        var requests = draft.getInputMethodRequests();
        return requests != null && requests.getCommittedTextLength() != draft.getDocument().getLength();
    }

    private void onSend() {
        String text = draft.getText().trim();
        if (text.isEmpty() || intents == null || !send.isEnabled()) return;
        intents.send(text);
    }

    /** The node accepted the send: the composer is cleared for the next question. Nothing else clears the draft. */
    public void draftAccepted() {
        draft.setText("");
    }

    /**
     * Explain (§3): put a question in the composer WITHOUT sending, never overwriting what the person is writing, and
     * focus it. The frame reveals whichever host is active first.
     */
    public void primeDraft(String question) {
        if (draft.getText().isBlank()) draft.setText(question);
        draft.requestFocusInWindow();
    }

    public String draftText() {
        return draft.getText();
    }

    /** Where the conversation is scrolled to, so a move between hosts can put it back. */
    public java.awt.Point scrollPosition() {
        return viewScroll.getViewport().getViewPosition();
    }

    /** Put the conversation back where it was (after a move between hosts has laid it out again). */
    public void restoreScrollPosition(java.awt.Point p) {
        if (p == null) return;
        SwingUtilities.invokeLater(() -> viewScroll.getViewport().setViewPosition(p));
    }

    /** For tests and the host: the composer. */
    JTextArea composerArea() {
        return draft;
    }

    JTextPane conversationView() {
        return view;
    }

    JButton sendButton() {
        return send;
    }

    JButton cancelButton() {
        return cancel;
    }

    JButton hostButton() {
        return hostButton;
    }

    JButton askAboutEvidenceButton() {
        return askAboutEvidence;
    }

    // ---- rendering ----------------------------------------------------------------------------------------------------

    /**
     * Render live mode from the session's decision. {@code demo} is null in live mode; otherwise the demonstration's
     * header and its already-rendered lines (OA-4). Unchanged input changes nothing on screen.
     */
    public void render(AssistantState state, Demo demo) {
        lastState = state;
        lastDemo = demo;
        boolean configured = Boolean.TRUE.equals(providerConfiguredSupplier.get());
        List<String> demoLines = demo == null ? null : demo.lines();
        if (state.equals(rendered) && java.util.Objects.equals(demoLines, renderedDemo) && configured == providerConfigured
                && docked == state.docked()) {
            return;
        }
        providerConfigured = configured;
        docked = state.docked();
        hostButton.setText(docked ? "Pop out" : "Dock");
        hostButton.getAccessibleContext().setAccessibleName(docked ? "Pop out the assistant" : "Dock the assistant");
        showAnalyser.setVisible(!docked);

        boolean inDemo = demo != null;
        mode.setText(inDemo ? demo.mode() : "Live assistant");
        mode.getAccessibleContext().setAccessibleDescription(mode.getText());
        composer.setVisible(!inDemo);
        demoBar.setVisible(inDemo);
        noProvider.setVisible(!inDemo && !configured);
        newChat.setEnabled(!inDemo);
        basis.setText(inDemo ? demo.walkTitle() : (state.basis().isBlank() ? " " : "About: " + state.basis()));
        send.setEnabled(!inDemo && configured && !state.busy() && !state.frozen());
        cancel.setEnabled(!inDemo && state.busy());
        copyPrompt.setEnabled(!inDemo && !state.busy());
        status.setText(inDemo ? demo.status() : phaseLine(state));

        boolean follow = following();
        java.awt.Point at = viewScroll.getViewport().getViewPosition();
        StyledDocument doc = view.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
            if (inDemo && !showConversation.isSelected()) {
                doc.insertString(doc.getLength(), "(conversation hidden — the walk's strip still steps it)\n", plain());
            } else if (inDemo) {
                for (String line : demoLines) doc.insertString(doc.getLength(), line + "\n", plain());
            } else {
                for (AssistantState.Entry e : state.visibleEntries()) renderEntry(doc, e, state);
            }
        } catch (BadLocationException ex) {
            throw new IllegalStateException(ex);
        }
        // new content scrolls only when the person was already following the bottom (§3)
        if (follow) {
            view.setCaretPosition(doc.getLength());
        } else {
            SwingUtilities.invokeLater(() -> viewScroll.getViewport().setViewPosition(at));
        }
        rendered = state;
        renderedDemo = demoLines;
    }

    /** A demonstration's projection, built by the frame from walkPlayback's frozen definition (OA-4). */
    public record Demo(String mode, String walkTitle, String status, List<String> lines) {
        public Demo {
            lines = List.copyOf(lines);
        }
    }

    private boolean following() {
        JScrollBar bar = viewScroll.getVerticalScrollBar();
        return bar.getValue() + bar.getVisibleAmount() >= bar.getMaximum() - 4;
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

    private void renderEntry(StyledDocument doc, AssistantState.Entry e, AssistantState state) throws BadLocationException {
        switch (e.kind()) {
            case AssistantState.USER -> {
                doc.insertString(doc.getLength(), "You\n", bold());
                doc.insertString(doc.getLength(), transcript.text(e.id()) + "\n\n", plain());
            }
            case AssistantState.ANSWER -> {
                doc.insertString(doc.getLength(), "Assistant — answer (the model's words, not a verified result)\n", bold());
                doc.insertString(doc.getLength(), withoutActionBlocks(transcript.text(e.id())) + "\n\n", plain());
            }
            case AssistantState.ACTION -> {
                String verb = e.verb().isBlank() ? "action" : e.verb();
                String outcome = switch (e.status()) {
                    case "OK" -> "result";
                    case "REFUSED" -> "REFUSED";
                    case "NOT_RUN" -> "NOT RUN";
                    default -> "running…";
                };
                doc.insertString(doc.getLength(), "▸ " + verb + " — " + outcome
                        + (e.detail().isBlank() ? "" : " (" + e.detail() + ")") + "\n", "REFUSED".equals(e.status())
                        || "NOT_RUN".equals(e.status()) ? warning() : bold());
                if (e.result() != 0) {
                    String json = transcript.text(e.result());
                    doc.insertString(doc.getLength(), "   " + (json.length() > 4000 ? json.substring(0, 4000)
                            + " … (" + json.length() + " characters; the whole result was given to the model)" : json)
                            + "\n\n", mono());
                } else {
                    doc.insertString(doc.getLength(), "\n", plain());
                }
            }
            case AssistantState.NOTE -> doc.insertString(doc.getLength(), "[" + e.status().toLowerCase(java.util.Locale.ROOT)
                    .replace('_', ' ') + "] " + e.detail() + "\n\n", warning());
            default -> { }
        }
    }

    /** An answer's fenced action blocks are shown as the action entries that follow it, not as prose. */
    static String withoutActionBlocks(String reply) {
        return reply.replaceAll("(?s)```analyser-action\\s.*?```", "[an action was requested — its result follows]").strip();
    }

    private static SimpleAttributeSet plain() {
        return new SimpleAttributeSet();
    }

    private static SimpleAttributeSet bold() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setBold(a, true);
        return a;
    }

    private static SimpleAttributeSet mono() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setFontFamily(a, Font.MONOSPACED);
        StyleConstants.setFontSize(a, 11);
        return a;
    }

    private static SimpleAttributeSet warning() {
        SimpleAttributeSet a = new SimpleAttributeSet();
        StyleConstants.setBold(a, true);
        StyleConstants.setForeground(a, new Color(0xB0, 0x5A, 0x00));
        return a;
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
