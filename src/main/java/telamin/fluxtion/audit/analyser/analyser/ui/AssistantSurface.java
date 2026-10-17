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
    final JTextArea draft = new JTextArea(3, 40);
    final JScrollPane draftScroll = new JScrollPane(draft);
    /** The full-posture badge above the composer (owner request, OD-74.27). */
    final JLabel fullBadge = UiText.label(" ");
    /** The hint a composed draft lights the composer with. */
    final WrapLine composerHint = new WrapLine(" ");
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

    /** The composed draft the composer holds (its appended range), the mode that placed it, and its highlight. */
    private long boundDraft;
    private AssistantMode boundMode;
    private Object boundHighlight;

    AssistantSurface() {
        draft.setLineWrap(true);
        draft.setWrapStyleWord(true);
        draft.getAccessibleContext().setAccessibleName("Your message to the assistant");
        draftBorder = draftScroll.getBorder();
        chips.setOpaque(false);
        chips.getAccessibleContext().setAccessibleName("Assistant session");
        fullBadge.setVisible(false);
        composerHint.setVisible(false);
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
                if (send.isEnabled()) onSend.run();
            }
        });
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
            boundMode = mode;
        } catch (BadLocationException e) {
            throw new IllegalStateException(e);
        }
        composerHint.setText(hint == null ? " " : hint);
        composerHint.setVisible(hint != null && !hint.isBlank());
        draft.setCaretPosition(to);
        draft.requestFocusInWindow();
        return from;
    }

    /** The composed draft's binding for {@code mode} while any of its appended text remains; 0 otherwise. */
    long binding(AssistantMode mode) {
        if (boundDraft == 0 || boundHighlight == null || boundMode != mode) return 0;
        for (Highlighter.Highlight h : draft.getHighlighter().getHighlights()) {
            if (h == boundHighlight) return h.getEndOffset() > h.getStartOffset() ? boundDraft : 0;
        }
        return 0;
    }

    /** The node accepted the send: clear the composer. Nothing else clears it (a refused draft stays editable). */
    void accepted() {
        draft.setText("");
        clearDraftMarks();
    }

    void clearDraftMarks() {
        if (boundHighlight != null) draft.getHighlighter().removeHighlight(boundHighlight);
        boundHighlight = null;
        boundDraft = 0;
        boundMode = null;
        composerHint.setVisible(false);
    }

    /** The composer's border, or the full-posture cue's accent border. */
    void cueBorder(java.awt.Color c, int width) {
        draftScroll.setBorder(c == null ? draftBorder : BorderFactory.createLineBorder(c, width));
    }
}
