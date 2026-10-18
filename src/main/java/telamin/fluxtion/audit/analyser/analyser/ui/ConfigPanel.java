package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;

import javax.swing.*;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Settings dialog (spec §8.1): source roots (add / remove / drag-drop folders), the EventProcessor
 * FQN, and LLM provider/model/key/base-url. All values — including every path — are written back to
 * {@link AppConfig} on OK (improvements.md). API key is stored in cleartext by decision.
 */
public final class ConfigPanel extends JDialog {

    private final AppConfig config;
    /**
     * Where a folder chooser opens. A chooser that starts in the home directory makes the person walk to
     * their project every time; the caller knows the useful place and this is where it says so. Null falls
     * back to the active project's root, then to the platform default.
     */
    private java.nio.file.Path startDir;
    private final Runnable onSaved;

    private final DefaultListModel<String> rootsModel = new DefaultListModel<>();
    // SF-2: roots, processors and Maven repos are profile values — a cloned project's, possibly
    private final JList<String> rootsList = UiText.plainCells(new JList<>(rootsModel));
    private final DefaultListModel<String> epModel = new DefaultListModel<>();
    private final JList<String> epList = UiText.plainCells(new JList<>(epModel));
    private String activeEp;   // the EventProcessor FQN in use (marked in the list)
    private final DefaultListModel<String> mavenModel = new DefaultListModel<>();
    private final JList<String> mavenList = UiText.plainCells(new JList<>(mavenModel));
    private final JCheckBox mavenDisabled = new JCheckBox("Don't search local Maven repositories");
    private final JComboBox<String> providerCombo = new JComboBox<>(new String[]{"anthropic", "openai"});
    private final JTextField modelField = new JTextField(22);
    private final JTextField baseUrlField = new JTextField(22);
    private final JTextField apiKeyField = new JTextField(28);
    private final JTextField awsProfileField = new JTextField(18);
    private final JTextField awsRegionField = new JTextField(18);
    private final JSpinner memThresholdSpinner =
            new JSpinner(new SpinnerNumberModel(500, 0, 1_000_000, 50));
    private final JCheckBox actionsInProcess = new JCheckBox("Let the in-app assistant change the views as it answers");
    private final JCheckBox actionsRest = new JCheckBox("External MCP/REST access (for an AI client on this machine)");
    private final JCheckBox exportsToggle = new JCheckBox("Allow assistant file exchange (screenshot/report writes, external-series reads)");
    // M73 D-73.7: machine tier, off by default; gates only requests of assistant origin — a person is never gated
    private final JCheckBox createProjectsToggle = new JCheckBox("Allow the assistant to create projects");
    private final JTextField exportDirField = new JTextField(28);
    // M74 §5.1: machine tier, all off by default; the full posture (P1b) is turned on only through its warning
    private final JCheckBox claudeCodeToggle = new JCheckBox("Allow the analyser to start Claude Code");
    private final JCheckBox claudeCodeFullToggle = new JCheckBox("…with my full Claude Code permissions");
    private final JComboBox<String> claudeCodeModeCombo = UiText.plainCells(new JComboBox<>(
            telamin.fluxtion.audit.analyser.analyser.config.ConfigStore.FULL_MODES.toArray(new String[0])));
    private final JLabel claudeCodeModeLabel = new JLabel("Mode:");
    private final JTextArea claudeCodeModeNote = plainNote();
    private final JTextField claudeCodeModelField = new JTextField(12);
    private final JLabel claudeCodeModelLabel = new JLabel("Claude Code model:");
    // owner, 2026-10-17: optional and off by default — the person's own Claude plan has its own limits
    private final JCheckBox claudeCodeBudgetToggle = new JCheckBox("Limit spend per session (US$):");
    private final JSpinner claudeCodeBudgetSpinner = new JSpinner(new SpinnerNumberModel(5.0, 0.5, 1000.0, 0.5));

    /** §5.1: the full posture's warning, shown as a confirmation whose default is Cancel. Plain text, never HTML. */
    // OD-74.26: the PREVIEW banner went when approvals in the analyser (P3) shipped; the warning's substance stays
    static final String FULL_POSTURE_WARNING = "Full Claude Code runs your own Claude Code inside the analyser, as in a "
            + "terminal. It loads your settings and permission rules, skills, plugins, subagents, hooks and MCP servers "
            + "(including claude.ai connectors), and the folder's own hooks and .mcp.json — these run when you press "
            + "Start, before you send anything. Claude Code can then use your own tools — Bash, file edits, web access — as "
            + "far as your permission rules and the mode below allow; the analyser does not confine it to the folder. "
            + "When Claude Code asks for an approval, the analyser asks you in a dialog — Deny is the default — but a call "
            + "your own permission rules, hooks or the auto mode's classifier approve is never asked in the analyser.\n\n"
            + "Audit-log records are data from the system you are investigating and can contain text written to steer an "
            + "AI; in full Claude Code that text reaches an agent that may have your shell. The analyser still never lets "
            + "Claude Code change what is open or write a file through its own connection without asking you in the "
            + "analyser, but it cannot limit what your own tools do — they can read files in your home folder, including "
            + "the analyser's local REST token, and use it to drive the analyser as any external client can. Turn this on "
            + "only for folders and logs you trust.";
    static final String FULL_POSTURE_TURN_ON = "Turn on full Claude Code";

    /** §5.1: what each mode does, as the Settings note under the mode says it. */
    static String modeNote(String mode) {
        return switch (mode == null ? "default" : mode) {
            case "acceptEdits" -> "acceptEdits — edits files in the folder without asking; anything else as default.";
            case "auto" -> "auto — Claude Code's own safety classifier approves or blocks each action; it does not ask "
                    + "you. It needs a model that supports it (sonnet or opus): with another, Claude Code starts in "
                    + "default instead, and the analyser stops it and says so.";
            case "plan" -> "plan — reads and plans; edits and runs nothing.";
            default -> "default — asks before anything your rules don't allow; the analyser shows each request in a "
                    + "dialog, and Deny is the default.";
        };
    }

