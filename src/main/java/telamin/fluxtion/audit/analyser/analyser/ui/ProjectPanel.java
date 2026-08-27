package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Files;

/**
 * M37 — the Project panel: five sections stating what is in force, rendered from a {@link ProjectModel}
 * and nothing else. Lives in the west column under Event types (spec D-L5).
 *
 * <p>Every action here REVEALS or NAVIGATES (D-L3): copy a path, show it in the file manager, go to the
 * tab that owns the thing. Nothing here closes, switches, or edits — a display that can mutate state is
 * a display people learn not to trust. The only way out of this class is {@link Navigator}, whose two
 * methods both move the eye, not the state; a bytecode test proves this class never names MainFrame.
 */
public final class ProjectPanel extends JPanel {

    /** How the panel asks the frame to show something. Navigation only — see class doc. */
    public interface Navigator {
        /** Bring a right-hand tab forward by title ("Topology", "Source"). */
        void showTab(String title);

        /** Open Settings on the named page ("Source roots", "Event processor", "Assistant"). */
        void openSettings(String page);
    }

    private final Navigator navigator;
    private final JPanel body = new TracksWidth();
    private JScrollPane scroll;
    private ProjectModel model = ProjectModel.from(null);

    public ProjectPanel(Navigator navigator) {
        super(new BorderLayout());
        this.navigator = navigator;
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(UiTheme.pad());
        scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        UiTheme.applySurface(scroll, body);
        add(scroll, BorderLayout.CENTER);
        setPreferredSize(new Dimension(300, 260));
        render(model);
    }

    public ProjectModel model() {
        return model;
    }

    /**
     * Owner, 2026-08-27: the panel kept the old theme's colours after Theme ▸ …. Every row sets its foreground,
     * font and borders explicitly from UiTheme at render time, and updateComponentTreeUI leaves explicit values
     * alone — so a theme switch must re-render, which recomputes all of them from the new UIManager defaults.
     */
    public void refreshTheme() {
        UiTheme.applySurface(scroll, body);
        render(model);
    }

    /** Replace what is shown. Called on every lifecycle event by whoever owns the facts (D-L6). */
    public void render(ProjectModel m) {
        this.model = m == null ? ProjectModel.from(null) : m;
        // a re-render must not move the user's eye: keep where they had scrolled to (the first live shots
        // showed the panel jumping to its bottom on every lifecycle event — the new text areas dragged the
        // viewport to their caret)
        final int scrolledTo = scroll == null ? 0 : scroll.getVerticalScrollBar().getValue();
        body.removeAll();
        // review N4: the toggle says "Project"; the panel also states what is merely in force
        JLabel caption = new JLabel("What is in force — what `context` reports, for people");
        UiTheme.status(caption);
        caption.setAlignmentX(LEFT_ALIGNMENT);
        caption.setBorder(BorderFactory.createEmptyBorder(0, 0, UiTheme.GAP, 0));
        body.add(caption);
        for (ProjectModel.Section s : model.sections()) {
            JPanel sec = new JPanel();
            sec.setLayout(new BoxLayout(sec, BoxLayout.Y_AXIS));
            sec.setOpaque(false);
            sec.setBorder(UiTheme.section(s.title()));
            sec.setAlignmentX(LEFT_ALIGNMENT);
            for (ProjectModel.Row r : s.rows()) sec.add(row(r));
            body.add(sec);
            body.add(Box.createVerticalStrut(UiTheme.GAP));
        }
        body.add(Box.createVerticalGlue());
        body.revalidate();
        body.repaint();
        // after the deferred layout has run — two hops, because revalidate itself is deferred one
        if (scroll != null) SwingUtilities.invokeLater(() -> SwingUtilities.invokeLater(
                () -> scroll.getVerticalScrollBar().setValue(scrolledTo)));
    }

