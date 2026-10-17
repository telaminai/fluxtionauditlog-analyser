package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import javax.swing.*;
import java.awt.*;

/**
 * M74 P2b (the simplified Move, owner 2026-10-17): the person switched projects while Claude Code runs. Three answers —
 * <i>Stop, and get Start ready in the new project</i>, <i>Keep it running here</i>, <i>Cancel</i>. There is no handoff
 * report (deferred until after P3). It RENDERS the question; {@code localAgentSession} and {@code sessionBoundary}
 * decide what each answer does. Closing it is Cancel.
 */
public final class LocalAgentSwitchDialog {

    private LocalAgentSwitchDialog() {
    }

    static final Object[] OPTIONS = {MainFrame.SWITCH_STOP, MainFrame.SWITCH_KEEP, MainFrame.SWITCH_CANCEL};

    /** The question's words: what runs where, and what each answer does. */
    static String message(String current, String target) {
        String here = current == null || current.isBlank() ? "your own settings" : current;
        String there = target == null || target.isBlank() ? "your own settings (no project)" : target;
        return "Claude Code is running in " + here + ". You asked to switch to " + there + ".\n\n"
                + "• Stop, and get Start ready in the new project — the session ends; nothing starts until you press "
                + "Start there, and the new folder is confirmed then.\n"
                + "• Keep it running here — the switch happens; this session keeps working in its own folder, and its "
                + "context describes " + here + ".\n"
                + "• Cancel — no switch.";
    }

    static SessionEvents.SwitchChoice choiceOf(int answer) {
        return switch (answer) {
            case 0 -> SessionEvents.SwitchChoice.STOP_AND_ARM;
            case 1 -> SessionEvents.SwitchChoice.KEEP;
            default -> SessionEvents.SwitchChoice.CANCEL;
        };
    }

    public static SessionEvents.SwitchChoice ask(Component parent, String current, String target) {
        JTextArea body = Fluid.text(message(current, target));
        body.setColumns(56);
        JComponent message = body;                         // a component: its text is never HTML
        int answer = JOptionPane.showOptionDialog(parent, message, "Claude Code is running",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, OPTIONS, OPTIONS[2]);
        return choiceOf(answer);
    }
}
