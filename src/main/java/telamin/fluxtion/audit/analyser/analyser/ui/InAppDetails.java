package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantProfiles;

import javax.swing.BoxLayout;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import java.awt.Dimension;
import java.awt.FlowLayout;

/**
 * M77-2 (spec-ai-setup-wizard.md §6.5, review R1-25): the in-app assistant's details — provider, model and API key — as
 * their own component, extracted from the profile cards so the cards and <i>Set up AI ▸ Other options</i> show the same
 * fields with the same words (and M77-4 can delete the cards without losing them). It holds what the person typed; the
 * Apply performer reads {@link #details()}; nothing here is a fact, and the key never leaves it except to that write.
 */
final class InAppDetails {

    final JComboBox<String> provider = UiText.plainCells(new JComboBox<>(new String[]{"anthropic", "openai"}));
    final JTextField model = new JTextField(16);
    final JPasswordField apiKey = new JPasswordField(24);
    final WrapLine keyNote = new WrapLine(" ");

    InAppDetails() {
        provider.getAccessibleContext().setAccessibleName("In-app provider");
        model.getAccessibleContext().setAccessibleName("In-app model");
        apiKey.getAccessibleContext().setAccessibleName("In-app API key");
    }

    /** Start from this machine's settings: the provider and model stored, the key field empty (a stored key is kept). */
    void load(String providerName, String modelName, boolean keyStored) {
        provider.setSelectedItem(providerName == null || providerName.isBlank() ? "anthropic" : providerName);
        model.setText(modelName == null ? "" : modelName);
        apiKey.setText("");
        keyNote.setText(keyStored ? "A key is stored on this machine; leave this blank to keep it."
                : "No key is stored yet.");
    }

    /** Whether the key field holds text — never the text itself. */
    boolean keyEntered() {
        return apiKey.getPassword().length > 0;
    }

    /** The details for the in-app profile's write, read by the Apply performer only. */
    AssistantProfiles.Details details() {
        return new AssistantProfiles.Details(null, null, String.valueOf(provider.getSelectedItem()), model.getText().trim(),
                new String(apiKey.getPassword()).trim());
    }

    /** The three fields and the key's note, as rows. */
    JComponent rows() {
        JPanel box = new JPanel();
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        box.setOpaque(false);
        box.add(field("Provider:", provider));
        box.add(field("Model:", model));
        box.add(field("API key:", apiKey));
        box.add(keyNote);
        for (java.awt.Component c : box.getComponents()) ((JComponent) c).setAlignmentX(JComponent.LEFT_ALIGNMENT);
        return box;
    }

    /** A label and its field on one row, as tall as they are. */
    static JComponent field(String label, JComponent field) {
        JPanel row = new JPanel(new WrapLayout(FlowLayout.LEFT, UiTheme.SPACE_SM, UiTheme.SPACE_XS)) {
            @Override public Dimension getMaximumSize() {
                return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
            }
        };
        row.setOpaque(false);
        JLabel l = UiText.label(label);
        l.setLabelFor(field);
        row.add(l);
        row.add(field);
        return row;
    }
}
