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
 * it first, so each older test still lands on its own pins. {@link #BEFORE} is what {@code WorkModeKeepsTheApiTest}
 * pinned before this change; {@link #AFTER} is what it pins now.
 *
 * <p>Witness: change any other word of the {@code open} schema, or add a second parameter — the peeled digest no longer
 * matches {@link #BEFORE} and the named surface fails.
 */
class ProjectReloadApiDeltaTest {

    /** {@link WorkModeKeepsTheApiTest#PINNED} before #177 — the combined M80 pins. */
    static final Map<String, String> BEFORE = Map.of(
            "VerbSchemas.all", "9c13fb070c6c6be1b20e65e1359824264d685494b6e9388fcde8d2727983aa54",
            "VerbSchemas.hosted", "686b70bf0308d32fa7fe640efbe7034c768d4f6284c1ccdf9c6510e0e68c21cc",
            "McpTools.list", "be6bcdfa779d941fe0f6f2ba2a62fa76040daf11bee3a36b0d230e28835488aa",
            "McpTools.hostedList", "54c1cd7bbd4304dc0277584f7e1eaef1ec0c2ed2fdab209f2b32e2ecdf5be6cd",
            "PromptBuilder.inProcessActionManifest", "8dae33cf340c384a1bd16508e8b4fe05c3e9a45130003c08459a675d9f782979");

    /** …and after it: {@link WorkModeKeepsTheApiTest#PINNED} holds these now. */
    static final Map<String, String> AFTER = Map.of(
            "VerbSchemas.all", "0a8bd7ec27c442ff27f7d676b63ac103b1009db980c82d06dddcf21dad7bf9c4",
            "VerbSchemas.hosted", "633f05a2989abef2f8eec294f5210d9facdd3e5d0f730b57d6d41e350644ad07",
            "McpTools.list", "78b434f6a321c5023d272ab92a3fe104f256e6d71099397b5223b4d8d426390f",
            "McpTools.hostedList", "04f6619bbcab56479e16e6659576c193b502eab286eb49763c3cb0794a4acd85",
            "PromptBuilder.inProcessActionManifest", "150eebe3666980ae31f7ef9ae094dcd69f0f52785eeedd3feefa8bc09cf2626d");

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
