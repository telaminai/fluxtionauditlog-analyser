package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.design.*;
import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/** A producer's findings remain visible even when they cannot be mapped to the working copy. */
final class ProducerFindingsPanel extends JPanel {
    ProducerFindingsPanel() { super(new BorderLayout()); }
    void render(ProducerResult result, DesignDocument design, String designPath, DesignFiles files, String error, Consumer<DiagnosticLocation> show) {
        removeAll();
        JPanel items = new JPanel(); items.setLayout(new BoxLayout(items, BoxLayout.Y_AXIS));
        if (result == null) items.add(message(error.isEmpty() ? "No producer result open. Use open {diagnostics: path}." : "Result cleared: " + error));
        else {
            items.add(message(result.file() + "\n" + result.description(design)));
            if (result.findings().isEmpty()) items.add(message("This producer result contains no findings."));
            for (var finding : result.findings()) {
                JPanel row = new JPanel(new BorderLayout(6, 6)); row.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
                row.add(message(finding.severity() + " · " + finding.code() + "\n" + finding.message()
                        + (finding.why().isEmpty() ? "" : "\n" + finding.why()) + (finding.fix().isEmpty() ? "" : "\nSuggested fix: " + finding.fix())
                        + (finding.field("xmlDeclaration").isEmpty() ? "" : "\nXML declaration: " + finding.field("xmlDeclaration"))), BorderLayout.CENTER);
                var location = DiagnosticLocation.resolve(result, finding, design, files, designPath);
                JButton button = new JButton(location.available() ? location.approximate() ? "Show (approximate)" : "Show" : "Unavailable");
                button.setEnabled(location.available()); button.setToolTipText(location.reason());
                button.addActionListener(e -> show.accept(location));
                JPanel action = new JPanel(new BorderLayout()); action.add(button, BorderLayout.NORTH);
                action.add(message(location.reason() + (location.candidates().isEmpty() ? "" : " · lines " + location.candidates())), BorderLayout.CENTER);
                var related = DiagnosticLocation.related(finding, design, files);
                if (related.available()) {
                    JButton secondary = new JButton(related.mode().equals("DESIGN") ? "Show quoted declaration (approximate)" : "Show node source");
                    secondary.setToolTipText(related.reason()); secondary.addActionListener(e -> show.accept(related));
                    action.add(secondary, BorderLayout.SOUTH);
                }
                row.add(action, BorderLayout.SOUTH); items.add(row);
            }
        }
        JPanel top = new JPanel(new BorderLayout()); top.add(items, BorderLayout.NORTH);
        add(new JScrollPane(top)); revalidate(); repaint();
    }
    private static JTextArea message(String message) {
        var text = new JTextArea(message); text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setBackground(UIManager.getColor("Panel.background")); text.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        return text;
    }
}
