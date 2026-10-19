package telamin.fluxtion.audit.analyser.analyser.assistant.setup;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.session.AiSetupState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M77 (spec-ai-setup-wizard.md §8.6, §9, ★ T7): the PARSE BOUNDARY for {@code claude --version} and
 * {@code claude auth status --json}. Pure.
 *
 * <p>Verified 2026-10-18 against Claude Code 2.1.282: {@code auth status --json} carries {@code email}, {@code orgId} and
 * {@code orgName} besides the fields used. This class reads ONLY {@code loggedIn}, {@code authMethod},
 * {@code subscriptionType} and {@code apiKeySource}, by name, each mapped to an enum, and returns nothing else: the raw
 * output is never logged, shown, recorded or put in a fact (S7; the {@code ClaudeMcpClient.Result} rule).
 */
public final class ClaudeAuthParse {

    private ClaudeAuthParse() {
    }

    /** {@code 2.1.282 (Claude Code)}: the digits at the start of the first line, or none. */
    private static final Pattern VERSION = Pattern.compile("^\\s*(\\d+\\.\\d+\\.\\d+)");

    /** What {@code claude auth status --json} said, as enums — the only shape that leaves this class. */
    public record Auth(SessionEvents.AuthOutcome outcome, boolean loggedIn, AiSetupState.Method method,
                       AiSetupState.Plan plan, AiSetupState.KeySource keySource) {
        public static final Auth UNREADABLE = new Auth(SessionEvents.AuthOutcome.UNREADABLE, false,
                AiSetupState.Method.NONE, AiSetupState.Plan.NONE, AiSetupState.KeySource.NONE);

        @Override
        public String toString() {             // the enums only: there is nothing personal here to leak
            return "ClaudeAuthParse.Auth[" + outcome + ", loggedIn=" + loggedIn + ", " + method + ", " + plan + ", "
                    + keySource + "]";
        }
    }

    /**
     * {@code claude --version}'s answer: the version's digits when the process exited 0 and its first line starts with
     * {@code N.N.N}; otherwise empty — the probe is then {@code DID_NOT_RUN}.
     */
    public static String version(int exit, String stdout) {
        if (exit != 0 || stdout == null) return "";
        String first = stdout.lines().findFirst().orElse("");
        Matcher m = VERSION.matcher(first);
        return m.find() ? m.group(1) : "";
    }

    /**
     * {@code claude auth status --json}'s answer. The verdict reads {@code loggedIn} from the JSON, not the exit code
     * (signed out exits 1 with parseable JSON — §9); output that is not that JSON is {@code UNREADABLE}.
     */
    public static Auth auth(int exit, String stdout, boolean timedOut) {
        if (timedOut) {
            return new Auth(SessionEvents.AuthOutcome.TIMED_OUT, false, AiSetupState.Method.NONE, AiSetupState.Plan.NONE,
                    AiSetupState.KeySource.NONE);
        }
        if (stdout == null || stdout.isBlank()) return Auth.UNREADABLE;
        Object root;
        try {
            root = Json.parse(stdout.trim());
        } catch (RuntimeException notJson) {
            return Auth.UNREADABLE;
        }
        if (!(root instanceof Map<?, ?> map)) return Auth.UNREADABLE;
        // by name, and nothing else: email, orgId, orgName and any field added later stay here and are dropped
        Object loggedIn = map.get("loggedIn");
        if (!(loggedIn instanceof Boolean in)) return Auth.UNREADABLE;
        return new Auth(SessionEvents.AuthOutcome.READ, in, method(string(map.get("authMethod"))),
                AiSetupState.Plan.parse(string(map.get("subscriptionType"))), keySource(string(map.get("apiKeySource"))));
    }

    private static String string(Object o) {
        return o instanceof String s ? s : null;
    }

    /** §8.6: {@code claude.ai}, {@code api_key}, {@code api_key_helper}; anything else is OTHER (rendered "Could not tell"). */
    static AiSetupState.Method method(String s) {
        if (s == null || s.isBlank() || s.equals("none")) return AiSetupState.Method.NONE;
        return switch (s) {
            case "claude.ai" -> AiSetupState.Method.CLAUDE_AI;
            case "api_key" -> AiSetupState.Method.API_KEY;
            case "api_key_helper" -> AiSetupState.Method.API_KEY_HELPER;
            default -> AiSetupState.Method.OTHER;
        };
    }

    static AiSetupState.KeySource keySource(String s) {
        if (s == null || s.isBlank()) return AiSetupState.KeySource.NONE;
        return switch (s) {
            case "ANTHROPIC_API_KEY" -> AiSetupState.KeySource.ANTHROPIC_API_KEY;
            case "apiKeyHelper" -> AiSetupState.KeySource.API_KEY_HELPER;
            default -> AiSetupState.KeySource.OTHER;
        };
    }
}
