package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.ExampleSource;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * M74 P2b (owner request 2026-10-17, and the owner's addition): the one confirmation before an example is fetched. It
 * LEADS with the publisher — host / owner / repo @ short commit — marked <b>third-party</b> unless it is the Telamin GitHub
 * organisation; then the exact URL, the full pinned commit, the integrity digest (or that there is none), what will and
 * will not happen, and the target folder the person chooses. There is no "validated" or "approved" mark: that needs a
 * Telamin-published catalogue (a later owner item). Downloading never trusts the folder for Claude Code.
 */
public final class ExampleDownloadDialog {

    private ExampleDownloadDialog() {
    }

    /** The line the confirmation leads with: the publisher, and whose it is. */
    static String publisherLine(ExampleSource s, boolean thirdParty) {
        return s.publisher() + (thirdParty ? "   — third-party: not published by Telamin"
                : "   — " + ExampleSource.TELAMIN_ADDRESS);
    }

    /** The words under the publisher line, in order (a test reads them). */
    static String words(ExampleSource s, boolean thirdParty) {
        return "Address:  " + s.url() + "\n"
                + "Commit:   " + s.commit() + " (pinned: this exact version)\n"
                + "Integrity: " + (s.sha256() == null
                ? "no sha256 in the pack — the archive is identified by its pinned commit only"
                : "sha256 " + s.sha256() + " — checked before anything is unpacked; a mismatch is refused") + "\n\n"
                + "The analyser downloads this one archive over https and unpacks it into the folder below. Nothing is "
                + "built, run or installed, and no git, hook or submodule is used. You are then offered to open it as a "
                + "project. Downloading does not trust the folder for Claude Code.";
    }

    /** What is observed about a chosen folder; the node decides what it means. */
    static String targetProblem(Path target) {
        if (target == null) return "NO_PARENT";
        Path parent = target.getParent();
        if (parent == null || !Files.isDirectory(parent)) return "NO_PARENT";
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) return "NOT_A_DIRECTORY";
        try (Stream<Path> s = Files.list(target)) {
            return s.findAny().isPresent() ? "EXISTS_NOT_EMPTY" : null;
        } catch (java.io.IOException e) {
            return "NOT_A_DIRECTORY";
        }
    }

    /** The confirmation's body: the words, then the folder field and its chooser. */
    record Body(JPanel panel, JTextField folder) {
    }

    static Body body(Component parent, ExampleSource s, boolean thirdParty, Path suggested) {
        JPanel body = new JPanel(new BorderLayout(6, 8));
        JLabel lead = UiText.label(publisherLine(s, thirdParty));
        lead.setFont(lead.getFont().deriveFont(Font.BOLD));
        if (thirdParty) lead.setForeground(UiTheme.warnForeground());
        JTextArea text = Fluid.text(words(s, thirdParty));
        text.setColumns(72);
        JPanel top = new JPanel(new BorderLayout(4, 8));
        top.add(lead, BorderLayout.NORTH);
        top.add(text, BorderLayout.CENTER);
        body.add(top, BorderLayout.NORTH);
        JPanel folder = new JPanel(new BorderLayout(6, 0));
        folder.add(UiText.label("Unpack into (a new or empty folder):"), BorderLayout.WEST);
        JTextField field = new JTextField(suggested == null ? "" : suggested.toString(), 36);
        field.getAccessibleContext().setAccessibleName("The folder the example is unpacked into");
        folder.add(field, BorderLayout.CENTER);
        JButton choose = UiText.button("Choose…");
        choose.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setDialogTitle("Choose where to unpack the example");
            if (fc.showSaveDialog(parent) == JFileChooser.APPROVE_OPTION) field.setText(fc.getSelectedFile().toString());
        });
        folder.add(choose, BorderLayout.EAST);
        body.add(folder, BorderLayout.SOUTH);
        return new Body(body, field);
    }

    static final Object[] OPTIONS = {"Download", "Cancel"};
    static final String TITLE = "Download this example?";

    /** Ask; the chosen folder on Download, null on Cancel. */
    public static Path ask(Component parent, ExampleSource s, boolean thirdParty, Path suggested) {
        Body b = body(parent, s, thirdParty, suggested);
        JComponent message = b.panel();
        int answer = JOptionPane.showOptionDialog(parent, message, TITLE,
                JOptionPane.DEFAULT_OPTION, thirdParty ? JOptionPane.WARNING_MESSAGE : JOptionPane.QUESTION_MESSAGE, null,
                OPTIONS, OPTIONS[1]);
        if (answer != 0 || b.folder().getText().isBlank()) return null;
        return Path.of(b.folder().getText().strip());
    }
}
