package telamin.fluxtion.audit.analyser.analyser.core;

import java.util.regex.Pattern;

/**
 * The one redaction of secret-shaped text before it is shown or kept: CLI output, a child process's stderr, a
 * diagnostics line. Shared by the Claude Code and Codex registration clients and the local agent (M74 review S4), so the
 * shapes cannot drift apart.
 */
public final class Redaction {

    private Redaction() {
    }

    /** {@code token=…}, {@code api-key: …}, {@code password=…}, {@code authorization: bearer …} and the like. */
    public static final Pattern SECRET_VALUE = Pattern.compile(
            "(?i)\\b(token|api[_-]?key|password|secret|authorization)\\b(\\s*[:=]\\s*|\\s+bearer\\s+)([^\\s,;]+)");

    /** The analyser's own hosted-credential header, whatever value follows it. */
    private static final Pattern HOSTED_HEADER = Pattern.compile("(?i)(X-Analyser-(?:Hosted|Token)(?:[ \\t]*[:=][ \\t]*|[ \\t]+))([^\\s,;]+)");
    /** A bare 64-hex string — the shape of a hosted credential. */
    private static final Pattern HEX_64 = Pattern.compile("\\b[0-9a-fA-F]{64}\\b");
    /** An Anthropic-style API key. */
    private static final Pattern KEY_SHAPE = Pattern.compile("\\bsk-[A-Za-z0-9_-]{8,}");

    /** Redact the key=value shapes only — what the registration clients have always done. */
    public static String keyValues(String text) {
        if (text == null || text.isEmpty()) return "";
        return SECRET_VALUE.matcher(text).replaceAll("$1$2…");
    }

    /**
     * Redact every secret shape, and every one of {@code known} — exact values this process holds (the REST token, a
     * hosted credential) — wherever it appears.
     */
    public static String all(String text, String... known) {
        if (text == null || text.isEmpty()) return "";
        String out = text;
        if (known != null) {
            for (String k : known) if (k != null && k.length() >= 8) out = out.replace(k, "‹redacted›");
        }
        out = HOSTED_HEADER.matcher(out).replaceAll("$1…");
        out = keyValues(out);
        out = HEX_64.matcher(out).replaceAll("‹redacted›");
        return KEY_SHAPE.matcher(out).replaceAll("sk-…");
    }
}
