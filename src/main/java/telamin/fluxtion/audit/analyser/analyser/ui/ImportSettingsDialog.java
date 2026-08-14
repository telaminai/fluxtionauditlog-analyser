package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.Category;
import telamin.fluxtion.audit.analyser.analyser.config.SettingsShare.ImportPlan;

import javax.swing.*;
import java.awt.*;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * File → Import settings… summary dialog (M15, spec-settings-share §4.2). Shows, per category, what the
 * file contains and what applying it would do; each category is individually deselectable before OK.
 * Returns the chosen categories, or {@code null} if cancelled. Applies nothing itself.
 */
final class ImportSettingsDialog extends JDialog {

    private final Map<Category, JCheckBox> boxes = new EnumMap<>(Category.class);
    private Set<Category> result;   // null until OK

    private ImportSettingsDialog(Frame owner, ImportPlan plan, String sourceName) {
        super(owner, "Import settings", true);

        JPanel content = new JPanel(new BorderLayout(0, UiTheme.PAD));
        content.setBorder(UiTheme.pad());

        JLabel intro = new JLabel("<html>From <b>" + escape(sourceName)
                + "</b>. Choose what to apply — nothing is changed until you click Import.</html>");
        content.add(intro, BorderLayout.NORTH);

        JPanel rows = new JPanel(new GridBagLayout());
        rows.setBorder(UiTheme.section("Contents"));
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = 0;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(2, 2, 2, 8);
        for (Category c : Category.values()) {
            if (!plan.present().contains(c)) continue;
            JCheckBox cb = new JCheckBox(c.label, true);
            boxes.put(c, cb);
            g.gridx = 0;
            g.weightx = 0;
            rows.add(cb, g);
            JLabel detail = new JLabel(plan.summary().getOrDefault(c, ""));
            UiTheme.status(detail);
            g.gridx = 1;
            g.weightx = 1;
            rows.add(detail, g);
            g.gridy++;
        }
        content.add(rows, BorderLayout.CENTER);

        JButton ok = new JButton("Import");
        JButton cancel = new JButton("Cancel");
        ok.addActionListener(e -> { result = selected(); dispose(); });
        cancel.addActionListener(e -> { result = null; dispose(); });
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, UiTheme.GAP, 0));
        actions.add(cancel);
        actions.add(ok);
        content.add(actions, BorderLayout.SOUTH);

        setContentPane(content);
        getRootPane().setDefaultButton(ok);
        pack();
        setLocationRelativeTo(owner);
    }

    private Set<Category> selected() {
        Set<Category> s = EnumSet.noneOf(Category.class);
        boxes.forEach((c, cb) -> { if (cb.isSelected()) s.add(c); });
        return s;
    }

    /** Show the summary; returns the selected categories, or {@code null} if cancelled. */
    static Set<Category> show(Frame owner, ImportPlan plan, String sourceName) {
        ImportSettingsDialog d = new ImportSettingsDialog(owner, plan, sourceName);
        d.setVisible(true);   // modal — blocks here
        return d.result;
    }

    private static String escape(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}