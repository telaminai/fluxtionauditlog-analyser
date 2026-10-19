package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FullFolderTrust;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.TrustedProjectScripts;

import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import java.awt.Component;
import java.nio.file.Path;

/**
 * M74 §5.2 / OD-74.12: the FULL posture's folder confirmation. It names what will run when Start is pressed — the
 * folder's hook events and its {@code .mcp.json} server names, read by {@link FullFolderTrust} (bounded, names only) —
 * and says when a file could not be read, never hiding it. The names come from the folder, so they are shown in a
 * plain-text component: a planted server name such as {@code <html><img src=…>} reads as its characters and fetches
 * nothing (a {@code JLabel} or a stock option-pane string message would render it as HTML). The default is Don't trust,
 * under the rule every consent dialog shares ({@link SafeChoice}).
 */
public final class LocalAgentFolderDialog {

    private LocalAgentFolderDialog() {
    }

    static final String TRUST = "Trust for full Claude Code";
    static final String DONT = "Don't trust";

    /** The dialog's body: the words of {@link FullFolderTrust#dialogText}, in a read-only plain-text area. */
    static JComponent content(Path dir, FullFolderTrust.Scan scan) {
        return content(dir, scan, java.util.List.of(), null);
    }

    /** With the folder's earlier full-posture trust records: a re-ask says what changed (review H6). */
    static JComponent content(Path dir, FullFolderTrust.Scan scan, java.util.List<String> records, String realPath) {
        JTextArea a = new JTextArea(FullFolderTrust.dialogText(dir, scan, records, realPath) + "\n\n" + STARTING, 12, 64);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.putClientProperty("html.disable", Boolean.TRUE);
        a.getAccessibleContext().setAccessibleName("What full Claude Code will run in this folder");
        JScrollPane sp = new JScrollPane(a);
        sp.setBorder(null);
        return sp;
    }

    /** Ask; true only on the person's explicit {@link #TRUST}. Closing the dialog, Escape or Enter on the default, is No. */
    static boolean confirmFull(Component parent, Path dir, FullFolderTrust.Scan scan, java.util.List<String> records,
                               String realPath) {
        // review M74 usability #7: the shared rule — Don't trust is the default, at the right; Trust at the left
        return SafeChoice.ask(parent, FULL_TITLE, content(dir, scan, records, realPath), TRUST, DONT);
    }

    record Answer(boolean yes, boolean scriptsSelected) { }
    record ScriptContent(JComponent component, JCheckBox scripts) { }

    /** M81 B: the optional set choice is always initially unticked, including a changed-content re-ask. */
    static ScriptContent scriptContent(Path dir, FullFolderTrust.Scan scan, java.util.List<String> records,
                                       String realPath, TrustedProjectScripts.Scan scripts, java.util.List<String> grants) {
        JPanel root = new JPanel(new java.awt.BorderLayout(0, 8));
        root.add(content(dir, scan, records, realPath), java.awt.BorderLayout.CENTER);
        JPanel choices = new JPanel();
        choices.setLayout(new javax.swing.BoxLayout(choices, javax.swing.BoxLayout.Y_AXIS));
        String note = TrustedProjectScripts.changeNote(grants, realPath, scripts.scripts(), scripts.digest());
        if (!note.isEmpty()) {
            JTextArea changed = new JTextArea(note);
            changed.setEditable(false);
            changed.setOpaque(false);
            changed.setLineWrap(true);
            changed.setWrapStyleWord(true);
            choices.add(changed);
        }
        JCheckBox checkbox = null;
        if (TrustedProjectScripts.supported() && !scripts.entries().isEmpty()) {
            checkbox = UiText.checkBox("run this project's own build scripts without asking: "
                    + String.join(", ", scripts.scripts()));
            checkbox.setSelected(false);
            checkbox.setName("trustProjectScripts");
            choices.add(checkbox);
            JTextArea warning = new JTextArea("Verifies script identity, not what it does: flags and environment are not "
                    + "a sandbox. Remembered on this machine only. New trust applies at New chat.");
            warning.setEditable(false);
            warning.setOpaque(false);
            warning.setLineWrap(true);
            warning.setWrapStyleWord(true);
            choices.add(warning);
        }
        root.add(choices, java.awt.BorderLayout.SOUTH);
        return new ScriptContent(root, checkbox);
    }

    static Answer confirmFullWithScripts(Component parent, Path dir, FullFolderTrust.Scan scan,
                                          java.util.List<String> records, String realPath,
                                          TrustedProjectScripts.Scan scripts, java.util.List<String> grants) {
        var body = scriptContent(dir, scan, records, realPath, scripts, grants);
        boolean yes = SafeChoice.ask(parent, FULL_TITLE, body.component(), TRUST, DONT);
        return new Answer(yes, yes && body.scripts() != null && body.scripts().isSelected());
    }

    static final String FULL_TITLE = "Full Claude Code — confirm this folder";
    static final String SAFE_TITLE = "Claude Code — confirm this folder";
    /** Review M74 usability F2: verb labels, as the full dialog has, under the same rule. */
    static final String SAFE_TRUST = "Trust this folder";

    /** The full dialog, built and not shown — for the frame test of the shared rule. */
    static SafeChoice.Asked fullDialog(Component parent, Path dir, FullFolderTrust.Scan scan) {
        return SafeChoice.build(parent, FULL_TITLE, content(dir, scan), javax.swing.UIManager.getIcon("OptionPane.warningIcon"),
                TRUST, DONT, false);
    }

    /**
     * Safe analysis's folder question. Review M74 usability F3: when the folder is the analyser's own scratch folder (the
     * sample project, a bundle's working copy), the words say so FIRST — a careful newcomer shown only a temporary path
     * they never chose presses No.
     */
    static String safeText(Path dir, boolean scratch) {
        String what = scratch
                ? "This is the analyser's own private working folder (made for this run; it starts empty). Claude Code reads "
                + "the evidence through the analyser's own tools, not from this folder.\n\n"
                : "Claude Code will read this folder's CLAUDE.md as instructions.\n\n";
        return what + "Trust " + dir + " for Claude Code in the analyser?\n\nRemembered on this machine only; never in a "
                + "project, a shared settings file or a bundle.\n\n" + STARTING;
    }

    /**
     * M78 D1 (OD-Q.9): what starting contacts — the sentence that sat in the panel before every first Send — said once,
     * here, in the first-Start folder question, where it is read. The node's constant, unchanged.
     */
    static final String STARTING = telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession.CONTACTS_ANTHROPIC;

    static JComponent safeContent(Path dir, boolean scratch) {
        JTextArea a = new JTextArea(safeText(dir, scratch), 9, 56);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setFocusable(false);
        a.putClientProperty("html.disable", Boolean.TRUE);
        a.getAccessibleContext().setAccessibleName("The folder Claude Code will work in");
        return a;
    }

    /** Ask the Safe analysis question; true only on {@link #SAFE_TRUST}. */
    static boolean confirmSafe(Component parent, Path dir, boolean scratch) {
        return SafeChoice.ask(parent, SAFE_TITLE, safeContent(dir, scratch), SAFE_TRUST, DONT);
    }

    /** The Safe dialog, built and not shown — for the frame test of the shared rule. */
    static SafeChoice.Asked safeDialog(Component parent, Path dir, boolean scratch) {
        return SafeChoice.build(parent, SAFE_TITLE, safeContent(dir, scratch),
                javax.swing.UIManager.getIcon("OptionPane.questionIcon"), SAFE_TRUST, DONT, false);
    }
}
