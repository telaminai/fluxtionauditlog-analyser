package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.border.Border;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * M74 UX (owner, 2026-10-17): the parts of the ONE assistant panel that both modes share — one transcript, one composer,
 * one row of header chips, one Start / Stop / Send / New chat. The in-app assistant and Claude Code render into these;
 * neither has a composer or a conversation of its own, so there is never a second place to type.
 *
 * <p>It decides nothing: what Send, Stop and New chat MEAN is the mode the session put the tab in, and the panel routes
 * each press to that mode's intents ({@link #onSend}, {@link #onStop}, {@link #onNewChat}). A composed draft is placed
 * by its mode and remembers that mode, so a draft placed for one assistant is never sent bound to the other's draft.
 */
final class AssistantSurface {

    final TranscriptView view = new TranscriptView();
    final JScrollPane viewScroll = new JScrollPane(view);
    final JTextArea draft = new JTextArea(3, 40) {
        @Override protected void paintComponent(java.awt.Graphics g) {
            super.paintComponent(g);
            UiTheme.paintPlaceholder(this, g, COMPOSER_PLACEHOLDER);
        }
    };
    /** Review M74 usability #15: the composer's keys, said where they apply while it is empty. */
    static final String COMPOSER_PLACEHOLDER = "Your question — Enter sends, Shift+Enter starts a new line";
    final JScrollPane draftScroll = new JScrollPane(draft);
    /** The full-posture badge above the composer (owner request, OD-74.27). */
    final JLabel fullBadge = UiText.label(" ");
    /** The hint a composed draft lights the composer with. */
    final WrapLine composerHint = new WrapLine(" ");
    /**
     * Final review #12 (owner decision, 2026-10-17): the small muted chip that says which log the composer's drafted
     * questions are about — "this log · opened 14:05" — in place of the "(log generation N)" the drafts used to carry.
     * It renders the snapshot's open log against each draft's binding (the node's, carried on its prime effect); the
     * model is told the binding with the turn it is sent.
     */
    final JLabel bindingChip = UiText.label(" ");
    final JButton start = new JButton("Start Claude Code");
    final JButton stop = new JButton("Stop");
    final JButton send = UiText.button("Send");
    final JButton newChat = new JButton("New chat");
    /** The header's chips: model, posture, billing, provider — whichever mode is in force. */
    final JPanel chips = new JPanel(new WrapLayout(FlowLayout.LEFT, 4, 3));
    final Border draftBorder;

    Runnable onSend = () -> { };
    Runnable onStop = () -> { };
    Runnable onNewChat = () -> { };
    Runnable onStart = () -> { };

    /**
     * Every composed draft the composer holds — its id, the mode that placed it, its highlight over the appended range.
     * Review S2 (P2, 2026-10-17): ALL of them, not only the newest: an earlier draft appended before a later one still
     * names records, so each is checked at Send.
     */
    private record Bound(long draftRef, AssistantMode mode, Object highlight, long logGeneration) { }
    private final java.util.List<Bound> bound = new java.util.ArrayList<>();

