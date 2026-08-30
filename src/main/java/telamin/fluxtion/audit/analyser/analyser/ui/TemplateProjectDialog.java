package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.template.TemplateCatalogue;
import telamin.fluxtion.audit.analyser.analyser.config.ReferenceSet;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateClient;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Human confirmations for M19.5. Network/archive work stays outside this class and off the EDT. */
final class TemplateProjectDialog {

    /**
     * @param referenceGuide whether to offer a {@code CLAUDE.md} of canonical authoring links once the
     *     archive is open. Only one of the catalogue's onboarding templates ships agent instructions of
     *     its own; the rest arrive bare, and this is the one thing the analyser already has for that.
     */
    record Choice(TemplateClient.Download download, Path destination, boolean referenceGuide) { }

    /** A modeless, cancellable progress surface; work itself remains on {@code Background}. */
    static final class Progress {
        private final JDialog dialog;
        private final AtomicReference<Future<?>> task = new AtomicReference<>();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        private Progress(Component owner, String message, Runnable onCancel) {
            dialog = new JDialog(javax.swing.SwingUtilities.getWindowAncestor(owner),
                    "New project from template", Dialog.ModalityType.MODELESS);
            JLabel label = new JLabel(message);
            JProgressBar bar = new JProgressBar();
            bar.setIndeterminate(true);
            JButton cancel = new JButton("Cancel");
            cancel.addActionListener(e -> {
                cancelled.set(true);
                Future<?> future = task.get();
                if (future != null) future.cancel(true);
                dialog.dispose();
                if (onCancel != null) onCancel.run();
            });
            JPanel panel = new JPanel(new BorderLayout(8, 8));
            panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
            panel.add(label, BorderLayout.NORTH);
            panel.add(bar, BorderLayout.CENTER);
            panel.add(cancel, BorderLayout.SOUTH);
            dialog.setContentPane(panel);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            dialog.pack();
            dialog.setLocationRelativeTo(owner);
            dialog.setVisible(true);
        }

        void attach(Future<?> future) {
            task.set(future);
        }

        void finish() {
            dialog.dispose();
        }

        boolean cancelled() {
            return cancelled.get();
        }
    }

    private TemplateProjectDialog() { }

    static Progress showProgress(Component owner, String message, Runnable onCancel) {
        return new Progress(owner, message, onCancel);
    }

