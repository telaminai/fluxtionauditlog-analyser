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

    /**
     * Review F10: every OTHER surface still hashes to the pin it had before this change — so a schema, a tool list or a
     * manifest that moved alongside the sentence fails here — and none of them carries the sentence.
     */
    @Test
    void onlyTheInstructionsMoved() throws Exception {
        for (var surface : WorkModeKeepsTheApiTest.surfaces().entrySet()) {
            if (surface.getKey().equals("McpBridge.INSTRUCTIONS")) continue;
            assertEquals(OTHERS_BEFORE.get(surface.getKey()), WorkModeKeepsTheApiTest.sha(surface.getValue()),
                    surface.getKey() + " moved: only the instructions may");
            assertEquals(surface.getValue(), withoutEvidenceSet(surface.getValue()), surface.getKey() + " carries no such sentence");
        }
        assertEquals(WorkModeKeepsTheApiTest.surfaces().size() - 1, OTHERS_BEFORE.size(), "every other surface is pinned here");
    }

    /** The other ten surfaces' pins at this change, copied literally (never derived from WorkModeKeepsTheApiTest). */
    static final Map<String, String> OTHERS_BEFORE = Map.ofEntries(
            Map.entry("VerbSchemas.all", "9c13fb070c6c6be1b20e65e1359824264d685494b6e9388fcde8d2727983aa54"),
            Map.entry("VerbSchemas.hosted", "686b70bf0308d32fa7fe640efbe7034c768d4f6284c1ccdf9c6510e0e68c21cc"),
            Map.entry("McpTools.list", "be6bcdfa779d941fe0f6f2ba2a62fa76040daf11bee3a36b0d230e28835488aa"),
            Map.entry("McpTools.hostedList", "54c1cd7bbd4304dc0277584f7e1eaef1ec0c2ed2fdab209f2b32e2ecdf5be6cd"),
            Map.entry("PromptBuilder.inProcessActionManifest", "8dae33cf340c384a1bd16508e8b4fe05c3e9a45130003c08459a675d9f782979"),
            Map.entry("PromptBuilder.restActionManifest", "b6a4c625c3ecba2fa55bc9001d97fe71ab53ffd899c8c4a37e4850178e99aea8"),
            Map.entry("context.handoff derived, project", "591833ea9ec7b553f868403da4ba5d909b2e45b628c93b43fd70dcb170426bb1"),
            Map.entry("context.handoff derived, none", "1623b4af915aef11455cfc7a7587804fa90504f869dde67de3f5177393a04679"),
            Map.entry("context.handoff set research by you", "8037233aed4d5153383d60bb0e73e2924a0828785867e83a5d54be0becc493d1"),
            Map.entry("context.handoff set authoring by socket", "6f810064351561ccd3026f4ca1f2d062e26540d4eb5d8d01dad6c05d8fedfe32"));

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
