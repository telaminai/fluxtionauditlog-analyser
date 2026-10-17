package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Holistic review B6 (2026-10-17): the cards choose the assistant IN the analyser; the external loop is said apart. */
class ProfileCardsWordsTest {

    /** ★ Witness: render the external line from a constant (always "off") — the ON assertion fails. */
    @Test
    @DisplayName("★ under the cards: external MCP / REST access on or off, with where to change it; Off leaves it alone")
    void theExternalLoopIsSaid() throws Exception {
        assertTrue(ProfileOverlay.externalLine(true).startsWith("External MCP / REST access is ON"));
        assertTrue(ProfileOverlay.externalLine(false).startsWith("External MCP / REST access is off"));
        assertTrue(ProfileOverlay.externalLine(true).contains("Settings ▸ Assistant"));
        assertTrue(ProfileOverlay.cardLine(AssistantProfile.OFF).startsWith("No assistant in the analyser"));
        assertTrue(ProfileOverlay.detailLines(AssistantProfile.OFF).stream().anyMatch(l -> l.contains("not affected")));
        String frame = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        assertTrue(frame.contains("config.apiKey != null && !config.apiKey.isBlank(), config.assistantActionsRest));"),
                "the cards' defaults carry the REST switch as it is");
    }

    /** Review B6: one vocabulary — the guide's heading names the in-app assistant as the card and the selector do. */
    @Test
    @DisplayName("the guide calls it the in-app assistant, as the card and the selector do")
    void oneVocabulary() throws Exception {
        String guide = Files.readString(Path.of("docs/site/user-guide/assistant.md"));
        assertTrue(guide.contains("\n## The in-app assistant\n"));
        assertFalse(guide.contains("## The onboard assistant"));
        assertEquals("In-app assistant", AssistantProfile.IN_APP.title());
        assertEquals("In-app · API key", AssistantPanel.MODE_IN_APP);
    }
}
