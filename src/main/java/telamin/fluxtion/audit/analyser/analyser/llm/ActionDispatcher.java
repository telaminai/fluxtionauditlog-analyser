package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.index.LogIndex;

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
    private final RenderExecutor render;          // render verbs (filter/graph/goto/flag); null = not enabled

    public ActionDispatcher(boolean requireToken, String token,
                            Supplier<LogIndex.Snapshot> snapshot, IntFunction<String> rawText) {
        this(requireToken, token, snapshot, rawText, null);
    }

    public ActionDispatcher(boolean requireToken, String token, Supplier<LogIndex.Snapshot> snapshot,
                            IntFunction<String> rawText, RenderExecutor render) {
        if (requireToken && token == null) {
            throw new IllegalArgumentException("a token-guarded dispatcher requires a non-null token");
        }
        this.requireToken = requireToken;
        this.token = token;
        this.snapshot = snapshot;
        this.rawText = rawText;
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

        try {
            return switch (action) {
                case "aggregate" -> ActionResult.ok("aggregate", "result",
                        AggregateService.aggregate(snapshot.get(), params, rawText));
                case "read" -> ActionResult.ok("read", "result",
                        ReadService.read(snapshot.get(), params, rawText));
                case "filter", "graph", "goto", "flag" -> render != null
                        ? render.render(action, params)
                        : ActionResult.error("render verb '" + action + "' is not enabled here");
                case "" -> ActionResult.error("missing 'action'");
                default -> ActionResult.error("unknown verb '" + action + "'");
            };
        } catch (RuntimeException e) {
            return ActionResult.error(action + " failed: " + e.getMessage());
        }
    }
}
