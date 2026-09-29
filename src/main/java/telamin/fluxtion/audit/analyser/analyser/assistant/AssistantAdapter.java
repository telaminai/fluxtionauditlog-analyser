package telamin.fluxtion.audit.analyser.analyser.assistant;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionDispatcher;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionParser;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.llm.LlmClient;
import telamin.fluxtion.audit.analyser.analyser.llm.LogFileInfo;
import telamin.fluxtion.audit.analyser.analyser.llm.Message;
import telamin.fluxtion.audit.analyser.analyser.llm.PromptBuilder;
import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;
import telamin.fluxtion.audit.analyser.analyser.ui.ActionExecutor;

import javax.swing.SwingUtilities;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Performs what the {@code assistantLoop} node asks (OA-1) and reports each outcome as a fact carrying the node's ticket.
 * It DECIDES nothing: not whether a turn may start, not which action runs, not whether a reply still counts (rule 9).
 *
 * <p><b>Threads.</b> Effects arrive on the event thread inside a session operation, and each is answered at once. The
 * work runs elsewhere: the provider call on a worker, record and source assembly on a worker, actions one at a time on a
 * serial worker — exactly as the external bridge runs them, because a render verb may itself submit to the session and
 * the session is single-in-flight. Outcomes return through {@link Environment#post}, which marshals to the event thread.
 *
 * <p><b>The guard.</b> An action's Swing mutations run in {@link ActionExecutor}'s event-thread tasks. The worker binds a
 * guard that is evaluated INSIDE each such task: if the node has moved its ticket (Cancel, New chat, a superseding change)
 * the task throws before touching anything. The node rejects the stale result as well; the guard is what stops the effect.
 *
 * <p><b>Credentials.</b> The key is read from the configuration when a request is performed, is used by the provider
 * client only, and is never put in a fact, a transcript entry or an error. Failure facts contain only a safe category
 * or HTTP status; provider error bodies never enter session state.
 */
public final class AssistantAdapter {

    /** What the adapter reads from, and reports to, the running application. */
    public interface Environment {
        /** The live configuration, read on the event thread at the moment an effect is performed. */
        AppConfig config();

        List<LogRecord> selection();

        String epFqn();

        LogFileInfo fileInfo();

        String vocabulary();

        SourceService sources();

        /** A dispatcher constructed as the external bridge's is: record verbs refuse without a log, the rest run. */
        ActionDispatcher dispatcher();

        /** The node's published decision, as of the last completed session operation. */
        AssistantState current();

        /** Report a fact to the session on the event thread. */
        void post(Object fact);

        /** Move the assistant between its hosts; null when done, else why not. */
        String showHost(boolean docked);
    }

    /** Builds the provider client for a route. Replaceable so a test can point the REAL adapter at a local fake server. */
    @FunctionalInterface
    public interface ClientFactory {
        LlmClient create(SessionEvents.AssistantRoute route, String apiKey);
    }

    public static final ClientFactory PROVIDERS =
            (route, key) -> LlmClient.forProvider(route.provider(), key, route.model(), route.baseUrl());

    private static final String RESULTS_HEADER = "Action results (JSON) — use these to answer:\n";

    private final AssistantTranscript transcript;
    private final Environment env;
    private final ClientFactory clients;
    private final ExecutorService workers = Executors.newCachedThreadPool(daemon("analyser-assistant"));
    private final ExecutorService actions = Executors.newSingleThreadExecutor(daemon("analyser-assistant-action"));
    private final Map<Long, List<Future<?>>> inFlight = new ConcurrentHashMap<>();
    /** Provider requests actually started — for tests (one Send is one request) and for the record. */
    private final AtomicInteger requestsStarted = new AtomicInteger();

    public AssistantAdapter(AssistantTranscript transcript, Environment env, ClientFactory clients) {
        this.transcript = transcript;
        this.env = env;
        this.clients = clients == null ? PROVIDERS : clients;
    }

    public AssistantTranscript transcript() {
        return transcript;
    }

    public int requestsStarted() {
        return requestsStarted.get();
    }

    /** Perform one assistant effect; the answer is immediate, the outcome is posted later. */
    public SessionEvents.Result perform(SessionEffects effect) {
        return switch (effect) {
            case SessionEffects.PrepareAssistantContextEffect e -> prepare(e);
            case SessionEffects.RequestAssistantCompletionEffect e -> complete(e);
            case SessionEffects.RunAssistantActionEffect e -> runAction(e);
            case SessionEffects.CancelAssistantTransportEffect e -> {
                List<Future<?>> running = inFlight.remove(e.ticket());
                int stopped = 0;
                if (running != null) for (Future<?> f : running) if (f.cancel(true)) stopped++;
                yield new SessionEvents.AssistantEffectStarted(e.opId(), e.ticket(), "cancelTransport:" + stopped);
            }
            case SessionEffects.ShowAssistantHostEffect e -> {
                String error = env.showHost(e.docked());
                yield new SessionEvents.AssistantHostShown(e.opId(), error == null ? e.docked() : !e.docked(), error == null,
                        error == null ? "" : error);
            }
            default -> throw new IllegalArgumentException("not an assistant effect: " + effect);
        };
    }

    /** Whether {@code effect} is one of the assistant's. */
    public static boolean handles(SessionEffects effect) {
        return effect instanceof SessionEffects.PrepareAssistantContextEffect
                || effect instanceof SessionEffects.RequestAssistantCompletionEffect
                || effect instanceof SessionEffects.RunAssistantActionEffect
                || effect instanceof SessionEffects.CancelAssistantTransportEffect
                || effect instanceof SessionEffects.ShowAssistantHostEffect;
    }

    // ---- the first content of a turn ----------------------------------------------------------------------------------

    private SessionEvents.Result prepare(SessionEffects.PrepareAssistantContextEffect e) {
        // captured NOW, on the event thread, as immutable values: the worker never sees a mutable config or selection
        String question = transcript.text(e.draft());
        List<LogRecord> records = e.includeRecordContext() ? List.copyOf(nullToEmpty(env.selection())) : List.of();
        String fqn = env.epFqn();
        LogFileInfo file = env.fileInfo();
        String vocabulary = env.vocabulary();
        SourceService sources = env.sources();
        long ticket = e.ticket();
        track(ticket, workers.submit(() -> {
            try {
                String context = records.isEmpty() ? "" : PromptBuilder.recordContext(records, fqn, sources, file, vocabulary);
                String content = context.isEmpty() ? question : "Context follows.\n\n" + context + "\n\nQuestion: " + question;
                if (e.includeManifest()) content = PromptBuilder.inProcessActionManifest(e.maxActionsPerReply()) + "\n\n" + content;
                long prompt = transcript.add(AssistantTranscript.Kind.PROMPT, content);
                env.post(new SessionEvents.AssistantContextPrepared(ticket, prompt));
            } catch (RuntimeException ex) {
                env.post(new SessionEvents.AssistantContextFailed(ticket, safeFailure(ex)));
            }
        }));
        return new SessionEvents.AssistantEffectStarted(e.opId(), ticket, "prepareContext");
    }

    // ---- one provider round -------------------------------------------------------------------------------------------

    private SessionEvents.Result complete(SessionEffects.RequestAssistantCompletionEffect e) {
        AppConfig cfg = env.config();
        String key = cfg == null || cfg.apiKey == null ? "" : cfg.apiKey;   // read now; used only by the client
        List<Message> messages = materialise(e.history());
        String system = PromptBuilder.systemPrompt();
        long ticket = e.ticket();
        int round = e.round();
        requestsStarted.incrementAndGet();
        track(ticket, workers.submit(() -> {
            try {
                LlmClient client = clients.create(e.route(), key);
                String reply = client.complete(system, messages);
                long replyId = transcript.add(AssistantTranscript.Kind.ANSWER, reply);
                List<Long> blocks = new ArrayList<>();
                for (String block : ActionParser.extract(reply)) blocks.add(transcript.add(AssistantTranscript.Kind.ACTION, block));
                env.post(new SessionEvents.AssistantCompletionReceived(ticket, round, replyId, blocks));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                env.post(new SessionEvents.AssistantCompletionFailed(ticket, round, "the request was stopped"));
            } catch (Exception ex) {
                env.post(new SessionEvents.AssistantCompletionFailed(ticket, round, safeFailure(ex)));
            }
        }));
        return new SessionEvents.AssistantEffectStarted(e.opId(), ticket, "requestCompletion:" + round);
    }

    /** The provider messages the node's history names, built from the transcript. */
    List<Message> materialise(List<SessionEffects.HistoryMessage> history) {
        List<Message> out = new ArrayList<>();
        for (SessionEffects.HistoryMessage m : history) {
            StringBuilder text = new StringBuilder();
            if ("RESULTS".equals(m.kind())) {
                text.append(RESULTS_HEADER);
                for (long id : m.entries()) text.append(transcript.text(id)).append('\n');
            } else {
                for (long id : m.entries()) text.append(transcript.text(id));
            }
            out.add("assistant".equals(m.role()) ? Message.assistant(text.toString()) : Message.user(text.toString()));
        }
        return out;
    }

    // ---- one action ---------------------------------------------------------------------------------------------------

    private SessionEvents.Result runAction(SessionEffects.RunAssistantActionEffect e) {
        String block = transcript.text(e.action());
        String verb = verbOf(block);
        long ticket = e.ticket();
        long action = e.action();
        ActionDispatcher dispatcher = env.dispatcher();
        java.util.function.BooleanSupplier current = () -> {
            AssistantState s = env.current();
            return s.ticket() == ticket && "RUNNING_ACTION".equals(s.phase());
        };
        track(ticket, actions.submit(() -> {
            ActionResult result;
            try {
                // checked on the event thread, after the operation that asked for this action has published its decision
                if (!onEdt(current)) {
                    result = ActionResult.error("not run: the turn ended before this action started");
                } else {
                    ActionExecutor.bindGuard(current);
                    try {
                        result = dispatcher.dispatch(block);
                    } finally {
                        ActionExecutor.bindGuard(null);
                    }
                }
            } catch (RuntimeException ex) {
                result = ActionResult.error(verb + " failed: " + bounded(rootMessage(ex), null));
            }
            long resultId = transcript.add(AssistantTranscript.Kind.RESULT, result.toJson());
            env.post(new SessionEvents.AssistantActionFinished(ticket, action, verb, result.ok(), resultId));
        }));
        return new SessionEvents.AssistantEffectStarted(e.opId(), ticket, "action:" + verb);
    }

    /** The verb an action block names, read from its JSON; "?" when it names none. */
    static String verbOf(String block) {
        try {
            Object root = Json.parse(block);
            if (root instanceof Map<?, ?> m && m.get("action") != null) {
                String name = m.get("action").toString();
                // Only schema vocabulary may enter session facts; an unknown name is provider-authored text.
                return telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas.all().containsKey(name) ? name : "?";
            }
        } catch (RuntimeException ignored) {
            // an unparseable block is still dispatched, and refused by the dispatcher with its own words
        }
        return "?";
    }

    private static boolean onEdt(java.util.function.BooleanSupplier check) {
        if (SwingUtilities.isEventDispatchThread()) return check.getAsBoolean();
        boolean[] out = new boolean[1];
        try {
            SwingUtilities.invokeAndWait(() -> out[0] = check.getAsBoolean());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (java.lang.reflect.InvocationTargetException ex) {
            return false;
        }
        return out[0];
    }

    // ---- housekeeping -------------------------------------------------------------------------------------------------

    private void track(long ticket, Future<?> f) {
        inFlight.computeIfAbsent(ticket, t -> new java.util.concurrent.CopyOnWriteArrayList<>()).add(f);
    }

    /** Stop everything: the application is exiting. */
    public void shutdown() {
        workers.shutdownNow();
        actions.shutdownNow();
    }

    private static <T> List<T> nullToEmpty(List<T> l) {
        return l == null ? List.of() : l;
    }

    static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        String m = r.getMessage();
        return r.getClass().getSimpleName() + (m == null ? "" : ": " + m);
    }

    /** Failure facts carry a safe category/status, never arbitrary exception or provider-response text. */
    static String safeFailure(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        var status = java.util.regex.Pattern.compile("^(Anthropic|OpenAI) HTTP ([1-5][0-9]{2})(?::|$)")
                .matcher(message == null ? "" : message);
        if (status.find()) return status.group(1) + " HTTP " + status.group(2);
        if (root instanceof java.net.http.HttpTimeoutException) return "request timed out";
        if (root instanceof java.io.IOException) return "provider or context I/O failed";
        return "request processing failed";
    }

    /** At most 300 characters, one line, and never the key, however the transport phrased its error. */
    static String bounded(String s, String key) {
        String out = s == null ? "" : s.replace('\n', ' ').replace('\r', ' ');
        if (key != null && key.length() >= 8) out = out.replace(key, "‹key›");
        return out.length() <= 300 ? out : out.substring(0, 297) + "…";
    }

    private static java.util.concurrent.ThreadFactory daemon(String name) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, name + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }
}
