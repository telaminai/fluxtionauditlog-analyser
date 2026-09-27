package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PR #51 review of the response: what an assistant is told about putting a chart away.
 *
 * <p>With {@code graph {close}} the socket gained the act the guidance had been pointing at, but two
 * sentences survived from before it. "Close its tab" is a desktop gesture a socket caller cannot make, so it
 * named the one thing it could not do; and "close is what to use when tidying up" sent an assistant's probes
 * into the profile, because a closed chart keeps its definition — the accumulation #50 exists to stop. Both
 * texts reach the assistant (the in-process action manifest and the MCP schema), so both are pinned.
 */
class ChartRemovalGuidanceTest {

    /** The in-process assistant's verb reference, where the graph guidance lives. */
    private static String manifest() {
        return PromptBuilder.inProcessActionManifest(8);
    }

    private static String graphSchema() {
        return String.valueOf(VerbSchemas.all().get("graph"));
    }

    @Test
    @DisplayName("Both texts name the verb's close, never a tab only a person can close")
    void closeIsTheVerbNotATab() {
        for (String text : new String[]{manifest(), graphSchema()}) {
            assertFalse(text.contains("close its tab"),
                    "a socket caller cannot close a tab; name graph {name, close: true}");
        }
        assertTrue(manifest().contains("close with {name, close:true}"));
    }

    @Test
    @DisplayName("Neither text recommends close for tidying up: a closed probe stays in the profile")
    void closeIsNotHowProbesAreCleanedUp() {
        for (String text : new String[]{manifest(), graphSchema()}) {
            assertFalse(text.contains("what to use when tidying up"),
                    "close keeps the definition, so tidying with it leaves every probe in the profile");
        }
        assertTrue(graphSchema().contains("a closed probe still sits in the profile"),
                "the schema says which act is for probes");
    }
}
