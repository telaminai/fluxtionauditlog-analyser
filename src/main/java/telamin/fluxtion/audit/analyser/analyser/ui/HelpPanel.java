package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.BorderFactory;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Static help page (spec §8.10) rendered from the bundled {@code /help/help.html} in a read-only
 * {@link JEditorPane}. Explains the UI, Fluxtion audit logs, filtering/graphing/source and the LLM
 * workflow.
 */
public final class HelpPanel extends JPanel {

    public HelpPanel() {
        super(new BorderLayout());
        setBorder(UiTheme.pad());
        JEditorPane pane = new JEditorPane();
        pane.setEditable(false);
        pane.setContentType("text/html");
        // the help HTML uses dark text; render it as a light "document" (white page) so it stays readable
        // in the app's dark theme, where the pane would otherwise inherit a dark background
        pane.setText(withLightDocumentStyle(loadHtml()));
        pane.setOpaque(true);
        pane.setBackground(java.awt.Color.WHITE);
        pane.setForeground(new java.awt.Color(0x1B1B1B));
        // open links in the system browser (a JEditorPane does nothing on click by default)
        pane.addHyperlinkListener(HelpPanel::openLink);
        JScrollPane scroll = new JScrollPane(pane);
        scroll.getViewport().setBackground(java.awt.Color.WHITE);
        add(scroll, BorderLayout.CENTER);
        SwingUtilities.invokeLater(() -> pane.setCaretPosition(0));
    }

    /** Open an activated hyperlink in the system browser. */
    private static void openLink(javax.swing.event.HyperlinkEvent e) {
        if (e.getEventType() != javax.swing.event.HyperlinkEvent.EventType.ACTIVATED) return;
        try {
            java.net.URI uri = e.getURL() != null ? e.getURL().toURI() : new java.net.URI(e.getDescription());
            if (java.awt.Desktop.isDesktopSupported()
                    && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(uri);
            }
        } catch (Exception ignore) {
            // best-effort — a malformed or unbrowsable link just does nothing
        }
    }

    /** Inject a light stylesheet so the help renders as a white document regardless of the app theme. */
    private static String withLightDocumentStyle(String html) {
        String style = "<style>"
                + "body{background:#ffffff;color:#1b1b1b;font-family:sans-serif;}"
                + "a{color:#0a58ca;} code{color:#0b7285;}"
                + "table,th,td{border-color:#d0d0d0;} th{background:#f2f2f2;color:#1b1b1b;}"
                + "</style>";
        int head = html.indexOf("</head>");
        if (head >= 0) return html.substring(0, head) + style + html.substring(head);
        int body = html.indexOf("<body");
        if (body >= 0) return html.substring(0, body) + "<head>" + style + "</head>" + html.substring(body);
        return "<html><head>" + style + "</head><body>" + html + "</body></html>";
    }

    private static String loadHtml() {
        try (InputStream in = HelpPanel.class.getResourceAsStream("/help/help.html")) {
            if (in == null) return "<html><body>Help content not found.</body></html>";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "<html><body>Failed to load help: " + e.getMessage() + "</body></html>";
        }
    }
}
