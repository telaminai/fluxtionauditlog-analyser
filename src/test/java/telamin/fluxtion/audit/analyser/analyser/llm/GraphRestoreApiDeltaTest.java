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
 * Owner decision 2026-10-18 (saved charts are restorable): the external API changes by exactly ONE added optional
 * parameter — {@code graph {restore}} — and the one sentence that stopped being true, {@code graph {delete}}'s "NOT
 * recoverable". Undo exactly those two edits and every external surface is byte-identical to the digest it had before
 * ({@link WorkModeKeepsTheApiTest}'s pins at the time, kept here as {@link #BEFORE}). The in-process manifest (the in-app
 * assistant's, not an external surface) changed the same sentence and is checked the same way.
 *
 * <p>Witness: change any other part of the graph schema (a word of another parameter, a type), or add a second
 * parameter — the reverted digest no longer matches {@link #BEFORE} and the named surface fails.
 */
class GraphRestoreApiDeltaTest {

    /** The pins {@link WorkModeKeepsTheApiTest} held before this change. */
    static final Map<String, String> BEFORE = Map.of(
            "VerbSchemas.all", "93be851191d2fd8f4721be9b1da434a876e6d7eb46c1b5949f4dd396bcceaafa",
            "VerbSchemas.hosted", "1d7f30b286cc6b9edaf44165de627661584cfc137498317ed3f327cafb89e9ca",
            "McpTools.list", "c0c886ccbc54e31e8468dfc8a9249c8ed89bfad6aee7b0760fcd20064815a74b",
            "McpTools.hostedList", "013b88dd2adf39047d51a88b3b4f89fef565ab5cd945d5a2407e56dbd492b632",
            "PromptBuilder.inProcessActionManifest", "65eed4b076b84e808cbb6f61e1bf980f699b3cd8834e209dbacbb2e2de138224");

    static final String OLD_DELETE = "with {name}, remove that chart's DEFINITION — series, formulas, pinned notes and "
            + "explanation. Goes ALONE, like rename. NOT recoverable (unlike a report delete); to put a chart away without "
            + "losing it, use close instead";

    static final String NEW_MANIFEST = "          probes. Each goes ALONE, like rename. A delete, and a call that replaces an existing chart's\n"
            + "          notes, series, guides or bands, keeps the old definition in this machine's chart bin:\n"
            + "          {restore:true} lists it, {restore:\"name or id\"} brings it back. CLEAN UP after yourself:\n";
    static final String OLD_MANIFEST = "          probes. Each goes ALONE, like rename; delete is NOT recoverable. CLEAN UP after yourself:\n";

    /** A graph schema (or a graph tool's input schema) with the restore parameter removed and the old delete words. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> revertGraph(Map<String, Object> graph) {
        Map<String, Object> out = new LinkedHashMap<>(graph);
        Map<String, Object> props = new LinkedHashMap<>((Map<String, Object>) graph.get("properties"));
        assertNotNull(props.remove("restore"), "the added parameter is there");
        Map<String, Object> delete = new LinkedHashMap<>((Map<String, Object>) props.get("delete"));
        delete.put("description", OLD_DELETE);
        props.put("delete", delete);
        out.put("properties", props);
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> revertSchemas(Map<String, Object> schemas) {
        Map<String, Object> out = new LinkedHashMap<>(schemas);
        out.put("graph", revertGraph((Map<String, Object>) schemas.get("graph")));
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> revertTools(List<Map<String, Object>> tools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> t : tools) {
            if ((McpTools.PREFIX + "graph").equals(t.get("name"))) {
                Map<String, Object> copy = new LinkedHashMap<>(t);
                copy.put("inputSchema", revertGraph((Map<String, Object>) t.get("inputSchema")));
                out.add(copy);
            } else {
                out.add(t);
            }
        }
        return out;
    }

    @Test
    @DisplayName("★ the external API gains only graph {restore} (and delete's corrected words): undone, every surface is as before")
    void theOnlyDeltaIsTheRestoreForm() throws Exception {
        assertEquals(BEFORE.get("VerbSchemas.all"), WorkModeKeepsTheApiTest.sha(revertSchemas(VerbSchemas.all())));
        assertEquals(BEFORE.get("VerbSchemas.hosted"), WorkModeKeepsTheApiTest.sha(revertSchemas(VerbSchemas.hosted())));
        assertEquals(BEFORE.get("McpTools.list"), WorkModeKeepsTheApiTest.sha(revertTools(McpTools.list())));
        assertEquals(BEFORE.get("McpTools.hostedList"), WorkModeKeepsTheApiTest.sha(revertTools(McpTools.hostedList())));
        String manifest = PromptBuilder.inProcessActionManifest(3);
        assertTrue(manifest.contains(NEW_MANIFEST), "the in-process manifest says a chart can be restored");
        assertEquals(BEFORE.get("PromptBuilder.inProcessActionManifest"),
                WorkModeKeepsTheApiTest.sha(manifest.replace(NEW_MANIFEST, OLD_MANIFEST)));
    }

    @Test
    @DisplayName("graph {restore} has report's and walk's shape: a name, or true to list; delete no longer says unrecoverable")
    @SuppressWarnings("unchecked")
    void theRestoreFormIsReportsShape() {
        Map<String, Object> graph = (Map<String, Object>) VerbSchemas.all().get("graph");
        Map<String, Object> report = (Map<String, Object>) VerbSchemas.all().get("report");
        Map<String, Object> gp = (Map<String, Object>) ((Map<String, Object>) graph.get("properties")).get("restore");
        Map<String, Object> rp = (Map<String, Object>) ((Map<String, Object>) report.get("properties")).get("restore");
        assertEquals(rp.get("anyOf"), gp.get("anyOf"), "the same value shape as report {restore}");
        assertFalse(Json.write(graph).contains("NOT recoverable"));
        assertFalse(((List<?>) graph.getOrDefault("required", List.of())).contains("restore"), "optional");
    }
}
