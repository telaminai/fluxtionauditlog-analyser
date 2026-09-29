package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.design.DesignDocument;
import telamin.fluxtion.audit.analyser.analyser.design.DesignFiles;
import telamin.fluxtion.audit.analyser.analyser.design.DiagnosticLocation;
import telamin.fluxtion.audit.analyser.analyser.design.ProducerResult;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.function.Consumer;

/** A producer's findings remain visible even when they cannot be mapped to the working copy. */
final class ProducerFindingsPanel extends JPanel {
    ProducerFindingsPanel() { super(new BorderLayout()); }

    void render(ProducerResult result, DesignDocument design, String designPath, DesignFiles files,
                String error, Consumer<DiagnosticLocation> show) {
        removeAll();
        Fluid.Panel page = column();
        page.setBorder(BorderFactory.createEmptyBorder(18, 20, 24, 20));
        page.setBackground(UIManager.getColor("Panel.background"));
        page.add(heading("Producer findings", 18));
        page.add(Box.createVerticalStrut(4));

        if (result == null) {
            page.add(muted(error == null || error.isEmpty()
                    ? "No producer result open. Use open {diagnostics: path}."
                    : "Result cleared: " + error));
        } else {
            int count = result.findings().size();
            page.add(muted(count + (count == 1 ? " finding" : " findings") + " · " + result.stage() + " stage"));
            page.add(Box.createVerticalStrut(14));
            page.add(context(result, design));
            page.add(Box.createVerticalStrut(16));
            if (count == 0) page.add(muted("This producer result contains no findings."));
            for (int i = 0; i < count; i++) {
                page.add(finding(result, result.findings().get(i), i + 1, design, designPath, files, show));
                page.add(Box.createVerticalStrut(10));
            }
        }

        JScrollPane scroll = new JScrollPane(Fluid.column(page),
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(page.getBackground());
        add(scroll, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    private static Fluid.Panel context(ProducerResult result, DesignDocument design) {
        Fluid.Panel card = card();
        card.add(heading("RESULT CONTEXT", 11));
        card.add(Box.createVerticalStrut(7));
        card.add(muted("Result file"));
        JTextArea path = Fluid.text(result.file());
        path.setFont(UiTheme.mono(12));
        card.add(path);
        card.add(Box.createVerticalStrut(8));
        // Keep every qualified relationship statement from the existing producer result.
        for (String line : result.description(design).split("\\n", -1)) {
            card.add(muted(line));
            card.add(Box.createVerticalStrut(3));
        }
        return card;
    }

    private static Fluid.Panel finding(ProducerResult result, ProducerResult.Finding finding, int number,
                                       DesignDocument design, String designPath, DesignFiles files,
                                       Consumer<DiagnosticLocation> show) {
        Fluid.Panel card = card();
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        header.setOpaque(false);
        JLabel severity = new JLabel(finding.severity());
        Color ink = switch (finding.severity().toUpperCase(java.util.Locale.ROOT)) {
            case "ERROR" -> UiTheme.warnForeground();
            case "WARNING", "WARN" -> UiTheme.attentionForeground();
            default -> UiTheme.accentText();
        };
        severity.setForeground(ink);
        severity.setOpaque(true);
        severity.setBackground(UiTheme.mix(UiTheme.surface(), ink, 0.12f));
        severity.setBorder(BorderFactory.createEmptyBorder(3, 7, 3, 7));
        header.add(severity);
        JLabel code = new JLabel(finding.code());
        code.setFont(UiTheme.mono(12));
        code.setForeground(UiTheme.mutedForeground());
        header.add(code);
        card.add(header);
        card.add(Box.createVerticalStrut(9));
        JTextArea title = Fluid.text(finding.message());
        Font base = UIManager.getFont("Label.font");
        if (base != null) title.setFont(base.deriveFont(Font.BOLD, 14f));
        card.add(title);
        section(card, "Why this matters", finding.why());
        section(card, "Suggested fix", finding.fix());
        section(card, "XML declaration", finding.field("xmlDeclaration"));

        DiagnosticLocation location = DiagnosticLocation.resolve(result, finding, design, files, designPath);
        card.add(Box.createVerticalStrut(12));
        card.add(muted(location.reason() + (location.candidates().isEmpty()
                ? "" : " · lines " + location.candidates())));
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.setOpaque(false);
        JButton primary = new JButton(location.available()
                ? location.approximate() ? "Show (approximate)" : "Show" : "Unavailable");
        primary.setEnabled(location.available());
        primary.setToolTipText(location.reason());
        primary.addActionListener(e -> show.accept(location));
        actions.add(primary);
        DiagnosticLocation related = DiagnosticLocation.related(finding, design, files);
        if (related.available()) {
            JButton secondary = new JButton(related.mode().equals("DESIGN")
                    ? "Show quoted declaration (approximate)" : "Show node source");
            secondary.setToolTipText(related.reason());
            secondary.addActionListener(e -> show.accept(related));
            actions.add(secondary);
        }
        card.add(Box.createVerticalStrut(5));
        card.add(actions);
        card.getAccessibleContext().setAccessibleName("Finding " + number + ": " + finding.message());
        return card;
    }

    private static void section(Fluid.Panel card, String title, String body) {
        if (body == null || body.isEmpty()) return;
        card.add(Box.createVerticalStrut(11));
        card.add(heading(title, 11));
        card.add(Box.createVerticalStrut(3));
        JTextArea value = Fluid.text(body);
        if (title.equals("XML declaration")) {
            value.setFont(UiTheme.mono(12));
            value.setBackground(UiTheme.accentWash(0.06f));
            value.setOpaque(true);
            value.setBorder(BorderFactory.createEmptyBorder(7, 8, 7, 8));
        }
        card.add(value);
    }

    private static Fluid.Panel card() {
        Fluid.Panel card = column();
        card.setBackground(UiTheme.surface());
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UiTheme.surfaceEdge()),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));
        return card;
    }

    private static Fluid.Panel column() {
        Fluid.Panel panel = new Fluid.Panel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        return panel;
    }

    private static JLabel heading(String text, int size) {
        JLabel label = new JLabel(text);
        Font base = UIManager.getFont("Label.font");
        if (base != null) label.setFont(base.deriveFont(Font.BOLD, (float) size));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private static JTextArea muted(String text) {
        JTextArea area = Fluid.text(text);
        area.setForeground(UiTheme.mutedForeground());
        return area;
    }
}
