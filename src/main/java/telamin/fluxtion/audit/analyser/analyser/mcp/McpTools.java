package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapts the shipped {@link VerbSchemas} to MCP tool descriptors (spec-assistant-actions-mcp §9, M13.2).
 *
 * <p>Deliberately <b>thin</b>: {@code VerbSchemas} is the single source of truth that already backs REST
 * {@code /manifest}, and a parallel schema holder here would fork the transports — the exact drift §9
 * exists to prevent. Each verb becomes one tool named {@code analyser_<verb>}, so a new verb on the
 * dispatcher publishes a new MCP tool for free.
 *
 * <p>A verb's schema {@code description} is lifted to the tool description, leaving the rest as the
 * {@code inputSchema}. Order follows {@code VerbSchemas}' insertion order, which MCP asks servers to keep
 * deterministic so clients can cache the tool list.
 */
public final class McpTools {
    private McpTools() { }

    /** Tool-name prefix — namespaces our verbs when a client aggregates several MCP servers. */
    public static final String PREFIX = "analyser_";

    /** Read-only data operations; source additionally changes the transient navigation view. */
    static final Set<String> READ_ONLY = Set.of("aggregate", "read", "context", "coverage", "series", "source");
    // (propose is not read-only — it records an offer — and not destructive: annotations() reports it so)

    /** One MCP tool descriptor per verb, in {@code VerbSchemas} order. */
    public static List<Map<String, Object>> list() {
        return list(VerbSchemas.all());
    }

    /** M74 P2: the hosted bridge's tools — {@code VerbSchemas.hosted()}, which adds {@code propose} (§8.2 (a)). */
    public static List<Map<String, Object>> hostedList() {
        return list(VerbSchemas.hosted());
    }

