package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner, 2026-10-18: the external API changes by exactly ONE added, optional, read-only parameter — {@code source {text}}
 * — and one added sentence of {@code source}'s description (use it to show code, instead of reading files). Undo exactly
 * those two edits and every surface is byte-identical to the digest {@link WorkModeKeepsTheApiTest} pinned before
 * ({@link #BEFORE}); the in-process manifest lists the parameter names, so it gains {@code text} and is checked the same
 * way. The MCP instructions, the REST manifest and {@code context.handoff} did not change.
 *
 * <p>Witness: change any other part of the source schema (a word of {@code fqn}'s description), or add a second
 * parameter — the reverted digest no longer matches {@link #BEFORE} and the named surface fails.
 *
 * <p>{@link #AFTER} was this change's live pin at the time ({@code WorkModeKeepsTheApiTest.PINNED} held exactly these
 * values, and this test said so). A later change — {@code SpotlightLineRangeApiDeltaTest} (M80) — moved four of these
 * five pins again, so that check now belongs there, not here; {@link #AFTER} stays as the historical record of what
 * this change alone produced.
 */
class SourceTextApiDeltaTest {

    /** The pins {@link WorkModeKeepsTheApiTest} held before {@code source {text}}. */
    static final Map<String, String> BEFORE = Map.of(
            "VerbSchemas.all", "057e731d90ba55d7d28a7f0972c237cfd2d0f420395716d43579d21d676259d4",
            "VerbSchemas.hosted", "27f0694acacfa40a9afdce46e97e6ab37cfe7a1d239b95da96cdbdc83c408ebd",
            "McpTools.list", "4b7a627285c8cb81a01be2566ecb6294b0feb33b789ef7f53baf6220f3b308da",
            "McpTools.hostedList", "099c47f18645d90e517b910246cc55287dced8a875abff19716ac973f4038a0b",
            "PromptBuilder.inProcessActionManifest", "2df1e7478f92c76b9bf4eef1f52f225effb09d0c552bb42f8806141d0a58d40d");

    /** The pins after source-text and before the separately approved M80 metadata delta. */
    static final Map<String, String> AFTER = Map.of(
            "VerbSchemas.all", "7c0cfc883f7b0dbb52a94f972f4b676d0cdeb7541ae40d30a4096459e5e93772",
            "VerbSchemas.hosted", "e939a7ab31794f339718b9cf03e9d2b31c8257fee288eab5293039f86f5d40ce",
            "McpTools.list", "ee123c503dc3225505b0bacccb0f24335617bf0698a138801cfc6ea76f4c79b3",
            "McpTools.hostedList", "e4d2cb45af519154c41289bc5d564bc183bc8ac202681f13bc88253d667cc251",
            "PromptBuilder.inProcessActionManifest", "01a161d22a7ea6dca8d83a0e4a0446d68236028603112c7597b989b49d3cae7e");

    static final String OLD_DESCRIPTION = "Read-only source glance; rereads XML or Java under authorised source roots. "
            + "Exclusive selectors: {file}, {file,line}, {line}, {bean}, {file,bean}, {fqn}, {fqn,method}. "
            + "A glance never replaces open.design. Bean matches aid navigation, not evidence; relationship to the loaded run "
            + "remains unverified.";

    @SuppressWarnings("unchecked")
    static Map<String, Object> revertSource(Map<String, Object> source, boolean asTool) {
        Map<String, Object> out = new LinkedHashMap<>(source);
        if (!asTool) out.put("description", OLD_DESCRIPTION);
        Map<String, Object> props = new LinkedHashMap<>((Map<String, Object>) source.get("properties"));
        assertNotNull(props.remove("text"), "the added parameter is there");
        out.put("properties", props);
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> revertSchemas(Map<String, Object> schemas) {
        schemas = M80DesignApiDeltaTest.withoutM80(schemas);
        Map<String, Object> out = new LinkedHashMap<>(schemas);
        out.put("source", revertSource((Map<String, Object>) schemas.get("source"), false));
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> revertTools(List<Map<String, Object>> tools) {
        tools = M80DesignApiDeltaTest.withoutM80(tools);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> t : tools) {
            if ((McpTools.PREFIX + "source").equals(t.get("name"))) {
                Map<String, Object> copy = new LinkedHashMap<>(t);
                copy.put("description", OLD_DESCRIPTION);
                copy.put("inputSchema", revertSource((Map<String, Object>) t.get("inputSchema"), true));
                out.add(copy);
            } else {
                out.add(t);
            }
        }
        return out;
    }

    @Test
    @DisplayName("★ source gains only the optional text parameter and one sentence: undone, every surface is as before")
    void theOnlyDeltaIsSourceText() throws Exception {
        // M80's line-range grammar (this branch) and M80 PR2's node synonym are both LATER, sibling deltas
        // (SpotlightLineRangeApiDeltaTest / M80DesignApiDeltaTest); undone first everywhere. revertSchemas/
        // revertTools already strip M80 PR2's text internally, so only this branch's needs stripping here too.
        assertEquals(BEFORE.get("VerbSchemas.all"), WorkModeKeepsTheApiTest.sha(
                revertSchemas(SpotlightLineRangeApiDeltaTest.revertSchemas(VerbSchemas.all()))));
        assertEquals(BEFORE.get("VerbSchemas.hosted"), WorkModeKeepsTheApiTest.sha(
                revertSchemas(SpotlightLineRangeApiDeltaTest.revertSchemas(VerbSchemas.hosted()))));
        assertEquals(BEFORE.get("McpTools.list"), WorkModeKeepsTheApiTest.sha(
                revertTools(SpotlightLineRangeApiDeltaTest.revertTools(McpTools.list()))));
        assertEquals(BEFORE.get("McpTools.hostedList"), WorkModeKeepsTheApiTest.sha(
                revertTools(SpotlightLineRangeApiDeltaTest.revertTools(McpTools.hostedList()))));
        String manifest = M80DesignApiDeltaTest.withoutM80(SpotlightLineRangeApiDeltaTest.withoutLineRange(
                PromptBuilder.inProcessActionManifest(3)));
        String added = "  source {file, bean, line, fqn, method, text} -> ";
        assertTrue(manifest.contains(added), "the in-process manifest lists the parameter");
        assertEquals(BEFORE.get("PromptBuilder.inProcessActionManifest"), WorkModeKeepsTheApiTest.sha(
                manifest.replace(added, "  source {file, bean, line, fqn, method} -> ")));
        // "the pin before M80 and the line-range grammar": strip BOTH sibling deltas from the LIVE surfaces and
        // you get back to what this change alone produced (AFTER) — proves neither sibling touched source {text}.
        for (var e : AFTER.entrySet()) {
            assertEquals(e.getValue(), WorkModeKeepsTheApiTest.sha(M80DesignApiDeltaTest.withoutM80(
                    SpotlightLineRangeApiDeltaTest.withoutLineRange(WorkModeKeepsTheApiTest.surfaces().get(e.getKey())))),
                    "the pin before M80 and the line-range grammar: " + e.getKey());
        }
    }

    @Test
    @DisplayName("source {text} is optional, read-only, and no new verb: the verb count stays 18")
    void additiveAndReadOnly() {
        @SuppressWarnings("unchecked")
        Map<String, Object> source = (Map<String, Object>) VerbSchemas.all().get("source");
        assertTrue(((List<?>) source.getOrDefault("required", List.of())).isEmpty(), "nothing is required");
        assertEquals(18, VerbSchemas.all().size(), "no verb was added: the verb count stays 18");
        assertEquals(19, VerbSchemas.hosted().size(), "…and the hosted list is those 18 + propose");
        Map<?, ?> annotations = (Map<?, ?>) McpTools.list().stream().filter(t -> "analyser_source".equals(t.get("name")))
                .findFirst().orElseThrow().get("annotations");
        assertEquals(Boolean.TRUE, annotations.get("readOnlyHint"), "source stays read-only");
    }
}