    AssistantSurface() {
        draft.setLineWrap(true);
        draft.setWrapStyleWord(true);
        draft.getAccessibleContext().setAccessibleName("Your message to the assistant");
        // review M74 usability #15: the same keys as the start page's box, said where they apply
        draft.getAccessibleContext().setAccessibleDescription(StartPromptView.KEY_HINT);

        draftBorder = draftScroll.getBorder();
        chips.setOpaque(false);
        chips.getAccessibleContext().setAccessibleName("Assistant session");
        fullBadge.setVisible(false);
        composerHint.setVisible(false);
        bindingChip.setVisible(false);
        bindingChip.setOpaque(false);
        bindingChip.setFont(bindingChip.getFont().deriveFont(bindingChip.getFont().getSize2D() - 1f));
        stop.setEnabled(false);
        send.addActionListener(e -> onSend.run());
        stop.addActionListener(e -> onStop.run());
        newChat.addActionListener(e -> onNewChat.run());
        start.addActionListener(e -> onStart.run());
        // Enter sends; Shift+Enter is a newline. While an input method is composing, Enter belongs to it.
        draft.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "assistant-send");
        draft.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.SHIFT_DOWN_MASK),
                "insert-break");
        draft.getActionMap().put("assistant-send", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (composing()) return;
                if (enterHeldSinceFocus) return;          // final review #7: the start page's Enter, still held
                if (send.isEnabled()) onSend.run();
            }
        });
        // final review #12: deleting a draft's words drops its binding, and the chip with it
        draft.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) {
                edited();
            }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) {
                edited();
                javax.swing.SwingUtilities.invokeLater(AssistantSurface.this::renderBindingChip);
            }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { }
        });
        installHistoryKeys();
        // final review #7: an Enter whose press began before the composer had the focus (the start page's Enter that
        // placed this draft, still held and auto-repeating) never sends; the composer's own next press does
        HeldEnter.install();
        draft.addFocusListener(new java.awt.event.FocusAdapter() {
            @Override public void focusGained(java.awt.event.FocusEvent e) {
                enterArrived(HeldEnter.down());
            }
        });
        draft.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) enterHeldSinceFocus = false;
            }
        });
    }

    // ---- owner, 2026-10-18: ↑ / ↓ recall the questions sent, as a terminal does -------------------------------------

    /**
     * The questions sent, per mode, newest last — in memory only: never saved, never sent anywhere, gone when the analyser
     * closes. A walk starts with ↑ on the composer's first line and ends when the person edits the text; ↓ past the newest
     * puts back the draft that was being typed. Plain ↑ / ↓ only: Shift and Alt keep their own meaning, and away from the
     * first (or last) line the caret moves as it always did, so multi-line editing is untouched.
     */
    private final java.util.Map<AssistantMode, java.util.List<String>> history = new java.util.EnumMap<>(AssistantMode.class);
    private AssistantMode historyMode = AssistantMode.IN_APP;
    /** The entry shown while walking, or -1 when the composer holds the person's own draft. */
    private int historyIndex = -1;
    private String historyDraft = "";
    private boolean recalling;
    static final int HISTORY_LIMIT = 100;

    /** The mode whose questions ↑ recalls — the mode the panel shows. A change ends a walk. */
    void historyMode(AssistantMode mode) {
        AssistantMode m = mode == null ? AssistantMode.IN_APP : mode;
        if (m == historyMode) return;
        historyMode = m;
        historyIndex = -1;
    }

    /** The questions sent in {@code mode}, oldest first (for the tests). */
    java.util.List<String> history(AssistantMode mode) {
        return java.util.List.copyOf(history.getOrDefault(mode, java.util.List.of()));
    }

    private void remember(AssistantMode mode, String sent) {
        if (sent == null || sent.isBlank()) return;
        java.util.List<String> h = history.computeIfAbsent(mode == null ? AssistantMode.IN_APP : mode,
                k -> new java.util.ArrayList<>());
        if (h.isEmpty() || !h.get(h.size() - 1).equals(sent)) h.add(sent);
        while (h.size() > HISTORY_LIMIT) h.remove(0);
    }

    private void edited() {
        if (!recalling) historyIndex = -1;         // the person's edit makes the text their draft
    }

    private void installHistoryKeys() {
        javax.swing.InputMap keys = draft.getInputMap(JComponent.WHEN_FOCUSED);
        Object upWas = keys.get(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0));
        Object downWas = keys.get(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0));
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), "assistant-history-previous");
        keys.put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), "assistant-history-next");
        // the caret's own actions, looked up when pressed (a theme switch replaces the look and feel's)
        draft.getActionMap().put("assistant-history-previous", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!historyPrevious()) caret(upWas, e);
            }
        });
        draft.getActionMap().put("assistant-history-next", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                if (!historyNext()) caret(downWas, e);
            }
        });
    }

    private void caret(Object name, ActionEvent e) {
        javax.swing.Action a = name == null ? null : draft.getActionMap().get(name);
        if (a != null) a.actionPerformed(e);
    }

    /** ↑: an older question, when the caret is on the first line. False: the key moves the caret as usual. */
    boolean historyPrevious() {
        java.util.List<String> h = history.getOrDefault(historyMode, java.util.List.of());
        if (h.isEmpty() || composing() || draft.getSelectionStart() != draft.getSelectionEnd() || !onFirstLine()) {
            return false;
        }
        if (historyIndex < 0) {
            historyDraft = draft.getText();
            show(h.get(h.size() - 1), h.size() - 1);
        } else if (historyIndex > 0) {
            show(h.get(historyIndex - 1), historyIndex - 1);
        } else {
            draft.setCaretPosition(0);                // the oldest: stay, at its start
        }
        return true;
    }

    /** ↓: a newer question, or the draft back past the newest, when walking and on the last line. */
    boolean historyNext() {
        java.util.List<String> h = history.getOrDefault(historyMode, java.util.List.of());
        if (historyIndex < 0 || composing() || draft.getSelectionStart() != draft.getSelectionEnd() || !onLastLine()) {
            return false;
        }
        if (historyIndex + 1 < h.size()) {
            show(h.get(historyIndex + 1), historyIndex + 1);
        } else {
            show(historyDraft, -1);
        }
        return true;
    }

    private void show(String text, int index) {
        recalling = true;
        try {
            draft.setText(text);
            draft.setCaretPosition(draft.getDocument().getLength());
        } finally {
            recalling = false;
        }
        historyIndex = index;
    }

    /** The caret is on the composer's first row (as wrapped on screen when laid out, else the first line of text). */
    private boolean onFirstLine() {
        return sameRow(draft.getCaretPosition(), 0, true);
    }

    private boolean onLastLine() {
        return sameRow(draft.getCaretPosition(), draft.getDocument().getLength(), false);
    }

    private boolean sameRow(int caret, int edge, boolean first) {
        if (draft.getWidth() > 0) {
            try {
                java.awt.geom.Rectangle2D a = draft.modelToView2D(caret);
                java.awt.geom.Rectangle2D b = draft.modelToView2D(edge);
                if (a != null && b != null) return Math.abs(a.getY() - b.getY()) < 1;
            } catch (BadLocationException ignored) {
                // fall back to the text's own lines
            }
        }
        String text = draft.getText();
        return first ? text.lastIndexOf('\n', Math.max(0, caret - 1)) < 0 || caret == 0
                : text.indexOf('\n', caret) < 0;
    }

    /** Whether the Enter now held was pressed before the composer gained the focus (final review #7). */
    private boolean enterHeldSinceFocus;

    /** The composer gained the focus while Enter was {@code held} (or not). Package-private for the tests. */
    void enterArrived(boolean held) {
        enterHeldSinceFocus = held;
    }

    /** An input method is mid-composition: the committed text is shorter than the document. */
    boolean composing() {
        var requests = draft.getInputMethodRequests();
        return requests != null && requests.getCommittedTextLength() != draft.getDocument().getLength();
    }

    /**
     * Place a composed draft for {@code mode} (OD-74.14): APPENDED after whatever the person has written — never over it
     * — with exactly the appended range highlighted, the composer focused and lit with {@code hint}. Nothing is sent.
     *
     * @return the appended range's start
     */
    int appendDraft(AssistantMode mode, long draftRef, String text, String hint) {
        return appendDraft(mode, draftRef, text, hint, 0);
    }

    /** As above, for a draft bound to log generation {@code logGeneration} (0: it names no log) — final review #12. */
    int appendDraft(AssistantMode mode, long draftRef, String text, String hint, long logGeneration) {
        replaceEarlier(mode, text);
        String was = draft.getText();
        String sep = was.isBlank() ? "" : was.endsWith("\n\n") ? "" : was.endsWith("\n") ? "\n" : "\n\n";
        if (was.isBlank()) draft.setText("");
        int from = draft.getDocument().getLength() + sep.length();
        // A Swing highlight ending at the document's end grows with every append; pin each earlier draft's range first,
        // so a later draft never extends an earlier one's (review S2: every draft keeps its own range)
        java.util.Map<Object, int[]> ranges = new java.util.HashMap<>();
        for (Highlighter.Highlight h : draft.getHighlighter().getHighlights()) {
            ranges.put(h, new int[]{h.getStartOffset(), h.getEndOffset()});
        }
        draft.append(sep + text);
        for (Bound b : bound) {
            int[] r = ranges.get(b.highlight());
            if (r == null) continue;
            try {
                draft.getHighlighter().changeHighlight(b.highlight(), r[0], r[1]);
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        }
        int to = draft.getDocument().getLength();
        try {
            Object highlight = draft.getHighlighter().addHighlight(from, to,
                    new DefaultHighlighter.DefaultHighlightPainter(UiTheme.accentWash(0.28f)));
            bound.add(new Bound(draftRef, mode, highlight, logGeneration));
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        composerHint.setText(hint == null ? " " : hint);
        composerHint.setForeground(UiTheme.noteForeground());      // review §2: a status note wears the note token
        composerHint.setVisible(hint != null && !hint.isBlank());
        renderBindingChip();
        draft.setCaretPosition(to);
        enterArrived(HeldEnter.down());       // final review #7: placed by a held Enter? (the focus may already be here)
        draft.requestFocusInWindow();
        return from;
    }

    /**
     * Robot walkthrough #1 (2026-10-18): asking again REPLACES rather than appends. Before a draft is placed, an earlier
     * composed draft with the same words, and any composed draft placed for the OTHER assistant (which this one can never
     * send — it would be refused as unknown), are taken out of the composer; the person's own words stay.
     */
    private void replaceEarlier(AssistantMode mode, String text) {
        String words = text == null ? "" : text.strip();
        for (Bound b : java.util.List.copyOf(bound)) {
            Highlighter.Highlight h = live(b.highlight());
            if (h == null) continue;
            int from = h.getStartOffset();
            int to = h.getEndOffset();
            String held;
            try {
                held = draft.getText(from, to - from);
            } catch (BadLocationException e) {
                continue;
            }
            if (b.mode() == mode && !held.strip().equals(words)) continue;
            draft.getHighlighter().removeHighlight(b.highlight());
            bound.remove(b);
            try {
                // the separator the earlier placement put before it goes with it
                int cut = from;
                String before = draft.getText(0, from);
                while (cut > 0 && before.charAt(cut - 1) == '\n') cut--;
                draft.getDocument().remove(cut, to - cut);
            } catch (BadLocationException e) {
                throw new IllegalStateException(e);
            }
        }
        if (draft.getText().isBlank()) draft.setText("");
    }

    private Highlighter.Highlight live(Object highlight) {
        for (Highlighter.Highlight h : draft.getHighlighter().getHighlights()) {
            if (h == highlight && h.getEndOffset() > h.getStartOffset()) return h;
        }
        return null;
    }

    /**
     * Every composed draft the composer still holds — any of its appended text remains — in the order placed; empty when
     * none. Review S2: all of them, and a draft placed for the OTHER mode too — the node in force does not know that id,
     * so it refuses it as unknown rather than send the other assistant's words unchecked. {@code mode} is kept for the
     * callers' symmetry; a draft is never silently dropped because of it.
     */
    java.util.List<Long> bindings(AssistantMode mode) {
        java.util.List<Long> out = new java.util.ArrayList<>();
        java.util.List<Object> live = new java.util.ArrayList<>();
        for (Highlighter.Highlight h : draft.getHighlighter().getHighlights()) {
            if (h.getEndOffset() > h.getStartOffset()) live.add(h);
        }
        for (Bound b : bound) {
            if (b.draftRef() != 0 && live.contains(b.highlight())) out.add(b.draftRef());
        }
        return out;
    }

    // ---- final review #12: the binding chip ----------------------------------------------------------------------------

    private boolean chipLogOpen;
    private long chipGeneration;
    private long chipOpenedAt;

    /** The snapshot's open log — whether one is open, its generation, when it was opened (epoch millis; 0 unknown). */
    void renderOpenLog(boolean logOpen, long generation, long openedAtMillis) {
        chipLogOpen = logOpen;
        chipGeneration = generation;
        chipOpenedAt = openedAtMillis;
        renderBindingChip();
    }

    /** The chip's words for drafts bound to {@code draftGeneration} against the open log; null when no draft is bound. */
    static String chipText(long draftGeneration, boolean logOpen, long generation, long openedAtMillis) {
        if (draftGeneration <= 0) return null;
        if (!logOpen || generation != draftGeneration) return "an earlier log · Send will refuse it — ask again";
        return openedAtMillis > 0 ? "this log · opened " + hhmm(openedAtMillis) : "this log";
    }

    /** The chip's accessible name: what a screen reader says for it — never the generation. */
    static String chipName(long draftGeneration, boolean logOpen, long generation, long openedAtMillis) {
        if (draftGeneration <= 0) return null;
        if (!logOpen || generation != draftGeneration) {
            return "The drafted question is about a log that is no longer open; Send will refuse it — ask again";
        }
        return "The drafted question is about the log open now" + (openedAtMillis > 0 ? ", opened at "
                + hhmm(openedAtMillis) : "");
    }

    static String hhmm(long epochMillis) {
        return java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(
                java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()));
    }

    /** The newest bound draft the composer still holds (any of its text remains), or 0. */
    private long boundGeneration() {
        java.util.List<Object> live = new java.util.ArrayList<>();
        for (Highlighter.Highlight h : draft.getHighlighter().getHighlights()) {
            if (h.getEndOffset() > h.getStartOffset()) live.add(h);
        }
        long g = 0;
        for (Bound b : bound) if (b.logGeneration() > 0 && live.contains(b.highlight())) g = b.logGeneration();
        return g;
    }

    void renderBindingChip() {
        long g = boundGeneration();
        String text = chipText(g, chipLogOpen, chipGeneration, chipOpenedAt);
        bindingChip.setVisible(text != null);
        bindingChip.setText(text == null ? " " : text);
        bindingChip.setForeground(text != null && text.startsWith("an earlier") ? UiTheme.noteForeground()
                : UiTheme.mutedForeground());
        bindingChip.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.controlBorder(), 1, true),
                BorderFactory.createEmptyBorder(1, 6, 1, 6)));
        bindingChip.getAccessibleContext().setAccessibleName(chipName(g, chipLogOpen, chipGeneration, chipOpenedAt));
    }

    /**
     * The node accepted the send in {@code mode}: the question joins that mode's ↑ history (in memory only) and the
     * composer is cleared. Nothing else clears it (a refused draft stays editable).
     */
    void accepted() {
        accepted(historyMode);
    }

    void accepted(AssistantMode mode) {
        remember(mode, draft.getText().strip());
        draft.setText("");
        historyIndex = -1;
        historyDraft = "";
        clearDraftMarks();
    }

    void clearDraftMarks() {
        for (Bound b : bound) draft.getHighlighter().removeHighlight(b.highlight());
        bound.clear();
        composerHint.setVisible(false);
        renderBindingChip();
    }

    /** The composer's border, or the full-posture cue's accent border. */
    void cueBorder(java.awt.Color c, int width) {
        draftScroll.setBorder(c == null ? draftBorder : BorderFactory.createLineBorder(c, width));
    }
}
