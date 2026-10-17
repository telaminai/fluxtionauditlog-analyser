package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.FullFolderTrust;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicHTML;
import javax.swing.text.JTextComponent;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P1b §5.2 / OD-74.12 (§10 P1b ★ dialog): the full-posture folder dialog shows the names it read from the folder
 * as PLAIN TEXT. Headless: the dialog's content is built and inspected; nothing is shown.
 */
class LocalAgentFolderDialogTest {

    @TempDir
    Path dir;

    static final String PLANTED = "<html><img src='http://127.0.0.1:9/b'>owned";

    /**
     * ★ Witness: build the content from a {@code JLabel} (or a {@code JOptionPane} string message, which is one label per
     * line) — a label whose text is the planted name renders it as HTML, which the in-place check below shows, and the
     * "no label" assertion fails.
     */
    @Test
    @DisplayName("★ hook and server names from planted files are shown as plain text; <html> markup reads as its characters")
    void plantedNamesArePlainText() throws Exception {
        Files.createDirectories(dir.resolve(".claude"));
        Files.writeString(dir.resolve(".claude/settings.json"), "{\"hooks\":{\"SessionStart\":[{\"hooks\":[{}]}]}}");
        Files.writeString(dir.resolve(".mcp.json"), "{\"mcpServers\":{\"" + PLANTED.replace("'", "\\u0027") + "\":{}}}");
        JComponent content = LocalAgentFolderDialog.content(dir, FullFolderTrust.scan(dir));

        List<Component> all = new ArrayList<>();
        collect(content, all);
        JTextComponent text = all.stream().filter(JTextComponent.class::isInstance).map(JTextComponent.class::cast)
                .findFirst().orElseThrow();
        assertFalse(text.isEditable());
        assertTrue(text.getText().contains("MCP servers in this folder: " + PLANTED), text.getText());
        assertTrue(text.getText().contains("Hooks in this folder: SessionStart (1)"), text.getText());
        assertTrue(all.stream().noneMatch(JLabel.class::isInstance), "no component that renders HTML shows a folder name");
        assertNull(text.getClientProperty(BasicHTML.propertyKey), "the text area has no HTML renderer");

        // in place: what a label would have done with the same name
        JLabel naive = new JLabel(PLANTED);
        assertNotNull(naive.getClientProperty(BasicHTML.propertyKey), "a JLabel renders the planted name as HTML");
    }

    private static void collect(Component c, List<Component> out) {
        out.add(c);
        if (c instanceof Container k) for (Component child : k.getComponents()) collect(child, out);
    }
}
