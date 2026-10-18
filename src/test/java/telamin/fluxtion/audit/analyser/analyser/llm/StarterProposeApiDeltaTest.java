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
 * Option 2 (owner, 2026-10-18): the HOSTED {@code propose} gains the {@code starter} kind and its four optional fields.
 * {@code propose} is hosted-only — never in {@code VerbSchemas.all()}, {@code /manifest} or the external MCP tool list —
 * so the external surfaces {@link WorkModeKeepsTheApiTest} pins are untouched, and only its two hosted pins moved. Undo
 * exactly the starter edits and both hosted surfaces give the pins they had before ({@link #BEFORE}).
 *
 * <p>Witness: change any other part of propose's schema (a word of {@code className}, {@code maxItems}) — the reverted
 * digest no longer matches.
 */
class StarterProposeApiDeltaTest {

    /** The hosted pins {@link WorkModeKeepsTheApiTest} held before the starter kind. */
    static final Map<String, String> BEFORE = Map.of(
            "VerbSchemas.hosted", "d54a546ac0ed8a2ec0b0f91fcdda320219c3da5f5e252c3409d3a205daf7e433",
            "McpTools.hostedList", "18ab09ebd59e49aa3c4005b51d4bb232e24e750f7c469a5e1682a90a48d16e44");

    static final String OLD_KIND = "what this item sets up";
    static final String OLD_PATH = "sourceRoot: a directory; graphml: a .graphml file; runbook: a SKILL.md or .md file; "
            + "logToOpen: an audit log file; processor: the generated processor's source file, if known";
    static final String OLD_DESCRIPTION = "Hosted Claude Code only. Offer the person a project set-up. Records an offer and "
            + "changes NOTHING: the analyser checks each item, the person answers each one, and the analyser applies the "
            + "accepted ones itself. Call context afterwards to see each item's outcome. Paths are relative to your working "
            + "directory or absolute inside it.";

    /** propose's schema with the starter kind, its four fields and the added words taken out. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> revertPropose(Map<String, Object> propose, boolean asTool) {
        Map<String, Object> out = new LinkedHashMap<>(propose);
        if (!asTool) out.put("description", OLD_DESCRIPTION);
        Map<String, Object> props = new LinkedHashMap<>((Map<String, Object>) out.get("properties"));
        Map<String, Object> items = new LinkedHashMap<>((Map<String, Object>) props.get("items"));
        Map<String, Object> item = new LinkedHashMap<>((Map<String, Object>) items.get("items"));
        Map<String, Object> fields = new LinkedHashMap<>((Map<String, Object>) item.get("properties"));
        for (String added : List.of("starter", "artifact", "group", "basePackage")) {
            assertNotNull(fields.remove(added), "the added field is there: " + added);
        }
        Map<String, Object> kind = new LinkedHashMap<>((Map<String, Object>) fields.get("kind"));
        List<Object> values = new ArrayList<>((List<Object>) kind.get("enum"));
        assertTrue(values.remove("starter"), "the starter kind is there");
        kind.put("enum", values);
        kind.put("description", OLD_KIND);
        fields.put("kind", kind);
        Map<String, Object> path = new LinkedHashMap<>((Map<String, Object>) fields.get("path"));
        path.put("description", OLD_PATH);
        fields.put("path", path);
        // keep the published key order: kind, path, className, reason
        Map<String, Object> ordered = new LinkedHashMap<>();
        for (String k : List.of("kind", "path", "className", "reason")) ordered.put(k, fields.get(k));
        item.put("properties", ordered);
        items.put("items", item);
        props.put("items", items);
        out.put("properties", props);
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> revertSchemas(Map<String, Object> hosted) {
        Map<String, Object> out = new LinkedHashMap<>(hosted);
        out.put(VerbSchemas.PROPOSE, revertPropose((Map<String, Object>) hosted.get(VerbSchemas.PROPOSE), false));
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> revertTools(List<Map<String, Object>> tools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> t : tools) {
            if ((McpTools.PREFIX + VerbSchemas.PROPOSE).equals(t.get("name"))) {
                Map<String, Object> copy = new LinkedHashMap<>(t);
                copy.put("description", OLD_DESCRIPTION);
                copy.put("inputSchema", revertPropose((Map<String, Object>) t.get("inputSchema"), true));
                out.add(copy);
            } else {
                out.add(t);
            }
        }
        return out;
    }

    @Test
    @DisplayName("★ the hosted propose gains only the starter kind and its fields: undone, both hosted surfaces are as before")
    void theOnlyDeltaIsTheStarterKind() throws Exception {
        // owner 2026-10-18: source {text} is its own later delta (SourceTextApiDeltaTest), undone first
        assertEquals(BEFORE.get("VerbSchemas.hosted"), WorkModeKeepsTheApiTest.sha(revertSchemas(
                SourceTextApiDeltaTest.revertSchemas(VerbSchemas.hosted()))));
        assertEquals(BEFORE.get("McpTools.hostedList"), WorkModeKeepsTheApiTest.sha(revertTools(
                SourceTextApiDeltaTest.revertTools(McpTools.hostedList()))));
    }

    @Test
    @DisplayName("propose stays hosted-only: not in all(), the external tool list, or the manifests")
    void proposeStaysHostedOnly() {
        assertFalse(VerbSchemas.all().containsKey(VerbSchemas.PROPOSE));
        assertTrue(McpTools.list().stream().noneMatch(t -> (McpTools.PREFIX + VerbSchemas.PROPOSE).equals(t.get("name"))));
        assertFalse(PromptBuilder.inProcessActionManifest(3).contains("starter item"));
    }
}
