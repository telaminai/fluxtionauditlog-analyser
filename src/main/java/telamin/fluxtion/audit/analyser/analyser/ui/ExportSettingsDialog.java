package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.Category;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * File → Export settings… (M15, spec-settings-share §5). Pick which categories to share, then hand the
 * result off by clipboard, a saved {@code .fluxtion-settings} file (revealed in the file manager for
 * drag-drop into Slack/WhatsApp), or an email draft. The headless {@link SettingsShare} does the real
 * work; this is a thin transport wrapper.
 */
final class ExportSettingsDialog extends JDialog {

    private static final String SUGGESTED_NAME = "analysis-setup.fluxtion-settings";

    private final AppConfig config;
    private final SettingsShare share = new SettingsShare();
    private final Map<Category, JCheckBox> boxes = new EnumMap<>(Category.class);

    ExportSettingsDialog(Frame owner, AppConfig config) {
        super(owner, "Export settings", true);
        this.config = config;

        JPanel content = new JPanel(new BorderLayout(0, UiTheme.PAD));
        content.setBorder(UiTheme.pad());

        JLabel intro = new JLabel("<html>Share your analysis setup. Choose what to include —"
                + " then copy it, save a file, or email it.</html>");
        content.add(intro, BorderLayout.NORTH);

        JPanel cats = new JPanel(new GridLayout(0, 1, 0, 2));
        cats.setBorder(UiTheme.section("Include"));
        Set<Category> defaults = Category.defaults();
        for (Category c : Category.values()) {
            JCheckBox cb = new JCheckBox(c.label, defaults.contains(c));
            boxes.put(c, cb);
            cats.add(cb);
        }
        content.add(cats, BorderLayout.CENTER);

        JLabel note = new JLabel("API keys and machine-local settings are never exported.");
        UiTheme.status(note);

        JButton copy = new JButton("Copy to clipboard");
        JButton save = new JButton("Save file…");
        JButton email = new JButton("Email…");
        JButton close = new JButton("Close");
        copy.addActionListener(e -> copyToClipboard());
        save.addActionListener(e -> saveToFile());
        email.addActionListener(e -> emailDraft());
        close.addActionListener(e -> dispose());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.GAP, 0));
        actions.add(copy);
        actions.add(save);
        actions.add(email);
        actions.add(close);

        JPanel south = new JPanel(new BorderLayout(0, UiTheme.GAP));
        south.add(note, BorderLayout.NORTH);
        south.add(actions, BorderLayout.SOUTH);
        content.add(south, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(save);
        pack();
        setLocationRelativeTo(owner);
    }

    private Set<Category> selected() {
        Set<Category> s = EnumSet.noneOf(Category.class);
        boxes.forEach((c, cb) -> { if (cb.isSelected()) s.add(c); });
        return s;
    }

    private String buildText() {
        return share.export(config, selected());
    }

    private void copyToClipboard() {
        if (nothingSelected()) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(buildText()), null);
        info("Settings copied to the clipboard — paste them into a message or document.");
    }

    private void saveToFile() {
        if (nothingSelected()) return;
        JFileChooser fc = new JFileChooser();
        fc.setDialogTitle("Save settings");
        fc.setSelectedFile(new File(SUGGESTED_NAME));
        if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = fc.getSelectedFile();
        try {
            Files.writeString(file.toPath(), buildText());
        } catch (Exception ex) {
            error("Could not save the file: " + rootMessage(ex));
            return;
        }
        revealInFileManager(file);
        info("Saved " + file.getName() + ".\nDrag it into Slack, WhatsApp or email to share.");
    }

    private void emailDraft() {
        if (nothingSelected()) return;
        String text = buildText();
        // mailto bodies are size-limited by many clients — inline only when small, else use the clipboard
        boolean inlined = text.length() < 1500;
        if (!inlined) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
        }
        String body = inlined
                ? text
                : "The settings are on your clipboard — paste them below (or attach the saved "
                  + SUGGESTED_NAME + " file).";
        if (!openMail("Fluxtion analyser — shared settings", body)) {
            // no mail client wired up: fall back to the clipboard so the action still does something useful
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
            info("No email client is available. The settings were copied to the clipboard instead.");
        }
    }

    // ---- desktop integration (all guarded; degrade gracefully when unsupported) -----------------

    private boolean openMail(String subject, String body) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.MAIL)) return false;
        try {
            String uri = "mailto:?subject=" + enc(subject) + "&body=" + enc(body);
            Desktop.getDesktop().mail(new URI(uri));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void revealInFileManager(File file) {
        Desktop d = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
        if (d == null) return;
        try {
            if (d.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                d.browseFileDirectory(file);   // selects the file in Finder/Explorer/most Linux managers
            } else if (d.isSupported(Desktop.Action.OPEN) && file.getParentFile() != null) {
                d.open(file.getParentFile());
            }
        } catch (Exception ignore) {
            // revealing is a nicety — a failure here shouldn't mask a successful save
        }
    }

    private static String enc(String s) {
        // mailto wants %20 for spaces (URLEncoder emits '+') and keeps %0A for newlines
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private boolean nothingSelected() {
        if (!selected().isEmpty()) return false;
        error("Select at least one category to export.");
        return true;
    }

    private void info(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Export settings", JOptionPane.INFORMATION_MESSAGE);
    }

    private void error(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Export settings", JOptionPane.WARNING_MESSAGE);
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}