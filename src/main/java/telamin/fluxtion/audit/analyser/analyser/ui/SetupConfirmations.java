package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.mcp.McpRegistrar;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.Component;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;

/**
 * M77-3 (spec-ai-setup-wizard.md §9.2, §9.3, G7): the two confirmations the external client path raises — turning on
 * External MCP/REST access, and registering with a client — each under the SafeChoice rule: the risky choice alone at the
 * left and never default-capable, <i>Cancel</i> the default at the right, and Enter, Escape and the window's close all
 * Cancel. They replace two stock OK-default {@code JOptionPane}s (robot E/02–E/03). Every word is {@link AiSetupLines}'.
 */
final class SetupConfirmations {

    private SetupConfirmations() {
    }

    /** §9.2: <i>Turn on External MCP/REST access?</i> — the facts of what it opens, stated where the consent is given. */
    static SafeChoice.Asked externalAccess(Component parent, boolean modal) {
        JTextArea a = plain(AiSetupLines.ACCESS_CONFIRM_TEXT);
        a.getAccessibleContext().setAccessibleName("What turning on access does");
        return SafeChoice.build(parent, AiSetupLines.ACCESS_CONFIRM_TITLE, a, UIManager.getIcon("OptionPane.warningIcon"),
                AiSetupLines.ACCESS_CONFIRM_TURN_ON, AiSetupLines.CANCEL, modal);
    }

    /**
     * §9.3: <i>Register the analyser with Claude Code</i> — the VERBATIM argument vectors that will run, in a read-only,
     * selectable, monospaced, wrapping area (S5: the same lists the performer then executes).
     */
    static SafeChoice.Asked register(Component parent, AiSetupState.Client client, boolean replace,
                                     List<List<String>> vectors, boolean modal) {
        boolean codex = client == AiSetupState.Client.CODEX;
        JPanel body = new JPanel();
        body.setOpaque(false);
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        JTextArea runs = plain(replace ? AiSetupLines.REPLACE_RUNS : AiSetupLines.REGISTER_RUNS);
        body.add(runs);
        body.add(javax.swing.Box.createVerticalStrut(UiTheme.SPACE_SM));
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < vectors.size(); i++) {
            lines.add((vectors.size() > 1 ? (i + 1) + ". " : "") + McpRegistrar.display(codex, vectors.get(i)));
        }
        JTextArea command = commandArea(String.join("\n", lines));
        body.add(command);
        body.add(javax.swing.Box.createVerticalStrut(UiTheme.SPACE_SM));
        body.add(plain(AiSetupLines.registerAfter(client)));
        for (Component c : body.getComponents()) ((JComponent) c).setAlignmentX(Component.LEFT_ALIGNMENT);
        return SafeChoice.build(parent, AiSetupLines.registerTitle(client), body,
                UIManager.getIcon("OptionPane.warningIcon"),
                replace ? AiSetupLines.REPLACE_BUTTON : AiSetupLines.REGISTER_BUTTON, AiSetupLines.CANCEL, modal);
    }

    /** A wrapping, read-only sentence: plain text, never HTML (SF-1). */
    static JTextArea plain(String text) {
        JTextArea a = new JTextArea(UiText.inert(text), 0, 52);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setFocusable(false);
        a.setBorder(BorderFactory.createEmptyBorder());
        return a;
    }

    /** The command: selectable and monospaced, wrapping inside its box rather than widening the dialog (§15). */
    static JTextArea commandArea(String text) {
        JTextArea a = new JTextArea(UiText.inert(text), 0, 52);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(false);
        Font base = UIManager.getFont("TextField.font");
        if (base == null) base = a.getFont();
        a.setFont(new Font(Font.MONOSPACED, Font.PLAIN, base.getSize()));
        a.setBorder(BorderFactory.createCompoundBorder(UIManager.getBorder("TextField.border"),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)));
        a.getAccessibleContext().setAccessibleName("The exact command");
        return a;
    }
}
