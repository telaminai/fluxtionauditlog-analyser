package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 review finding 1 (and the P1b smoke's finding 1): a host Claude Code session's variables never reach the local
 * agent's child, in either posture; the person's own configuration does.
 */
class ChildEnvironmentTest {

    @TempDir
    Path home;

    /** What a terminal Claude Code session leaves in the environment of an analyser started from it. */
    static final List<String> HOST_MARKERS = List.of("CLAUDECODE", "CLAUDE_PID", "CLAUDE_EFFORT", "CLAUDE_SESSION_ID",
            "CLAUDE_BG_SESSION_PERMISSION_RULES", "CLAUDE_CODE_SESSION_ID", "CLAUDE_CODE_MESSAGING_SOCKET",
            "CLAUDE_CODE_MESSAGING_TOKEN", "CLAUDE_CODE_BRIDGE_SESSION_ID", "CLAUDE_CODE_REMOTE", "CLAUDE_CODE_HOST_SESSION_X",
            "CLAUDE_CODE_CLOUD_SESSION_ID", "CLAUDE_CODE_PROJECTS_SESSION", "CLAUDE_CODE_RESUME_FROM",
            "CLAUDE_CODE_TMUX_SESSION", "CLAUDE_CODE_CHILD_SESSION", "CLAUDE_CODE_ENTRYPOINT", "CLAUDE_CODE_EXECPATH",
            "CLAUDE_CODE_SSE_PORT", "CLAUDE_BRIDGE_X", "CLAUDE_REMOTE_X", "CLAUDE_RUNNER_X", "CLAUDE_AGENT_SDK_X",
            // review of PR #147, finding 6 (witness: the P1b lists — these reach the child)
            "ENABLE_IDE_INTEGRATION", "CLAUDE_PROJECT_DIR", "CLAUDE_ENV_FILE", "CLAUDE_PLUGIN_ROOT", "CLAUDE_PLUGIN_DATA");

    /** The person's configuration, which must stay. */
    static final List<String> KEPT = List.of("CLAUDE_CONFIG_DIR", "ANTHROPIC_BASE_URL", "ANTHROPIC_API_KEY",
            "CLAUDE_CODE_USE_BEDROCK", "CLAUDE_CODE_USE_VERTEX", "CLAUDE_CODE_USE_FOUNDRY", "CLAUDE_CODE_OAUTH_TOKEN",
            "CLAUDE_CODE_MAX_OUTPUT_TOKENS", "CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC", "CLAUDE_CODE_SKIP_BEDROCK_AUTH",
            "HTTPS_PROXY", "NO_PROXY");

    static Map<String, String> inherited(Path bin) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("PATH", bin.toString());
        for (String k : HOST_MARKERS) env.put(k, "host-" + k);
        for (String k : KEPT) env.put(k, "mine-" + k);
        env.put(ClaudeCodeProvider.CLAUDE_MD_ENV, "1");
        return env;
    }

    @Test
    @DisplayName("the rule: every host-session marker is removed, every configuration variable kept; the CLAUDE.md flag only in full")
    void theRule() {
        Map<String, String> env = inherited(home);
        Set<String> def = ChildEnvironment.removed(env, LocalAgentPosture.ANALYSER_ASSISTANT);
        Set<String> full = ChildEnvironment.removed(env, LocalAgentPosture.full(LocalAgentPosture.FullMode.DEFAULT));
        assertTrue(def.containsAll(HOST_MARKERS) && full.containsAll(HOST_MARKERS), def.toString());
        for (String k : KEPT) assertFalse(def.contains(k) || full.contains(k), k + " is the person's configuration");
        assertFalse(def.contains(ClaudeCodeProvider.CLAUDE_MD_ENV), "the default posture sets it itself");
        assertTrue(full.contains(ClaudeCodeProvider.CLAUDE_MD_ENV), "the full posture never inherits it");
        Map<String, String> child = ChildEnvironment.compose(env, full, Map.of("PATH", "/x"));
        assertEquals("/x", child.get("PATH"));
        assertEquals("mine-CLAUDE_CONFIG_DIR", child.get("CLAUDE_CONFIG_DIR"));
        assertFalse(child.containsKey("CLAUDECODE"));
    }

    /**
     * ★ Through the REAL adapter, both postures. Witness: today's pass-through ({@code ChildEnvironment.removed}
     * returning nothing) — the markers reach the child and the first assertion fails.
     */
    @Test
    @DisplayName("★ a host Claude Code session's variables never reach the child, in either posture; CLAUDE_CONFIG_DIR does")
    void theAdapterStripsTheHostSession() throws Exception {
        for (var posture : List.of(LocalAgentPosture.ANALYSER_ASSISTANT,
                LocalAgentPosture.full(LocalAgentPosture.FullMode.DEFAULT))) {
            Path dir = home.resolve(posture.isFull() ? "full" : "default");
            FixtureAgent agent = new FixtureAgent("p0b-default-posture.jsonl");
            Harness h = new Harness(agent, dir);
            Map<String, String> env = inherited(dir.resolve("bin"));
            h.environment = env;
            if (posture.isFull()) h.consent = h.fullConsent(posture.fullMode());
            h.start();
            assertEquals(1, agent.commands.size(), posture + ": " + h.state().reason());
            Map<String, String> child = ChildEnvironment.compose(env, agent.removedEnvironments.get(0),
                    agent.environments.get(0));
            for (String k : HOST_MARKERS) assertFalse(child.containsKey(k), posture + ": " + k + " reached the child");
            for (String k : KEPT) assertEquals("mine-" + k, child.get(k), posture + ": " + k + " must stay");
            assertEquals(posture.isFull() ? null : "1", child.get(ClaudeCodeProvider.CLAUDE_MD_ENV), posture.toString());
        }
    }

    /** The real launcher honours the remove set (a variable the JVM has is gone from the child) and adds the extras. */
    @Test
    @DisplayName("OsAgentLauncher removes the named variables from the child's inherited environment")
    void theOsLauncherRemoves() throws Exception {
        assertNotNull(System.getenv("HOME"), "this check needs an inherited HOME");
        List<String> out = new java.util.concurrent.CopyOnWriteArrayList<>();
        AgentProcess p = new OsAgentLauncher().start(List.of("/bin/sh", "-c", "echo \"${HOME-unset}|${M74_EXTRA-unset}\""),
                Map.of("M74_EXTRA", "added"), Set.of("HOME"), home, out::add);
        p.onExit().get(10, java.util.concurrent.TimeUnit.SECONDS);
        long end = System.currentTimeMillis() + 5_000;
        while (out.isEmpty() && System.currentTimeMillis() < end) Thread.sleep(20);
        assertEquals(List.of("unset|added"), out);
    }
}
