package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The scorecard's requirements R1, R2 and R7 (docs/proposals/beta-testing/m74-onboarding/SCORECARD.md, §7) and the P3
 * rule that no approval-shaped tool exists anywhere the model can reach (§10 P3) — the facts a run is scored from, read
 * from recorded lines through the REAL parser and adapter.
 */
class LocalAgentScorecardFactsTest {

    @TempDir
    Path home;

    /** ★ R2. Witness: post the note without the tool ({@code LocalAgentNoted(…, 0)}) — the tool assertion fails. */
    @Test
    @DisplayName("★ R2: a denial note names the tool from the fixed vocabulary (the recorded outside Read)")
    void aDenialNamesItsTool() throws Exception {
        FixtureAgent agent = new FixtureAgent("restricted-outside-read-denied.jsonl");
        agent.conformInit = true;
        Harness h = new Harness(agent, home);
        h.start();
        h.send("read outside");
        var notes = h.facts(SessionEvents.LocalAgentNoted.class).stream()
                .filter(n -> n.note() == SessionEvents.AgentNote.PERMISSION_DENIED).toList();
        assertFalse(notes.isEmpty(), "the recorded denial was noted");
        assertEquals("Read", notes.get(0).tool());
        assertTrue(h.state().entries().stream().anyMatch(e -> e.detail().contains("(Read)")), "and the note says so");
        assertEquals("analyser_report", ToolVocabulary.word("mcp__fluxtion-analyser-hosted__analyser_report"));
        assertEquals("other-mcp", ToolVocabulary.word("mcp__db-tools__query"), "a person's server tool: its class only");
        assertEquals("persons-fluxtion-analyser", ToolVocabulary.word("mcp__fluxtion-analyser__analyser_open"));
        assertEquals(ToolVocabulary.OTHER_BUILT_IN, ToolVocabulary.word("NewCliTool"));
        assertEquals("unknown", ToolVocabulary.word("<html>x"));
    }

    /** ★ R7. Witness: drop the skills/plugins from the init fact — the names are missing. */
    @Test
    @DisplayName("★ R7: the init fact records the skills and plugins a turn's system/init reports — names only, no path")
    void theInitRecordsSkillsAndPlugins() throws Exception {
        String line = Files.readAllLines(LocalAgentPermissionReplayTest.P3_FIXTURES.resolve("p3-lines-full-auto-iso.jsonl"))
                .stream().filter(l -> l.contains("\"subtype\": \"init\"")).findFirst().orElseThrow();
        var init = (StreamJson.SystemInit) StreamJson.parse(line);
        assertEquals(List.of("agents-md", "telemetry"), init.plugins(), "plugins by name; their paths are never read");
        assertEquals(17, init.skills().size());
        assertFalse(init.plugins().toString().contains("builtin@") || init.plugins().toString().contains("path"));
    }

    /** ★ R1. Witness: post the admission fact without the verb — the record's verb is null. */
    @Test
    @DisplayName("★ R1: a hosted call's admission fact names its verb, and the node records the turn beside the verdict")
    void theAdmissionNamesItsVerb() {
        var fact = new SessionEvents.LocalAgentCallAdmissionRequested(1, 2, "aggregate");
        assertEquals("aggregate", fact.verb());
        String src = readSource("net/ActionServer.java");
        assertTrue(src.contains("gate.admit(generation, verb)"), "the server names the verb it admits");
        String node = readSource("session/node/LocalAgentSession.java");
        assertTrue(node.contains(".info(\"verb\", String.valueOf(e.verb())).info(\"ticket\", openTicket)"),
                "the node records the verb and the turn");
    }

    private static String readSource(String rel) {
        try {
            return Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser").resolve(rel));
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * ★ §10 P3: no approval the model can call. Witness: add an {@code approve} tool to the hosted list (the P0b stub's
     * shape) — the name assertion fails; build the line with an MCP permission tool — the line assertion fails.
     */
    @Test
    @DisplayName("★ no MCP permission tool exists on the bridge in any mode, and no line names one")
    void noApprovalTheModelCanCall() {
        Pattern approvalShaped = Pattern.compile("(?i).*(approv|permission|grant|consent|allow|authori).*");
        for (Map<String, Object> t : McpTools.hostedList()) {
            assertFalse(approvalShaped.matcher(String.valueOf(t.get("name"))).matches(), "hosted: " + t.get("name"));
        }
        for (Map<String, Object> t : McpTools.list()) {
            assertFalse(approvalShaped.matcher(String.valueOf(t.get("name"))).matches(), "public: " + t.get("name"));
        }
        for (var posture : List.of(telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.ANALYSER_ASSISTANT,
                telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.full(
                        telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.FullMode.DEFAULT),
                telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.full(
                        telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.FullMode.AUTO))) {
            List<String> line = new ClaudeCodeProvider().command(new LocalAgentProvider.LaunchSpec(
                    Path.of("/opt/claude"), "11111111-2222-4333-8444-555555555555", false, "sonnet", Path.of("/w"),
                    List.of("/usr/bin/java", "-jar", "/a.jar"), Path.of("/e.json"), 0, posture));
            ClaudeCodeProviderTest.assertPromptsGoToTheAnalyser(line);
        }
    }

    /** P3-3: the bridge reads the CLI's tool-use id from {@code _meta}, a plain token only. */
    @Test
    @DisplayName("P3-3: the hosted bridge forwards _meta[claudecode/toolUseId], and only a plain token")
    void theBridgeForwardsTheToolUseId() throws Exception {
        var m = telamin.fluxtion.audit.analyser.analyser.mcp.McpBridge.class.getDeclaredMethod("toolUseIdOf", Map.class);
        m.setAccessible(true);
        assertEquals("toolu_015GvWAsKbyYxopquhWBrPuD", m.invoke(null, Json.parse(
                "{\"name\":\"analyser_report\",\"_meta\":{\"claudecode/toolUseId\":\"toolu_015GvWAsKbyYxopquhWBrPuD\"}}")));
        assertNull(m.invoke(null, Json.parse("{\"_meta\":{\"claudecode/toolUseId\":\"a\\r\\nX-Injected: 1\"}}")),
                "a header injection is not forwarded");
        assertNull(m.invoke(null, Json.parse("{\"name\":\"analyser_report\"}")));
    }
}
