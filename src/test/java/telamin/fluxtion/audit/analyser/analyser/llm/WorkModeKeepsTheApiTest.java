package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpBridge;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner feedback, 2026-10-17: the shared canvas's "posture" is "Work mode" (Investigate / Build) in everything a PERSON
 * reads; the external API keeps saying posture. Each surface an external client reads is pinned by the SHA-256 of its
 * JSON, taken at the rename ({@code ff478f33}).
 *
 * <p><b>What the pin proves</b> (review PR151 F3, 2026-10-18): from the rename on, these surfaces do not change — the
 * MCP/REST schemas ({@code VerbSchemas}), the MCP tool lists, {@code context.handoff}, the MCP server instructions, the
 * in-process manifest and the REST action manifest Copy prompt appends. Of these, the schemas, the tool lists and
 * {@code context.handoff} are also unchanged across M74 P3 (their sources are not in its diff).
 *
 * <p><b>What it does not prove</b>: that the instructions and manifest TEXT are unchanged across M74 P3. Earlier on the
 * same branch (before the pin) the MCP server instructions — and with them {@code /manifest}'s {@code instructions} and
 * the spotlight guidance in the REST manifest — gained the caption-length sentence and the name "External MCP/REST
 * access", and the bridge's not-running and the 403 texts changed; those changes are listed in CHANGELOG ▸ Unreleased.
 * The {@code /manifest} envelope's other fields are not pinned here.
 *
 * <p>Witness: rename the parameter's description, a value, or the context note's words — the matching digest fails.
 */
class WorkModeKeepsTheApiTest {

    static String sha(Object o) throws Exception {
        // canonical JSON (sorted keys): a schema map's iteration order is not part of the API
        String json = o instanceof String s ? s : CanonicalJson.canonical(o);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));
    }

    static final Instant AT = Instant.parse("2026-10-17T12:00:00Z");

    static Map<String, Object> surfaces() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("VerbSchemas.all", VerbSchemas.all());
        out.put("VerbSchemas.hosted", VerbSchemas.hosted());
        out.put("McpTools.list", McpTools.list());
        out.put("McpTools.hostedList", McpTools.hostedList());
        out.put("McpBridge.INSTRUCTIONS", McpBridge.INSTRUCTIONS);
        out.put("PromptBuilder.inProcessActionManifest", PromptBuilder.inProcessActionManifest(3));
        // review PR151 F3: Copy prompt's REST manifest, for a fixed endpoint and token
        out.put("PromptBuilder.restActionManifest", PromptBuilder.restActionManifest("http://127.0.0.1:1", "T", 3));
        out.put("context.handoff derived, project", CanvasHandoff.toContext(null, null, true));
        out.put("context.handoff derived, none", CanvasHandoff.toContext(null, null, false));
        out.put("context.handoff set research by you", CanvasHandoff.toContext(
                new CanvasHandoff.PostureSetting(CanvasHandoff.Posture.RESEARCH, CanvasHandoff.Author.HUMAN, AT), null, true));
        out.put("context.handoff set authoring by socket", CanvasHandoff.toContext(
                new CanvasHandoff.PostureSetting(CanvasHandoff.Posture.AUTHORING, CanvasHandoff.Author.AGENT, AT), null, false));
        return out;
    }

    /**
     * The digests at the rename (and, for the REST manifest, at review PR151 F3 — no change since the rename) — except
     * the five the owner decision of 2026-10-18 changed deliberately (graph {restore} added, delete's "NOT recoverable"
     * corrected): {@link GraphRestoreApiDeltaTest} proves undoing exactly that gives the earlier pins back.
     */
    static final Map<String, String> PINNED = Map.ofEntries(
            Map.entry("VerbSchemas.all", "057e731d90ba55d7d28a7f0972c237cfd2d0f420395716d43579d21d676259d4"),
            Map.entry("VerbSchemas.hosted", "d54a546ac0ed8a2ec0b0f91fcdda320219c3da5f5e252c3409d3a205daf7e433"),
            Map.entry("McpTools.list", "4b7a627285c8cb81a01be2566ecb6294b0feb33b789ef7f53baf6220f3b308da"),
            Map.entry("McpTools.hostedList", "18ab09ebd59e49aa3c4005b51d4bb232e24e750f7c469a5e1682a90a48d16e44"),
            Map.entry("McpBridge.INSTRUCTIONS", "fdcf8b03dab8d45d9c197d230961a41518faa0319b6f5a9cc6a51662cfdf144a"),
            Map.entry("PromptBuilder.inProcessActionManifest", "2df1e7478f92c76b9bf4eef1f52f225effb09d0c552bb42f8806141d0a58d40d"),
            Map.entry("PromptBuilder.restActionManifest", "b6a4c625c3ecba2fa55bc9001d97fe71ab53ffd899c8c4a37e4850178e99aea8"),
            Map.entry("context.handoff derived, project", "591833ea9ec7b553f868403da4ba5d909b2e45b628c93b43fd70dcb170426bb1"),
            Map.entry("context.handoff derived, none", "1623b4af915aef11455cfc7a7587804fa90504f869dde67de3f5177393a04679"),
            Map.entry("context.handoff set research by you", "8037233aed4d5153383d60bb0e73e2924a0828785867e83a5d54be0becc493d1"),
            Map.entry("context.handoff set authoring by socket", "6f810064351561ccd3026f4ca1f2d062e26540d4eb5d8d01dad6c05d8fedfe32"));

    @Test
    @DisplayName("★ the external API is byte-identical: schemas, MCP tools, instructions, manifest, context.handoff")
    void theApiIsUnchanged() throws Exception {
        for (var e : surfaces().entrySet()) {
            assertEquals(PINNED.get(e.getKey()), sha(e.getValue()), e.getKey() + " changed — an external client reads it");
        }
        assertEquals(11, PINNED.size());
    }

    @Test
    @DisplayName("the API still says posture: the open parameter, its three values, and context.handoff.posture")
    void theApiStillSaysPosture() {
        @SuppressWarnings("unchecked")
        Map<String, Object> open = (Map<String, Object>) VerbSchemas.all().get("open");
        assertTrue(Json.write(open).contains("\"posture\""), "open {posture} is the parameter");
        assertTrue(Json.write(open).contains("\"research\"") && Json.write(open).contains("\"authoring\"")
                && Json.write(open).contains("\"derived\""));
        assertTrue(CanvasHandoff.toContext(null, null, true).containsKey("posture"));
        assertEquals(List.of("research/support", "authoring/deploy"),
                List.of(CanvasHandoff.Posture.RESEARCH.label(), CanvasHandoff.Posture.AUTHORING.label()),
                "the values clients read");
    }
}
