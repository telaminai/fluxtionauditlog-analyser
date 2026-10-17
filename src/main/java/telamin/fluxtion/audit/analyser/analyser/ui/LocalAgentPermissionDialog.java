package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * M74 P3 (spec-claude-code-provider.md §5.7): the ONE permission dialog. It renders what {@code localAgentPermission}
 * asked to be shown and reports the person's buttons; it decides nothing (rule 9) — it opens on
 * {@code ShowPermissionDialogEffect} and closes on {@code ClosePermissionDialogEffect}, and the 120 s deadline is the
 * node's, from the adapter's clock (the countdown here only shows it).
 *
 * <ul>
 *   <li><b>Deny is the default</b>: the default button, and Enter and Escape both deny; closing the window denies.</li>
 *   <li><b>Allow once</b> has no mnemonic and is enabled only {@link #ALLOW_DELAY_MS} after the dialog appears, so a
 *       keystroke meant for the composer cannot approve. There is no <i>Always allow</i>: a suggestion is never shown.</li>
 *   <li><b>The input is data</b>: a read-only, monospaced text area (never an HTML-rendering component), headed as the
 *       model's words, every invisible character shown escaped; the title and the server's names are plain labels.</li>
 *   <li><b>Stop Claude Code</b> is in the dialog, because the modal covers the tab's Stop: it reports a Stop, and the node
 *       denies the request before it asks for the interrupt.</li>
 * </ul>
 */
public final class LocalAgentPermissionDialog extends JDialog {

    /** §5.7: Allow is not active for this long after the dialog appears. */
    public static final int ALLOW_DELAY_MS = 500;

    static final String DENY = "Deny";
    static final String ALLOW = "Allow once";
    static final String STOP = "Stop Claude Code";

    /** What the person did. */
    public interface Answers {
        void chosen(boolean allow);

        void stop();
    }

    private final JButton deny = UiText.button(DENY);
    private final JButton allow = UiText.button(ALLOW);
    private final JButton stop = UiText.button(STOP);
    private final JLabel countdown = UiText.label(" ");
    private final JTextArea input = new JTextArea();
    private final Timer enable;
    private final Timer tick;
    private final long deadlineMs;
    private final long shownAt;
    private boolean answered;

    /**
     * @param modal true in the application (one modal, owned by the analyser window); a frame test passes false so its
     *              thread is not held by the dialog's own loop
     */
    public LocalAgentPermissionDialog(Window owner, PermissionText.Content c, long deadlineMs, Answers answers,
                                      boolean modal) {
        super(owner, "Claude Code asks for your approval", modal ? Dialog.ModalityType.APPLICATION_MODAL
                : Dialog.ModalityType.MODELESS);
        this.deadlineMs = deadlineMs;
        this.shownAt = System.currentTimeMillis();
        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel top = new JPanel();
        top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
        top.setBorder(BorderFactory.createEmptyBorder(12, 14, 6, 14));
        JLabel title = UiText.label(c.title());
        title.setFont(title.getFont().deriveFont(Font.BOLD, title.getFont().getSize2D() + 1f));
        title.getAccessibleContext().setAccessibleName("What Claude Code asks to do");
        top.add(title);
        top.add(Box.createVerticalStrut(4));
        JLabel changes = UiText.label(c.changes());
        changes.getAccessibleContext().setAccessibleName("What it changes");
        top.add(changes);
        if (!c.serverLine().isBlank()) {
            top.add(Box.createVerticalStrut(2));
            top.add(UiText.label(c.serverLine()));
        }
        top.add(Box.createVerticalStrut(8));
        JLabel heading = UiText.label(PermissionText.INPUT_HEADING);
        top.add(heading);
        if (c.escaped()) top.add(UiText.label(PermissionText.ESCAPED_NOTE));
        for (JComponent j : new JComponent[]{title, changes, heading}) j.setAlignmentX(LEFT_ALIGNMENT);

        input.setText(c.input());
        input.setEditable(false);
        input.setLineWrap(false);
        input.setFont(new Font(Font.MONOSPACED, Font.PLAIN, Math.max(12, title.getFont().getSize() - 1)));
        input.putClientProperty(UiText.HTML_DISABLE, Boolean.TRUE);
        input.getAccessibleContext().setAccessibleName("The call's input, as the model wrote it");
        input.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(input);
        scroll.setPreferredSize(new Dimension(620, Math.min(320, 40 + 18 * (int) c.input().lines().count())));
        JPanel middle = new JPanel(new BorderLayout());
        middle.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        middle.add(scroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 14, 12, 14));
        countdown.getAccessibleContext().setAccessibleName("Time left to answer");
        bottom.add(countdown, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.add(stop);
        buttons.add(allow);
        buttons.add(deny);
        bottom.add(buttons, BorderLayout.EAST);

        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(top, BorderLayout.NORTH);
        getContentPane().add(middle, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);

        allow.setEnabled(false);
        allow.setMnemonic(0);
        deny.addActionListener(e -> answer(answers, false));
        allow.addActionListener(e -> {
            if (allow.isEnabled()) answer(answers, true);
        });
        stop.addActionListener(e -> {
            if (answered) return;
            answered = true;
            answers.stop();
        });
        getRootPane().setDefaultButton(deny);
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "deny");
        getRootPane().getActionMap().put("deny", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) {
                answer(answers, false);
            }
        });
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                answer(answers, false);
            }
        });

        enable = new Timer(ALLOW_DELAY_MS, e -> allow.setEnabled(true));
        enable.setRepeats(false);
        tick = new Timer(1000, e -> showCountdown());
        showCountdown();
        pack();
        setLocationRelativeTo(owner);
        addWindowListener(new WindowAdapter() {
            @Override public void windowOpened(WindowEvent e) {
                enable.start();
                tick.start();
                deny.requestFocusInWindow();
            }
        });
    }

    /** The countdown's words: what the node will do at the deadline. */
    static String countdownText(long remainingMs) {
        long s = Math.max(0, (remainingMs + 999) / 1000);
        return s == 0 ? "Not answered — it is being denied" : "Denied automatically in " + s + " s if not answered";
    }

    private void showCountdown() {
        countdown.setText(countdownText(deadlineMs - (System.currentTimeMillis() - shownAt)));
    }

    private void answer(Answers answers, boolean yes) {
        if (answered) return;
        answered = true;
        answers.chosen(yes);
    }

    /** Close on the node's effect (the person's answer, the deadline, Stop, the turn's end). */
    public void close() {
        enable.stop();
        tick.stop();
        answered = true;
        dispose();
    }

    // ---- for the frame test, which dispatches directly (posted key events are dropped on a Mac without focus) ------

    JButton denyButton() {
        return deny;
    }

    JButton allowButton() {
        return allow;
    }

    JButton stopButton() {
        return stop;
    }

    JTextArea inputArea() {
        return input;
    }

    JLabel countdownLabel() {
        return countdown;
    }

    /** Fire the Allow delay now (the test's 500 ms). */
    void allowDelayElapsed() {
        enable.stop();
        allow.setEnabled(true);
    }
}
