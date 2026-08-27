package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.io.File;

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
    private ProjectModel model = ProjectModel.from(null);

    public ProjectPanel(Navigator navigator) {
        super(new BorderLayout());
        this.navigator = navigator;
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBorder(UiTheme.pad());
        JScrollPane scroll = new JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
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

    /** Replace what is shown. Called on every lifecycle event by whoever owns the facts (D-L6). */
    public void render(ProjectModel m) {
        this.model = m == null ? ProjectModel.from(null) : m;
        body.removeAll();
        // review N4: the toggle says "Project"; the panel also states what is merely in force
        JLabel caption = new JLabel("What is in force — the same facts `context` reports");
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
    }

    private JComponent row(ProjectModel.Row r) {
        JPanel line = new JPanel(new BorderLayout(UiTheme.GAP, 0));
        line.setOpaque(false);
        line.setAlignmentX(LEFT_ALIGNMENT);
        line.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.setOpaque(false);
        // C4: draw the abbreviated form; the full value is the tooltip and what Copy copies
        JLabel primary = new JLabel(r.primary() == null ? "?" : ProjectModel.abbreviate(r.primary()));
        primary.setMinimumSize(new Dimension(40, primary.getPreferredSize().height));   // let the row shrink; JLabel elides
        if (r.tone() == ProjectModel.Tone.MUTED) primary.setForeground(UiTheme.mutedForeground());
        if (r.tone() == ProjectModel.Tone.WARN) primary.setForeground(new Color(0xB0, 0x40, 0x20));
        primary.setAlignmentX(LEFT_ALIGNMENT);
        text.add(primary);
        String sub = ProjectModel.abbreviate(r.secondary());
        if (r.provenance() != null) sub = (sub == null || sub.isBlank() ? "" : sub + "  ·  ") + r.provenance();
        if (sub != null && !sub.isBlank()) {
            JLabel secondary = new JLabel(sub);
            UiTheme.status(secondary);
            secondary.setAlignmentX(LEFT_ALIGNMENT);
            text.add(secondary);
        }
        String tip = r.path() != null ? r.path() : r.primary();
        primary.setToolTipText(tip);
        text.setMinimumSize(new Dimension(40, 10));
        line.add(text, BorderLayout.CENTER);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        actions.setOpaque(false);
        if (r.path() != null) {
            actions.add(small("Copy", "Copy the full path: " + r.path(), () -> copy(r.path())));
            if (!r.path().contains("://")) {                 // a remote origin has nothing to show locally
                actions.add(small("Show", "Show in " + fileManagerName(), () -> reveal(r.path())));
            }
        }
        switch (r.target()) {
            case TOPOLOGY -> actions.add(small("Go", "Go to the Topology tab", () -> navigator.showTab("Topology")));
            case SOURCE -> actions.add(small("Go", "Go to the Source tab", () -> navigator.showTab("Source")));
            case REPORTS -> actions.add(small("Go", "Go to the Reports tab", () -> navigator.showTab("Reports")));
            case SETTINGS_SOURCE -> actions.add(small("Settings…", "Settings ▸ Source roots", () -> navigator.openSettings("Source roots")));
            case SETTINGS_PROCESSORS -> actions.add(small("Settings…", "Settings ▸ Event processor", () -> navigator.openSettings("Event processor")));
            case SETTINGS_ASSISTANT -> actions.add(small("Settings…", "Settings ▸ Assistant", () -> navigator.openSettings("Assistant")));
            default -> { }
        }
        if (actions.getComponentCount() > 0) line.add(actions, BorderLayout.EAST);
        line.setMaximumSize(new Dimension(Integer.MAX_VALUE, line.getPreferredSize().height));
        return line;
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
