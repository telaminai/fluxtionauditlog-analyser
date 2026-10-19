package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalAgentActivityReplayTest {
    @Test
    void aCompleteToolBlockStartsActivityBeforeItsResultAndItsWordsAreBounded(@TempDir Path home) throws Exception {
        var agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        agent.rewrite = line -> line.contains("command_lifecycle") && line.contains("completed")
                || line.contains("\"type\": \"result\"") ? null : line;
        var h = new Harness(agent, home);
        h.start();
        h.send("Check the DEMO build.");
        h.facts.clear();
        agent.last().emit("{\"type\":\"assistant\",\"message\":{\"id\":\"DEMO-message\",\"content\":["
                + "{\"type\":\"tool_use\",\"id\":\"DEMO-call\",\"name\":\"Read\",\"input\":{\"file_path\":\"/DEMO/private/audit.log\"}}]}}");
        var started = h.facts(SessionEvents.LocalAgentToolStarted.class).getLast();
        assertEquals("Reading audit.log…", ActivityText.step(h.state().extra().guide(), h.store::text));
        assertEquals("audit.log", h.store.text(started.wordsRef()));
        assertEquals(java.util.Map.of("phase", "BUSY", "currentStep", "Reading audit.log…",
                "secondsSinceLastEvent", 0, "approvalPending", false), ActivityText.context(h.state(), h.store::text));
        assertTrue(h.facts(SessionEvents.LocalAgentToolFinished.class).isEmpty(), "tool START is not delayed until the result");
        agent.last().emit("{\"type\":\"user\",\"message\":{\"content\":[{\"type\":\"tool_result\","
                + "\"tool_use_id\":\"DEMO-call\",\"content\":\"DEMO result must not become the step\"}]}}");
        assertEquals(started.callRef(), h.facts(SessionEvents.LocalAgentToolFinished.class).getLast().callRef());
        assertTrue(ActivityText.step(h.state().extra().guide(), h.store::text).startsWith("Thinking…"));
        agent.last().emit("{\"type\":\"assistant\",\"message\":{\"id\":\"DEMO-message-2\",\"content\":["
                + "{\"type\":\"tool_use\",\"id\":\"DEMO-call-2\",\"name\":\"mcp__DEMO_person__DEMO_tool\",\"input\":{}}]}}");
        var other = h.facts(SessionEvents.LocalAgentToolStarted.class).getLast();
        assertEquals(SessionEvents.ToolClass.OTHER_MCP, other.toolClass());
        assertNull(other.name(), "activity facts carry vocabulary, not a person's raw MCP names");
        assertFalse(other.toString().contains("DEMO_person"));
    }

    @Test
    void activityWordsContainNoAbsolutePathAndFitTheBusyWordBudget() {
        assertEquals("audit.log", ActivityText.words("Read", "{\"file_path\":\"C:\\\\DEMO\\\\private\\\\audit.log\"}"));
        String bash = ActivityText.words("Bash", "{\"command\":\"./mvnw -f /DEMO/private/pom.xml test\\nDEMO second line\"}");
        assertEquals("./mvnw -f …/pom.xml test", bash);
        assertFalse(bash.contains("/DEMO/private"));
        assertFalse(bash.contains("second"));
        assertFalse(ActivityText.words("Bash", "{\"command\":\"./run.sh /DEMO\"}").contains(" /DEMO"));
        assertEquals("./run.sh …/audit.log", ActivityText.words("Bash", "{\"command\":\"./run.sh \\\\\\\\DEMO\\\\share\\\\audit.log\"}"));
        assertTrue(bash.length() <= 40);
        assertTrue(ActivityText.words("Bash", "{\"command\":\"a b c d e f g h i j k l m n o p q r s t\"}").split("\\s+").length <= 8);
        assertTrue(ActivityText.words("Bash", "{\"command\":\"a b c d e f g h i j k l\"}").split("\\s+").length <= 8);
        assertEquals("", ActivityText.words("Read", "{unreadable"));
        assertEquals("", ActivityText.words("Grep", "{\"pattern\":\"DEMO record text\"}"));
    }
}
