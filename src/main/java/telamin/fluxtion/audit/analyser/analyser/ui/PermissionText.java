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
        String raw = p == null ? null : p.inputRaw();
        java.util.Map<?, ?> params = params(raw);
        String title;
        String changes;
        if (cls == ToolClass.HOSTED_DESTRUCTIVE && name != null) {
            // review M74 usability #6: what THIS call does, from its own input — the verb's general rationale only when
            // the input says nothing more specific; the exact input is still shown below, unchanged
            Call call = hostedCall(name, params);
            title = call == null ? "Claude Code asks to run " + McpTools.PREFIX + name : "Claude Code asks to " + call.what();
            changes = call == null ? McpTools.destructiveRationale(name) : call.consequence();
        } else {
            Call call = toolCall(name, params);
            title = call == null ? "Claude Code asks to use " + tool : "Claude Code asks to " + call.what();
            changes = call != null ? call.consequence() : switch (name == null ? "" : name) {
                case "Bash" -> "Bash runs a command on your computer, as your own Claude Code does in a terminal";
                case "Edit", "MultiEdit", "Write", "NotebookEdit" -> name + " changes a file on your computer";
                case "WebFetch", "WebSearch" -> name + " contacts the web";
                default -> cls == ToolClass.OTHER_MCP
                        ? "a tool of one of your own MCP servers; what it does is the server's"
                        : "one of your own Claude Code's tools";
            };
        }
        Visible in = visible(pretty(raw));
        return new Content(title, changes, serverLine(p, cls, name), in.text(), in.escaped());
    }

    /** One call in words: what it does (a phrase after "Claude Code asks to"), and its consequence. */
    record Call(String what, String consequence) {
    }

    /** The longest value quoted in a sentence; the input below always has all of it. */
    static final int QUOTE_MAX = 60;

    /** A value from the model's input, quoted, its invisible characters escaped, at most {@link #QUOTE_MAX} long. */
    static String q(Object v) {
        // the JSON reader gives numbers as doubles: a whole number reads as written (3, never 3.0)
        String raw = v instanceof Double d && d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString(d.longValue())
                : v == null ? "" : String.valueOf(v);
        String t = visible(raw).text();
        if (t.length() > QUOTE_MAX) t = t.substring(0, QUOTE_MAX) + "…";
        return "“" + t + "”";
    }

    private static java.util.Map<?, ?> params(String raw) {
        if (raw == null || raw.isBlank()) return java.util.Map.of();
        try {
            Object o = telamin.fluxtion.audit.analyser.analyser.llm.Json.parse(raw);
            return o instanceof java.util.Map<?, ?> m ? m : java.util.Map.of();
        } catch (RuntimeException unreadable) {
            return java.util.Map.of();
        }
    }

    private static boolean has(java.util.Map<?, ?> p, String k) {
        return p.containsKey(k) && p.get(k) != null;
    }

    private static final String RESTORABLE = "It can be restored: it moves to this project's recently deleted list.";
    private static final String EXCHANGE = " in the exchange folder; an existing file is never overwritten.";

    /**
     * Review M74 usability #6: an asked analyser verb's call, by its sub-action — one sentence per operation the verb's
     * schema names; null when the input names none (the general rationale is shown then).
     */
    static Call hostedCall(String verb, java.util.Map<?, ?> p) {
        Object name = p.get("name");
        switch (verb) {
            case "walk" -> {
                // final review #2: the operation is WalkVerb.choose's — the executor's own choice — and a flag the
                // executor requires to be true (delete, play, end) is judged as it judges it
                @SuppressWarnings("unchecked")
                WalkVerb.Chosen chosen = WalkVerb.choose((java.util.Map<String, Object>) p, () -> "");
                if (!chosen.ok()) return null;
                return switch (chosen.op()) {
                    case "reel" -> new Call("record the walk " + q(name) + " as a page, written to " + q(p.get("reel")),
                            "It plays the walk step by step and writes one HTML page" + EXCHANGE);
                    case "delete" -> Boolean.TRUE.equals(p.get("delete"))
                            ? new Call("delete the saved walk " + q(name), RESTORABLE) : null;
                    case "rename" -> new Call("rename the walk " + q(name) + " to " + q(p.get("rename")),
                            "The walk keeps its steps; only its name changes.");
                    case "restore" -> Boolean.TRUE.equals(p.get("restore"))
                            ? new Call("list the deleted walks", "It changes nothing.")
                            : new Call("restore the deleted walk " + q(p.get("restore")),
                            "It comes back into this project under its name.");
                    case "play" -> Boolean.TRUE.equals(p.get("play")) ? new Call("play the walk " + q(name)
                            + (has(p, "step") ? " from step " + q(p.get("step")) : ""),
                            "It shows the walk's steps on the analyser; nothing saved changes.") : null;
                    case "end" -> Boolean.TRUE.equals(p.get("end"))
                            ? new Call("end the walk that is showing", "It changes nothing saved.") : null;
                    case "steps" -> new Call("save the walk " + q(name),
                            "A saved walk of that name is replaced; a new name adds one.");
                    case "conversation" -> new Call(p.get("conversation") == null
                            ? "remove the conversation from the walk " + q(name)
                            : "attach a conversation to the walk " + q(name), "The walk's steps do not change.");
                    default -> null;
                };
            }
            case "report" -> {
                // final review #2: VerbOperations.report — the executor's own choice, so `delete: false` beside
                // sections reads as the build it is, never as a delete
                var chosen = telamin.fluxtion.audit.analyser.analyser.mcp.VerbOperations.report(p);
                if (!chosen.ok()) return null;
                return switch (chosen.op()) {
                    case BUNDLE -> new Call("write an evidence bundle to "
                            + q(((java.util.Map<?, ?>) p.get("bundle")).get("path")),
                            "It packs the open log, its graph and this project's reports into one file" + EXCHANGE);
                    case LIST_DELETED -> new Call("list the deleted reports", "It changes nothing.");
                    case RESTORE -> new Call("restore the deleted report " + q(p.get("restore")),
                            "It comes back into this project under its name.");
                    case DELETE -> new Call("delete the report " + q(name), RESTORABLE);
                    case RENAME -> new Call("rename the report " + q(name) + " to " + q(p.get("rename")),
                            "The report keeps its sections; only its name changes.");
                    case CSV -> new Call("write table " + q(p.get("csv")) + " of the report " + q(name)
                            + " as CSV to " + q(p.get("path")), "It writes one file" + EXCHANGE);
                    case BUILD -> new Call("build the report " + q(name)
                            + (has(p, "path") ? " and write it to " + q(p.get("path")) : ""),
                            (has(p, "path") ? "It writes one file" + EXCHANGE + " " : "")
                                    + "A report of that name in this project is replaced; a new name adds one.");
                    case FINDING -> has(p, "path") ? new Call("write a finding" + (has(p, "recordIndex") ? " for record "
                            + q(p.get("recordIndex")) : "") + " to " + q(p.get("path")), "It writes one PDF file" + EXCHANGE)
                            : null;
                    case INVALID -> null;
                };
            }
            case "screenshot" -> {
                return new Call("write a screenshot of " + (has(p, "scope") ? "the " + q(p.get("scope")) : "the window")
                        + " to " + q(p.get("path")), "It writes one PNG file" + EXCHANGE);
            }
            case "open" -> {
                // final review #2: VerbOperations.open — the act the executor dispatches on, in its order
                return switch (telamin.fluxtion.audit.analyser.analyser.mcp.VerbOperations.open(p)) {
                    case CREATE -> new Call((Boolean.TRUE.equals(p.get("create")) ? "preview creating a project in "
                            : "create a project in ") + q(p.get("project")), Boolean.TRUE.equals(p.get("create"))
                            ? "It shows what would be created and changes nothing."
                            : "It writes a new project in that folder.");
                    case FOLLOW -> new Call(Boolean.TRUE.equals(p.get("follow")) ? "follow the open log as it grows"
                            : "stop following the open log", "It changes nothing saved.");
                    case CANVAS -> new Call("change what the shared canvas shows",
                            "It changes only the shared canvas; no file is opened.");
                    case ANALYSIS -> new Call("run the saved analysis " + q(p.get("analysis")),
                            "It opens what the analysis names; what is open now is replaced.");
                    case RESTORE_SESSION -> "dismiss".equals(p.get("restore"))
                            ? new Call("dismiss the offer to restore the last session", "It changes nothing open.")
                            : new Call("restore the last session", "What is open now is replaced.");
                    case BUNDLE -> new Call("open the evidence bundle " + q(p.get("bundle")), "What is open now is replaced.");
                    case PROJECT -> new Call("open the project " + q(p.get("project")), "What is open now is replaced.");
                    case DISCOVER -> new Call("list what could be opened beside the log",
                            "It lists candidates and opens nothing.");
                    case CLOSE -> new Call("close the " + q(p.get("close")), "It closes what is open now.");
                    case FILES -> {
                        for (String k : new String[]{"log", "logs", "graphml", "processor", "design", "diagnostics"}) {
                            if (has(p, k)) {
                                String kind = switch (k) {
                                    case "log" -> "the audit log";
                                    case "logs" -> "the audit log set";
                                    case "graphml" -> "the graph file";
                                    case "processor" -> "the processor";
                                    case "diagnostics" -> "the diagnostics";
                                    default -> "the design";
                                };
                                yield new Call("open " + kind + " " + q(p.get(k)), "What is open now is replaced.");
                            }
                        }
                        yield null;
                    }
                };
            }
            case "source_root" -> {
                if (has(p, "add") || has(p, "remove")) {
                    return new Call((has(p, "add") ? "add the source folder(s) " + q(p.get("add")) : "")
                            + (has(p, "add") && has(p, "remove") ? " and " : "")
                            + (has(p, "remove") ? "remove the source folder(s) " + q(p.get("remove")) : ""),
                            "Saved in your settings: the analyser reads source from these folders.");
                }
                return new Call("list the configured source folders", "It changes nothing; it shows the folders' paths.");
            }
            case "import" -> {
                if (has(p, "categories")) return new Call("borrow " + q(p.get("categories")) + " from the bundle "
                        + q(p.get("bundle")) + " into this project", "This project's settings for those categories change.");
                if (has(p, "bundle")) return new Call("preview what the bundle " + q(p.get("bundle")) + " would bring",
                        "It reads the bundle file and changes nothing.");
                return null;
            }
            default -> {
                return null;
            }
        }
    }

    /** A person's own tool, in words where its input says what it does; null otherwise. */
    static Call toolCall(String name, java.util.Map<?, ?> p) {
        if (name == null) return null;
        return switch (name) {
            case "Bash" -> new Call("run a command on your computer", "Bash runs it as your own Claude Code does in a "
                    + "terminal — the exact command is below.");
            case "Edit", "MultiEdit" -> has(p, "file_path") ? new Call("change the file " + q(p.get("file_path")),
                    name + " edits it in place.") : null;
            case "Write" -> has(p, "file_path") ? new Call("write the file " + q(p.get("file_path")),
                    "Write replaces the file if it exists.") : null;
            case "NotebookEdit" -> has(p, "notebook_path") ? new Call("change the notebook " + q(p.get("notebook_path")),
                    "NotebookEdit edits it in place.") : null;
            case "WebFetch" -> has(p, "url") ? new Call("fetch " + q(p.get("url")), "WebFetch contacts the web.") : null;
            case "WebSearch" -> has(p, "query") ? new Call("search the web for " + q(p.get("query")),
                    "WebSearch contacts the web.") : null;
            default -> null;
        };
    }

    /** The secondary line for a built-in tool: it belongs to Claude Code itself, so no server is claimed. */
    public static final String BUILT_IN_LINE = "built-in tool";

    /**
     * M74 UX (P3 leftover b): who names the tool. A built-in (its request carries {@code mcp_server: null}) is Claude
     * Code's own tool — it has no server, so the line says {@code built-in tool}; an MCP tool keeps its server and the
     * name its server gave it. Review M74 usability D1: the analyser's own verb says so in words — never the bridge's
     * server name or "named by the server".
     */
    static String serverLine(LocalAgentTranscript.Permission p, ToolClass cls) {
        return serverLine(p, cls, null);
    }

    static String serverLine(LocalAgentTranscript.Permission p, ToolClass cls, String name) {
        if (cls == ToolClass.BUILT_IN) return BUILT_IN_LINE;
        if (cls == ToolClass.HOSTED_DESTRUCTIVE || cls == ToolClass.HOSTED_OTHER) {
            return "the analyser's own tool" + (name == null ? "" : " (" + McpTools.PREFIX + name + ")");
        }
        StringBuilder server = new StringBuilder();
        if (p != null && p.serverName() != null) server.append("server: ").append(visible(p.serverName()).text());
        if (p != null && p.displayName() != null) {
            if (!server.isEmpty()) server.append(" · ");
            server.append("its name for the tool: ").append(visible(p.displayName()).text());
        }
        return server.toString();
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

    /** Characters that are letters or marks to Unicode but draw as nothing (review P3 N5). */
    static final java.util.Set<Integer> BLANK_LOOKING = java.util.Set.of(0x115F, 0x1160, 0x3164, 0xFFA0, 0x2800, 0x034F,
            0x17B4, 0x17B5);

    /** Text with its invisible characters made visible. */
    public record Visible(String text, boolean escaped) {
    }

    /**
     * Control characters (but the newline the re-indent wrote), format characters (bidirectional overrides and isolates,
     * zero-width characters), line and paragraph separators, and unassigned or private-use code points become
     * a visible escape (backslash, u, four hex digits); everything else is kept. Review P3 N5 (2026-10-17): so do the
     * spaces that look like an ordinary space but are not (U+00A0, U+1680, U+2000–U+200A, U+202F, U+205F, U+3000) and the
     * characters that draw as blank ({@link #BLANK_LOOKING}: the Hangul fillers U+115F, U+1160, U+3164, U+FFA0, the
     * blank Braille pattern U+2800, and the combining grapheme joiner and Khmer inherent vowels) — any of them can make
     * two different commands read the same.
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
                    || t == Character.PRIVATE_USE || t == Character.SURROGATE
                    || (t == Character.SPACE_SEPARATOR && cp != ' ') || BLANK_LOOKING.contains(cp));
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
