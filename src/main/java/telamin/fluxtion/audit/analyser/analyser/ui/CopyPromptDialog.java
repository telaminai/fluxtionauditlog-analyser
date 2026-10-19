package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder;

import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * SF-1 — the body of the Copy prompt dialog. It renders {@link PromptBuilder#copyPrompt} and nothing else: the first
 * copy is made WITHOUT the token, and the token box (shown only while REST is on, unticked every time) recomposes
 * and re-copies. Built apart from the dialog so a headless test can check what reached the clipboard.
 */
final class CopyPromptDialog {

    static final String PARTS = "copy-prompt-parts";
    static final String TEXT = "copy-prompt-text";
    static final String TOKEN = "copy-prompt-token";

    private CopyPromptDialog() {
    }

    /**
     * @param compose   the composition, given whether the person ticked the token box
     * @param clipboard where each composed text is copied
     */
    static JPanel body(Function<Boolean, PromptBuilder.CopiedPrompt> compose, Consumer<String> clipboard) {
        JTextArea parts = new JTextArea(2, 70);
        parts.setName(PARTS);
        parts.setEditable(false);
        parts.setLineWrap(true);
        parts.setWrapStyleWord(true);
        parts.setOpaque(false);
        JTextArea shown = new JTextArea(18, 70);
        shown.setName(TEXT);
        shown.setEditable(false);
        shown.setLineWrap(true);
        Consumer<PromptBuilder.CopiedPrompt> show = c -> {
            clipboard.accept(c.text());
            parts.setText(c.sentence());
            shown.setText(c.text());
            shown.setCaretPosition(0);
        };
        PromptBuilder.CopiedPrompt first = compose.apply(false);       // never the token until it is asked for
        show.accept(first);
        JPanel body = new JPanel(new BorderLayout(0, 6));
        body.add(parts, BorderLayout.NORTH);
        body.add(new JScrollPane(shown), BorderLayout.CENTER);
        if (first.tokenOffered()) {
            JCheckBox token = new JCheckBox(PromptBuilder.TOKEN_BOX, false);
            token.setName(TOKEN);
            token.addActionListener(e -> show.accept(compose.apply(token.isSelected())));
            body.add(token, BorderLayout.SOUTH);
        }
        return body;
    }
}
