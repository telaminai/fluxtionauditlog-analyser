package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ChartBin;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.config.SavedGraphMerge;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.parse.Samples;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner decision 2026-10-18: a saved chart is never destroyed irrecoverably by an assistant or an external client — a
 * delete, or a call that replaces a chart's stored definition, keeps the previous definition in the chart bin, and
 * {@code graph {restore}} brings it back. Driven through the real executor and tabs, wired as the frame wires them.
 */
class ChartsAreRestorableTest {

    private final HeapLogStore store = new HeapLogStore(Samples.sample());
    private final AppConfig config = new AppConfig();
    private GraphTabs tabs;
    private ActionExecutor ex;

    private void wire() {
        config.activeProjectPath = "/work/demo/project.fluxtion-settings";
        tabs = new GraphTabs();
        tabs.setSavedDefinitions(() -> config.savedGraphs);
        tabs.setKnownNames(() -> {
            java.util.Set<String> names = new java.util.LinkedHashSet<>();
            for (var g : config.savedGraphs) names.add(g.name());
            return names;
        });
        tabs.setDeleteListener(name -> config.savedGraphs.removeIf(g -> g.name().equals(name)));
        tabs.setChartBin(GraphTabs.binOver(() -> config));
        tabs.setRestoredListener(spec -> {
            config.savedGraphs.removeIf(g -> g.name().equals(spec.name()));
            config.savedGraphs.add(spec);
        });
        // as MainFrame.syncOpenGraphsIntoConfig: every change merges the open tabs into the saved list
        tabs.setChangeListener(() -> {
            var merged = SavedGraphMerge.merge(config.savedGraphs, tabs.specs());
            config.savedGraphs.clear();
            config.savedGraphs.addAll(merged);
        });
        tabs.bind(store, new FilterState());
        FilterState filter = new FilterState();
        ex = new ActionExecutor(() -> store, () -> filter, tabs, new LogTablePanel(), (r, n, f, k) -> { });
    }

    private static Map<String, Object> fullChart(String name) {
        return Map.of("newTab", true, "name", name, "series", List.of("bidMakerOrder.price"),
                "explanation", "what this chart shows",
                "notes", List.of(Map.of("at", 1_000L, "text", "A"), Map.of("at", 2_000L, "text", "B")),
                "guides", List.of(Map.of("value", 0.004, "label", "limit")),
                "bands", List.of(Map.of("expr", "bidMakerOrder.price > 1", "label", "over")));
    }