    private JComponent row(ProjectModel.Row r) {
        // two lines: [name/path ..................... buttons] over a full-width sentence. The sentence used to
        // sit beside the buttons and wrapped into a five-line sliver (review F2, second look).
        JPanel row = new JPanel();
        row.setLayout(new BoxLayout(row, BoxLayout.Y_AXIS));
        row.setOpaque(false);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setBorder(BorderFactory.createEmptyBorder(2, 0, 3, 0));

        JPanel head = new JPanel(new BorderLayout(UiTheme.GAP, 0));
        head.setOpaque(false);
        head.setAlignmentX(LEFT_ALIGNMENT);
        // C4/D-L8: draw the abbreviated form; the full value is the tooltip and what Copy copies
        JLabel primary = new JLabel(r.primary() == null ? "?" : ProjectModel.abbreviate(r.primary()));
        primary.setMinimumSize(new Dimension(40, primary.getPreferredSize().height));   // let the row shrink; JLabel elides
        if (r.tone() == ProjectModel.Tone.MUTED) primary.setForeground(UiTheme.mutedForeground());
        if (r.tone() == ProjectModel.Tone.WARN) primary.setForeground(UiTheme.warnForeground());
        primary.setToolTipText(r.path() != null ? r.path() : r.primary());
        head.add(primary, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actions.setOpaque(false);
        if (r.path() != null) {
            actions.add(small("Copy", "Copy the full path: " + r.path(), () -> copy(r.path())));
            if (!r.path().contains("://")) {                 // a remote origin has nothing to show locally
                // owner, 2026-08-27: the buttons say what they do — "Show file" opens the file manager, "Open" opens the thing
                actions.add(small("Show file", "Show in " + fileManagerName(), () -> reveal(r.path())));
            }
        }
        switch (r.target()) {
            case TOPOLOGY -> actions.add(small("Open", "Open in the Topology tab", () -> navigator.showTab("Topology")));
            case SOURCE -> actions.add(small("Open", "Open in the Source tab", () -> navigator.showTab("Source")));
            case REPORTS -> actions.add(small("Open", "Open in the Reports tab", () -> navigator.showTab("Reports")));
            case VIEW_FILE -> actions.add(small("Open", "Read the file here, as written — nothing is run", () -> viewFile(r.path(), r.primary())));
            case SETTINGS_SOURCE -> actions.add(small("Settings…", "Settings ▸ Source roots", () -> navigator.openSettings("Source roots")));
            case SETTINGS_PROCESSORS -> actions.add(small("Settings…", "Settings ▸ Event processor", () -> navigator.openSettings("Event processor")));
            case ADD_SOURCE -> actions.add(small("Add source", "Settings ▸ Source roots — add the root that holds this class", () -> navigator.openSettings("Source roots")));
            case SETTINGS_ASSISTANT -> actions.add(small("Settings…", "Settings ▸ Assistant", () -> navigator.openSettings("Assistant")));
            default -> { }
        }
        if (actions.getComponentCount() > 0) head.add(actions, BorderLayout.EAST);
        head.setMaximumSize(new Dimension(Integer.MAX_VALUE, head.getPreferredSize().height));
        row.add(head);

        // Review F2: the second line is a SENTENCE — the pairing verdict, "source NOT found", the tier — whose
        // tail carries the meaning, so it WRAPS across the full row. Elision (D-L8) stays for the first line.
        String sub = r.secondary();
        if (r.provenance() != null) sub = (sub == null || sub.isBlank() ? "" : sub + "  ·  ") + r.provenance();
        if (sub != null && !sub.isBlank()) {
            JTextArea secondary = new JTextArea();
            // a text area asks its viewport to show its caret when its text changes — scheduled on the EDT, so
            // it fires AFTER the area is in the scroll pane and drags the whole panel to wherever that area
            // is (the first live shots opened scrolled to the bottom). Policy first, then the text.
            if (secondary.getCaret() instanceof javax.swing.text.DefaultCaret dc) dc.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);
            secondary.setText(sub);
            secondary.setEditable(false);
            secondary.setFocusable(false);
            secondary.setOpaque(false);
            secondary.setLineWrap(true);
            secondary.setWrapStyleWord(true);
            secondary.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
            secondary.setFont(UIManager.getFont("Label.font").deriveFont(UIManager.getFont("Label.font").getSize2D() - 1f));
            secondary.setForeground(UiTheme.mutedForeground());
            secondary.setAlignmentX(LEFT_ALIGNMENT);
            row.add(secondary);
        }
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, Short.MAX_VALUE));
        return row;
    }

    /**
     * A BoxLayout column that is exactly as wide as its viewport. Without this the column takes the widest
     * row's preferred width, the scroll pane (horizontal bar: never) clips it, and the Copy/Show buttons
     * on long paths fall off the right edge — seen on the first live render. Tracking the width lets each
     * row's BorderLayout give the buttons their space and the label the rest, which JLabel elides with "…".
     */
    private static final class TracksWidth extends JPanel implements Scrollable {
        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return r.height; }
        @Override public boolean getScrollableTracksViewportWidth() { return true; }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    private static JButton small(String text, String tip, Runnable action) {
        JButton b = new JButton(text);
        b.setToolTipText(tip);
        b.setFocusable(false);
        b.setMargin(new Insets(0, 4, 0, 4));
        b.putClientProperty("JButton.buttonType", "toolBarButton");
        b.addActionListener(e -> action.run());
        return b;
    }

    /** Longest file the viewer shows whole; a runbook or glossary is a page, not a dump. */
    static final int VIEWER_MAX_CHARS = 256_000;

    /**
     * A read-only viewer for a file the profile points at (owner, 2026-08-27). Plain text, as written — not
     * rendered markdown, which would need a dependency and would be a second reading of something a person
     * may act on. This is a HUMAN surface: D-C2 forbids the analyser executing a runbook or serving its contents
     * to an agent with the analyser's authority, and a person reading the file in the app is neither. Reveal only.
     */
    private void viewFile(String path, String title) {
        String text;
        try {
            java.nio.file.Path p = java.nio.file.Path.of(path);
            if (!Files.isRegularFile(p)) { text = "(no file at " + path + ")"; }
            else {
                text = Files.readString(p);
                if (text.length() > VIEWER_MAX_CHARS) {
                    text = text.substring(0, VIEWER_MAX_CHARS) + "\n\n… [" + (text.length() - VIEWER_MAX_CHARS) + " more characters not shown — Show file to read the rest]";
                }
            }
        } catch (Exception e) {
            text = "(could not read " + path + ": " + e.getMessage() + ")";
        }
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(UiTheme.mono(12));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(720, 480));
        JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this), title + " — " + path, java.awt.Dialog.ModalityType.MODELESS);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        JButton showFile = new JButton("Show file");
        showFile.addActionListener(e -> reveal(path));
        JButton copyPath = new JButton("Copy path");
        copyPath.addActionListener(e -> copy(path));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        buttons.add(showFile); buttons.add(copyPath); buttons.add(close);
        JLabel note = new JLabel("Read-only. The analyser shows this file for you; it never runs it and never hands its contents to an agent.");
        UiTheme.status(note);
        note.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 8));
        dialog.getContentPane().setLayout(new BorderLayout());
        dialog.getContentPane().add(note, BorderLayout.NORTH);
        dialog.getContentPane().add(scroll, BorderLayout.CENTER);
        dialog.getContentPane().add(buttons, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private static void copy(String path) {
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(path), null);
    }

    /** Show the file in the OS file manager; a path that no longer exists shows its parent. */
    static void reveal(String path) {
        try {
            File f = new File(path);
            if (!f.exists()) f = f.getParentFile();
            if (f == null) return;
            if (Desktop.isDesktopSupported()) {
                Desktop d = Desktop.getDesktop();
                if (f.isFile() && d.isSupported(Desktop.Action.BROWSE_FILE_DIR)) {
                    d.browseFileDirectory(f);
                    return;
                }
                if (d.isSupported(Desktop.Action.OPEN)) d.open(f.isDirectory() ? f : f.getParentFile());
            }
        } catch (Exception ignored) {
            // reveal is a courtesy; a platform without a file manager loses nothing else
        }
    }

    private static String fileManagerName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("mac") ? "Finder" : os.contains("win") ? "Explorer" : "file manager";
    }
}
