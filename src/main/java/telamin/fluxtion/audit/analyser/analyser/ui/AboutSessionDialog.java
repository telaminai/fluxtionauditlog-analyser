package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.KeyEvent;

/**
 * M78 D1 (spec-quiet-assistant-panel.md §4.3): <i>About this session…</i>, opened from the assistant's ⋯. One dialog that
 * holds what the header used to repeat on every use — the assistant and provider, the model, the posture with the
 * not-local-only sentence, the billing and the plan or spend, the session's folder and the fork warning, the basis, and
 * the limits in full. It renders the words it is given (the panel's, from the snapshot) and composes nothing; the text is
 * plain (a model name or folder is shown as its characters, never as markup).
 */
final class AboutSessionDialog {

    static final String TITLE = "About this session";

    private AboutSessionDialog() {
    }

    /** Show {@code text}, modeless, over {@code owner}'s window; Escape or Close dismisses it. */
    static JDialog show(Component owner, String text) {
        return show(owner, text, null, null);
    }

    static JDialog show(Component owner, String text, String actionLabel, Runnable action) {
        Window w = owner == null ? null : SwingUtilities.getWindowAncestor(owner);
        JDialog d = new JDialog(w, TITLE);
        d.setModal(false);
        d.setContentPane(content(text, d, actionLabel, action));
        d.pack();
        d.setLocationRelativeTo(owner);
        d.setVisible(true);
        return d;
    }

    static JComponent content(String text, JDialog d) {
        return content(text, d, null, null);
    }

    static JComponent content(String text, JDialog d, String actionLabel, Runnable action) {
        JTextArea a = new JTextArea(text, 14, 52);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.putClientProperty("html.disable", Boolean.TRUE);
        a.setCaretPosition(0);
        a.getAccessibleContext().setAccessibleName(TITLE);
        JScrollPane sp = new JScrollPane(a);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.setPreferredSize(new Dimension(520, 320));
        JButton close = UiText.button("Close");
        close.addActionListener(e -> d.dispose());
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        if (actionLabel != null && action != null) {
            JButton act = UiText.button(actionLabel);
            act.addActionListener(e -> { action.run(); d.dispose(); });
            buttons.add(act);
        }
        buttons.add(close);
        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(BorderFactory.createEmptyBorder(12, 12, 8, 12));
        root.add(sp, BorderLayout.CENTER);
        root.add(buttons, BorderLayout.SOUTH);
        root.registerKeyboardAction(e -> d.dispose(), KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
        d.getRootPane().setDefaultButton(close);
        return root;
    }
}
