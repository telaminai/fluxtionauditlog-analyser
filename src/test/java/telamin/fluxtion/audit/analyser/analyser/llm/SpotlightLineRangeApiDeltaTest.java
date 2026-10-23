package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M80 PR1 (this branch): the external API's ONLY delta is widening {@code spotlight}'s {@code target} grammar — a
 * Java or design LINE target may now be a range, {@code :line:<from>-<to>}, not only a single {@code :line:<n>}. No
 * parameter was added or renamed; {@code target}/{@code targets}/{@code caption}/{@code add}/{@code clear} are
 * exactly as released ({@code CustomerApiKeysTest}). What moved is two bits of the {@code spotlight} schema's
 * {@code description} text — {@link SpotlightVocabulary#TEXT} (the target grammar) and the hand-written Java
 * paragraph — and, because the in-process manifest embeds {@code SpotlightVocabulary.TEXT} directly, that manifest
 * too.
 *
 * <p>This landed as a SIBLING of M80 PR2's {@code source:design:node:<id>} synonym ({@code M80DesignApiDeltaTest}) —
 * both branched from the same base and both touch the five shared digests, in disjoint text ("spotlight"'s
 * description here, "source"'s there). Combined, they are treated as layered mine-on-top-of-PR2's for the chain's
 * bookkeeping: {@link #BEFORE} is the state with PR2's delta already applied and this one not yet (what
 * {@link #withoutLineRange} recovers from the live surfaces); {@link #AFTER} is the combined state
 * {@link WorkModeKeepsTheApiTest#PINNED} holds now. {@code SourceTextApiDeltaTest} / {@code GraphRestoreApiDeltaTest}
 * / {@code StarterProposeApiDeltaTest} each peel this layer (and PR2's, via {@code M80DesignApiDeltaTest.withoutM80})
 * off first, in {@code true} chronological order, to reach their own older pins; {@code SourceTextApiDeltaTest}'s
 * once-"live" tail check now peels both siblings and lands on ITS OWN after-state instead.
 *
 * <p>Witness: change any OTHER word of the spotlight description, or widen a different verb's grammar — the
 * reverted digest no longer matches {@link #BEFORE} and the named surface fails.
 */
class SpotlightLineRangeApiDeltaTest {

    /**
     * The pins with M80 PR2's delta applied and this one not yet — i.e. what {@link #withoutLineRange} recovers
     * from the live (combined) surfaces. Recomputed at the merge of the two branches (M80 PR1 + PR2).
     */
    static final Map<String, String> BEFORE = Map.of(
            "VerbSchemas.all", "a43554eb3362d79d9944a9af01ab5c15a67e3bb1e04ff88be537822dcd038d24",
            "VerbSchemas.hosted", "f7432fe97137ceb098f1ee922a037dfbc3d0865ccc738c39c1fbd2fe0c4938b9",
            "McpTools.list", "2e3d0a49d91030759dcbb4f607aaa8f2fbd393083802c63e263ef88981b336cb",
            "McpTools.hostedList", "e84985ddd397e591fb92c82d2f408620a002e99ccee75d692f9be62167567014",
            "PromptBuilder.inProcessActionManifest", "276fe69a4b9e9d19a6074b893ad470f9ff0f49efc16a85a7e8d5e7a0bcbe205e");

    /** The pins after it — {@link WorkModeKeepsTheApiTest#PINNED} holds these now (combined with M80 PR2). */
    static final Map<String, String> AFTER = Map.of(
            "VerbSchemas.all", "9c13fb070c6c6be1b20e65e1359824264d685494b6e9388fcde8d2727983aa54",
            "VerbSchemas.hosted", "686b70bf0308d32fa7fe640efbe7034c768d4f6284c1ccdf9c6510e0e68c21cc",
            "McpTools.list", "be6bcdfa779d941fe0f6f2ba2a62fa76040daf11bee3a36b0d230e28835488aa",
            "McpTools.hostedList", "54c1cd7bbd4304dc0277584f7e1eaef1ec0c2ed2fdab209f2b32e2ecdf5be6cd",
            "PromptBuilder.inProcessActionManifest", "8dae33cf340c384a1bd16508e8b4fe05c3e9a45130003c08459a675d9f782979");

    /** {@link SpotlightVocabulary#TEXT} before the two {@code line:<n>} forms gained a range. */
    static final String OLD_TARGETS_TEXT =
            "tab:<summary|source|graph|topology|reports|assistant> · source:design · source:design:bean:<id>|source:design:node:<id> · source:design:line:<n> · source:java:<fqn> · source:java:<fqn>:line:<n> · records · records:row:<recordIndex> · "
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

    /**
     * Remove only this branch's two literal text edits, recursively over any surface shape (String/Map/List) —
     * mirrors {@code M80DesignApiDeltaTest.withoutM80}, so the two sibling deltas compose in either order (they
     * touch disjoint text: "spotlight"'s description here, "source"'s there) and either can be applied directly to
     * {@code WorkModeKeepsTheApiTest.surfaces()} without knowing the other exists.
     */
    @SuppressWarnings("unchecked")
    static <T> T withoutLineRange(T value) {
        if (value instanceof String text) return (T) text.replace(NEW_JAVA_BLOCK, OLD_JAVA_BLOCK).replace(SpotlightVocabulary.TEXT, OLD_TARGETS_TEXT);
        if (value instanceof Map<?, ?> map) {
            Map<Object, Object> out = new LinkedHashMap<>();
            map.forEach((key, entry) -> out.put(key, withoutLineRange(entry)));
            return (T) out;
        }
        if (value instanceof List<?> list) return (T) list.stream().map(SpotlightLineRangeApiDeltaTest::withoutLineRange).toList();
        return value;
    }

    /** Kept for the older *ApiDeltaTest chains, which call these by name: thin, typed wrappers over {@link #withoutLineRange}. */
    static Map<String, Object> revertSchemas(Map<String, Object> schemas) { return withoutLineRange(schemas); }
    static List<Map<String, Object>> revertTools(List<Map<String, Object>> tools) { return withoutLineRange(tools); }

    @Test
    @DisplayName("★ spotlight gains only a line-range grammar and its description text: undone, every surface is as before")
    void theOnlyDeltaIsTheLineRangeGrammar() throws Exception {
        // this branch's delta alone, peeled off the LIVE (PR2-combined) surfaces — never a freshly-built schema —
        // so a change to PR2's own text would show up here too if the two edits ever started to overlap
        assertEquals(BEFORE.get("VerbSchemas.all"), WorkModeKeepsTheApiTest.sha(withoutLineRange(VerbSchemas.all())));
        assertEquals(BEFORE.get("VerbSchemas.hosted"), WorkModeKeepsTheApiTest.sha(withoutLineRange(VerbSchemas.hosted())));
        assertEquals(BEFORE.get("McpTools.list"), WorkModeKeepsTheApiTest.sha(withoutLineRange(McpTools.list())));
        assertEquals(BEFORE.get("McpTools.hostedList"), WorkModeKeepsTheApiTest.sha(withoutLineRange(McpTools.hostedList())));
        String manifest = PromptBuilder.inProcessActionManifest(3);
        assertTrue(manifest.contains(SpotlightVocabulary.TEXT), "the in-process manifest embeds the live target grammar directly");
        assertEquals(BEFORE.get("PromptBuilder.inProcessActionManifest"), WorkModeKeepsTheApiTest.sha(withoutLineRange(manifest)));
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
