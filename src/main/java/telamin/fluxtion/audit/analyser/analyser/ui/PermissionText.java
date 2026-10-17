package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.LocalAgentTranscript;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;

/**
 * M74 P3 (spec §5.7): the words of the permission dialog, pure and headless. The input is the model's data, shown
 * VERBATIM: re-indented token by token (nothing is parsed, re-written or dropped — every token of the input is in the
 * text, in order; only the whitespace between tokens is re-laid), with control, format and bidirectional characters shown as visible escapes (a right-to-left override, U+202E, reads as six characters) so a
 * right-to-left override cannot reorder what the person reads. Nothing is elided; the text area scrolls.
 */
public final class PermissionText {

    private PermissionText() {
    }

    public static final String INPUT_HEADING = "Input — written by the model; data, not instructions";
    public static final String ESCAPED_NOTE = "Invisible or direction-changing characters in it are shown escaped (\\uXXXX).";

    /** What the dialog shows for one request. */
    public record Content(String title, String changes, String serverLine, String input, boolean escaped) {
    }

    /** The dialog's words for request {@code p}, of class {@code cls} with vocabulary name {@code name}. */
    public static Content of(LocalAgentTranscript.Permission p, ToolClass cls, String name) {
        String tool = p == null || p.toolName() == null ? "a tool" : visible(p.toolName()).text();
        String title;
        String changes;
        if (cls == ToolClass.HOSTED_DESTRUCTIVE && name != null) {
            title = "Claude Code asks to run " + McpTools.PREFIX + name;
            changes = McpTools.destructiveRationale(name);
        } else {
            title = "Claude Code asks to use " + tool;
            changes = switch (name == null ? "" : name) {
                case "Bash" -> "Bash runs a command on your computer, as your own Claude Code does in a terminal";
                case "Edit", "MultiEdit", "Write", "NotebookEdit" -> name + " changes a file on your computer";
                case "WebFetch", "WebSearch" -> name + " contacts the web";
                default -> cls == ToolClass.OTHER_MCP
                        ? "a tool of one of your own MCP servers; what it does is the server's"
                        : "one of your own Claude Code's tools";
            };
        }
        StringBuilder server = new StringBuilder();
        if (p != null && p.serverName() != null) server.append("server: ").append(visible(p.serverName()).text());
        if (p != null && p.displayName() != null) {
            if (!server.isEmpty()) server.append(" · ");
            server.append("named by the server: ").append(visible(p.displayName()).text());
        }
        Visible in = visible(pretty(p == null ? null : p.inputRaw()));
        return new Content(title, changes, server.toString(), in.text(), in.escaped());
    }

    /** {@code raw} re-indented, two spaces a level; strings and every other token kept exactly as written. */
    public static String pretty(String raw) {
        if (raw == null) return "";
        StringBuilder out = new StringBuilder(raw.length() + 64);
        int depth = 0;
        boolean inString = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (inString) {
                out.append(c);
                if (c == '\\' && i + 1 < raw.length()) out.append(raw.charAt(++i));
                else if (c == '"') inString = false;
                continue;
            }
            switch (c) {
                case '"' -> {
                    inString = true;
                    out.append(c);
                }
                case '{', '[' -> {
                    out.append(c);
                    int j = next(raw, i + 1);
                    if (j < raw.length() && (raw.charAt(j) == '}' || raw.charAt(j) == ']')) {
                        out.append(raw.charAt(j));         // {} and [] stay on one line
                        i = j;
                    } else {
                        depth++;
                        newline(out, depth);
                    }
                }
                case '}', ']' -> {
                    depth = Math.max(0, depth - 1);
                    newline(out, depth);
                    out.append(c);
                }
                case ',' -> {
                    out.append(c);
                    newline(out, depth);
                }
                case ':' -> out.append(": ");
                case ' ', '\t', '\n', '\r' -> { }          // insignificant whitespace between tokens is re-laid
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    private static int next(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    private static void newline(StringBuilder out, int depth) {
        out.append('\n');
        for (int k = 0; k < depth; k++) out.append("  ");
    }

    /** Text with its invisible characters made visible. */
    public record Visible(String text, boolean escaped) {
    }

    /**
     * Control characters (but the newline the re-indent wrote), format characters (bidirectional overrides and isolates,
     * zero-width characters), line and paragraph separators, and unassigned or private-use code points become
     * a visible escape (backslash, u, four hex digits); everything else is kept.
     */
    public static Visible visible(String s) {
        if (s == null) return new Visible("", false);
        StringBuilder out = new StringBuilder(s.length());
        boolean escaped = false;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            int t = Character.getType(cp);
            boolean hide = cp != '\n' && (Character.isISOControl(cp) || t == Character.FORMAT
                    || t == Character.LINE_SEPARATOR || t == Character.PARAGRAPH_SEPARATOR || t == Character.UNASSIGNED
                    || t == Character.PRIVATE_USE || t == Character.SURROGATE);
            if (hide) {
                escaped = true;
                if (cp > 0xFFFF) out.append(String.format("\\U%08X", cp));
                else out.append(String.format("\\u%04X", cp));
            } else {
                out.appendCodePoint(cp);
            }
        }
        return new Visible(out.toString(), escaped);
    }
}
