package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.graph.GraphKey;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

/**
 * A searchable, multi-select picker for graph series (spec §8.7): type to filter the discovered
 * {@code instanceId.key} list, select several with the mouse/keyboard, and add them all at once.
 */
public final class SeriesPickerDialog {

    private SeriesPickerDialog() {
    }

    /** Shows the picker; returns the chosen keys (empty if cancelled). */
    public static List<GraphKey> pick(Component parent, List<GraphKey> all) {
        JDialog dialog = new JDialog(windowFor(parent), "Add series", Dialog_ModalityType());
        JTextField search = new JTextField();
        DefaultListModel<GraphKey> model = new DefaultListModel<>();
        all.forEach(model::addElement);
        JList<GraphKey> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setCellRenderer(new DefaultListCellRenderer() {
            @Override public Component getListCellRendererComponent(JList<?> l, Object v, int i, boolean s, boolean f) {
                super.getListCellRendererComponent(l, v, i, s, f);
                if (v instanceof GraphKey k) setText(k.display());
                return this;
            }
        });

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refilter(); }
            @Override public void removeUpdate(DocumentEvent e) { refilter(); }
            @Override public void changedUpdate(DocumentEvent e) { refilter(); }
            private void refilter() {
                String q = search.getText().trim().toLowerCase();
                model.clear();
                for (GraphKey k : all) if (q.isEmpty() || k.display().toLowerCase().contains(q)) model.addElement(k);
            }
        });

        List<GraphKey> chosen = new ArrayList<>();
        JButton add = new JButton("Add selected");
        JButton cancel = new JButton("Cancel");
        add.addActionListener(e -> { chosen.addAll(list.getSelectedValuesList()); dialog.dispose(); });
        cancel.addActionListener(e -> dialog.dispose());

        JPanel buttons = new JPanel();
        buttons.add(add);
        buttons.add(cancel);
        JPanel top = new JPanel(new BorderLayout(4, 4));
        top.add(new JLabel("Filter:"), BorderLayout.WEST);
        top.add(search, BorderLayout.CENTER);

        dialog.setLayout(new BorderLayout(6, 6));
        dialog.add(top, BorderLayout.NORTH);
        dialog.add(new JScrollPane(list), BorderLayout.CENTER);
        dialog.add(buttons, BorderLayout.SOUTH);
        dialog.setSize(new Dimension(420, 460));
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);   // modal: blocks until disposed
        return chosen;
    }

    private static Window windowFor(Component c) {
        return c == null ? null : SwingUtilities.getWindowAncestor(c);
    }

    private static java.awt.Dialog.ModalityType Dialog_ModalityType() {
        return java.awt.Dialog.ModalityType.APPLICATION_MODAL;
    }
}
