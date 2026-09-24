package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * Validates and routes one assistant action to its handler (spec-assistant-actions §8), transport-agnostic
 * — the same dispatcher backs the in-process executor and the localhost REST server. Never throws: every
 * bad input (malformed JSON, wrong version/token, unknown verb, handler failure) becomes a structured
 * {@code ok:false} so the model gets actionable feedback (#3).
 *
 * <p>Slice 1 wires the {@code aggregate} query verb (read-only over a {@link LogIndex.Snapshot}); the
 * render verbs ({@code filter}/{@code graph}/{@code goto}/{@code flag}) are recognised but report
 * not-yet-enabled until slice 3.
 */
public final class ActionDispatcher {

    public static final int SCHEMA_VERSION = 1;

    private final boolean requireToken;      // true on the REST path, false in-process
    private final String token;
    private final Supplier<LogIndex.Snapshot> snapshot;
    private final IntFunction<String> rawText;   // row → raw record text, for a text filter; may be null
    private final IntFunction<telamin.fluxtion.audit.analyser.analyser.model.LogRecord> record; // row → parsed, under the store's grammar; may be null
    private final RenderExecutor render;          // render verbs (filter/graph/goto/flag); null = not enabled

    public ActionDispatcher(boolean requireToken, String token,
                            Supplier<LogIndex.Snapshot> snapshot, IntFunction<String> rawText) {
        this(requireToken, token, snapshot, rawText, null);
    }

    public ActionDispatcher(boolean requireToken, String token, Supplier<LogIndex.Snapshot> snapshot,
                            IntFunction<String> rawText, RenderExecutor render) {
        this(requireToken, token, snapshot, rawText, null, render);
    }

    /**
     * @param record row → the store's parsed record, so {@code read} with {@code fields} projects under
     *               the grammar the store's reader declared rather than re-parsing text as legacy
     */
    public ActionDispatcher(boolean requireToken, String token, Supplier<LogIndex.Snapshot> snapshot,
                            IntFunction<String> rawText,
                            IntFunction<telamin.fluxtion.audit.analyser.analyser.model.LogRecord> record,
                            RenderExecutor render) {
        if (requireToken && token == null) {
            throw new IllegalArgumentException("a token-guarded dispatcher requires a non-null token");
        }
        this.requireToken = requireToken;
        this.token = token;
        this.snapshot = snapshot;
        this.rawText = rawText;
        this.record = record;
        this.render = render;
    }

    /** Parse a request JSON body and dispatch it. */
    public ActionResult dispatch(String jsonBody) {
        Object root;
        try {
            root = Json.parse(jsonBody);
        } catch (RuntimeException e) {
            return ActionResult.error("malformed JSON: " + e.getMessage());
        }
        if (!(root instanceof Map<?, ?> m)) return ActionResult.error("request must be a JSON object");
        return dispatch(m);
    }

    @SuppressWarnings("unchecked")
    public ActionResult dispatch(Map<?, ?> req) {
        Object vv = req.get("v");
        int v = vv instanceof Number n ? n.intValue() : SCHEMA_VERSION;
        if (v != SCHEMA_VERSION) return ActionResult.error("unsupported schema version " + v
                + " (this build speaks v" + SCHEMA_VERSION + ")");

        if (requireToken) {
            Object t = req.get("token");
            if (t == null || !token.equals(t.toString())) return ActionResult.error("bad or missing token");
        }

        Object a = req.get("action");
        String action = a == null ? "" : a.toString();
        Map<String, Object> params = req.get("params") instanceof Map<?, ?> p
                ? (Map<String, Object>) p : Map.of();

        // M68.5 (D-E6): observed BEFORE anything is served. A verb that reads records is refused while the opened file
        // has changed in place under a store that reads through to it, and labelled when what it reads is the
        // opened content of a file that has since been replaced.
        var identity = READS_RECORDS.contains(action) && render != null ? render.readIdentity() : null;
        if (identity != null && identity.suspendsReads()) {
            return ActionResult.error(identity.reason() + ".");
        }
        try {
            ActionResult result = switch (action) {
                case "aggregate" -> ActionResult.ok("aggregate", "result",
                        AggregateService.aggregate(snapshot.get(), params, rawText));
                case "read" -> ActionResult.ok("read", "result",
                        ReadService.read(snapshot.get(), params, rawText, record));
                case "filter", "graph", "goto", "flag", "topology", "open", "source", "source_root", "screenshot",
                     "report", "coverage", "series", "context", "spotlight" -> render != null
                        ? render.render(action, params)
                        : ActionResult.error("render verb '" + action + "' is not enabled here");
                case "" -> ActionResult.error("missing 'action'");
                default -> ActionResult.error("unknown verb '" + action + "'");
            };
            return withIdentityNote(withIgnoredParams(result, action, params), identity);
        } catch (RuntimeException e) {
            return ActionResult.error(action + " failed: " + e.getMessage());
        }
    }

    /** The verbs whose answers are made of records; the rest describe the view, the graph or the transport. */
    static final java.util.Set<String> READS_RECORDS = java.util.Set.of(
            "aggregate", "read", "filter", "graph", "goto", "flag", "coverage", "series", "report");

    private static ActionResult withIdentityNote(ActionResult result,
                                                 telamin.fluxtion.audit.analyser.analyser.parse.ReadThroughIdentity identity) {
        if (identity == null || !result.ok() || result.payload() == null) return result;
        Map<String, Object> payload = new java.util.LinkedHashMap<>(result.payload());
        payload.put("identityNote", identity.reason());
        return ActionResult.ok(result.action(), result.payloadKey(), payload);
    }

    /**
     * Every verb echo names what it ignored (M26.4): a param this verb's schema doesn't declare was
     * silently dropped — the caller (usually a mistyped or misplaced key) deserves to hear that, not to
     * wonder why nothing changed. Piggybacks on {@link VerbSchemas} being the single source of truth, so
     * a verb that gains a param can never be accused of ignoring it.
     */
    private static ActionResult withIgnoredParams(ActionResult result, String action, Map<String, Object> params) {
        if (params.isEmpty()) return result;
        if (!(VerbSchemas.all().get(action) instanceof Map<?, ?> schema)
                || !(schema.get("properties") instanceof Map<?, ?> props)) return result;
        List<String> ignored = params.keySet().stream().filter(k -> !props.containsKey(k)).sorted().toList();
        if (ignored.isEmpty()) return result;
        if (!result.ok()) {
            // M68.4 (D-E3): a refusal names them too. A misspelled key is often WHY a call failed, and it used to be
            // named only on success — the one reply that did not need it
            Object why = result.toMap().get("error");
            return ActionResult.error(why + " (also not read — not parameters of '" + action + "': "
                    + String.join(", ", ignored) + ")");
        }
        if (result.payload() == null) return result;
        Map<String, Object> payload = new java.util.LinkedHashMap<>(result.payload());
        payload.put("ignoredParams", ignored);
        return ActionResult.ok(result.action(), result.payloadKey(), payload);
    }
}