    private static JTextArea plainNote() {
        JTextArea a = new JTextArea(2, 40);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.setFocusable(false);
        a.setFont(UIManager.getFont("Label.font"));
        return a;
    }
    /** #20 — the project's workspace anchor, offered as the handful of depths PathForm accepts. */
    private final JComboBox<telamin.fluxtion.audit.analyser.analyser.config.WorkspaceAnchorChoices.Choice>
            anchorCombo = UiText.plainCells(new JComboBox<>());
    /** #21 — the open PROJECT's preferred exchange directory, project-relative. */
    private final JTextField projectExchangeField = new JTextField(28);
    private final JSpinner maxRoundsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
    /** OA-1: the onboard assistant's per-turn action budget, independent of the per-reply cap. */
    private final JSpinner maxTurnActionsSpinner = new JSpinner(new SpinnerNumberModel(30, 1, 500, 1));
    private final JSpinner maxActionsSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 200, 1));

    private ConfigPanel(JFrame owner, AppConfig config, Runnable onSaved) {
        this(owner, config, onSaved, null);
    }

    private ConfigPanel(JFrame owner, AppConfig config, Runnable onSaved,
                        java.util.function.Supplier<java.util.List<String>> readerSummaries) {
        super(owner, "Settings", true);
        this.readerSummaries = readerSummaries;
        this.config = config;
        this.onSaved = onSaved;
        buildUi();
        loadFromConfig();
        pack();
        setMinimumSize(new Dimension(620, getHeight()));
        setLocationRelativeTo(owner);
    }

    /**
     * Start a chooser somewhere worth starting: what the caller asked for, else the active project's
     * directory, else leave the platform default alone. Never throws — a bad path just means the default.
     */
    private void openWhereItIsUseful(JFileChooser fc) {
        java.nio.file.Path where = startDir;
        if (where == null && config.activeProjectPath != null && !config.activeProjectPath.isBlank()) {
            java.nio.file.Path settings = java.nio.file.Path.of(config.activeProjectPath);
            // <root>/.analyser/project*.fluxtion-settings — two up is the project itself
            java.nio.file.Path analyser = settings.getParent();
            where = analyser == null ? null : analyser.getParent();
        }
        try {
            if (where != null && java.nio.file.Files.isDirectory(where)) {
                fc.setCurrentDirectory(where.toFile());
            }
        } catch (RuntimeException ignored) {
            // the default is a perfectly good answer
        }
    }

    public static void show(JFrame owner, AppConfig config, Runnable onSaved) {
        new ConfigPanel(owner, config, onSaved).setVisible(true);
    }

    public static void show(JFrame owner, AppConfig config, Runnable onSaved,
                            java.util.function.Supplier<java.util.List<String>> readerSummaries) {
        new ConfigPanel(owner, config, onSaved, readerSummaries).setVisible(true);
    }

    /**
     * M37: open on a named page ("Source roots", "Event processor", "Assistant", …). The Project panel's
     * Settings… buttons say which page holds the thing the row is about; landing on the first tab regardless
     * made the button a lie (found by the owner on the Reports row, 2026-08-27).
     */
    public static void show(JFrame owner, AppConfig config, Runnable onSaved,
                            java.util.function.Supplier<java.util.List<String>> readerSummaries, String page) {
        show(owner, config, onSaved, readerSummaries, page, null);
    }

    /** As above, opening folder choosers at {@code startDir} — where the caller knows the work is. */
    public static void show(JFrame owner, AppConfig config, Runnable onSaved,
                            java.util.function.Supplier<java.util.List<String>> readerSummaries, String page,
                            java.nio.file.Path startDir) {
        show(owner, config, onSaved, readerSummaries, page, startDir, null);
    }

    /**
     * As above, landing with {@code highlight} selected and scrolled to.
     *
     * <p>A Settings… button that names a row should arrive AT that row. Landing on the right page and
     * leaving a person to find their own path among a dozen was the same gap as landing on the wrong
     * page: the button knows which one it means, so it says so (asked for in use, 2026-09-30).
     */
    public static void show(JFrame owner, AppConfig config, Runnable onSaved,
                            java.util.function.Supplier<java.util.List<String>> readerSummaries, String page,
                            java.nio.file.Path startDir, String highlight) {
        ConfigPanel panel = new ConfigPanel(owner, config, onSaved, readerSummaries);
        panel.startDir = startDir;
        panel.selectPage(page);
        panel.highlightRoot(highlight);
        panel.setVisible(true);
    }

    /**
     * Select and scroll to one source root. Silent when it is not there — a row can name a root that a
     * person removed since the panel was drawn, and a dialog that complained about it would be noise.
     */
    void highlightRoot(String path) {
        if (path == null || path.isBlank()) return;
        for (int i = 0; i < rootsModel.size(); i++) {
            if (path.equals(rootsModel.get(i))) {
                rootsList.setSelectedIndex(i);
                rootsList.ensureIndexIsVisible(i);
                return;
            }
        }
    }

    private JTabbedPane tabs;

    /** Select the tab whose title matches (case-insensitive); an unknown title leaves the first tab showing. */
    void selectPage(String title) {
        if (tabs == null || title == null) return;
        for (int i = 0; i < tabs.getTabCount(); i++) {
            if (tabs.getTitleAt(i).equalsIgnoreCase(title)) {
                tabs.setSelectedIndex(i);
                return;
            }
        }
    }

    private void buildUi() {
        // Tabs on the LEFT: eight pages of settings across the top wrap or scroll and read as a
        // ribbon; down the side they are a list that can grow (owner, 2026-09-30). Titles keep their
        // natural left alignment rather than being centred in a vertical strip.
        tabs = new JTabbedPane(javax.swing.JTabbedPane.LEFT);
        tabs.addTab("Source roots", buildRootsTab());
        tabs.addTab("Maven repos", buildMavenTab());
        tabs.addTab("Event processor", buildEpTab());
        tabs.addTab(PROVIDER_PAGE, buildLlmTab());
        tabs.addTab("Performance & S3", buildS3Tab());
        // M74 visual check: the tab grows when the full posture's mode and notes appear, so it scrolls rather than clips
        tabs.addTab("Assistant", scrollingTab(buildAssistantTab()));
        tabs.addTab("History", buildHistoryTab());
        tabs.addTab("Plugins", buildPluginsTab());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        buttons.setBorder(BorderFactory.createEmptyBorder(0, 12, 4, 12));
        JButton ok = new JButton("OK");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> { saveToConfig(); dispose(); });
        cancel.addActionListener(e -> dispose());
        buttons.add(cancel);
        buttons.add(ok);

        setLayout(new BorderLayout());
        add(tabs, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);

        getRootPane().setDefaultButton(ok);   // Enter = OK
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /**
     * A two-column form: right-aligned labels in column 0, fields that grow to fill column 1, an
     * optional muted help note spanning both columns, and glue that keeps rows pinned to the top.
     */
    private JPanel form(String helpHtml, Object... labelFieldPairs) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 6, 5, 6);
        int row = 0;
        for (int i = 0; i + 1 < labelFieldPairs.length; i += 2) {
            c.gridx = 0; c.gridy = row; c.weightx = 0;
            c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.LINE_END;
            p.add(UiText.label((String) labelFieldPairs[i]), c);

            c.gridx = 1; c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.LINE_START;
            p.add((JComponent) labelFieldPairs[i + 1], c);
            row++;
        }
        if (helpHtml != null) {
            c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL; c.anchor = GridBagConstraints.LINE_START;
            c.insets = new Insets(14, 6, 4, 6);
            p.add(mutedNote(helpHtml), c);
            row++;
        }
        c.gridx = 0; c.gridy = row; c.gridwidth = 2; c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        p.add(Box.createGlue(), c);
        return p;
    }

    /** Width of the wrapped help notes — also what keeps the dialog's packed width sane. */
    private static final int NOTE_WIDTH = 500;

    /**
     * A muted, <b>width-constrained</b> help note. Takes the inner HTML (no {@code <html>} wrapper);
     * the fixed CSS width makes the label wrap instead of reporting a one-line preferred width that
     * would stretch the whole dialog on {@code pack()}.
     */
    private static JLabel mutedNote(String innerHtml) {
        JLabel note = new JLabel("<html><div style='width:" + NOTE_WIDTH + "px'>" + innerHtml + "</div></html>");
        // review M74 usability #2: secondary ink at 4.5:1, never the DISABLED grey (3.0:1 on the panel)
        note.setForeground(UiTheme.mutedForeground());
        return note;
    }

    /** Log-source reader plugins (M31 D-P3) — the trust boundary stated in plain words. */
    private JPanel buildPluginsTab() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        java.nio.file.Path pluginsDir = java.nio.file.Path.of(
                System.getProperty("user.home"), ".fluxtion-analyser", "plugins");

        JTextArea warning = new JTextArea(
                "Log-source plugins let the analyser read audit records from other containers "
                + "(parquet, Chronicle, a database).\n\n"
                + "A plugin is a jar you place in:  " + pluginsDir + "\n\n"
                + "Installing a jar is ARBITRARY CODE EXECUTION — a plugin can do anything this "
                + "application can. Install only jars you trust. Nothing is ever downloaded or "
                + "bundled; without plugins this application is byte-identical to a plain build.\n\n"
                + "Add or remove plugins by adding/removing jars in that folder, then restart.");
        warning.setEditable(false);
        warning.setLineWrap(true);
        warning.setWrapStyleWord(true);
        warning.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(warning, BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        if (readerSummaries != null) {
            for (String line : readerSummaries.get()) model.addElement(line);
        }
        JList<String> list = UiText.plainCells(new JList<>(model));   // a plugin's own summary line
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createTitledBorder("Installed readers"));
        panel.add(scroll, BorderLayout.CENTER);
        return panel;
    }

    /** Supplied at construction by MainFrame: a line per installed reader + plugin load notes. */
    private final java.util.function.Supplier<java.util.List<String>> readerSummaries;

    private JPanel buildRootsTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        rootsList.setVisibleRowCount(8);
        rootsList.setTransferHandler(new FolderDropHandler());
        rootsList.setDropMode(DropMode.INSERT);
        rootsList.setDragEnabled(false);
        rootsList.setCellRenderer(UiText.plainRenderer(new RootCellRenderer()));
        panel.add(new JScrollPane(rootsList), BorderLayout.CENTER);

        JButton add = new JButton("Add…");
        JButton remove = new JButton("Remove");
        add.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            openWhereItIsUseful(fc);
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setMultiSelectionEnabled(true);
            fc.setDialogTitle("Add a project or Java source folder");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                for (File f : fc.getSelectedFiles()) addWithDetection(f.toPath());
            }
        });
        remove.addActionListener(e -> {
            int[] idx = rootsList.getSelectedIndices();
            for (int i = idx.length - 1; i >= 0; i--) rootsModel.remove(idx[i]);
        });
        panel.add(sideButtons(add, remove), BorderLayout.EAST);

        JLabel help = mutedNote("Add a <b>Java source root</b> — the folder that directly contains your top-level "
                + "packages (e.g. <code>.../src/main/java</code>). You can also pick a <b>project folder</b> and we will "
                + "find its <code>src/main/java</code> (incl. sub-modules). Drag folders in, or use Add…. "
                + "Roots that don't look like Java sources are shown in red.");
        help.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));

        JPanel north = new JPanel(new BorderLayout(0, 6));
        north.add(help, BorderLayout.NORTH);
        north.add(buildAnchorRow(), BorderLayout.SOUTH);
        panel.add(north, BorderLayout.NORTH);
        panel.setPreferredSize(new Dimension(640, 340));
        return panel;
    }

    /**
     * #20 — the workspace anchor, declared where the roots are.
     *
     * <p>It belongs on this tab and nowhere else: the anchor exists ONLY to make these roots
     * portable, the Project panel's warn rows already send people here, and until now the remedy
     * those rows named had no control anywhere in the app — the value could be set by hand-editing a
     * profile, which is not a remedy.
     *
     * <p>A combo, not a chooser, because the value is a DEPTH. Each entry states the directory it
     * resolves to and how many of the roots above it would become portable, so the choice is made by
     * comparison rather than by understanding what "{@code ../..}" means for this layout.
     */
    private JPanel buildAnchorRow() {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        JLabel label = new JLabel("Workspace anchor:");
        anchorCombo.setRenderer(UiText.plainRenderer(new DefaultListCellRenderer() {
            @Override public java.awt.Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean selected, boolean focused) {
                var c = (JLabel) super.getListCellRendererComponent(list, value, index, selected, focused);
                if (value instanceof telamin.fluxtion.audit.analyser.analyser.config
                        .WorkspaceAnchorChoices.Choice choice) {
                    UiText.plain(c).setText(choice.label());
                    c.setToolTipText(choice.dir() == null ? null : UiText.tooltip(choice.dir().toString()));
                }
                return c;
            }
        }));
        row.add(label, BorderLayout.WEST);
        row.add(anchorCombo, BorderLayout.CENTER);
        JLabel note = mutedNote("A root in a <b>sibling checkout</b> is outside this project, so it is stored as "
                + "an absolute or <code>~/…</code> path — correct on your machine and wrong on a colleague's. "
                + "Declaring how far above the project your checkouts live lets those roots be stored relative "
                + "to the project instead. It is a <b>depth</b>, not a location: nothing above the anchor can "
                + "ever be written into the profile.");
        note.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
        JPanel wrap = new JPanel(new BorderLayout(0, 0));
        wrap.add(row, BorderLayout.NORTH);
        wrap.add(note, BorderLayout.CENTER);
        return wrap;
    }

    /** Fill the anchor combo from the live config; called whenever the roots list is loaded. */
    private void loadAnchorChoices() {
        var projectRoot = telamin.fluxtion.audit.analyser.analyser.config.ExchangeDir.projectRoot(config);
        var roots = new ArrayList<String>();
        for (int i = 0; i < rootsModel.size(); i++) roots.add(rootsModel.get(i));
        var choices = telamin.fluxtion.audit.analyser.analyser.config.WorkspaceAnchorChoices.withCurrent(
                projectRoot, roots, System.getProperty("user.home"), config.workspaceRoot);
        anchorCombo.setModel(new DefaultComboBoxModel<>(choices.toArray(
                new telamin.fluxtion.audit.analyser.analyser.config.WorkspaceAnchorChoices.Choice[0])));
        String current = config.workspaceRoot == null ? "" : config.workspaceRoot.trim();
        for (var choice : choices) {
            if (choice.anchor().equals(current)) {
                anchorCombo.setSelectedItem(choice);
                break;
            }
        }
        // with no project open the anchor has nothing to be relative TO, and storing one would be
        // storing it in the machine's own settings, where it means nothing
        boolean hasProject = projectRoot != null;
        anchorCombo.setEnabled(hasProject);
        anchorCombo.setToolTipText(hasProject ? null
                : "Open a project first — an anchor is declared by a project, relative to its root");
    }

    /** Adds a chosen folder, expanding a project dir to its detected Java source root(s). */
    private void addWithDetection(Path chosen) {
        // P5 (owner decision 2026-10-18): never the home folder or a credential folder — refused before it is scanned
        var verdict = telamin.fluxtion.audit.analyser.analyser.config.SourceRootPolicy.decide(chosen,
                telamin.fluxtion.audit.analyser.analyser.config.SourceRootPolicy.home(), null,
                telamin.fluxtion.audit.analyser.analyser.config.SourceRootPolicy.Caller.PERSON);
        if (!verdict.allowed()) {
            JOptionPane.showMessageDialog(this, UiText.inert(verdict.words()), "Source root", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<Path> detected = detectSourceRoots(chosen);
        if (detected.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "That folder doesn't look like a Java source root and has no src/main/java under it.\n"
                            + "It was added as-is — pick the folder that contains your top-level packages\n"
                            + "(e.g. .../src/main/java) if source lookups don't resolve.",
                    "Source root", JOptionPane.WARNING_MESSAGE);
            addRoot(chosen);
            return;
        }
        for (Path p : detected) addRoot(p);
    }

    /** Detects Java source roots from a chosen folder: itself if it looks like one, plus src/main/java (incl. sub-modules). */
    static List<Path> detectSourceRoots(Path dir) {
        Set<Path> out = new LinkedHashSet<>();
        if (dir == null || !Files.isDirectory(dir)) return new ArrayList<>(out);
        if (looksLikeSourceRoot(dir)) out.add(dir);
        Path smj = dir.resolve("src/main/java");
        if (Files.isDirectory(smj)) out.add(smj);
        try (Stream<Path> children = Files.list(dir)) {
            children.filter(Files::isDirectory).forEach(c -> {
                Path s = c.resolve("src/main/java");
                if (Files.isDirectory(s)) out.add(s);
            });
        } catch (IOException ignore) {
            // unreadable dir — nothing more to detect
        }
        return new ArrayList<>(out);
    }

    /** A dir is a source root if it is named {@code java} or directly contains a top-level package dir. */
    static boolean looksLikeSourceRoot(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return false;
        if ("java".equals(dir.getFileName() == null ? "" : dir.getFileName().toString())) return true;
        for (String pkg : new String[]{"com", "org", "io", "net", "co", "uk", "ai", "dev", "gov", "app"}) {
            if (Files.isDirectory(dir.resolve(pkg))) return true;
        }
        return false;
    }

    /** Marks roots that don't look like Java source roots in red so mistakes are visible. */
    private static final class RootCellRenderer extends DefaultListCellRenderer {
        RootCellRenderer() {
            UiText.plain(this);        // SF-2: a root is a profile's path
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean sel, boolean focus) {
            super.getListCellRendererComponent(list, value, index, sel, focus);
            boolean ok = value instanceof String s && looksLikeSourceRoot(Path.of(s));
            if (!sel) setForeground(ok ? java.awt.Color.BLACK : new java.awt.Color(0xB31D28));
            if (!ok) setText(value + "   (not a Java source root?)");
            return this;
        }
    }

    /** Uniform right-hand button stack for the list tabs (roots / maven repos / event processors). */
    private static JPanel sideButtons(JButton... buttons) {
        JPanel side = new JPanel(new GridLayout(0, 1, 4, 4));
        int w = 96;
        for (JButton b : buttons) w = Math.max(w, b.getPreferredSize().width);
        for (JButton b : buttons) {
            b.setPreferredSize(new Dimension(w, b.getPreferredSize().height));
            side.add(b);
        }
        JPanel sideWrap = new JPanel(new BorderLayout());
        sideWrap.add(side, BorderLayout.NORTH);
        return sideWrap;
    }

    private JPanel buildEpTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        epList.setVisibleRowCount(8);
        epList.setCellRenderer(UiText.plainRenderer(new DefaultListCellRenderer() {
            { UiText.plain(this); }        // SF-2: a processor FQN is a profile value
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                if (value instanceof String s && s.equals(activeEp)) {
                    setFont(getFont().deriveFont(java.awt.Font.BOLD));
                    setText(s + "   (active)");
                }
                return this;
            }
        }));
        panel.add(new JScrollPane(epList), BorderLayout.CENTER);

        JButton add = new JButton("Add…");
        JButton edit = new JButton("Edit…");
        JButton remove = new JButton("Remove");
        JButton setActive = new JButton("Set active");
        add.addActionListener(e -> {
            String fqn = JOptionPane.showInputDialog(this,
                    "Fully-qualified EventProcessor class name:", "Add event processor",
                    JOptionPane.PLAIN_MESSAGE);
            if (fqn == null || fqn.isBlank()) return;
            fqn = fqn.trim();
            if (!epModel.contains(fqn)) epModel.addElement(fqn);
            if (activeEp == null) activeEp = fqn;
            epList.setSelectedValue(fqn, true);
            epList.repaint();
        });
        edit.addActionListener(e -> editSelectedEp());
        remove.addActionListener(e -> {
            int[] idx = epList.getSelectedIndices();
            for (int i = idx.length - 1; i >= 0; i--) {
                String removed = epModel.remove(idx[i]);
                if (removed.equals(activeEp)) activeEp = epModel.isEmpty() ? null : epModel.get(0);
            }
            epList.repaint();
        });
        setActive.addActionListener(e -> {
            String sel = epList.getSelectedValue();
            if (sel != null) { activeEp = sel; epList.repaint(); }
        });
        epList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedEp();   // double-click edits the FQN in place
            }
        });
        panel.add(sideButtons(add, edit, remove, setActive), BorderLayout.EAST);

        JLabel help = mutedNote("The processors whose node fields map instanceIds to source files. The "
                + "<b>active</b> one resolves clicks in the detail/source views; it is auto-inferred when a "
                + "log is loaded and can be overridden here (Set active). Double-click an entry to edit its name.");
        help.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        panel.add(help, BorderLayout.NORTH);
        panel.setPreferredSize(new Dimension(640, 300));
        return panel;
    }

    /** Edit the selected EventProcessor FQN in place (also reachable by double-clicking it). */
    private void editSelectedEp() {
        int i = epList.getSelectedIndex();
        if (i < 0) return;
        String current = epModel.get(i);
        Object edited = JOptionPane.showInputDialog(this, "EventProcessor class name:",
                "Edit event processor", JOptionPane.PLAIN_MESSAGE, null, null, current);
        if (edited == null || edited.toString().isBlank()) return;
        String fqn = edited.toString().trim();
        if (current.equals(activeEp)) activeEp = fqn;
        epModel.set(i, fqn);
        epList.repaint();
    }

    private JPanel buildMavenTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        mavenList.setVisibleRowCount(8);
        panel.add(new JScrollPane(mavenList), BorderLayout.CENTER);

        JButton add = new JButton("Add…");
        JButton remove = new JButton("Remove");
        add.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            fc.setMultiSelectionEnabled(true);
            fc.setDialogTitle("Add a local Maven repository (e.g. ~/.m2/repository)");
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                for (File f : fc.getSelectedFiles()) {
                    String s = f.toPath().toString();
                    if (!mavenModel.contains(s)) mavenModel.addElement(s);
                }
            }
        });
        remove.addActionListener(e -> {
            int[] idx = mavenList.getSelectedIndices();
            for (int i = idx.length - 1; i >= 0; i--) mavenModel.remove(idx[i]);
        });
        panel.add(sideButtons(add, remove), BorderLayout.EAST);

        JPanel south = new JPanel(new BorderLayout());
        mavenDisabled.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        south.add(mavenDisabled, BorderLayout.WEST);
        panel.add(south, BorderLayout.SOUTH);

        JLabel help = mutedNote("Local Maven repositories searched for <code>*-sources.jar</code> files when a "
                + "class can't be found under the source roots (default: <code>~/.m2/repository</code>). "
                + "The first lookup indexes the repositories in the background; results are cached for the session.");
        help.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
        panel.add(help, BorderLayout.NORTH);
        panel.setPreferredSize(new Dimension(640, 300));
        return panel;
    }

    private JPanel buildLlmTab() {
        return form("Used by the assistant tab. The API key is stored in <b>cleartext</b> in "
                        + "<code>~/.fluxtion-analyser/config</code> (local, single-user tool).",
                "Provider:", providerCombo,
                "Model:", modelField,
                "API key:", apiKeyField,
                "Base URL (optional):", baseUrlField);
    }

    private JPanel buildS3Tab() {
        return form("Logs at or below the <b>memory threshold</b> load into heap; larger files are "
                        + "<b>memory-mapped</b> (index built off-heap, records read on demand — scales past 2 GB). "
                        + "<code>0</code> forces memory-mapped always. Applies to the next file opened.<br><br>"
                        + "Open <code>s3://bucket/key</code> logs via <b>Audit log → Open log from S3…</b> — uses your local "
                        + "<code>aws</code> CLI &amp; credentials (profiles / SSO); no AWS SDK required. Large S3 objects "
                        + "stream to a temp file and take the memory-mapped path too.",
                "Memory threshold (MB):", leftWrap(memThresholdSpinner),
                "AWS profile (optional):", awsProfileField,
                "AWS region (optional):", awsRegionField);
    }

    /**
     * Wraps a control so the form's horizontal fill doesn't stretch it full-width. A single field (a spinner) is given
     * a fixed 110 px; a ROW of controls keeps its natural width — forcing 110 px on a row clipped everything after its
     * first control (the "per turn, across its rounds" spinner beside Max actions per reply).
     */
    static JComponent leftWrap(JComponent inner) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        if (!(inner instanceof JPanel)) inner.setPreferredSize(new Dimension(110, inner.getPreferredSize().height));
        p.add(inner);
        return p;
    }

    /** A tab whose content tracks the tab's width and scrolls vertically when it is taller than the dialog. */
    static JScrollPane scrollingTab(JComponent content) {
        JPanel holder = new TracksWidth(new BorderLayout());
        holder.add(content, BorderLayout.CENTER);
        JScrollPane sp = new JScrollPane(holder, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        sp.setBorder(BorderFactory.createEmptyBorder());
        sp.getVerticalScrollBar().setUnitIncrement(16);
        return sp;
    }

    /**
     * Follows the viewport's width (so text wraps instead of scrolling sideways) only while the viewport is at least as
     * wide as the content needs: narrower, a GridBag falls back to its components' MINIMUM sizes and the fields collapse
     * to empty slivers (the M74 visual check found "Max action rounds" and the model field drawn that way once the
     * vertical scrollbar appeared), so then it scrolls sideways instead. Never its height.
     */
    static final class TracksWidth extends JPanel implements Scrollable {
        TracksWidth(LayoutManager layout) {
            super(layout);
        }

        @Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
        @Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 16; }
        @Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return Math.max(16, r.height - 32); }
        @Override public boolean getScrollableTracksViewportWidth() {
            return getParent() instanceof JViewport v && v.getWidth() >= getPreferredSize().width;
        }
        @Override public boolean getScrollableTracksViewportHeight() { return false; }
    }

    /**
     * M74 UX (owner, 2026-10-17): every assistant option, grouped by the profile it belongs to — Safe analysis, Full Claude
     * Code, the in-app assistant — and then what applies to every assistant. The four cards in the assistant's header set
     * these in one step; this page keeps every option for power users. A child is enabled only while its parent is on:
     * the full switch, the model and the spend limit need <i>Allow the analyser to start Claude Code</i>, the mode needs
     * the full switch, the amount needs its box.
     */
    /**
     * M77-2 (spec-ai-setup-wizard.md §5 entry 6): what <i>Set up AI…</i> at the top of this page does — set by the frame,
     * which reports it as the person's act. Settings closes first, unsaved: it is modal (the wizard could not be used
     * behind it), and a later Save of its stale switches would undo what the wizard applied.
     */
    static volatile Runnable setUpAi;

    private JPanel buildAssistantTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.gridwidth = 1; c.weightx = 1; c.anchor = GridBagConstraints.LINE_START;
        c.fill = GridBagConstraints.HORIZONTAL; c.insets = new Insets(0, 0, 10, 0);
        c.gridy = 0;
        JButton setUp = UiText.button(telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines.SET_UP_LINK);
        setUp.getAccessibleContext().setAccessibleName("Set up AI");
        setUp.setToolTipText(UiText.tooltip("Closes Settings without saving and opens the step-by-step set-up; this page "
                + "keeps every option"));
        setUp.addActionListener(e -> {
            Runnable r = setUpAi;
            dispose();
            if (r != null) SwingUtilities.invokeLater(r);
        });
        JPanel setUpRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        setUpRow.add(setUp);
        p.add(setUpRow, c);
        c.gridy++;
        // final review #15: the header control's label as it reads (AssistantVocabularyTest holds it to
        // AssistantPanel.assistantControlText, so the two never drift); a literal, as every HTML door's text is
        p.add(mutedNote("Choose how the assistant works in one step from the control at the top of the assistant's "
                + "panel that names the profile in force (for example <b>Assistant: Safe analysis ▾</b>): "
                + "<b>Safe analysis</b>, <b>Full Claude Code</b>, <b>In-app assistant</b> or <b>Off</b>. Every option "
                + "those cards set is here, grouped by the profile it belongs to. All of them are this machine's and "
                + "this user's only — never in a project, a shared settings file or a bundle; an administrator cannot "
                + "lock them yet. A shared settings file carries only the in-app assistant's two action limits; "
                + "External MCP/REST access, file exchange and every other switch here are set on each machine."), c);
        c.gridy = 1;
        p.add(buildClaudeCodeSection(), c);
        c.gridy = 2;
        p.add(buildFullSection(), c);
        c.gridy = 3;
        p.add(buildInAppSection(), c);
        c.gridy = 4;
        p.add(buildEveryAssistantSection(), c);
        c.gridy = 5; c.weighty = 1; c.fill = GridBagConstraints.BOTH;
        p.add(Box.createGlue(), c);
        claudeCodeToggle.addActionListener(e -> childrenFollowParents());
        claudeCodeBudgetToggle.addActionListener(e -> childrenFollowParents());
        return p;
    }

    /** A titled group, its rows in a two-column grid. */
    private static JPanel group(String title) {
        JPanel box = new JPanel(new GridBagLayout());
        box.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createTitledBorder(title),
                BorderFactory.createEmptyBorder(2, 4, 4, 4)));
        return box;
    }

    /** M74 §5.1 / UX: Safe analysis — Claude Code in the Assistant tab, reading the evidence only. */
    private JPanel buildClaudeCodeSection() {
        JPanel box = group("Safe analysis — Claude Code reads your evidence");
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridwidth = 2; g.anchor = GridBagConstraints.LINE_START; g.insets = new Insets(2, 4, 2, 4);
        claudeCodeToggle.setToolTipText("Lets the Assistant tab's Start button run YOUR installed claude, logged in as you, "
                + "as Safe analysis: reading files in the folder (Read, Grep and Glob), the analyser's own "
                + "tools (those that change what is open or write a file only after you approve each call), the folder's "
                + "CLAUDE.md — no Bash, no edits, no web, none of your skills, hooks or "
                + "MCP servers. Off by default; this machine only.");
        claudeCodeBudgetToggle.setToolTipText("For API-key billing: stop a Claude Code session once it has spent this much. "
                + "Off by default — on a Claude plan your plan's own usage limits apply anyway.");
        g.gridy = 0; box.add(claudeCodeToggle, g);
        g.gridwidth = 1;
        g.gridy = 1; g.gridx = 0; g.insets = new Insets(2, 24, 2, 4); box.add(claudeCodeModelLabel, g);
        g.gridx = 1; g.insets = new Insets(2, 4, 2, 4); box.add(claudeCodeModelField, g);
        g.gridy = 2; g.gridx = 0; g.insets = new Insets(2, 20, 2, 4); box.add(claudeCodeBudgetToggle, g);
        g.gridx = 1; g.insets = new Insets(2, 4, 2, 4); box.add(leftWrap(claudeCodeBudgetSpinner), g);
        g.gridy = 3; g.gridx = 0; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        box.add(mutedNote("Start runs <b>your own</b> <code>claude</code> CLI, on your own Claude account; the analyser "
                + "never reads or stores your Claude Code credentials. Start contacts Anthropic before your first Send "
                + "(and in Full Claude Code runs hooks); no record content is sent until you press Send. The spend limit "
                + "is for API-key billing — on a Claude plan, your plan's own usage limits apply anyway. The model and "
                + "limit are Claude Code's own (not the in-app provider's). <b>"
                + telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.CLAUDE_CODE_SENDS + "</b>"), g);
        return box;
    }

    /** M74 §5.1 / UX: Full Claude Code — the second switch, its warning and its mode. */
    private JPanel buildFullSection() {
        JPanel box = group("Full Claude Code — your own set-up, skills and tools");
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridwidth = 2; g.anchor = GridBagConstraints.LINE_START; g.insets = new Insets(2, 4, 2, 4);
        claudeCodeFullToggle.setToolTipText("Your own Claude Code — your settings, skills, hooks and MCP servers, and your "
                + "tools as far as your permission rules and the mode allow. Turning it on shows a warning first; it needs "
                + "Allow the analyser to start Claude Code.");
        claudeCodeFullToggle.addActionListener(e -> onFullToggled());
        claudeCodeModeCombo.addActionListener(e -> claudeCodeModeNote.setText(modeNote(
                String.valueOf(claudeCodeModeCombo.getSelectedItem()))));
        claudeCodeModeCombo.getAccessibleContext().setAccessibleName("Full Claude Code mode");
        g.gridy = 0; g.weightx = 1; box.add(claudeCodeFullToggle, g);
        g.weightx = 0;
        g.gridwidth = 1;
        g.gridy = 1; g.gridx = 0; g.insets = new Insets(2, 24, 2, 4); box.add(claudeCodeModeLabel, g);
        g.gridx = 1; g.insets = new Insets(2, 4, 2, 4); box.add(claudeCodeModeCombo, g);
        g.gridy = 2; g.gridx = 0; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL; g.weightx = 1;
        g.insets = new Insets(2, 24, 2, 4);
        box.add(claudeCodeModeNote, g);
        return box;
    }

    /** M74 UX: the in-app assistant — its provider is on the LLM page; how far it may act is here. */
    private JPanel buildInAppSection() {
        JPanel box = group("In-app assistant — your API key");
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0; g.gridwidth = 2; g.anchor = GridBagConstraints.LINE_START; g.insets = new Insets(2, 4, 2, 4);
        JButton llm = UiText.button("In-app assistant: provider, model and key…");
        llm.addActionListener(e -> selectPage("LLM"));
        g.gridy = 0; box.add(llm, g);
        g.gridy = 1; box.add(actionsInProcess, g);
        g.gridx = 0; g.gridwidth = 2; g.fill = GridBagConstraints.HORIZONTAL;
        g.gridy = 4; box.add(mutedNote("<b>"
                + telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles.IN_APP_SENDS + "</b>"), g);
        g.fill = GridBagConstraints.NONE;
        g.gridwidth = 1; g.insets = new Insets(6, 4, 3, 8);
        g.gridy = 2; g.gridx = 0; g.anchor = GridBagConstraints.LINE_END; box.add(new JLabel("Max action rounds:"), g);
        g.gridx = 1; g.weightx = 1; g.anchor = GridBagConstraints.LINE_START; box.add(leftWrap(maxRoundsSpinner), g);
        g.weightx = 0;
        g.gridy = 3; g.gridx = 0; g.anchor = GridBagConstraints.LINE_END; box.add(new JLabel("Max actions per reply:"), g);
        JPanel caps = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 6, 0));
        caps.add(maxActionsSpinner);
        caps.add(new JLabel("per turn, across its rounds:"));
        maxTurnActionsSpinner.setToolTipText("The onboard assistant's budget for one question: when it is spent the turn "
                + "ends and says so, whatever the per-reply cap and round limit would still allow");
        caps.add(maxTurnActionsSpinner);
        g.gridx = 1; g.weightx = 1; g.anchor = GridBagConstraints.LINE_START; box.add(leftWrap(caps), g);
        return box;
    }

    /** What applies to every assistant: the local bridge, file exchange, project creation. */
    private JPanel buildEveryAssistantSection() {
        JPanel box = group("Every assistant — the bridge, files and projects");
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.LINE_START; c.fill = GridBagConstraints.NONE;
        c.gridwidth = 2; c.insets = new Insets(3, 4, 3, 4);
        // M77-4 (OD-77.12): no Connect an AI client… here — Set up AI… at the top of this page is the door, and the
        // client dialog is behind its Your own AI client ▸ More options…
        c.gridy = 1; box.add(actionsRest, c);
        c.gridy = 2; box.add(exportsToggle, c);
        createProjectsToggle.setToolTipText("Lets an assistant (MCP/REST or the in-app one) create a project with "
                + "open {project, create}: it writes .analyser/project.fluxtion-settings in a directory it names, never "
                + "over an existing one. Project ▸ New project… is never gated by this. This machine only — never in a "
                + "project, a shared settings file or a bundle.");
        c.gridy = 3; box.add(createProjectsToggle, c);

        c.gridwidth = 1; c.insets = new Insets(6, 4, 3, 8);
        c.gridy = 4; c.gridx = 0; c.weightx = 0; c.anchor = GridBagConstraints.LINE_END; box.add(new JLabel("Exchange folder (this machine):"), c);
        c.gridx = 1; c.weightx = 1; c.anchor = GridBagConstraints.LINE_START; c.fill = GridBagConstraints.HORIZONTAL;
        JPanel dirRow = new JPanel(new BorderLayout(6, 0));
        dirRow.setOpaque(false);
        dirRow.add(exportDirField, BorderLayout.CENTER);
        JButton browseExport = new JButton("Browse…");
        browseExport.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(exportDirField.getText().isBlank() ? null : exportDirField.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                exportDirField.setText(fc.getSelectedFile().getAbsolutePath());
            }
        });
        dirRow.add(browseExport, BorderLayout.EAST);
        box.add(dirRow, c);

        // #21: the project's own preference, typed rather than browsed — it is a path INSIDE the
        // repository, and a file chooser would invite the absolute machine path the gate refuses.
        c.gridy = 5; c.gridx = 0; c.weightx = 0; c.fill = GridBagConstraints.NONE; c.anchor = GridBagConstraints.LINE_END;
        box.add(new JLabel("Exchange folder (this project):"), c);
        c.gridx = 1; c.weightx = 1; c.anchor = GridBagConstraints.LINE_START; c.fill = GridBagConstraints.HORIZONTAL;
        projectExchangeField.setToolTipText("Relative to the project root, e.g. src/report/shared — "
                + "no '..', no absolute path. Blank means use the exchange folder above.");
        box.add(projectExchangeField, c);

        c.gridx = 0; c.gridy = 6; c.gridwidth = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 4, 4, 4);
        box.add(mutedNote("The in-app assistant can count and summarise the log and change the views (filter, chart, "
                + "go to a record, flag) as it answers, without opening any port. <b>External MCP/REST access</b> opens a "
                + "<b>loopback-only</b> endpoint (127.0.0.1), guarded by a per-run token, for an AI client on this "
                + "machine: the URL and token are written to <code>~/.fluxtion-analyser/rest-endpoint</code>, readable "
                + "only by you, and the console shows only the token's first 8 characters. "
                + "<b>File exports are off by default</b>: when allowed, the screenshot/report verbs may write "
                + "<b>only inside the exchange folder</b> above; external-series reads are confined to the same directory (or files you pick in a chooser), and writes never overwrite an existing file."), c);
        return box;
    }

    /**
     * M74 UX (the holistic review): a child is enabled only while its parent is on — the full switch, the model and the
     * spend limit need Claude Code allowed; the amount needs its box; the mode needs the full switch (and is shown only
     * then, as before).
     */
    void childrenFollowParents() {
        boolean allowed = claudeCodeToggle.isSelected();
        claudeCodeFullToggle.setEnabled(allowed);
        claudeCodeModelLabel.setEnabled(allowed);
        claudeCodeModelField.setEnabled(allowed);
        claudeCodeBudgetToggle.setEnabled(allowed);
        claudeCodeBudgetSpinner.setEnabled(allowed && claudeCodeBudgetToggle.isSelected());
        boolean full = allowed && claudeCodeFullToggle.isSelected();
        claudeCodeModeLabel.setEnabled(full);
        claudeCodeModeCombo.setEnabled(full);
        claudeCodeModeNote.setEnabled(full);
    }

    /**
     * §5.1: turning the full switch ON shows the warning as a confirmation, default Cancel; Cancel leaves it off.
     * Turning it off asks nothing.
     */
    private void onFullToggled() {
        if (claudeCodeFullToggle.isSelected() && !confirmFullPosture(this)) claudeCodeFullToggle.setSelected(false);
        fullPostureShown();
    }

    /** The mode is shown only while the full switch is on. */
    private void fullPostureShown() {
        boolean on = claudeCodeFullToggle.isSelected();
        claudeCodeModeLabel.setVisible(on);
        claudeCodeModeCombo.setVisible(on);
        claudeCodeModeNote.setVisible(on);
        childrenFollowParents();
        revalidate();
        repaint();
        if (isShowing()) SwingUtilities.invokeLater(this::growToFit);
    }

    /**
     * M74 visual check: the full posture's rows make the Assistant tab taller than the dialog packed for it, so the
     * dialog grows to its preferred size, never past the screen's usable area; the tab scrolls beyond that.
     */
    private void growToFit() {
        Dimension want = getPreferredSize();
        java.awt.Rectangle screen = getGraphicsConfiguration() == null ? null
                : java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = Math.max(getWidth(), want.width);
        int h = Math.max(getHeight(), want.height);
        if (screen != null) {
            w = Math.min(w, screen.width);
            h = Math.min(h, screen.height);
        }
        if (w != getWidth() || h != getHeight()) {
            setSize(w, h);
            if (screen != null && getY() + h > screen.y + screen.height) setLocation(getX(), Math.max(screen.y, screen.y + screen.height - h));
            validate();
        }
    }

    static final String FULL_POSTURE_TITLE = "Full Claude Code — read this first";
    /** The page holding the in-app assistant's provider, model and API key (Configure provider opens it). */
    static final String PROVIDER_PAGE = "LLM";
    static final String FULL_POSTURE_CANCEL = "Cancel";

    /**
     * The confirmation itself: the warning in a plain-text area, under the SafeChoice rule — <i>Turn on full Claude
     * Code</i> alone at the left, never default-capable; Cancel at the right, the default, and Enter, Escape and closing
     * the window choose it. Review PR151 F4: the profile cards ask through the same dialog ({@link #fullPostureDialog}).
     */
    static boolean confirmFullPosture(java.awt.Component parent) {
        SafeChoice.Asked a = fullPostureDialog(parent, true);
        a.dialog().setVisible(true);
        return a.chosenRisky();
    }

    /** The confirmation, built and not shown; {@code modal} false is for a frame test. */
    static SafeChoice.Asked fullPostureDialog(java.awt.Component parent, boolean modal) {
        return SafeChoice.build(parent, FULL_POSTURE_TITLE, fullPostureWarningArea(),
                UIManager.getIcon("OptionPane.warningIcon"), FULL_POSTURE_TURN_ON, FULL_POSTURE_CANCEL, modal);
    }

    static JTextArea fullPostureWarningArea() {
        JTextArea a = new JTextArea(FULL_POSTURE_WARNING, 12, 56);
        a.setEditable(false);
        a.setLineWrap(true);
        a.setWrapStyleWord(true);
        a.setOpaque(false);
        a.getAccessibleContext().setAccessibleName("Full Claude Code warning");
        return a;
    }

    private JPanel buildHistoryTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.LINE_START; c.fill = GridBagConstraints.NONE;
        c.gridy = 0; c.insets = new Insets(2, 0, 10, 0);
        p.add(new JLabel("Clear remembered items (applies immediately):"), c);
        c.insets = new Insets(3, 0, 3, 0);
        JButton search = clearButton("Clear search history", config.searchHistory::clear);
        JButton graphs = clearButton("Clear saved graphs", config.savedGraphs::clear);
        // one button for both recent lists and both "last opened" paths: they are one idea to the user
        // ("forget what I have had open"), and splitting them would leave the topology quietly remembered
        JButton recents = clearButton("Clear recent files", () -> {
            config.recentFiles.clear();
            config.recentGraphml.clear();
            config.logFile = null;
            config.graphmlFile = null;
        });
        JButton view = clearButton("Reset topology view", config::clearTopologyView);
        view.setToolTipText("Zoom, pan, orientation, spacing and label size for the Topology tab");
        c.gridy = 1; p.add(search, c);
        c.gridy = 2; p.add(graphs, c);
        c.gridy = 3; p.add(recents, c);
        c.gridy = 4; p.add(view, c);
        c.gridy = 5; c.insets = new Insets(12, 0, 3, 0);
        JButton all = new JButton("Clear all");
        all.setPreferredSize(new Dimension(210, all.getPreferredSize().height));
        all.addActionListener(e -> {
            for (JButton b : new JButton[]{search, graphs, recents, view}) if (b.isEnabled()) b.doClick();
            all.setText("Clear all  ✓");
            all.setEnabled(false);
        });
        p.add(all, c);
        c.gridy = 6; c.weightx = 1; c.weighty = 1; c.fill = GridBagConstraints.BOTH;
        p.add(Box.createGlue(), c);
        return p;
    }

    private JButton clearButton(String text, Runnable action) {
        JButton b = UiText.button(text);
        b.setPreferredSize(new Dimension(210, b.getPreferredSize().height));
        b.addActionListener(e -> {
            action.run();
            if (onSaved != null) onSaved.run();   // persist
            b.setText(text + "  ✓");
            b.setEnabled(false);
        });
        return b;
    }

    private void addRoot(Path p) {
        if (telamin.fluxtion.audit.analyser.analyser.config.SourceRootPolicy.tier1(p,
                telamin.fluxtion.audit.analyser.analyser.config.SourceRootPolicy.home()) != null) return;   // P5
        String s = p.toString();
        if (!rootsModel.contains(s)) rootsModel.addElement(s);
    }

    private void loadFromConfig() {
        rootsModel.clear();
        for (String r : config.sourceRoots) rootsModel.addElement(r);
        loadAnchorChoices();      // #20: the counts are computed FROM the roots, so this follows them
        epModel.clear();
        for (String fqn : config.eventProcessorFqns) if (!epModel.contains(fqn)) epModel.addElement(fqn);
        activeEp = config.selectedEventProcessor;
        if (activeEp != null && !activeEp.isBlank() && !epModel.contains(activeEp)) epModel.addElement(activeEp);
        mavenModel.clear();
        for (String r : config.mavenRepos) mavenModel.addElement(r);
        mavenDisabled.setSelected(!config.searchMavenRepos);
        providerCombo.setSelectedItem(config.llmProvider);
        modelField.setText(config.llmModel);
        baseUrlField.setText(config.llmBaseUrl);
        apiKeyField.setText(config.apiKey);
        awsProfileField.setText(config.awsProfile);
        awsRegionField.setText(config.awsRegion);
        memThresholdSpinner.setValue(config.memoryThresholdMb);
        actionsInProcess.setSelected(config.assistantActionsInProcess);
        actionsRest.setSelected(config.assistantActionsRest);
        loadedRest = config.assistantActionsRest;
        exportsToggle.setSelected(config.assistantExports);
        createProjectsToggle.setSelected(config.assistantCreateProjects);
        claudeCodeToggle.setSelected(config.claudeCodeAllowed);
        claudeCodeFullToggle.setSelected(config.claudeCodeFull);
        claudeCodeModeCombo.setSelectedItem(telamin.fluxtion.audit.analyser.analyser.config.ConfigStore
                .fullMode(config.claudeCodeFullMode));
        claudeCodeModeNote.setText(modeNote(String.valueOf(claudeCodeModeCombo.getSelectedItem()))
                + (config.claudeCodeFullModeReadAsDefault
                ? "\nThe stored mode was not one the analyser offers; it reads as default." : ""));
        claudeCodeModelField.setText(config.claudeCodeModel == null ? "" : config.claudeCodeModel);
        claudeCodeBudgetToggle.setSelected(config.claudeCodeBudgetUsd != null);
        claudeCodeBudgetSpinner.setValue(config.claudeCodeBudgetUsd == null ? 5.0
                : Math.max(0.5, Math.min(1000.0, config.claudeCodeBudgetUsd)));
        claudeCodeBudgetSpinner.setEnabled(claudeCodeBudgetToggle.isSelected());
        fullPostureShown();
        loadedAssistant = assistantSwitches();
        exportDirField.setText(config.assistantExportDir == null ? "" : config.assistantExportDir);
        projectExchangeField.setText(config.projectExchangeDir == null ? "" : config.projectExchangeDir);
        maxRoundsSpinner.setValue(config.maxActionRounds);
        maxActionsSpinner.setValue(config.maxActionsPerReply);
        maxTurnActionsSpinner.setValue(config.maxActionsPerTurn);
    }

    /** M77-3: the external access box as Settings opened with it — a save asks only when the person changed it. */
    private boolean loadedRest;

    /** What Settings shows of the assistant's switches when it opened, to tell whether a save changed any. */
    private java.util.List<Object> loadedAssistant = java.util.List.of();

    private java.util.List<Object> assistantSwitches() {
        return java.util.List.of(claudeCodeToggle.isSelected(), claudeCodeFullToggle.isSelected(),
                String.valueOf(providerCombo.getSelectedItem()), apiKeyField.getText().trim());
    }

    private void saveToConfig() {
        config.sourceRoots.clear();
        List<String> listed = new ArrayList<>();
        for (int i = 0; i < rootsModel.size(); i++) listed.add(rootsModel.get(i));
        // P5: a refused root never reaches the settings, whatever put it in the list
        config.sourceRoots.addAll(telamin.fluxtion.audit.analyser.analyser.config.SourceRootPolicy.keepAllowed(listed,
                telamin.fluxtion.audit.analyser.analyser.config.SourceRootPolicy.home(), null));
        // #20: refuseWorkspaceRoot is the authority and its text is shown verbatim. The combo can only
        // offer values that pass, so this fires for a hand-edited profile whose value was carried into
        // the list by withCurrent — the one path by which an unacceptable value can be selected.
        if (anchorCombo.isEnabled()
                && anchorCombo.getSelectedItem() instanceof telamin.fluxtion.audit.analyser.analyser.config
                        .WorkspaceAnchorChoices.Choice chosen) {
            var refused = telamin.fluxtion.audit.analyser.analyser.config.PathForm
                    .refuseWorkspaceRoot(chosen.anchor());
            if (refused.isPresent()) {
                JOptionPane.showMessageDialog(this, UiText.inert(refused.get()), "Workspace anchor",
                        JOptionPane.WARNING_MESSAGE);
            } else {
                config.workspaceRoot = chosen.isNone() ? "" : chosen.anchor();
            }
        }
        config.eventProcessorFqns.clear();
        for (int i = 0; i < epModel.size(); i++) config.eventProcessorFqns.add(epModel.get(i));
        config.selectedEventProcessor = activeEp != null ? activeEp
                : (epModel.isEmpty() ? null : epModel.get(0));
        config.mavenRepos.clear();
        for (int i = 0; i < mavenModel.size(); i++) config.mavenRepos.add(mavenModel.get(i));
        config.searchMavenRepos = !mavenDisabled.isSelected();
        config.llmProvider = String.valueOf(providerCombo.getSelectedItem());
        config.llmModel = modelField.getText().trim();
        config.llmBaseUrl = baseUrlField.getText().trim();
        config.apiKey = apiKeyField.getText().trim();
        config.awsProfile = awsProfileField.getText().trim();
        config.awsRegion = awsRegionField.getText().trim();
        config.memoryThresholdMb = (Integer) memThresholdSpinner.getValue();
        config.assistantActionsInProcess = actionsInProcess.isSelected();
        // M77-3 (spec-ai-setup-wizard.md §9.2, OD-77.4, review R1-8): a changed external access box is the person's ASK,
        // decided by actionListener — on asks for the same confirmation as every other door (it shows once Settings has
        // closed); off is at once. Settings never writes the setting itself.
        if (actionsRest.isSelected() != loadedRest) {
            ExternalAccessDoor.request(actionsRest.isSelected(),
                    telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.AccessOrigin.SETTINGS);
            loadedRest = actionsRest.isSelected();
        }
        config.assistantExports = exportsToggle.isSelected();
        config.assistantCreateProjects = createProjectsToggle.isSelected();
        config.claudeCodeAllowed = claudeCodeToggle.isSelected();
        config.claudeCodeFull = claudeCodeFullToggle.isSelected();   // on only through the warning (onFullToggled)
        config.claudeCodeFullMode = telamin.fluxtion.audit.analyser.analyser.config.ConfigStore.fullMode(
                String.valueOf(claudeCodeModeCombo.getSelectedItem()));
        config.claudeCodeFullModeReadAsDefault = false;
        String model = claudeCodeModelField.getText().trim();
        if (telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ClaudeCodeProvider.validModel(model)) {
            config.claudeCodeModel = model;
        } else {   // review S7: never something Claude Code could read as a flag
            JOptionPane.showMessageDialog(this, UiText.inert("\"" + model + "\" is not a Claude Code model name (letters, digits, "
                    + ". _ - [ ], not starting with -). The model setting was not changed."), "Claude Code model",
                    JOptionPane.WARNING_MESSAGE);
        }
        config.claudeCodeBudgetUsd = claudeCodeBudgetToggle.isSelected()
                ? ((Number) claudeCodeBudgetSpinner.getValue()).doubleValue() : null;
        config.assistantExportDir = exportDirField.getText().trim();
        // #21: refuse at the door, with the reason — a stored value that can never be honoured is
        // worse than none, because nothing later says why the exports went elsewhere
        String wantedExchange = projectExchangeField.getText().trim();
        if (wantedExchange.isEmpty()) {
            config.projectExchangeDir = "";
        } else {
            var refused = telamin.fluxtion.audit.analyser.analyser.config.Runbooks
                    .refusePointer("assistant.exchangeDir", wantedExchange);
            if (refused.isPresent()) {
                javax.swing.JOptionPane.showMessageDialog(this, UiText.inert(refused.get()),
                        "Exchange directory", javax.swing.JOptionPane.WARNING_MESSAGE);
            } else {
                config.projectExchangeDir = wantedExchange;
            }
        }
        config.maxActionRounds = (Integer) maxRoundsSpinner.getValue();
        config.maxActionsPerTurn = (Integer) maxTurnActionsSpinner.getValue();
        config.maxActionsPerReply = (Integer) maxActionsSpinner.getValue();
        // M74 UX: an assistant switch or the key changed here — the stored profile follows the switches again (Off ends
        // when an assistant is turned on); otherwise the person's card choice stands
        if (!assistantSwitches().equals(loadedAssistant)) {
            config.assistantProfile = telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles
                    .storedFromSwitches(config);   // review P3 N1: the one mapping
        }
        if (onSaved != null) onSaved.run();
    }

    /** Accepts dropped folders (or files → their parent dir) onto the source-roots list. */
    private final class FolderDropHandler extends TransferHandler {
        @Override public boolean canImport(TransferSupport s) {
            return s.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
        }

        @Override public boolean importData(TransferSupport s) {
            if (!canImport(s)) return false;
            try {
                Transferable t = s.getTransferable();
                @SuppressWarnings("unchecked")
                List<File> files = (List<File>) t.getTransferData(DataFlavor.javaFileListFlavor);
                for (File f : files) {
                    Path p = f.toPath();
                    addWithDetection(Files.isDirectory(p) ? p : p.getParent());
                }
                return true;
            } catch (Exception e) {
                return false;
            }
        }
    }
}
