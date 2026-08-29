package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.FluxtionKeyStore;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.IOException;

/**
 * Manages the Fluxtion builder's canonical key file without ever redisplaying the stored value.
 *
 * <p>This is intentionally smaller than the IDE visualiser dialog it replaces: the analyser owns the
 * key-file convenience and named profiles, not builder host/proxy settings and not key validation.
 */
public final class FluxtionKeyDialog extends JDialog {

    private final FluxtionKeyStore store;
    private final JLabel status = new JLabel();
    private final JComboBox<String> profiles = new JComboBox<>();
    private final JTextField profileName = new JTextField(18);
    private final JPasswordField keyField = new JPasswordField(28);
    private boolean changed;

    private FluxtionKeyDialog(Window owner, FluxtionKeyStore store) {
        super(owner, "Fluxtion API key", ModalityType.APPLICATION_MODAL);
        this.store = store;
        setContentPane(content());
        refresh();
        pack();
        setMinimumSize(new Dimension(560, getPreferredSize().height));
        setLocationRelativeTo(owner);
    }

    /** @return true when the canonical key presence may have changed. */
    public static boolean show(Window owner, FluxtionKeyStore store) {
        FluxtionKeyDialog dialog = new FluxtionKeyDialog(owner, store);
        dialog.setVisible(true);
        return dialog.changed;
    }

    private JPanel content() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(BorderFactory.createEmptyBorder(16, 18, 14, 18));

        status.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(status);
        root.add(Box.createVerticalStrut(5));
        JLabel rule = new JLabel("A -Dfluxtion.apiKey passed to the build overrides this file. "
                + "FLUXTION_API_KEY is not read by the builder.");
        rule.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(rule);
        root.add(Box.createVerticalStrut(14));

        JPanel activate = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        activate.setAlignmentX(Component.LEFT_ALIGNMENT);
        activate.add(new JLabel("Saved profile:"));
        profiles.setPreferredSize(new Dimension(190, profiles.getPreferredSize().height));
        activate.add(profiles);
        JButton use = new JButton("Activate");
        use.addActionListener(e -> activateProfile());
        activate.add(use);
        JButton delete = new JButton("Delete");
        delete.addActionListener(e -> deleteProfile());
        activate.add(delete);
        root.add(activate);
        root.add(Box.createVerticalStrut(14));

        JPanel form = new JPanel(new java.awt.GridLayout(0, 2, 8, 7));
        form.setAlignmentX(Component.LEFT_ALIGNMENT);
        form.add(new JLabel("New key:"));
        keyField.setEchoChar('\u2022');
        keyField.setToolTipText("Stored values are never loaded back into this field");
        form.add(keyField);
        form.add(new JLabel("Profile name (optional):"));
        form.add(profileName);
        root.add(form);
        root.add(Box.createVerticalStrut(5));
        JLabel hint = new JLabel("Leave the profile blank to update only the canonical file; a name also saves a local profile.");
        hint.setAlignmentX(Component.LEFT_ALIGNMENT);
        root.add(hint);
        root.add(Box.createVerticalStrut(15));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setAlignmentX(Component.LEFT_ALIGNMENT);
        JButton close = new JButton("Close");
        close.addActionListener(e -> dispose());
        buttons.add(close);
        JButton save = new JButton("Save key");
        save.addActionListener(e -> save());
        buttons.add(save);
        root.add(buttons);
        return root;
    }

    private void refresh() {
        status.setText(store.keyPresent()
                ? "Key file: present at ~/.fluxtion/fluxtion.apiKeyFile"
                : "Key file: no configured key at ~/.fluxtion/fluxtion.apiKeyFile");
        String selected = (String) profiles.getSelectedItem();
        profiles.removeAllItems();
        for (String name : store.profiles()) profiles.addItem(name);
        String active = store.activeProfile();
        if (!active.isBlank()) profiles.setSelectedItem(active);
        else if (selected != null) profiles.setSelectedItem(selected);
    }

    private void save() {
        char[] key = keyField.getPassword();
        try {
            String name = profileName.getText().trim();
            if (name.isBlank()) store.save(key);
            else store.saveProfileAndActivate(name, key);
            keyField.setText("");
            profileName.setText("");
            changed = true;
            refresh();
        } catch (IllegalArgumentException | IOException e) {
            keyField.setText("");
            JOptionPane.showMessageDialog(this, e.getMessage(), "Fluxtion API key", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void activateProfile() {
        String name = (String) profiles.getSelectedItem();
        if (name == null) return;
        try {
            store.activate(name);
            changed = true;
            refresh();
        } catch (IllegalArgumentException | IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Fluxtion API key", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void deleteProfile() {
        String name = (String) profiles.getSelectedItem();
        if (name == null) return;
        int answer = JOptionPane.showConfirmDialog(this, "Delete local profile \"" + name + "\"?",
                "Delete profile", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (answer != JOptionPane.YES_OPTION) return;
        try {
            store.deleteProfile(name);
            refresh();
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Fluxtion API key", JOptionPane.ERROR_MESSAGE);
        }
    }
}
