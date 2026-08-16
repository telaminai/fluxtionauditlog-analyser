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

    /** Verbs that never mutate the app: query-only over the loaded log. */
    private static final Set<String> READ_ONLY = Set.of("aggregate", "read", "context", "coverage");

    /** One MCP tool descriptor per verb, in {@code VerbSchemas} order. */
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> list() {
        List<Map<String, Object>> tools = new ArrayList<>();
        for (Map.Entry<String, Object> e : VerbSchemas.all().entrySet()) {
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
     * anything held only in the session — flags, their notes — goes with it; {@code source_root} writes
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
            java.util.Set.of("open", "source_root", "screenshot", "report");

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