    private String digestOf(String name) {
        return ConfigStore.chartDefinitionDigest(tabs.definitionOf(name));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listing() {
        var r = ex.render("graph", Map.of("restore", true));
        assertTrue(r.ok(), r::toString);
        return (List<Map<String, Object>>) r.payload().get("restorable");
    }

    /**
     * ★ Witness: in {@code GraphTabs.deleteConfirmed}, drop the {@code bin.keep} — the bin is empty after the delete and
     * the restore is refused.
     */
    @Test
    @DisplayName("★ graph {delete} then graph {restore} returns the exact definition — series, notes, guides, bands, explanation")
    void deleteThenRestoreReturnsTheExactDefinition() {
        wire();
        assertTrue(ex.render("graph", fullChart("g")).ok());
        String before = digestOf("g");
        var deleted = ex.render("graph", Map.of("name", "g", "delete", true));
        assertTrue(deleted.ok(), deleted::toString);
        assertTrue(String.valueOf(deleted.payload().get("note")).contains("graph {restore"), "the reply says how");
        assertNull(tabs.definitionOf("g"), "gone from the project");
        var rows = listing();
        assertEquals(1, rows.size(), rows::toString);
        assertEquals("g", rows.get(0).get("name"));
        assertEquals("deleted", rows.get(0).get("reason"));

        var restored = ex.render("graph", Map.of("restore", "g"));
        assertTrue(restored.ok(), restored::toString);
        assertEquals("g", restored.payload().get("restored"));
        assertEquals(before, digestOf("g"), "exactly the definition that was deleted");
        assertTrue(tabs.graphNames().contains("g"), "and it is open again");
        assertTrue(listing().isEmpty(), "it left the bin");
    }

    /** ★ A closed chart's delete is kept too. Witness: drop the {@code bin.keep} in {@code GraphTabs.deleteNamed}. */
    @Test
    @DisplayName("★ deleting a CLOSED chart keeps its saved definition in the bin")
    void deletingAClosedChartIsKept() {
        wire();
        ex.render("graph", fullChart("g"));
        ex.render("graph", fullChart("other"));
        String before = digestOf("g");
        assertTrue(ex.render("graph", Map.of("name", "g", "close", true)).ok());
        assertTrue(ex.render("graph", Map.of("name", "g", "delete", true)).ok());
        assertTrue(ex.render("graph", Map.of("restore", "g")).ok());
        assertEquals(before, digestOf("g"));
    }

    /**
     * ★ Witness: in {@code ActionExecutor.doGraph}, drop {@code graphTabs.keepIfReplaced} — the bin is empty after the
     * re-send and the earlier notes are lost.
     */
    @Test
    @DisplayName("★ re-sending notes (or clearNotes) on an existing chart keeps the previous definition; an unchanged re-send keeps nothing")
    void anOverwriteKeepsThePrevious() {
        wire();
        ex.render("graph", fullChart("g"));
        String original = digestOf("g");
        GraphSpec originalSpec = tabs.definitionOf("g");

        var same = ex.render("graph", Map.of("name", "g", "notes",
                List.of(Map.of("at", 1_000L, "text", "A"), Map.of("at", 2_000L, "text", "B"))));
        assertTrue(same.ok());
        assertTrue(listing().isEmpty(), "a re-send that changes nothing replaces nothing");

        var replaced = ex.render("graph", Map.of("name", "g", "notes", List.of(Map.of("at", 3_000L, "text", "only C"))));
        assertTrue(replaced.ok(), replaced::toString);
        assertNotNull(replaced.payload().get("previousDefinition"), "the reply says the old one was kept");
        var rows = listing();
        assertEquals(1, rows.size());
        assertEquals("replaced", rows.get(0).get("reason"));
        assertEquals(1, tabs.definitionOf("g").notes().size(), "the call did replace the notes");

        ex.render("graph", Map.of("name", "g", "clearNotes", true));
        assertEquals(2, listing().size(), "clearNotes keeps what it cleared");

        // restore the ORIGINAL: refused while "g" exists, then by id after the current one is deleted (kept too)
        String originalId = String.valueOf(listing().get(1).get("id"));
        var refused = ex.render("graph", Map.of("restore", originalId));
        assertFalse(refused.ok(), "restoring never replaces the chart that has the name now");
        ex.render("graph", Map.of("name", "g", "delete", true));
        assertTrue(ex.render("graph", Map.of("restore", originalId)).ok());
        assertEquals(original, digestOf("g"), "the original definition, exactly");
        assertEquals(originalSpec.notes(), tabs.definitionOf("g").notes());
    }

    @Test
    @DisplayName("series, guides and bands re-sent to an existing chart keep the previous definition")
    void otherReplacementsAreKept() {
        wire();
        ex.render("graph", fullChart("g"));
        ex.render("graph", Map.of("name", "g", "guides", List.of(Map.of("value", 0.5, "label", "new"))));
        ex.render("graph", Map.of("name", "g", "bands", List.of()));
        ex.render("graph", Map.of("name", "g", "series", List.of("askMakerOrder.price")));
        assertEquals(3, listing().size(), listing()::toString);
    }

    @Test
    @DisplayName("graph {restore} is refused with any other field, and lists without a log loaded")
    void restoreGoesAlone() {
        wire();
        var mixed = ex.render("graph", Map.of("restore", true, "name", "g"));
        assertFalse(mixed.ok());
        assertTrue(mixed.toString().contains("must be used alone"), mixed::toString);
        tabs.unbind();
        assertTrue(ex.render("graph", Map.of("restore", true)).ok(), "the bin needs no log");
    }

    /** ★ Witness: restore the old "It cannot be undone." — this fails. */
    @Test
    @DisplayName("★ the chart-delete confirmation says the chart can be restored")
    void theConfirmationSaysRestorable() {
        String q = GraphTabs.deleteQuestion("probe");
        assertTrue(q.contains("It can be restored"), q);
        assertFalse(q.toLowerCase().contains("cannot be undone"), q);
    }

    static PermissionText.Content hosted(String input) {
        return PermissionText.of(new LocalAgentTranscript.Permission("mcp__fluxtion-analyser-hosted__analyser_graph",
                "fluxtion-analyser-hosted", "Analyser graph", input), ToolClass.HOSTED_DESTRUCTIVE, "graph");
    }

    /**
     * ★ The approval dialog says what THIS call does. Witness: drop the {@code "graph"} case in
     * {@code PermissionText.hostedCall} — the title reads "asks to run analyser_graph" and the first assertion fails.
     */
    @Test
    @DisplayName("★ the dialog: Delete the chart \"x\" (it can be restored); Replace the chart \"x\" (its old definition can be restored)")
    void theSentences() {
        var del = hosted("{\"name\":\"x\",\"delete\":true}");
        assertEquals("Claude Code asks to delete the chart “x” (it can be restored)", del.title());
        assertTrue(del.changes().contains("graph {restore} brings it back"), del.changes());
        var replace = hosted("{\"name\":\"x\",\"notes\":[{\"at\":1,\"text\":\"t\"}]}");
        assertEquals("Claude Code asks to replace the chart “x” (its old definition can be restored)", replace.title());
        assertTrue(replace.changes().contains("its pinned notes"), replace.changes());
        assertEquals("Claude Code asks to replace the chart “x” (its old definition can be restored)",
                hosted("{\"name\":\"x\",\"clearNotes\":true}").title());
        assertEquals("Claude Code asks to rename the chart “x” to “y”", hosted("{\"name\":\"x\",\"rename\":\"y\"}").title());
        assertEquals("Claude Code asks to restore the chart “x”", hosted("{\"restore\":\"x\"}").title());
        assertEquals("Claude Code asks to delete the chart “x” (it can be restored)",
                hosted("{\"name\":\"x\",\"delete\":\"true\"}").title(), "a string true is a delete, as the executor reads it");
    }
}
