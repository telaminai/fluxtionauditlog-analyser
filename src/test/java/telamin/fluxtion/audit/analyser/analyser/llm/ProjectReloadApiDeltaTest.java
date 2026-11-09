package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #177 (this branch): the external API's ONLY delta is one optional parameter, {@code open {reload}} — with
 * {@code project} naming the project already open, its profile is read from disk again. No parameter was renamed or
 * removed and no verb was added. What moved is the {@code open} schema's property list (both schema maps and both MCP
 * tool lists carry it) and two lines of the in-process manifest, which names every published parameter
 * ({@code InProcessManifestNamesEveryVerbTest}).
 *
 * <p>The LATEST layer of the chain: {@link #withoutReload} peels it off the live surfaces, and
 * {@code SpotlightLineRangeApiDeltaTest.withoutLineRange} — the entry every older *ApiDeltaTest goes through — applies
 * it first (then the flag-kind layer, {@code FlagKindApiDeltaTest}), so each older test still lands on its own pins. {@link #BEFORE} is what {@code WorkModeKeepsTheApiTest}
 * pinned before this change; {@link #AFTER} is what it pins now.
 *
 * <p>Witness: change any other word of the {@code open} schema, or add a second parameter — the peeled digest no longer
 * matches {@link #BEFORE} and the named surface fails.
 */
class ProjectReloadApiDeltaTest {

    /**
     * {@link WorkModeKeepsTheApiTest#PINNED} before #177: the pins after the flag-kind change
     * ({@link FlagKindApiDeltaTest#AFTER}), which this branch was merged on top of.
     */
    static final Map<String, String> BEFORE = Map.of(
            "VerbSchemas.all", "1200748e764954a8ac229ae9d5c4297000a361e44e7bf8a6259dec70560c1c12",
            "VerbSchemas.hosted", "5218b99c3052899b848104c9c55c6cc523a51f3201d044d194308369a974c57f",
            "McpTools.list", "bfdb1203d7486c5ff0cf1e6d19b8648c5cbac2d8203cee6d8c8c880cf0901872",
            "McpTools.hostedList", "413f8954eb879f9e0e1e0077db9701a965a4f01aff63c23f8a52675ce8e46885",
            "PromptBuilder.inProcessActionManifest", "edbf0498f259e609e7e7b8f03eaad26106df929d008e9232fc31001899a14a46");

    /** …and after it: {@link WorkModeKeepsTheApiTest#PINNED} holds these now. */
    static final Map<String, String> AFTER = Map.of(
            "VerbSchemas.all", "bff18ff3b6c2b9cb2154d2a6d5ccba4f6278e2512174045aed1fa861966ed1a1",
            "VerbSchemas.hosted", "69d31b6eba664d46b40cfce3c050bd5599488530e1b67ffa8c4b081bca4edd84",
            "McpTools.list", "1c944ccb7e43652bb379b6ae0c331890fc9caef8689dc57f23ad26ef19b18fbb",
            "McpTools.hostedList", "abcb97f9a66d3874e3b258cde899c45131549e9d46848317050781abf4505587",
            "PromptBuilder.inProcessActionManifest", "99046876c6e3ff88a0368eecbc6c5300f056e0ec4e4fcb085e0b113b43a44441");

    /** The two manifest lines this change added, verbatim. */
    static final String MANIFEST_LINES =
            "          {project, reload: true} on the project already open re-reads its profile from disk (an outside edit;\n"
                    + "          unsaved edits are discarded, the log and graph stay open);\n";

    /**
     * Remove only this branch's delta, recursively over any surface shape: the {@code reload} property from whichever
     * schema's {@code properties} carries it (only {@code open}'s does), and the manifest's two lines.
     */
    @SuppressWarnings("unchecked")
    static <T> T withoutReload(T value) {
        if (value instanceof String text) return (T) text.replace(MANIFEST_LINES, "");
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            map.forEach((key, entry) -> {
                if ("properties".equals(key) && entry instanceof Map<?, ?> props && props.containsKey("reload")) {
                    Map<Object, Object> kept = new LinkedHashMap<>();
                    props.forEach((k, v) -> { if (!"reload".equals(k)) kept.put(k, withoutReload(v)); });
                    out.put(key, kept);
                } else {
                    out.put(key, withoutReload(entry));
                }
            });
            return (T) out;
        }
        if (value instanceof List<?> list) return (T) list.stream().map(ProjectReloadApiDeltaTest::withoutReload).toList();
        return value;
    }

    @Test
    @DisplayName("★ open gains only an optional reload parameter (and the manifest names it): undone, every surface is as before")
    void theOnlyDeltaIsOpenReload() throws Exception {
        assertEquals(FlagKindApiDeltaTest.AFTER, BEFORE, "this layer starts where the flag-kind layer ended");
        for (var e : BEFORE.entrySet()) {
            Object live = WorkModeKeepsTheApiTest.surfaces().get(e.getKey());
            assertEquals(e.getValue(), WorkModeKeepsTheApiTest.sha(withoutReload(live)), "only open {reload} may change: " + e.getKey());
            assertEquals(AFTER.get(e.getKey()), WorkModeKeepsTheApiTest.sha(live), "the live surface now: " + e.getKey());
            assertEquals(AFTER.get(e.getKey()), WorkModeKeepsTheApiTest.PINNED.get(e.getKey()), "the pin now: " + e.getKey());
        }
        for (var e : WorkModeKeepsTheApiTest.surfaces().entrySet()) {
            if (BEFORE.containsKey(e.getKey())) continue;
            assertEquals(WorkModeKeepsTheApiTest.PINNED.get(e.getKey()), WorkModeKeepsTheApiTest.sha(e.getValue()),
                    "no other surface moved: " + e.getKey());
        }
    }

    @Test
    @DisplayName("reload is additive: optional, boolean, on open only; no verb or other parameter was added")
    @SuppressWarnings("unchecked")
    void reloadIsAdditiveOnly() {
        assertEquals(18, VerbSchemas.all().size(), "no verb was added");
        Map<String, Object> open = (Map<String, Object>) VerbSchemas.all().get("open");
        Map<String, Object> props = (Map<String, Object>) open.get("properties");
        assertTrue(props.containsKey("reload"));
        assertEquals("boolean", ((Map<String, Object>) props.get("reload")).get("type"));
        assertFalse(String.valueOf(open.get("required")).contains("reload"), "optional");
        for (var verb : VerbSchemas.all().entrySet()) {
            if (verb.getKey().equals("open")) continue;
            assertFalse(((Map<String, Object>) ((Map<String, Object>) verb.getValue()).get("properties")).containsKey("reload"),
                    verb.getKey() + " gained nothing");
        }
    }
}
