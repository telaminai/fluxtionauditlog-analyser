package telamin.fluxtion.audit.analyser.analyser.mcp;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder;
import telamin.fluxtion.audit.analyser.analyser.llm.SpotlightVocabulary;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M64.6 — "when to point" is GENERAL guidance only if every way an assistant arrives carries it. Until this
 * it lived in the verb's description and one skill, so an assistant diagnosing a real log had the verb and
 * no reason to reach for it. Three entrances, one text; a fourth entrance belongs in this test.
 */
class SpotlightGuidanceIsAtEveryEntranceTest {

    @Test
    void theInAppAssistantsManifestCarriesTheVerbItsTargetsAndWhenToUseIt() {
        String manifest = PromptBuilder.inProcessActionManifest(20);
        assertTrue(manifest.contains("spotlight {target, caption?}"), "the verb, with its several-at-once form");
        assertTrue(manifest.contains(SpotlightVocabulary.TEXT), "the targets — an assistant cannot guess a closed vocabulary");
        assertTrue(manifest.contains(SpotlightVocabulary.GUIDANCE));
    }

    @Test
    void theCopyPromptRestManifestCarriesIt() {
        assertTrue(PromptBuilder.restActionManifest("http://127.0.0.1:1", "tok", 20).contains(SpotlightVocabulary.GUIDANCE));
    }

    @Test
    void theMcpServerInstructionsCarryIt_namingTheToolAsAnMcpClientSeesIt() {
        assertTrue(McpBridge.INSTRUCTIONS.contains("analyser_spotlight"), McpBridge.INSTRUCTIONS);
        assertTrue(McpBridge.INSTRUCTIONS.contains("POINT BEFORE YOU EXPLAIN"));
    }

    @Test
    void theSystemPromptSaysItToo_conditionally_becauseTheCopyPathMayHaveNoActions() {
        String system = PromptBuilder.systemPrompt();
        assertTrue(system.contains("If you can run analyser actions, point before you explain"), "conditional on having actions");
    }

    @Test
    void theGuidanceSaysTheThreeThingsThatKeepPointingHonest() {
        String g = SpotlightVocabulary.GUIDANCE;
        assertTrue(g.contains("up to " + SpotlightVocabulary.MAX_LIT), "the bound is the real one, not a remembered one");
        assertTrue(g.contains("not evidence"), "a callout is testimony");
        assertTrue(g.contains("flag, a chart note or a report"), "and the durable forms are named");
        assertTrue(g.contains("AFTER filter / goto / graph / topology"), "the ordering trap every client hits once");
    }
}
