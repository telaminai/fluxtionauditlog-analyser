package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.Runbooks;
import telamin.fluxtion.audit.analyser.analyser.config.SkillFrontmatter;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * M43.3–.5 — add and remove the pointers a project declares: its runbooks and its domain glossary.
 *
 * <h2>What this dialog is allowed to do</h2>
 * It edits POINTERS, never content. The analyser stores where a file is and one line saying when to use
 * it; the file itself is written in the user's editor and reviewed in git, and nothing here runs it
 * (D-AI4, and D-C2 behind it). Adding a pointer through the UI is not a new hazard —
 * {@code ConfigStore.writeRunbooks} already writes them and the app has always been a profile writer —
 * it simply gives the profile an entrance a person can find.
 *
 * <h2>The gate is the existing one</h2>
 * Every candidate goes through {@link Runbooks#refuse} and {@link Runbooks#refuseDescription}, the same
 * calls the profile loader, share import and share export make. This is a fourth ENTRANCE to one gate,
 * not a fourth gate — and the reason is shown in the dialog rather than the OK button quietly staying
 * grey, because a control that refuses without saying why reads as a broken control (D-AI6).
 *
 * <h2>Prefill offers; the person declares (D-AI5)</h2>
 * Choosing a skill-shaped file reads its frontmatter to fill in the name and description. What is stored
 * is whatever is in the fields when OK is pressed. The analyser never re-reads the file to answer
 * questions about it, so editing that file later cannot silently change what {@code context} says.
 */
public final class PointerDialog {

    private PointerDialog() {
    }

    /** Manage the project's runbook pointers. */
    public static void runbooks(Component parent, AppConfig config, Runnable onChanged, Path projectRoot) {
        if (projectRoot == null) return;                       // the menu disables this; belt and braces
        JDialog dialog = dialog(parent, "Runbooks — pointers into this project");

        DefaultListModel<String> model = new DefaultListModel<>();
        config.runbooks.keySet().forEach(model::addElement);
        JList<String> list = new JList<>(model);
        list.setVisibleRowCount(8);

        JLabel detail = new JLabel(" ");
        detail.setForeground(UiTheme.mutedForeground());
        list.addListSelectionListener(e -> {
            Runbooks.Pointer p = selected(list, config);
            detail.setText(p == null ? " "
                    : p.path() + (p.description() == null ? "  —  (no description)" : "  —  " + p.description()));
        });

        JButton add = new JButton("Add…");
        JButton remove = new JButton("Remove");
        remove.setEnabled(false);
        list.addListSelectionListener(e -> remove.setEnabled(list.getSelectedValue() != null));

        add.addActionListener(e -> addRunbook(dialog, config, projectRoot).ifPresent(name -> {
            if (!model.contains(name)) model.addElement(name);
            list.setSelectedValue(name, true);
            onChanged.run();
        }));
        remove.addActionListener(e -> {
            String name = list.getSelectedValue();
            if (name == null) return;
            config.runbooks.remove(name);
            model.removeElement(name);
            detail.setText(" ");
            onChanged.run();
        });

        finish(dialog, list, detail, add, remove,
                "Pointers are stored in this project's profile — a location and one line, never the file's "
                        + "contents. The analyser never runs a runbook.");
    }

    /** The single glossary pointer — the same shape, because it is the same kind of thing. */
    public static void glossary(Component parent, AppConfig config, Runnable onChanged, Path projectRoot) {
        if (projectRoot == null) return;
        String current = config.vocabularyPath == null ? "" : config.vocabularyPath;
        JTextField path = new JTextField(current, 34);
        JLabel problem = problemLabel();

        JButton browse = new JButton("Choose file…");
        browse.addActionListener(e -> chooseRelative(browse, projectRoot).ifPresent(path::setText));

        JPanel body = new JPanel(new GridBagLayout());
        var c = gbc();
        body.add(new JLabel("Glossary file:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        body.add(path, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        body.add(browse, c);
        c.gridx = 0; c.gridy = 1; c.gridwidth = 3; c.fill = GridBagConstraints.HORIZONTAL;
        body.add(problem, c);
        c.gridy = 2;
        body.add(note("Its text is served to the assistant and in `context`, so the terms of this system are "
                + "read the way this system means them. A pointer into the repository, never a copy."), c);

        if (confirm(parent, "Domain glossary", body, () -> {
            String v = path.getText().trim();
            if (v.isEmpty()) return Optional.empty();                  // clearing it is allowed
            return Runbooks.refusePointer("glossary", v);
        }, problem)) {
            String v = path.getText().trim();
            config.vocabularyPath = v.isEmpty() ? null : v;
            onChanged.run();
        }
    }

    // ---- add one runbook --------------------------------------------------------------------------

    private static Optional<String> addRunbook(Component parent, AppConfig config, Path projectRoot) {
        JTextField name = new JTextField(24);
        JTextField path = new JTextField(30);
        JTextField description = new JTextField(30);
        JLabel problem = problemLabel();
        JLabel prefilled = new JLabel(" ");
        prefilled.setForeground(UiTheme.mutedForeground());

        JButton browse = new JButton("Choose file…");
        browse.addActionListener(e -> chooseRelative(browse, projectRoot).ifPresent(rel -> {
            path.setText(rel);
            // D-AI5: the file OFFERS; the person declares. Only ever fill an EMPTY field — overwriting
            // something they typed would make the suggestion the decision.
            var suggestion = SkillFrontmatter.read(projectRoot.resolve(rel));
            boolean used = false;
            if (name.getText().isBlank()) {
                used |= SkillFrontmatter.usableName(suggestion.name())
                        .map(n -> { name.setText(n); return true; }).orElse(false);
            }
            if (description.getText().isBlank()) {
                used |= SkillFrontmatter.usableDescription(suggestion.description())
                        .map(d -> { description.setText(d); return true; }).orElse(false);
            }
            prefilled.setText(used
                    ? "Name and description suggested from the file's frontmatter — edit them; what you "
                      + "leave here is what gets stored."
                    : " ");
        }));

        JPanel body = new JPanel(new GridBagLayout());
        var c = gbc();
        body.add(new JLabel("Name:"), c);
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        body.add(name, c);
        c.gridx = 0; c.gridy = 1; c.gridwidth = 1; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        body.add(new JLabel("File:"), c);
        c.gridx = 1; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        body.add(path, c);
        c.gridx = 2; c.weightx = 0; c.fill = GridBagConstraints.NONE;
        body.add(browse, c);
        c.gridx = 0; c.gridy = 2; c.fill = GridBagConstraints.NONE;
        body.add(new JLabel("Description:"), c);
        c.gridx = 1; c.gridwidth = 2; c.weightx = 1; c.fill = GridBagConstraints.HORIZONTAL;
        body.add(description, c);
        c.gridx = 0; c.gridy = 3; c.gridwidth = 3;
        body.add(prefilled, c);
        c.gridy = 4;
        body.add(problem, c);
        c.gridy = 5;
        body.add(note("One line saying WHEN to use it, so an AI client can choose between runbooks without "
                + "opening every file. Optional."), c);

        boolean ok = confirm(parent, "Add runbook", body, () -> {
            var bad = Runbooks.refuse(name.getText().trim(), path.getText().trim());
            return bad.isPresent() ? bad
                    : Runbooks.refuseDescription("runbook", description.getText().trim());
        }, problem);
        if (!ok) return Optional.empty();

        String n = name.getText().trim();
        String d = description.getText().trim();
        config.runbooks.put(n, new Runbooks.Pointer(path.getText().trim(), d.isEmpty() ? null : d));
        return Optional.of(n);
    }

    // ---- shared bits ------------------------------------------------------------------------------

    private static Runbooks.Pointer selected(JList<String> list, AppConfig config) {
        String name = list.getSelectedValue();
        return name == null ? null : config.runbooks.get(name);
    }

    /** A file chooser rooted at the project, returning a project-relative path, or empty. */
    private static Optional<String> chooseRelative(Component parent, Path projectRoot) {
        JFileChooser chooser = new JFileChooser(projectRoot.toFile());
        chooser.setDialogTitle("Choose a file inside the project");
        if (chooser.showOpenDialog(parent) != JFileChooser.APPROVE_OPTION) return Optional.empty();
        Path chosen = chooser.getSelectedFile().toPath().toAbsolutePath().normalize();
        Path root = projectRoot.toAbsolutePath().normalize();
        if (!chosen.startsWith(root)) {
            JOptionPane.showMessageDialog(parent,
                    "That file is outside the project.\n\nA pointer must be relative to the project root so "
                            + "it resolves on a colleague's checkout too.",
                    "Outside the project", JOptionPane.WARNING_MESSAGE);
            return Optional.empty();
        }
        return Optional.of(root.relativize(chosen).toString().replace('\\', '/'));
    }

    /**
     * Show the body until it is either cancelled or passes {@code gate}. The refusal REASON is shown in
     * the dialog (D-AI6) — a control that refuses silently reads as a broken control.
     */
    private static boolean confirm(Component parent, String title, JComponent body,
                                   java.util.function.Supplier<Optional<String>> gate, JLabel problem) {
        while (true) {
            int choice = JOptionPane.showConfirmDialog(parent, body, title,
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) return false;
            Optional<String> bad = gate.get();
            if (bad.isEmpty()) return true;
            problem.setText("<html><body style='width:380px'>" + escape(bad.get()) + "</body></html>");
        }
    }

    private static JDialog dialog(Component parent, String title) {
        JDialog d = new JDialog(SwingUtilities.getWindowAncestor(parent), title,
                Dialog.ModalityType.APPLICATION_MODAL);
        d.setLayout(new BorderLayout(8, 8));
        return d;
    }

    private static void finish(JDialog dialog, JList<String> list, JLabel detail,
                               JButton add, JButton remove, String noteText) {
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 6));
        JButton close = new JButton("Close");
        close.addActionListener(e -> dialog.dispose());
        south.add(add);
        south.add(remove);
        south.add(close);

        JPanel centre = new JPanel(new BorderLayout(6, 6));
        centre.add(new JScrollPane(list), BorderLayout.CENTER);
        centre.add(detail, BorderLayout.SOUTH);
        centre.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        JPanel north = new JPanel(new BorderLayout());
        north.add(note(noteText), BorderLayout.CENTER);
        north.setBorder(BorderFactory.createEmptyBorder(10, 10, 0, 10));

        dialog.add(north, BorderLayout.NORTH);
        dialog.add(centre, BorderLayout.CENTER);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.setPreferredSize(new Dimension(560, 380));
        dialog.pack();
        dialog.setLocationRelativeTo(dialog.getOwner());
        dialog.setVisible(true);
    }

    private static JLabel problemLabel() {
        JLabel l = new JLabel(" ");
        l.setForeground(UiTheme.warnForeground());
        return l;
    }

    private static JLabel note(String text) {
        JLabel l = new JLabel("<html><body style='width:420px'>" + escape(text) + "</body></html>");
        l.setForeground(UiTheme.mutedForeground());
        return l;
    }

    private static GridBagConstraints gbc() {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.gridx = 0;
        c.gridy = 0;
        return c;
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
