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
            case "graph" -> "graph deletes, renames, restores or replaces a saved chart's definition (the old definition "
                    + "can be restored)";
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
        return hostedCallKind(verb, params, null);
    }

    /**
     * Owner decision 2026-10-18: which charts exist now, open or saved — what a {@code graph} call's classification needs
     * to tell a call that CREATES a chart from one that replaces an existing chart's definition. Absent (null), every
     * name is taken to exist, so every definition-setting call is asked (fail-closed).
     */
    @FunctionalInterface
    public interface ChartState {
        /** Whether a chart called {@code name} exists now, open or saved. */
        boolean holds(String name);
    }

    /**
     * {@link #hostedCallKind(String, Object)} with the charts that exist now. For {@code graph} (owner decision
     * 2026-10-18), judged from {@link VerbOperations#graph}, the executor's own choice:
     *
     * <ul>
     *   <li>{@code NON_MUTATING} — {@code {restore: true}} (lists the chart bin).</li>
     *   <li>{@code PRE_ALLOWED} — showing, reopening, re-extracting or closing a chart ({@code SHOW}, {@code CLOSE}: the
     *       definition is kept), and an {@code EDIT} that CREATES a chart: {@code newTab: true} (the executor refuses a
     *       taken name), no {@code name} (the executor adds a new chart), or a {@code name} no chart holds now — which
     *       the server then runs as {@code newTab: true}
     *       ({@link #hostedConstrained}), so a chart created meanwhile is refused rather than replaced.</li>
     *   <li>{@code CHANGES} — everything else: a delete, a rename, a restore by name or id, and every {@code EDIT} of
     *       an existing chart — a re-sent notes, series, guides or
     *       bands set, {@code clearNotes}, a style or a pin. Each is asked; the previous definition is kept in the bin.</li>
     * </ul>
     */
    public static telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind hostedCallKind(String verb,
                                                                                                             Object params,
                                                                                                             ChartState charts) {
        return hostedCallKind(verb, params, charts, null);
    }

    /**
     * Owner, 2026-10-18: what the hosted session's {@code open} may do without asking — the session's REAL project folder
     * and whether it runs Full Claude Code, as observed for the call's own generation. Null: unknown, so every
     * {@code open} is asked (fail-closed). The external REST and MCP clients never have one: this is only ever judged
     * for a hosted call.
     */
    public record OpenScope(java.nio.file.Path projectRoot, boolean full, java.nio.file.Path sampleRoot) {
        /** The session's project folder only — no analyser-owned project is active. */
        public OpenScope(java.nio.file.Path projectRoot, boolean full) {
            this(projectRoot, full, null);
        }
    }

    /** The {@code open} keys a pre-allowed call may carry: a log and its GraphML, nothing else. */
    static final Set<String> IN_PROJECT_OPEN_KEYS = Set.of("log", "graphml");

    /**
     * {@link #hostedCallKind(String, Object, ChartState)} with the hosted session's {@code scope}. Owner, 2026-10-18 (the
     * live authoring run): in FULL Claude Code, an {@code open} of only a {@code log} and/or {@code graphml} whose REAL
     * paths (links resolved) are existing files inside the session's real project folder is {@code PRE_ALLOWED} — it
     * opens the project's own evidence. Everything else stays {@code CHANGES} and is asked: a project switch or close,
     * follow, a bundle, a create, any other key, a relative or {@code ~} path, a path outside the folder or one that
     * leads out through a link, and every {@code open} under Safe analysis.
     *
     * <p>Owner hands-on test, 2026-10-18: the analyser's OWN sample project works in a scratch folder (OD-74.22), so its
     * log and GraphML were never "inside the project folder" and every open of them was asked. While the sample is the
     * ACTIVE project, {@link OpenScope#sampleRoot()} is its real root, and a file inside it qualifies by the same
     * real-path checks — still Full only, still log and GraphML only.
     */
    public static telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind hostedCallKind(String verb,
                                                                                                             Object params,
                                                                                                             ChartState charts,
                                                                                                             OpenScope scope) {
        Map<?, ?> p = params instanceof Map<?, ?> m ? m : Map.of();
        String v = verb == null ? "" : verb;
        switch (v) {
            case "open" -> {
                if (openInProject(p, scope)) return preAllowed();
            }
            case "graph" -> {
                return switch (graphCall(p, charts)) {
                    case LIST -> nonMutating();
                    case SHOW, CREATE_NEW_TAB, CREATE_FREE_NAME -> preAllowed();
                    case CHANGES -> telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind.CHANGES;
                };
            }
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

    /** Whether a hosted {@code open} only opens the project's own files (see {@link #hostedCallKind(String, Object, ChartState, OpenScope)}). */
    static boolean openInProject(Map<?, ?> p, OpenScope scope) {
        if (scope == null || !scope.full() || scope.projectRoot() == null) return false;
        if (p.isEmpty() || !IN_PROJECT_OPEN_KEYS.containsAll(keys(p))) return false;
        if (VerbOperations.open(p) != VerbOperations.OpenOp.FILES) return false;
        java.util.List<java.nio.file.Path> roots = new java.util.ArrayList<>();
        for (java.nio.file.Path r : new java.nio.file.Path[]{scope.projectRoot(), scope.sampleRoot()}) {
            if (r == null) continue;
            try {
                roots.add(r.toRealPath());
            } catch (java.io.IOException | RuntimeException e) {
                // a root that does not resolve allows nothing
            }
        }
        if (roots.isEmpty()) return false;
        for (Object value : p.values()) {
            if (!(value instanceof String s) || s.isBlank() || s.startsWith("~")) return false;
            try {
                java.nio.file.Path given = java.nio.file.Path.of(s);
                if (!given.isAbsolute()) return false;
                java.nio.file.Path real = given.toRealPath();
                if (roots.stream().noneMatch(real::startsWith) || !java.nio.file.Files.isRegularFile(real)) return false;
            } catch (java.io.IOException | RuntimeException e) {
                return false;                     // missing, unreadable or not a path: asked
            }
        }
        return true;
    }

    /** A hosted {@code graph} call, judged: the one judgement the kind and the server's constraint share. */
    enum GraphCall { LIST, SHOW, CREATE_NEW_TAB, CREATE_FREE_NAME, CHANGES }

    static GraphCall graphCall(Map<?, ?> p, ChartState charts) {
        VerbOperations.Chosen<VerbOperations.GraphOp> chosen = VerbOperations.graph(p);
        if (!chosen.ok()) return GraphCall.CHANGES;
        return switch (chosen.op()) {
            case LIST_DELETED -> GraphCall.LIST;
            case SHOW, CLOSE -> GraphCall.SHOW;
            case EDIT -> {
                if (Boolean.TRUE.equals(p.get("newTab"))) yield GraphCall.CREATE_NEW_TAB;
                Object name = p.get("name");
                // no name: the executor adds a new chart (graphForAction(null, …)); the server runs it as newTab anyway
                if (name == null) yield GraphCall.CREATE_FREE_NAME;
                if (name instanceof String n && !n.isBlank() && charts != null && !charts.holds(n.trim())) {
                    yield GraphCall.CREATE_FREE_NAME;
                }
                yield GraphCall.CHANGES;
            }
            default -> GraphCall.CHANGES;
        };
    }

    /**
     * Owner decision 2026-10-18: the input the server dispatches for a hosted call it admitted. A {@code graph} call
     * admitted without the person's approval because its {@code name} was free runs as {@code newTab: true}, so the
     * executor refuses it if a chart of that name appeared meanwhile — it creates, or changes nothing; it never replaces.
     * Every other call is dispatched exactly as sent.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> hostedConstrained(String verb, Map<String, Object> params, ChartState charts) {
        if (!"graph".equals(verb) || params == null || graphCall(params, charts) != GraphCall.CREATE_FREE_NAME) return params;
        Map<String, Object> out = new LinkedHashMap<>(params);
        out.put("newTab", Boolean.TRUE);
        return out;
    }

    private static telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind preAllowed() {
        return telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind.PRE_ALLOWED;
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
            "context", "coverage", "source", "filter", "goto", "flag", "topology", "spotlight",
            // M74 P2 (§5.6): non-destructive — it only records an offer; the person answers and the analyser applies
            "propose");

    /**
     * Owner decision 2026-10-18: the hosted verbs judged CALL BY CALL ({@link #hostedCallKind(String, Object, ChartState)})
     * — {@code graph}, whose create and show calls are pre-allowed and whose delete, rename, restore and replacement of an
     * existing chart's definition are asked. Visible in every posture; named in {@code --allowedTools} only where the
     * mode never asks, and there the server refuses the calls that would need an approval (none can be given).
     */
    public static final java.util.List<String> HOSTED_PER_CALL = java.util.List.of("graph");

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
