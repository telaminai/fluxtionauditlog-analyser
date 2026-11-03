package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpBridge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence-set key (spec-evidence-set-key.md §4.5; owner, 2026-11-03): the MCP server instructions — the one text every
 * client is handed before it picks a tool — tell the assistant to state the graph-shape verdict before explaining
 * anything from a log and its graph. That sentence is the only change to any external surface.
 *
 * <p>Witness: edit any other word of the instructions, or any schema — {@link #onlyTheInstructionsMoved} or
 * {@link #removingTheSentenceGivesTheEarlierPinBack} fails.
 */
class EvidenceSetApiDeltaTest {

    /** The instructions' pin before this change (WorkModeKeepsTheApiTest held it from the first-move sentence on). */
    static final String INSTRUCTIONS_BEFORE = "6a241aab08422f281cd37c5d487238d96ef97c9ac09bfda0d59653eb0c527bb3";

    /** The sentence as the MCP instructions carry it. */
    static final String SENTENCE = SpotlightVocabulary.EVIDENCE_SET.replace("context.graphPairing", "analyser_context.graphPairing");

    /** Remove only the evidence-set sentence, recursively over any surface shape, so other delta chains compose. */
    @SuppressWarnings("unchecked")
    static <T> T withoutEvidenceSet(T value) {
        if (value instanceof String text) return (T) text.replace(SENTENCE, "");
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            map.forEach((key, entry) -> out.put(key, withoutEvidenceSet(entry)));
            return (T) out;
        }
        if (value instanceof List<?> list) return (T) list.stream().map(EvidenceSetApiDeltaTest::withoutEvidenceSet).toList();
        return value;
    }

    @Test
    @DisplayName("★ the instructions gain only the graph-shape sentence: removed, they hash to the earlier pin")
    void removingTheSentenceGivesTheEarlierPinBack() throws Exception {
        assertEquals(INSTRUCTIONS_BEFORE, WorkModeKeepsTheApiTest.sha(withoutEvidenceSet(McpBridge.INSTRUCTIONS)));
        assertEquals(WorkModeKeepsTheApiTest.PINNED.get("McpBridge.INSTRUCTIONS"),
                WorkModeKeepsTheApiTest.sha(McpBridge.INSTRUCTIONS), "the pin now");
    }

    @Test
    void onlyTheInstructionsMoved() throws Exception {
        for (var surface : WorkModeKeepsTheApiTest.surfaces().entrySet()) {
            if (surface.getKey().equals("McpBridge.INSTRUCTIONS")) continue;
            assertEquals(surface.getValue(), withoutEvidenceSet(surface.getValue()), surface.getKey() + " carries no such sentence");
        }
    }

    @Test
    void theSentenceSaysStateTheShapeFirst_inGraphShapeWords_neverSameBuild() {
        assertTrue(McpBridge.INSTRUCTIONS.contains(SENTENCE));
        String s = SENTENCE;
        assertTrue(s.startsWith("STATE THE GRAPH SHAPE FIRST. Before explaining anything"), s);
        assertTrue(s.contains("analyser_context.graphPairing.evidenceSet"), "where the verdict is read");
        for (String word : List.of("MATCH", "MISMATCH", "UNVERIFIED", "the graph shape matches",
                "the graph shape does not match", "the graph is not verified", "evidenceSetNote")) {
            assertTrue(s.contains(word), word);
        }
        assertTrue(s.contains("never say \"the same build\""), "D4: a match is never worded as the same build");
        assertFalse(s.toLowerCase(java.util.Locale.ROOT).contains("refuse"), "D2: a mismatch is stated, never a refusal");
    }
}
