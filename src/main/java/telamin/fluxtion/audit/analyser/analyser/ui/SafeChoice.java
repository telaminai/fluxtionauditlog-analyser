package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * Review M74 usability #7 (2026-10-17): ONE rule for every consent dialog the assistant raises — the approval dialog,
 * full Claude Code's folder trust and Safe analysis's folder confirmation.
 *
 * <ul>
 *   <li><b>The safe choice</b> (<i>Deny</i>, <i>Don't trust</i>) is the default button, sits at the RIGHT end of the
 *       button row (the platform default-button place), and Enter, Escape and closing the window all choose it.</li>
 *   <li><b>The risky choice</b> (<i>Allow once</i>, <i>Trust…</i>) sits at the LEFT end, never next to the safe one,
 *       is never default-capable, and has no mnemonic — so a person who has learned "the right-hand button is safe"
 *       from one dialog is right in every other.</li>
 * </ul>
 *
 * <p>Before this, the approval dialog put Deny far right with Allow beside it, the full folder trust put <i>Trust</i>
 * where Deny was, and the Safe folder confirmation was a stock Yes/No whose default was Yes.
 */
public final class SafeChoice {

    private SafeChoice() {
    }

    /** The client property a row's buttons carry: which side of the rule each is. */
    static final String ROLE = "safeChoice.role";

    /**
     * The button row: {@code risky} alone at the left end; {@code beside} (neither risky nor the answer, e.g. Stop) and
     * then {@code safe} at the right end, {@code safe} last.
     */
    public static JPanel buttonRow(JButton risky, JButton safe, JComponent... beside) {
        risky.setDefaultCapable(false);
        risky.setMnemonic(0);
        risky.putClientProperty(ROLE, "risky");
        safe.putClientProperty(ROLE, "safe");
        JPanel row = new JPanel(new BorderLayout(UiTheme.SPACE_LG, 0));
        row.setOpaque(false);
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(risky);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.SPACE_SM, 0));
        right.setOpaque(false);
        for (JComponent b : beside) {
            if (b instanceof JButton j) j.setDefaultCapable(false);
            right.add(b);
        }
        right.add(safe);
        row.add(left, BorderLayout.WEST);
        row.add(Box.createHorizontalStrut(UiTheme.SPACE_LG), BorderLayout.CENTER);
        row.add(right, BorderLayout.EAST);
        return row;
    }

    /** Make {@code safe} the dialog's default, and Escape and the window's close choose it. */
    static void safeByDefault(JDialog d, JButton safe) {
        d.getRootPane().setDefaultButton(safe);
        d.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        d.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "safe-choice");
        d.getRootPane().getActionMap().put("safe-choice", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) {
                safe.doClick();
            }
        });
        d.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                safe.doClick();
            }

            @Override public void windowOpened(WindowEvent e) {
                safe.requestFocusInWindow();
            }
        });
    }

    /** A built, not yet shown, two-choice dialog; {@link #chosenRisky()} says what the person chose once it closes. */
    public static final class Asked {
        final JDialog dialog;
        final JButton risky;
        final JButton safe;
        private boolean riskyChosen;

        Asked(JDialog dialog, JButton risky, JButton safe) {
            this.dialog = dialog;
            this.risky = risky;
            this.safe = safe;
        }

        public boolean chosenRisky() {
            return riskyChosen;
        }

        JDialog dialog() {
            return dialog;
        }

        JButton riskyButton() {
            return risky;
        }

        JButton safeButton() {
            return safe;
        }
    }

    /**
     * Build the dialog: {@code message} (a component, so a folder's names render as text, never HTML) beside the
     * {@code icon}, and the two buttons under the rule. {@code modal} false is for a frame test.
     */
    static Asked build(Component parent, String title, JComponent message, Icon icon, String riskyLabel, String safeLabel,
                       boolean modal) {
        Window owner = parent == null ? null : parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        JDialog d = new JDialog(owner, title, modal ? Dialog.ModalityType.APPLICATION_MODAL : Dialog.ModalityType.MODELESS);
        JButton risky = UiText.button(riskyLabel);
        JButton safe = UiText.button(safeLabel);
        Asked asked = new Asked(d, risky, safe);
        risky.addActionListener(e -> {
            asked.riskyChosen = true;
            d.dispose();
        });
        safe.addActionListener(e -> {
            asked.riskyChosen = false;
            d.dispose();
        });
        JPanel body = new JPanel(new BorderLayout(UiTheme.SPACE_MD, UiTheme.SPACE_MD));
        body.setBorder(BorderFactory.createEmptyBorder(UiTheme.SPACE_MD, UiTheme.SPACE_MD + 2, UiTheme.SPACE_MD,
                UiTheme.SPACE_MD + 2));
        if (icon != null) {
            JLabel i = new JLabel(icon);
            i.setVerticalAlignment(JLabel.TOP);
            body.add(i, BorderLayout.WEST);
        }
        body.add(message, BorderLayout.CENTER);
        body.add(buttonRow(risky, safe), BorderLayout.SOUTH);
        d.setContentPane(body);
        safeByDefault(d, safe);
        fitWrappedText(body);
        d.pack();
        d.setLocationRelativeTo(owner);
        return asked;
    }

    /** The tallest a fitted text area grows before it scrolls instead (most of a laptop screen). */
    static final int MAX_TEXT_HEIGHT = 560;

    /**
     * Robot walkthrough #7 (2026-10-18): a wrapping {@link javax.swing.JTextArea} packed with no width reports the height
     * of its row count, not of its wrapped text, so the dialog cut the full-posture warning off mid-sentence — the
     * sentence about the home folder and the REST token. Each wrapping area under {@code c} is laid out at the width it
     * will be shown at and given the height its text needs, so {@code pack()} shows every line; past
     * {@link #MAX_TEXT_HEIGHT} it is given that height inside a scroll pane, never clipped.
     */
    static void fitWrappedText(java.awt.Container c) {
        for (Component k : c.getComponents()) {
            if (k instanceof javax.swing.JTextArea a && a.getLineWrap()) {
                int width = a.getPreferredSize().width;
                a.setSize(width, Short.MAX_VALUE);                   // lays the text out at its shown width
                int height = a.getUI().getPreferredSize(a).height;
                a.setPreferredSize(new java.awt.Dimension(width, height));
                if (height > MAX_TEXT_HEIGHT && !(a.getParent() instanceof javax.swing.JViewport)) {
                    java.awt.Container parent = a.getParent();
                    Object constraint = parent.getLayout() instanceof BorderLayout bl ? bl.getConstraints(a) : null;
                    int at = java.util.Arrays.asList(parent.getComponents()).indexOf(a);
                    parent.remove(a);
                    javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(a,
                            javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                            javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
                    scroll.setBorder(BorderFactory.createEmptyBorder());
                    scroll.setPreferredSize(new java.awt.Dimension(width + 16, MAX_TEXT_HEIGHT));
                    if (constraint != null) parent.add(scroll, constraint);
                    else parent.add(scroll, at);
                }
            } else if (k instanceof java.awt.Container inner) {
                fitWrappedText(inner);
            }
        }
    }

    /** Ask, modally; true only on the person's explicit press of the risky choice. */
    public static boolean ask(Component parent, String title, JComponent message, String riskyLabel, String safeLabel) {
        Asked a = build(parent, title, message, UIManager.getIcon("OptionPane.warningIcon"), riskyLabel, safeLabel, true);
        a.dialog.setVisible(true);
        return a.chosenRisky();
    }
}
