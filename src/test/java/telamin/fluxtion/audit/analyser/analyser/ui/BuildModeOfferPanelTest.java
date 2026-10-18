package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupLines;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantProfile;
import telamin.fluxtion.audit.analyser.analyser.session.ProfileState;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner decision (2026-10-18): the Build-mode offer is one line and two buttons on the Claude Code panel, rendered from
 * {@link ProfileState#buildModeOffer()}; Switch and Keep asking are reported for the offer shown, and the panel decides
 * nothing (rule 9). Headless.
 */
class BuildModeOfferPanelTest {

    /**
     * ★ Witness: render the banner visible regardless of the offer (or report a fixed offer id) — the hidden and the
     * answered-offer assertions fail.
     */
    @Test
    @DisplayName("★ the offer renders from the snapshot; Switch and Keep asking report the offer shown")
    void theOfferRendersAndReports() throws Exception {
        List<String> said = new ArrayList<>();
        SwingUtilities.invokeAndWait(() -> {
            LocalAgentPanel p = new LocalAgentPanel(new LocalAgentTranscript());
            p.setIntents(new LocalAgentPanel.Intents() {
                @Override public void start() { }
                @Override public void stop() { }
                @Override public void send(String draft, List<Long> bindings) { }
                @Override public void newChat() { }
                @Override public void openSettings() { }
                @Override public void answerBuildMode(long offer, boolean switchMode) {
                    said.add(offer + ":" + (switchMode ? "switch" : "keep"));
                }
            });
            p.renderBuildModeOffer(ProfileState.of(AssistantProfile.FULL));
            assertFalse(p.buildModeBanner().isVisible(), "no offer: nothing shown");
            p.buildModeSwitchButton().doClick();
            assertTrue(said.isEmpty(), "a hidden offer reports nothing");

            p.renderBuildModeOffer(new ProfileState(AssistantProfile.FULL, "", false, true, false, 5));
            assertTrue(p.buildModeBanner().isVisible());
            assertEquals(AiSetupLines.BUILD_MODE_SWITCH, p.buildModeSwitchButton().getText());
            assertEquals(AiSetupLines.BUILD_MODE_KEEP, p.buildModeKeepButton().getText());
            assertTrue(AiSetupLines.BUILD_MODE_OFFER.contains("commands still ask you"), AiSetupLines.BUILD_MODE_OFFER);
            p.buildModeKeepButton().doClick();
            p.buildModeSwitchButton().doClick();
            assertEquals(List.of("5:keep", "5:switch"), said);

            p.renderBuildModeOffer(new ProfileState(AssistantProfile.FULL, "", false, true, false, 0));
            assertFalse(p.buildModeBanner().isVisible(), "answered: gone");
        });
    }
}