    static TemplateCatalogue.Entry chooseTemplate(Component owner, TemplateCatalogue.Selection selection) {
        if (selection == null || selection.entries().isEmpty()) {
            JOptionPane.showMessageDialog(owner, "The playground catalogue contains no templates.",
                    "New project from template", JOptionPane.WARNING_MESSAGE);
            return null;
        }
        JList<TemplateCatalogue.Entry> list = new JList<>(selection.entries().toArray(TemplateCatalogue.Entry[]::new));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> component, Object value, int index,
                                                                     boolean selected, boolean focus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(component, value, index, selected, focus);
                if (value instanceof TemplateCatalogue.Entry entry) label.setText(entry.name());
                return label;
            }
        });
        int initial = 0;
        for (int i = 0; i < selection.entries().size(); i++) {
            if (selection.entries().get(i).tagged("analyser")) {
                initial = i;
                break;
            }
        }
        list.setSelectedIndex(initial);
        JTextArea description = textArea(description(selection.entries().get(initial)), 5, 52);
        list.addListSelectionListener(e -> {
            TemplateCatalogue.Entry chosen = list.getSelectedValue();
            if (chosen != null) description.setText(description(chosen));
        });

        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        JPanel north = new JPanel(new java.awt.GridLayout(0, 1));
        // D-AX10: an experiment that silently downloads from somewhere else is how a rig artefact gets
        // mistaken for product behaviour. If the origin is overridden, the dialog SAYS which one.
        if (TemplateClient.originOverridden()) {
            JLabel banner = new JLabel("Templates are being read from " + TemplateClient.configuredOrigin()
                    + " (-D" + TemplateClient.ORIGIN_PROPERTY + "), not the Fluxtion playground.");
            banner.setForeground(UiTheme.warnForeground());
            north.add(banner);
        }
        if (!selection.note().isBlank()) north.add(new JLabel(selection.note()));
        if (north.getComponentCount() > 0) panel.add(north, BorderLayout.NORTH);
        JScrollPane templates = new JScrollPane(list);
        templates.setPreferredSize(new Dimension(620, 220));
        panel.add(templates, BorderLayout.CENTER);
        panel.add(new JScrollPane(description), BorderLayout.SOUTH);
        Object[] options = {"Use this template", "Cancel"};
        int answer = JOptionPane.showOptionDialog(owner, panel, "New project from template",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
        return answer == 0 ? list.getSelectedValue() : null;
    }

    static Choice chooseDestination(Component owner, TemplateCatalogue.Entry template,
                                    TemplateClient.Defaults defaults) {
        JTextField artifact = new JTextField(defaults.artifact(), 28);
        JTextField group = new JTextField(defaults.group(), 28);
        JTextField basePackage = new JTextField(defaults.basePackage(), 28);
        JTextField destination = new JTextField(
                Path.of(System.getProperty("user.home"), defaults.artifact()).toString(), 34);
        JButton browse = new JButton("Browse…");
        browse.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Choose the new project directory");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            Path current = pathOrNull(destination.getText());
            if (current != null) {
                Path existing = Files.isDirectory(current) ? current : current.getParent();
                if (existing != null && Files.isDirectory(existing)) chooser.setCurrentDirectory(existing.toFile());
            }
            if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
                destination.setText(chooser.getSelectedFile().toPath().toString());
            }
        });

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 0;
        row(form, c, 0, "Template", new JLabel(template.name()), null);
        row(form, c, 1, "Artifact", artifact, null);
        row(form, c, 2, "Group", group, null);
        row(form, c, 3, "Base package", basePackage, null);
        row(form, c, 4, "Project directory", destination, browse);

        // M35 removed the modals from the load path, so this is a checkbox on a dialog that already
        // exists rather than a second one after the download. Unchecked, like every other offer (M35.4).
        // Absent entirely when nothing is agreed, because a dead control is worse than no control.
        JCheckBox guide = new JCheckBox("Also create " + ReferenceSet.FILE_NAME
                + " with links to the canonical Fluxtion authoring docs");
        guide.setSelected(false);
        guide.setToolTipText("Skipped if the template already ships one — it is never overwritten.");
        boolean offerGuide = !ReferenceSet.agreed().isEmpty();
        if (offerGuide) row(form, c, 5, "", guide, null);

        JTextArea boundary = textArea("The analyser downloads and opens this project. It never runs code "
                + "from the archive; the next dialog shows copyable terminal commands.", 3, 58);
        boundary.setBackground(form.getBackground());
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(form, BorderLayout.CENTER);
        panel.add(boundary, BorderLayout.SOUTH);

        while (true) {
            Object[] options = {"Download and open", "Cancel"};
            int answer = JOptionPane.showOptionDialog(owner, panel, "Configure " + template.name(),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            if (answer != 0) return null;
            try {
                Path target = Path.of(destination.getText().strip()).toAbsolutePath().normalize();
                TemplateClient.Download request = new TemplateClient.Download(template, artifact.getText(),
                        group.getText(), basePackage.getText());
                if (target.getParent() == null || !Files.isDirectory(target.getParent())) {
                    throw new IllegalArgumentException("The project directory's parent must already exist.");
                }
                return new Choice(request, target, offerGuide && guide.isSelected());
            } catch (RuntimeException error) {
                JOptionPane.showMessageDialog(owner, error.getMessage(), "Check project details",
                        JOptionPane.WARNING_MESSAGE);
            }
        }
    }

    static void showCommands(Component owner, Path project, List<String> commands) {
        StringBuilder text = new StringBuilder("cd ").append(shellQuote(project.toString())).append('\n');
        if (commands == null || commands.isEmpty()) {
            text.append("# This template declares no recognised lifecycle scripts. See its README.");
        } else {
            commands.forEach(command -> text.append(command).append('\n'));
        }
        JTextArea area = textArea(text.toString().strip(), Math.max(5, commands == null ? 5 : commands.size() + 2), 64);
        area.setEditable(false);
        JButton copy = new JButton("Copy commands");
        copy.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(area.getText()), null));
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(new JLabel("Project downloaded and opened. Run these explicitly in a terminal:"), BorderLayout.NORTH);
        panel.add(new JScrollPane(area), BorderLayout.CENTER);
        panel.add(copy, BorderLayout.SOUTH);
        JOptionPane.showMessageDialog(owner, panel, "Project ready — commands are not executed",
                JOptionPane.INFORMATION_MESSAGE);
    }

    private static void row(JPanel panel, GridBagConstraints c, int row, String label,
                            Component field, Component trailing) {
        c.gridy = row;
        c.gridx = 0;
        c.weightx = 0;
        panel.add(new JLabel(label + ":"), c);
        c.gridx = 1;
        c.weightx = 1;
        panel.add(field, c);
        if (trailing != null) {
            c.gridx = 2;
            c.weightx = 0;
            panel.add(trailing, c);
        }
    }

    private static JTextArea textArea(String text, int rows, int columns) {
        JTextArea area = new JTextArea(text == null ? "" : text, rows, columns);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setEditable(false);
        return area;
    }

    private static String description(TemplateCatalogue.Entry entry) {
        // keyNeed is a catalogue fact. In particular, never derive this from mode: AOT can build keylessly.
        if ("none".equalsIgnoreCase(entry.keyNeed())) {
            return "Build key: none required\n\n" + entry.description();
        }
        return entry.description();
    }

    private static Path pathOrNull(String text) {
        try {
            return text == null || text.isBlank() ? null : Path.of(text.strip()).toAbsolutePath().normalize();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String shellQuote(String value) {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) return '"' + value + '"';
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
