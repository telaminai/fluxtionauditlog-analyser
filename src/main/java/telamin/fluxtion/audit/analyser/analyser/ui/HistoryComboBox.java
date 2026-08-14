package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import java.util.List;

/**
 * An editable combo box with a persisted history: pick a past term from the dropdown, or type with
 * inline <b>autocomplete</b> against the history (spec §13). Used for the search box.
 *
 * <p>A theme change runs {@code updateComponentTreeUI}, which <b>recreates the editor component</b>
 * (new {@link JTextField} + new {@code Document}). So the editor must be resolved dynamically and the
 * autocomplete filter + change bridge re-installed whenever the editor is swapped — otherwise search
 * silently stops working after switching themes.
 */
public final class HistoryComboBox extends JComboBox<String> {

    private boolean completing;
    private Runnable onChange = () -> { };

    private final DocumentListener changeBridge = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent e) { onChange.run(); }
        @Override public void removeUpdate(DocumentEvent e) { onChange.run(); }
        @Override public void changedUpdate(DocumentEvent e) { onChange.run(); }
    };

    public HistoryComboBox() {
        setEditable(true);
        installOnEditor();
        // the editor is replaced on look-and-feel / theme changes — re-install on each swap
        addPropertyChangeListener("editor", e -> installOnEditor());
    }

    private JTextField editor() {
        // getEditor() is briefly null mid-swap: BasicComboBoxUI.uninstallUI fires the "editor" property
        // change while tearing the old editor down (theme change → updateComponentTreeUI). Never throw
        // from that path — an exception there aborts the whole UI tree update and corrupts sibling combos.
        var ed = getEditor();
        return (ed == null || !(ed.getEditorComponent() instanceof JTextField tf)) ? null : tf;
    }

    private void installOnEditor() {
        JTextField ed = editor();
        if (ed == null) return;
        AbstractDocument doc = (AbstractDocument) ed.getDocument();
        if (!(doc.getDocumentFilter() instanceof AutoComplete)) doc.setDocumentFilter(new AutoComplete());
        doc.removeDocumentListener(changeBridge);   // idempotent: never attach twice
        doc.addDocumentListener(changeBridge);
    }

    /** True while the model is being refreshed — listeners should ignore events during this. */
    public boolean isAdjusting() {
        return completing;
    }

    public String getText() {
        JTextField ed = editor();
        return ed == null ? "" : ed.getText();
    }

    public void setText(String s) {
        JTextField ed = editor();
        if (ed != null) ed.setText(s == null ? "" : s);
    }

    /** Replace the dropdown history without disturbing the current editor text. */
    public void setHistory(List<String> items) {
        String current = getText();
        completing = true;
        try {
            setModel(new DefaultComboBoxModel<>(items.toArray(new String[0])));
            setSelectedItem(null);
            setText(current);
        } finally {
            completing = false;
        }
    }

    /** Registers a callback fired whenever the editor text changes (for debounced filtering). */
    public void onTextChanged(Runnable onChange) {
        this.onChange = onChange == null ? () -> { } : onChange;
    }

    private final class AutoComplete extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String text, AttributeSet attr) throws BadLocationException {
            super.insertString(fb, offset, text, attr);
            if (!completing) complete(fb);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attr) throws BadLocationException {
            super.replace(fb, offset, length, text, attr);
            if (!completing && text != null && !text.isEmpty()) complete(fb);
        }

        private void complete(FilterBypass fb) throws BadLocationException {
            JTextField ed = editor();
            if (ed == null) return;
            String typed = ed.getText();
            if (typed.isEmpty()) return;
            String lower = typed.toLowerCase();
            for (int i = 0; i < getItemCount(); i++) {
                String item = getItemAt(i);
                if (item != null && item.length() > typed.length() && item.toLowerCase().startsWith(lower)) {
                    completing = true;
                    fb.remove(0, ed.getDocument().getLength());
                    fb.insertString(0, item, null);
                    ed.setCaretPosition(item.length());
                    ed.moveCaretPosition(typed.length());   // select the completed suffix
                    completing = false;
                    return;
                }
            }
        }
    }
}
