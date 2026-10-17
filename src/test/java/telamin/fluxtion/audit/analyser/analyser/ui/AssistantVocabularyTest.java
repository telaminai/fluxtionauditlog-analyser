package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.AssistantTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;

import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review M74 usability #8, #12, #13, #14 (2026-10-17): one control for how the assistant works; the Settings help says
 * where the REST token really is; one name for external access everywhere; an accessible name contains its visible label.
 */
class AssistantVocabularyTest {

    static final Path CONFIG_PANEL = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ConfigPanel.java");

    static List<Component> all(Container root) {
        List<Component> out = new ArrayList<>();
        for (Component c : root.getComponents()) {
            out.add(c);
            if (c instanceof Container k) out.addAll(all(k));
        }
        return out;
    }

    /**
     * ★ #8. Witness: put the mode selector back beside the button — a combo named "Assistant mode" is found, and the
     * fixed "your plan" with it.
     */
    @Test
    @DisplayName("★ one control in the header names the profile in force and opens the cards; no mode selector, no 'your plan'")
    void oneControlForHowTheAssistantWorks() throws Exception {
        TranscriptViewTest.onEdt(() -> {
            AssistantPanel a = new AssistantPanel(new AssistantTranscript());
            a.renderProfiles(new ProfileState(AssistantProfile.SAFE, false, "", 0, "", false));
            assertEquals("Assistant: Safe analysis ▾", a.profileButton().getText());
            String name = a.profileButton().getAccessibleContext().getAccessibleName();
            assertTrue(name.startsWith("Assistant: Safe analysis"), "the name starts with the visible label: " + name);
            for (Component c : all(a)) {
                if (c instanceof JComboBox<?> box) {
                    assertNotEquals("Assistant mode", box.getAccessibleContext().getAccessibleName(),
                            "there is no second control for the same choice");
                    for (int i = 0; i < box.getItemCount(); i++) {
                        assertFalse(String.valueOf(box.getItemAt(i)).contains("your plan"), "no fixed 'your plan'");
                    }
                }
                if (c instanceof JLabel l && l.getText() != null) assertFalse(l.getText().contains("your plan"), l.getText());
                if (c instanceof AbstractButton b && b.getText() != null) {
                    assertFalse(b.getText().startsWith("Profile"), "the old Profile… button is gone: " + b.getText());
                }
            }
            a.renderProfiles(new ProfileState(AssistantProfile.IN_APP, false, "", 0, "", false));
            assertEquals("Assistant: In-app assistant ▾", a.profileButton().getText());
            return null;
        });
    }

    /** ★ #12. Witness: the old help sentence — "its URL + token appear in the status bar (and console)" — is found. */
    @Test
    @DisplayName("★ Settings says the token is in the endpoint file, readable only by you; the console shows 8 characters")
    void theSettingsHelpIsTrue() throws Exception {
        String src = Files.readString(CONFIG_PANEL);
        assertFalse(src.contains("appear in the status bar (and console)"), "a security claim that is no longer true");
        assertTrue(src.contains("~/.fluxtion-analyser/rest-endpoint"), "where the token is");
        assertTrue(src.contains("readable \"\n                + \"only by you") || src.contains("readable only by you"),
                "and who can read it");
        assertTrue(src.contains("first 8 characters"), "what the console shows");
    }

    /**
     * ★ Final review #15: Settings names the header control by its current label, read from the method that draws it.
     * Witness: the old "(Assistant ▾)" — a label the header no longer shows.
     */
    @Test
    @DisplayName("★ Settings' intro names the header control as it reads: 'Assistant: Safe analysis ▾'")
    void theSettingsIntroNamesTheControlAsItReads() throws Exception {
        String src = Files.readString(CONFIG_PANEL);
        assertFalse(src.contains("(<b>Assistant ▾</b>)"), "the header has no control labelled 'Assistant ▾'");
        assertTrue(src.contains("<b>" + AssistantPanel.assistantControlText(AssistantProfile.SAFE) + "</b>"),
                "the label as the control draws it");
    }

    /**
     * ★ Final review #14: the MCP bridge's own refusal uses the one name. Witness: the old "REST transport disabled".
     */
    @Test
    @DisplayName("★ the MCP bridge says 'External MCP/REST access is off', never 'REST transport disabled'")
    void theBridgeUsesTheOneName() throws Exception {
        String bridge = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/mcp/McpBridge.java"));
        assertFalse(bridge.contains("REST transport disabled"), "the old name");
        assertTrue(bridge.contains("\"analyser not running, or External MCP/REST access is off"), "the one name");
    }

    /** ★ #13. Witness: keep the old Settings checkbox label — the cards' name is not on it. */
    @Test
    @DisplayName("★ one name for external access: the cards, the Settings checkbox, the AI menu and the limits line agree")
    void oneNameForExternalAccess() throws Exception {
        String name = "External MCP/REST access";
        assertTrue(ProfileOverlay.externalLine(true).startsWith(name));
        assertTrue(ProfileOverlay.externalLine(false).startsWith(name));
        assertTrue(Files.readString(CONFIG_PANEL).contains("new JCheckBox(\"" + name), "the Settings checkbox");
        assertTrue(Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"))
                .contains("new JCheckBoxMenuItem(\"" + name + "\")"), "the AI menu item");
        assertTrue(LocalAgentPanel.EXTERNAL_CLOSED_FOR_SESSION.contains(name));
        assertTrue(LocalAgentPanel.EXTERNAL_OPEN_TO_TOOLS.contains(name));
        for (Path p : List.of(CONFIG_PANEL, Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/ProfileOverlay.java"))) {
            String s = Files.readString(p);
            assertFalse(s.contains("MCP / REST access"), p + " uses the old spelling");
            assertFalse(s.contains("drive the UI over localhost"), p + " uses the old checkbox words");
        }
    }

    /**
     * ★ #14 (WCAG 2.5.3, label in name). Witness: name a chip by its title alone — "Generic MCP setup" does not contain
     * the visible "Other MCP client", and the first assertion fails.
     */
    @Test
    @DisplayName("★ every start-page chip's accessible name contains its visible label")
    void theAccessibleNameContainsTheVisibleLabel() {
        assertTrue(StartPanel.accessibleName("Generic MCP setup", "Other MCP client").contains("Other MCP client"));
        assertTrue(StartPanel.accessibleName("Fluxtion API key — not found", "Fluxtion key").contains("Fluxtion key"));
        assertEquals("Open sample project", StartPanel.accessibleName("Open sample project", "Sample project"),
                "a title that contains the label is kept as it is");
        assertEquals("Fluxtion key — found", StartPanel.accessibleName("Fluxtion key — found", "Fluxtion key ✓"),
                "the tick is not part of the label a person says");
        assertEquals("Assistant: Off — choose how the assistant works", AssistantPanel.assistantControlName(AssistantProfile.OFF));
    }
}
