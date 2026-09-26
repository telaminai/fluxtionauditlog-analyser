package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M68.1 re-review R3: every surface that reports a node-name disagreement states the fact and draws no build
 * conclusion. Each sentence must carry its factual phrase, so none of these can pass on empty text.
 */
class MismatchWordingTest {

    @Test
    void eachSentenceStatesTheDisagreementAndNoBuildConclusion() {
        Map<String, String> said = Map.of(
                "finding export", MismatchWording.findingHasNoDeclaredNode(),
                "step-through status", MismatchWording.stepUnknownSuffix(2),
                "focus none declared", MismatchWording.focusNoneDeclared("hedge path", 4),
                "focus partly declared", MismatchWording.focusPartlyDeclared(1, 4));
        for (var e : said.entrySet()) {
            String s = e.getValue().toLowerCase();
            assertTrue(s.contains("not declared") || s.contains("are declared"),
                    e.getKey() + " states which nodes the graph declares: " + e.getValue());
            assertFalse(s.contains("build"), e.getKey() + " draws no build conclusion: " + e.getValue());
            assertFalse(s.contains("version"), e.getKey() + " draws no version conclusion: " + e.getValue());
        }
        assertTrue(MismatchWording.stepUnknownSuffix(2).contains("2 not declared"));
        assertTrue(MismatchWording.focusNoneDeclared("hedge path", 4).contains("'hedge path'"));
        assertTrue(MismatchWording.focusPartlyDeclared(1, 4).startsWith("1 of 4"));
    }
}
