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
 * M80 (this change): the external API's ONLY delta is widening {@code spotlight}'s {@code target} grammar — a Java
 * or design LINE target may now be a range, {@code :line:<from>-<to>}, not only a single {@code :line:<n>}. No
 * parameter was added or renamed; {@code target}/{@code targets}/{@code caption}/{@code add}/{@code clear} are
 * exactly as released ({@code CustomerApiKeysTest}). What moved is two bits of the {@code spotlight} schema's
 * {@code description} text — {@link SpotlightVocabulary#TEXT} (the target grammar) and the hand-written Java
 * paragraph — and, because the in-process manifest embeds {@code SpotlightVocabulary.TEXT} directly, that manifest
 * too. Undo exactly those two text edits and every surface this change touched is byte-identical to the digest
 * {@link WorkModeKeepsTheApiTest} held before it ({@link #BEFORE}); {@link #AFTER} is what it holds now — this test
 * is the new top of the chain {@code SourceTextApiDeltaTest} / {@code GraphRestoreApiDeltaTest} /
 * {@code StarterProposeApiDeltaTest} left off at, so (unlike theirs) its own {@code AFTER} is checked against the
 * LIVE pin, and {@code SourceTextApiDeltaTest}'s equivalent check was retired here for the same reason.
 *
 * <p>Witness: change any OTHER word of the spotlight description, or widen a different verb's grammar — the
 * reverted digest no longer matches {@link #BEFORE} and the named surface fails.
 */
class SpotlightLineRangeApiDeltaTest {

    /** The pins {@link WorkModeKeepsTheApiTest} held before this change (identical to the earlier chain's AFTER). */
    static final Map<String, String> BEFORE = Map.of(
            "VerbSchemas.all", "7c0cfc883f7b0dbb52a94f972f4b676d0cdeb7541ae40d30a4096459e5e93772",
            "VerbSchemas.hosted", "e939a7ab31794f339718b9cf03e9d2b31c8257fee288eab5293039f86f5d40ce",
            "McpTools.list", "ee123c503dc3225505b0bacccb0f24335617bf0698a138801cfc6ea76f4c79b3",
            "McpTools.hostedList", "e4d2cb45af519154c41289bc5d564bc183bc8ac202681f13bc88253d667cc251",
            "PromptBuilder.inProcessActionManifest", "01a161d22a7ea6dca8d83a0e4a0446d68236028603112c7597b989b49d3cae7e");

    /** The pins after it — {@link WorkModeKeepsTheApiTest#PINNED} holds these now. */
    static final Map<String, String> AFTER = Map.of(
            "VerbSchemas.all", "1bd5d0a63fdcf1619e1d9b48ad243c37601ec8180ce8fcbb509683e724d55104",
            "VerbSchemas.hosted", "febb41834b5a151a6b12259e6e18e9fe9de4431cb06f762a9ec4fd4aa7479f84",
            "McpTools.list", "c64dfa256e64f1ff3196c19534cc9524fded7046afadd37c790d9e06fd1bdaea",
            "McpTools.hostedList", "0a62468a2a4dd1efc3bb9014dc9a76c13927a041bc77d233b03c84ae4345746b",
            "PromptBuilder.inProcessActionManifest", "1cd93bb24ceccd21ef2e18055350e1a3776362a68173da94618d8a0b7e7ce54c");

    /** {@link SpotlightVocabulary#TEXT} before the two {@code line:<n>} forms gained a range. */
    static final String OLD_TARGETS_TEXT =
            "tab:<summary|source|graph|topology|reports|assistant> · source:design · source:design:bean:<id> · source:design:line:<n> · source:java:<fqn> · source:java:<fqn>:line:<n> · records · records:row:<recordIndex> · "
                    + "detail · detail:node:<instanceId> · topology · topology:node:<instanceId> · topology:verdict · "
                    + "graph[:<name>] · graph[:<name>]:note:<n> · graph[:<name>]:series:<label> · project · "
                    + "project:<log|graph|processors|roots> · toolbar:<open|flag|explain|follow> · menu:<Menu> · "
                    + "menu:<Menu>:<item> · status";

    /** The spotlight schema's hand-written Java paragraph, before the range sentence was added. */
    static final String OLD_JAVA_BLOCK =
            "Java: source:java:<fqn>[:line:<n>] uses the viewer's first matching root, then enabled local sources jars; "
                    + "source {fqn} instead refuses duplicate roots and never reads jars. Check the echoed origin before using a line number. "
                    + "A repeated Java spotlight rereads source; preparation expires after ten seconds without lighting late. "
                    + "One Java document per set; combine with topology targets to open source beside the graph. "
                    + "Java line partial means a clipped logical line; source/run relationship is unverified. ";

    /** …and after: one sentence about ranges inserted before the one about Java partial. */
    static final String NEW_JAVA_BLOCK =
            "Java: source:java:<fqn>[:line:<n>|<from>-<to>] uses the viewer's first matching root, then enabled local sources jars; "
                    + "source {fqn} instead refuses duplicate roots and never reads jars. Check the echoed origin before using a line number. "
                    + "A repeated Java spotlight rereads source; preparation expires after ten seconds without lighting late. "
                    + "One Java document per set; combine with topology targets to open source beside the graph. "
                    + "A line target may be a range, :line:<from>-<to> (first <= last, at most " + SpotlightVocabulary.MAX_LINE_SPAN
                    + " lines) — java and design alike; the cut-out spans first "
                    + "line's top to last line's bottom, and partial means the range (or a tall single line) is "
                    + "clipped to the viewport, showing its start. Java line partial means a clipped logical line; "
                    + "source/run relationship is unverified. ";

    /** The spotlight schema (or a spotlight tool's input schema) with both text edits undone. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> revertSpotlight(Map<String, Object> spotlight, boolean asTool) {
        Map<String, Object> out = new LinkedHashMap<>(spotlight);
        if (!asTool) {
            String desc = (String) spotlight.get("description");
            assertTrue(desc.contains(NEW_JAVA_BLOCK), "the Java paragraph carries the range sentence");
            assertTrue(desc.contains(SpotlightVocabulary.TEXT), "the description carries the live target grammar");
            desc = desc.replace(NEW_JAVA_BLOCK, OLD_JAVA_BLOCK).replace(SpotlightVocabulary.TEXT, OLD_TARGETS_TEXT);
            out.put("description", desc);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> revertSchemas(Map<String, Object> schemas) {
        Map<String, Object> out = new LinkedHashMap<>(schemas);
        out.put("spotlight", revertSpotlight((Map<String, Object>) schemas.get("spotlight"), false));
        return out;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> revertTools(List<Map<String, Object>> tools) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> t : tools) {
            if ((McpTools.PREFIX + "spotlight").equals(t.get("name"))) {
                Map<String, Object> copy = new LinkedHashMap<>(t);
                String desc = (String) t.get("description");
                assertNotNull(desc, "the tool carries the lifted description");
                copy.put("description", desc.replace(NEW_JAVA_BLOCK, OLD_JAVA_BLOCK).replace(SpotlightVocabulary.TEXT, OLD_TARGETS_TEXT));
                copy.put("inputSchema", revertSpotlight((Map<String, Object>) t.get("inputSchema"), true));
                out.add(copy);
            } else {
                out.add(t);
            }
        }
        return out;
    }

    @Test
    @DisplayName("★ spotlight gains only a line-range grammar and its description text: undone, every surface is as before")
    void theOnlyDeltaIsTheLineRangeGrammar() throws Exception {
        assertEquals(BEFORE.get("VerbSchemas.all"), WorkModeKeepsTheApiTest.sha(revertSchemas(VerbSchemas.all())));
        assertEquals(BEFORE.get("VerbSchemas.hosted"), WorkModeKeepsTheApiTest.sha(revertSchemas(VerbSchemas.hosted())));
        assertEquals(BEFORE.get("McpTools.list"), WorkModeKeepsTheApiTest.sha(revertTools(McpTools.list())));
        assertEquals(BEFORE.get("McpTools.hostedList"), WorkModeKeepsTheApiTest.sha(revertTools(McpTools.hostedList())));
        String manifest = PromptBuilder.inProcessActionManifest(3);
        assertTrue(manifest.contains(SpotlightVocabulary.TEXT), "the in-process manifest embeds the live target grammar directly");
        assertEquals(BEFORE.get("PromptBuilder.inProcessActionManifest"),
                WorkModeKeepsTheApiTest.sha(manifest.replace(SpotlightVocabulary.TEXT, OLD_TARGETS_TEXT)));
        for (var e : AFTER.entrySet()) {
            assertEquals(e.getValue(), WorkModeKeepsTheApiTest.PINNED.get(e.getKey()), "the pin now: " + e.getKey());
        }
    }

    @Test
    @DisplayName("a line range is additive: line:<n> still means exactly line n, and no verb or parameter was added")
    void theRangeIsAdditiveOnly() {
        assertEquals(18, VerbSchemas.all().size(), "no verb was added");
        @SuppressWarnings("unchecked")
        Map<String, Object> spotlight = (Map<String, Object>) VerbSchemas.all().get("spotlight");
        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) spotlight.get("properties");
        assertEquals(java.util.Set.of("target", "caption", "targets", "add", "clear"), props.keySet(),
                "spotlight's released parameters, unchanged");
        assertTrue(((String) spotlight.get("description")).contains("line:<from>-<to>"));
    }
}
