package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FullFolderTrust;

import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.Component;
import java.nio.file.Path;

/**
 * M74 §5.2 / OD-74.12: the FULL posture's folder confirmation. It names what will run when Start is pressed — the
 * folder's hook events and its {@code .mcp.json} server names, read by {@link FullFolderTrust} (bounded, names only) —
 * and says when a file could not be read, never hiding it. The names come from the folder, so they are shown in a
 * plain-text component: a planted server name such as {@code <html><img src=…>} reads as its characters and fetches
 * nothing (a {@code JLabel} or a {@code JOptionPane} string message would render it as HTML). The default is No.
 */
public final class LocalAgentFolderDialog {

    private LocalAgentFolderDialog() {
    }

    static final String TRUST = "Trust for full Claude Code";
    static final String DONT = "Don't trust";

    /** The dialog's body: the words of {@link FullFolderTrust#dialogText}, in a read-only plain-text area. */
    static JComponent content(Path dir, FullFolderTrust.Scan scan) {
        JTextArea a = new JTextArea(FullFolderTrust.dialogText(dir, scan), 12, 64);
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

    /** Ask; true only on the person's explicit {@link #TRUST}. Closing the dialog, or Enter on the default, is No. */
    static boolean confirmFull(Component parent, Path dir, FullFolderTrust.Scan scan) {
        Object[] options = {TRUST, DONT};
        int answer = JOptionPane.showOptionDialog(parent, content(dir, scan), "Full Claude Code — confirm this folder",
                JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return answer == 0;
    }
}
