package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Which of the analyser's own environment variables a local-agent child must NOT inherit (P1b review, finding 1; the
 * P1b live smoke's finding 1). An analyser started from a terminal Claude Code session carries that session's identity
 * — {@code CLAUDECODE}, {@code CLAUDE_CODE_SESSION_ID}, its messaging socket and token, its entry point — and a child
 * {@code claude} given them would believe it is nested in, or part of, the person's other session. Those are removed
 * in BOTH postures. In the full posture {@code CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD} is removed too: the full
 * posture loads {@code CLAUDE.md} through the project setting source, as in a terminal (§4.3).
 *
 * <p><b>Kept</b>: the person's configuration — {@code CLAUDE_CONFIG_DIR}, {@code ANTHROPIC_*}, the provider switches
 * ({@code CLAUDE_CODE_USE_BEDROCK/VERTEX/FOUNDRY}), {@code CLAUDE_CODE_OAUTH_TOKEN},
 * {@code CLAUDE_CODE_MAX_OUTPUT_TOKENS}, {@code CLAUDE_CODE_DISABLE_NONESSENTIAL_TRAFFIC},
 * {@code CLAUDE_CODE_SKIP_*_AUTH} and the proxy variables — because nothing below names them. Pure.
 */
public final class ChildEnvironment {

    private ChildEnvironment() {
    }

    /**
     * Review of PR #147, finding 6: {@code ENABLE_IDE_INTEGRATION} (the partner of {@code CLAUDE_CODE_SSE_PORT}: it would
     * connect the child to a running IDE) and what Claude Code sets for a hook or plugin script it runs —
     * {@code CLAUDE_PROJECT_DIR}, {@code CLAUDE_ENV_FILE} and {@code CLAUDE_PLUGIN_*} (in {@link #PREFIXES}).
     */
    static final Set<String> EXACT = Set.of("CLAUDECODE", "CLAUDE_PID", "CLAUDE_EFFORT", "CLAUDE_SESSION_ID",
            "CLAUDE_BG_SESSION_PERMISSION_RULES", "ENABLE_IDE_INTEGRATION", "CLAUDE_PROJECT_DIR", "CLAUDE_ENV_FILE");

    /** After {@code CLAUDE_CODE_}: a host session's identity, transport or launch, never the person's configuration. */
    static final List<String> CODE_SESSION_PARTS = List.of("SESSION_", "MESSAGING_", "BRIDGE_", "REMOTE", "HOST_SESSION",
            "CLOUD_SESSION", "PROJECTS_SESSION", "RESUME_FROM", "TMUX_SESSION", "CHILD_SESSION", "ENTRYPOINT", "EXECPATH",
            "SSE_PORT");

    static final List<String> PREFIXES = List.of("CLAUDE_BRIDGE_", "CLAUDE_REMOTE_", "CLAUDE_RUNNER_", "CLAUDE_AGENT_SDK_",
            "CLAUDE_PLUGIN_");

    /** Whether {@code name} belongs to a host Claude Code session (removed in both postures). */
    public static boolean hostSession(String name) {
        if (name == null) return false;
        if (EXACT.contains(name)) return true;
        for (String p : PREFIXES) if (name.startsWith(p)) return true;
        if (name.startsWith("CLAUDE_CODE_")) {
            String rest = name.substring("CLAUDE_CODE_".length());
            for (String part : CODE_SESSION_PARTS) if (rest.startsWith(part)) return true;
        }
        return false;
    }

    /** The names present in {@code inherited} that the child of {@code posture} must not see. */
    public static Set<String> removed(Map<String, String> inherited, LocalAgentPosture posture) {
        Set<String> out = new TreeSet<>();
        if (inherited == null) return out;
        boolean full = posture != null && posture.isFull();
        for (String name : inherited.keySet()) {
            if (hostSession(name) || (full && ClaudeCodeProvider.CLAUDE_MD_ENV.equals(name))) out.add(name);
        }
        return out;
    }

    /** What the child is given: {@code inherited}, less {@code removed}, plus {@code extra} (which wins). */
    public static Map<String, String> compose(Map<String, String> inherited, Set<String> removed, Map<String, String> extra) {
        Map<String, String> out = new LinkedHashMap<>(inherited == null ? Map.of() : inherited);
        if (removed != null) out.keySet().removeAll(removed);
        if (extra != null) out.putAll(extra);
        return out;
    }
}
