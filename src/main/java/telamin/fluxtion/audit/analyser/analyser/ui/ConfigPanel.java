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
    private final Runnable onSaved;

    private final DefaultListModel<String> rootsModel = new DefaultListModel<>();
    private final JList<String> rootsList = new JList<>(rootsModel);
    private final DefaultListModel<String> epModel = new DefaultListModel<>();
    private final JList<String> epList = new JList<>(epModel);
    private String activeEp;   // the EventProcessor FQN in use (marked in the list)
    private final DefaultListModel<String> mavenModel = new DefaultListModel<>();
    private final JList<String> mavenList = new JList<>(mavenModel);
    private final JCheckBox mavenDisabled = new JCheckBox("Don't search local Maven repositories");
    private final JComboBox<String> providerCombo = new JComboBox<>(new String[]{"anthropic", "openai"});
    private final JTextField modelField = new JTextField(22);
    private final JTextField baseUrlField = new JTextField(22);
    private final JTextField apiKeyField = new JTextField(28);
    private final JTextField awsProfileField = new JTextField(18);
    private final JTextField awsRegionField = new JTextField(18);
    private final JSpinner memThresholdSpinner =
            new JSpinner(new SpinnerNumberModel(500, 0, 1_000_000, 50));
    private final JCheckBox actionsInProcess = new JCheckBox("Let the assistant build views from its replies (in-process)");
    private final JCheckBox actionsRest = new JCheckBox("Allow the assistant to drive the UI over localhost (REST)");
    private final JSpinner maxRoundsSpinner = new JSpinner(new SpinnerNumberModel(3, 1, 20, 1));
    private final JSpinner maxActionsSpinner = new JSpinner(new SpinnerNumberModel(20, 1, 200, 1));

    private ConfigPanel(JFrame owner, AppConfig config, Runnable onSaved) {
        super(owner, "Settings", true);
        this.config = config;
        this.onSaved = onSaved;
        buildUi();
        loadFromConfig();
        pack();
        setMinimumSize(new Dimension(620, getHeight()));
        setLocationRelativeTo(owner);
    }

    public static void show(JFrame owner, AppConfig config, Runnable onSaved) {
        new ConfigPanel(owner, config, onSaved).setVisible(true);
    }

    private void buildUi() {
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Source roots", buildRootsTab());
        tabs.addTab("Maven repos", buildMavenTab());
        tabs.addTab("Event processor", buildEpTab());
        tabs.addTab("LLM", buildLlmTab());
        tabs.addTab("Performance & S3", buildS3Tab());
        tabs.addTab("Assistant", buildAssistantTab());
        tabs.addTab("History", buildHistoryTab());

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
            p.add(new JLabel((String) labelFieldPairs[i]), c);

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
        java.awt.Color fg = UIManager.getColor("Label.disabledForeground");
        if (fg != null) note.setForeground(fg);
        return note;
    }

    private JPanel buildRootsTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        rootsList.setVisibleRowCount(8);
        rootsList.setTransferHandler(new FolderDropHandler());
        rootsList.setDropMode(DropMode.INSERT);
        rootsList.setDragEnabled(false);
        rootsList.setCellRenderer(new RootCellRenderer());
        panel.add(new JScrollPane(rootsList), BorderLayout.CENTER);

        JButton add = new JButton("Add…");
        JButton remove = new JButton("Remove");
        add.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
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
        panel.add(help, BorderLayout.NORTH);
        panel.setPreferredSize(new Dimension(640, 300));
        return panel;
    }

    /** Adds a chosen folder, expanding a project dir to its detected Java source root(s). */
    private void addWithDetection(Path chosen) {
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
        epList.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                    boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, value, index, sel, focus);
                if (value instanceof String s && s.equals(activeEp)) {
                    setFont(getFont().deriveFont(java.awt.Font.BOLD));
                    setText(s + "   (active)");
                }
                return this;
            }
        });
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
                        + "Open <code>s3://bucket/key</code> logs via <b>File → Open from S3…</b> — uses your local "
                        + "<code>aws</code> CLI &amp; credentials (profiles / SSO); no AWS SDK required. Large S3 objects "
                        + "stream to a temp file and take the memory-mapped path too.",
                "Memory threshold (MB):", leftWrap(memThresholdSpinner),
                "AWS profile (optional):", awsProfileField,
                "AWS region (optional):", awsRegionField);
    }

    /** Wraps a fixed-size control so the form's horizontal fill doesn't stretch it full-width. */
    private static JComponent leftWrap(JComponent inner) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        inner.setPreferredSize(new Dimension(110, inner.getPreferredSize().height));
        p.add(inner);
        return p;
    }

    private JPanel buildAssistantTab() {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0; c.anchor = GridBagConstraints.LINE_START; c.fill = GridBagConstraints.NONE;
        c.gridwidth = 2; c.insets = new Insets(3, 0, 3, 0);
        c.gridy = 0; p.add(actionsInProcess, c);
        c.gridy = 1; p.add(actionsRest, c);

        // label column stays at preferred width; the value column takes all spare width so the
        // spinners keep their preferred size instead of being squeezed / clipped on the right
        c.gridwidth = 1; c.insets = new Insets(6, 0, 3, 8);
        c.gridy = 2; c.gridx = 0; c.weightx = 0; c.anchor = GridBagConstraints.LINE_END; p.add(new JLabel("Max action rounds:"), c);
        c.gridx = 1; c.weightx = 1; c.anchor = GridBagConstraints.LINE_START; p.add(leftWrap(maxRoundsSpinner), c);
        c.gridy = 3; c.gridx = 0; c.weightx = 0; c.anchor = GridBagConstraints.LINE_END; p.add(new JLabel("Max actions per reply:"), c);
        c.gridx = 1; c.weightx = 1; c.anchor = GridBagConstraints.LINE_START; p.add(leftWrap(maxActionsSpinner), c);
        c.weightx = 0;

        c.gridx = 0; c.gridy = 4; c.gridwidth = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(14, 0, 4, 0);
        p.add(mutedNote("The assistant can <b>compute over the index</b> and <b>build curation</b> (filter / "
                + "graph / goto / flag) as it answers. <b>In-process</b> opens no port. <b>REST</b> exposes a "
                + "<b>loopback-only</b> endpoint (127.0.0.1) guarded by a per-run token, for an external agent that "
                + "can make HTTP calls — its URL + token appear in the status bar (and console) when enabled. "
                + "Read-only over the log; reversible."), c);
        c.gridy = 5; c.weighty = 1; c.fill = GridBagConstraints.BOTH;
        p.add(Box.createGlue(), c);
        return p;
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
        JButton b = new JButton(text);
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
        String s = p.toString();
        if (!rootsModel.contains(s)) rootsModel.addElement(s);
    }

    private void loadFromConfig() {
        rootsModel.clear();
        for (String r : config.sourceRoots) rootsModel.addElement(r);
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
        maxRoundsSpinner.setValue(config.maxActionRounds);
        maxActionsSpinner.setValue(config.maxActionsPerReply);
    }

    private void saveToConfig() {
        config.sourceRoots.clear();
        for (int i = 0; i < rootsModel.size(); i++) config.sourceRoots.add(rootsModel.get(i));
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
        config.assistantActionsRest = actionsRest.isSelected();
        config.maxActionRounds = (Integer) maxRoundsSpinner.getValue();
        config.maxActionsPerReply = (Integer) maxActionsSpinner.getValue();
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