    /** The verb behind a hosted tool name: {@link #verbFor} plus {@code propose}. */
    public static String hostedVerbFor(String toolName) {
        if (toolName == null || !toolName.startsWith(PREFIX)) return null;
        String verb = toolName.substring(PREFIX.length());
        return VerbSchemas.hosted().containsKey(verb) ? verb : null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> schemas) {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Map.Entry<String, Object> e : schemas.entrySet()) {
            String verb = e.getKey();
            Map<String, Object> inputSchema = new LinkedHashMap<>((Map<String, Object>) e.getValue());
            Object description = inputSchema.remove("description");   // schema → tool description

            Map<String, Object> tool = new LinkedHashMap<>();
            tool.put("name", PREFIX + verb);
            tool.put("description", description == null ? verb : description.toString());
            tool.put("inputSchema", inputSchema);
            tool.put("annotations", annotations(verb));
            tools.add(tool);
        }
        return tools;
    }

    /**
     * The verb behind a tool name, or {@code null} if this is not one of ours. Used by
     * {@code tools/call} (M13.3) to build the {@code {action, params}} the dispatcher speaks.
     */
    public static String verbFor(String toolName) {
        if (toolName == null || !toolName.startsWith(PREFIX)) return null;
        String verb = toolName.substring(PREFIX.length());
        return VerbSchemas.all().containsKey(verb) ? verb : null;
    }

    /**
     * Verbs whose effect is <b>not</b> reversible from the UI: {@code open} replaces the loaded log, and
     * anything held only in the session — flags, their notes — goes with it; since M35.8 it can also
     * replace the whole PROJECT context ({@code open {project}}); {@code source_root} writes
     * the persisted config. Marking them {@code destructiveHint:true} is what lets a client prompt before
     * running them, which is the whole point of the hint. Calling them reversible because "no file is
     * deleted" would be true and useless.
     *
     * <p>{@code screenshot} and {@code report} are here for the same reason: both take a caller-supplied
     * path and write it unconditionally, so both can overwrite a file the app knows nothing about. That
     * {@code screenshot} was not marked so before M23.8 was an oversight — a file the app silently
     * replaced is exactly the case the hint exists to warn about.
     */
    private static final java.util.Set<String> DESTRUCTIVE =
            java.util.Set.of("open", "source_root", "screenshot", "report", "walk", "import");

    /**
     * The verbs marked destructive to MCP clients. Exposed so {@code FaqSecurityContractTest} can assert
     * every one of them is named in the FAQ's security answer — a mutating verb that the user-facing
     * security promise doesn't mention is exactly the drift that made review B1 a merge blocker.
     */
    public static java.util.Set<String> destructiveVerbs() {
        return DESTRUCTIVE;
    }

    /**
     * M74 P3 (spec §5.7): what a destructive verb changes, in the analyser's own words — the one line the permission
     * dialog shows beside the call's input, so the person knows what Allow once lets happen.
     */
    public static String destructiveRationale(String verb) {
        return switch (verb == null ? "" : verb) {
            case "open" -> "open replaces the loaded log or project (what is open now is closed)";
            case "source_root" -> "source_root changes the configured source roots, saved in your settings — the folders "
                    + "the analyser reads source from";
            case "screenshot" -> "screenshot writes an image file in the exchange directory";
            case "report" -> "report builds or replaces a named report in the project, or writes a file in the exchange "
                    + "directory";
            case "walk" -> "walk saves, replaces, renames, plays or deletes a saved walk, or records one as a page in the "
                    + "exchange directory";
            case "import" -> "import borrows charts, reports or walks from an evidence bundle into the open project";
            default -> "this verb changes what is open or writes files";
        };
    }

    /**
     * Review M74 usability #10 (2026-10-17), verified against the code that performs each call: what a hosted call of an
     * ASKED verb does, judged from its own parameters — the ONE classification the adapter (whether to ask) and the
     * action server (whether a grant is needed) share.
     *
     * <ul>
     *   <li>{@code NON_MUTATING} — exactly these, and only with no other field: {@code walk {name, play: true, step?}}
     *       (posts a play request; {@code WalkVerb.play} saves nothing), {@code walk {end: true}} (ends the showing walk),
     *       {@code walk {restore: true}} and {@code report {restore: true}} (list the bins). They change what is SHOWN,
     *       as the pre-allowed render verbs do, and nothing stored.</li>
     *   <li>{@code WRITES_FILE} — the calls that go through the exchange-folder guard ({@code ActionExecutor
     *       .guardedPath}): every {@code screenshot}; {@code walk {reel}}; {@code report {bundle}}; a named report with a
     *       {@code path}; the single-record report (always a file).</li>
     *   <li>{@code CHANGES} — everything else: save, replace, rename, delete, restore by name, {@code open},
     *       {@code import}, {@code source_root}. These are always asked.</li>
     * </ul>
     */
    public static telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind hostedCallKind(String verb,
                                                                                                             Object params) {
        Map<?, ?> p = params instanceof Map<?, ?> m ? m : Map.of();
        String v = verb == null ? "" : verb;
        switch (v) {
            case "walk" -> {
                if (p.containsKey("reel")) return telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind.WRITES_FILE;
                if (Boolean.TRUE.equals(p.get("play")) && Set.of("play", "name", "step").containsAll(keys(p))) return nonMutating();
                if (Boolean.TRUE.equals(p.get("end")) && keys(p).equals(Set.of("end"))) return nonMutating();
                if (Boolean.TRUE.equals(p.get("restore")) && keys(p).equals(Set.of("restore"))) return nonMutating();
            }
            case "report" -> {
                // final review #2: the operation is VerbOperations.report's — the executor's and the dialog's choice
                VerbOperations.ReportOp op = VerbOperations.report(p).op();
                if (op == VerbOperations.ReportOp.LIST_DELETED) return nonMutating();
                if (p.containsKey("bundle")) return writesFile();
                if (op == VerbOperations.ReportOp.FINDING || p.get("path") != null) return writesFile();
            }
            case "screenshot" -> {
                return writesFile();
            }
            default -> { }
        }
        return telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind.CHANGES;
    }

    private static Set<String> keys(Map<?, ?> p) {
        Set<String> out = new java.util.HashSet<>();
        for (Object k : p.keySet()) out.add(String.valueOf(k));
        return out;
    }

    private static telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind nonMutating() {
        return telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind.NON_MUTATING;
    }

    private static telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind writesFile() {
        return telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind.WRITES_FILE;
    }

    /**
     * M74 review H5: the verbs the analyser's own Claude Code session (the hosted bridge) may call without asking, BY
     * NAME — an allow-list, never "every verb minus the destructive ones". The one definition: {@code LocalAgentPosture}
     * (the command line and the posture check) and {@code ActionServer} (the server-side admission) both read it, so a
     * verb added to {@code VerbSchemas} stays hidden and refused for the hosted bridge until it is listed here. (It lives
     * here, not in the session package, because a persisting package may not name the overlay verb, D-SP4.)
     */
    public static final java.util.List<String> HOSTED_PRE_ALLOWED = java.util.List.of("read", "aggregate", "series",
            "context", "coverage", "source", "filter", "goto", "flag", "graph", "topology", "spotlight",
            // M74 P2 (§5.6): non-destructive — it only records an offer; the person answers and the analyser applies
            "propose");

    /**
     * Read-only hint on the query verbs only. The render verbs change what the UI shows, so they are not
     * read-only — but nothing is deleted and every one of them is reversible (a filter can be widened, a
     * graph closed, a flag cleared), hence {@code destructiveHint:false}.
     */
    private static Map<String, Object> annotations(String verb) {
        Map<String, Object> a = new LinkedHashMap<>();
        boolean readOnly = READ_ONLY.contains(verb);
        a.put("readOnlyHint", readOnly);
        if (!readOnly) a.put("destructiveHint", DESTRUCTIVE.contains(verb));
        return a;
    }
}
