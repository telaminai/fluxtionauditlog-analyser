package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.ToolVocabulary;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.PermissionBy;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Owner decision 2026-10-18: the hosted Claude Code ASKS before it deletes a chart or replaces an existing chart's stored
 * definition; creating a chart, or only viewing one, stays pre-allowed. One classification ({@code McpTools.hostedCallKind}
 * over {@code VerbOperations.graph}, the executor's own choice), fail-closed, used by the adapter (whether to ask) and the
 * server (whether a grant is needed); the node allows a PRE_ALLOWED call without a dialog.
 */
class GraphCallsAreJudgedTest {

    /** One chart exists: "Chart 1". */
    static final McpTools.ChartState CHARTS = name -> "Chart 1".equals(name);

    static HostedCallKind kind(String params) {
        return McpTools.hostedCallKind("graph", Json.parse(params), CHARTS);
    }

    /**
     * ★ Witnesses: classify an EDIT of a named chart without consulting {@code charts.holds} — the replace rows read
     * PRE_ALLOWED and fail; classify DELETE as SHOW — the delete row fails; treat a null chart state as "no chart exists"
     * — the fail-closed row fails.
     */
    @Test
    @DisplayName("★ delete, rename, restore-by-name and every replacement of an existing chart are asked; creating and viewing are not")
    void theClassification() {
        assertEquals(HostedCallKind.CHANGES, kind("{\"name\":\"Chart 1\",\"delete\":true}"));
        assertEquals(HostedCallKind.CHANGES, kind("{\"name\":\"Chart 1\",\"delete\":\"true\"}"), "the executor's reading");
        assertEquals(HostedCallKind.CHANGES, kind("{\"name\":\"nope\",\"delete\":true}"), "a delete is asked whatever the name");
        for (String replace : new String[]{"{\"name\":\"Chart 1\",\"notes\":[]}", "{\"name\":\"Chart 1\",\"clearNotes\":true}",
                "{\"name\":\"Chart 1\",\"series\":[\"a.b\"]}", "{\"name\":\"Chart 1\",\"guides\":[]}",
                "{\"name\":\"Chart 1\",\"bands\":[]}", "{\"name\":\"Chart 1\",\"style\":\"line\"}",
                "{\"name\":\" Chart 1 \",\"notes\":[]}", "{\"name\":\"Chart 1\",\"close\":false}",
                "{\"name\":\"\",\"notes\":[]}"}) {
            assertEquals(HostedCallKind.CHANGES, kind(replace), replace);
        }
        assertEquals(HostedCallKind.CHANGES, kind("{\"name\":\"Chart 1\",\"rename\":\"x\"}"));
        assertEquals(HostedCallKind.CHANGES, kind("{\"restore\":\"Chart 1\"}"));
        assertEquals(HostedCallKind.CHANGES, kind("{\"restore\":true,\"name\":\"x\"}"), "restore must go alone");

        assertEquals(HostedCallKind.PRE_ALLOWED, kind("{\"name\":\"fresh\",\"series\":[\"a.b\"],\"notes\":[]}"), "a new chart");
        assertEquals(HostedCallKind.PRE_ALLOWED, kind("{\"name\":\"Chart 1\",\"newTab\":true,\"series\":[\"a.b\"]}"),
                "newTab creates or is refused: never a replacement");
        assertEquals(HostedCallKind.PRE_ALLOWED, kind("{\"series\":[\"a.b\"]}"), "no name: a new chart");
        assertEquals(HostedCallKind.PRE_ALLOWED, kind("{\"name\":\"Chart 1\"}"), "showing it");
        assertEquals(HostedCallKind.PRE_ALLOWED, kind("{\"name\":\"Chart 1\",\"refresh\":true}"), "re-extracting it");
        assertEquals(HostedCallKind.PRE_ALLOWED, kind("{\"name\":\"Chart 1\",\"close\":true}"), "closing keeps it");
        assertEquals(HostedCallKind.NON_MUTATING, kind("{\"restore\":true}"), "listing the bin");

        assertEquals(HostedCallKind.CHANGES, McpTools.hostedCallKind("graph",
                Json.parse("{\"name\":\"fresh\",\"series\":[\"a.b\"]}"), null), "no chart state: asked (fail-closed)");
        assertEquals(HostedCallKind.CHANGES, McpTools.hostedCallKind("graph", Json.parse("{\"name\":\"fresh\",\"notes\":[]}")),
                "the two-argument form knows no charts");
    }

    /** ★ Witness: drop the newTab injection — a create admitted as free would replace a chart created meanwhile. */
    @Test
    @DisplayName("★ the server runs a create admitted by a free name as newTab; anything else exactly as sent")
    void aFreeNameRunsAsACreate() {
        var created = McpTools.hostedConstrained("graph", Map.of("name", "fresh", "notes", java.util.List.of()), CHARTS);
        assertEquals(Boolean.TRUE, created.get("newTab"));
        Map<String, Object> asked = Map.of("name", "Chart 1", "notes", java.util.List.of());
        assertSame(asked, McpTools.hostedConstrained("graph", asked, CHARTS));
        Map<String, Object> other = Map.of("name", "x");
        assertSame(other, McpTools.hostedConstrained("walk", other, CHARTS));
    }

    @Test
    @DisplayName("graph is judged call by call: asked class, never blanket pre-allowed, visible in every posture")
    void thePosture() {
        assertFalse(LocalAgentPosture.preAllowed("graph"));
        assertTrue(LocalAgentPosture.askable("graph"));
        assertFalse(LocalAgentPosture.hiddenWhereNotAsked("graph"));
        assertEquals(ToolClass.HOSTED_DESTRUCTIVE, ToolVocabulary.of(LocalAgentPosture.toolName("graph")).toolClass());
        var schema = telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas.hosted().keySet();
        for (var mode : LocalAgentPosture.FullMode.values()) {
            assertTrue(LocalAgentPosture.full(mode).visibleVerbs(schema).contains("graph"), "visible under " + mode);
        }
        assertTrue(LocalAgentPosture.ANALYSER_ASSISTANT.visibleVerbs(schema).contains("graph"));
        assertFalse(LocalAgentPosture.ANALYSER_ASSISTANT.cliAllowedVerbs(schema).contains("graph"),
                "where the mode asks, every graph call reaches the analyser as a request");
        assertTrue(LocalAgentPosture.full(LocalAgentPosture.FullMode.AUTO).cliAllowedVerbs(schema).contains("graph"),
                "where it never asks, the CLI runs it and the server refuses what would need an approval");
        assertFalse(McpTools.destructiveVerbs().contains("graph"), "the external MCP annotation is unchanged");
    }

    /**
     * ★ On the REAL generated processor. Witness: drop the PRE_ALLOWED branch in {@code LocalAgentPermission.onRequested}
     * — a new chart opens a dialog; allow every graph request — the delete is allowed with no dialog.
     */
    @Test
    @DisplayName("★ a new chart is allowed without a dialog or a grant; a delete or replacement opens the dialog")
    void theNodeAsksOnlyForChanges() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentPermissionTest.ready(d, a);
        d.post(ApprovalFrictionTest.request(start.generation(), 411, "graph", HostedCallKind.PRE_ALLOWED));
        assertEquals(0, a.permissionDialogs.size(), "creating a chart is pre-allowed: no dialog");
        var allowed = LocalAgentPermissionTest.lastAnswer(a);
        assertTrue(allowed.allow());
        assertEquals(PermissionBy.PRE_ALLOWED, allowed.by(), "recorded as the analyser's own decision, by name");
        assertEquals(0, allowed.grantId(), "no grant");
        assertTrue(d.snapshot().localAgent().entries().stream().noneMatch(e -> e.detail() != null
                && e.detail().contains("changes nothing")), "never described as changing nothing");

        d.post(ApprovalFrictionTest.request(start.generation(), 412, "graph", HostedCallKind.CHANGES));
        assertEquals(1, a.permissionDialogs.size(), "a delete or a replacement is asked");
    }
}
