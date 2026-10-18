package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Review M6: the hosted bridge's admission, asked of the SESSION. A call carrying a live hosted credential posts
 * {@link SessionEvents.LocalAgentCallAdmissionRequested} to the session's thread and waits (bounded) for the
 * {@code localAgentSession} node's verdict, which arrives as {@link SessionEffects.AnswerHostedCallEffect}. So the
 * decision is made in the session's own order: a posture kill the session processed first refuses the call, and nothing
 * on the HTTP thread decides from a published snapshot that may already be out of date.
 *
 * <p>It decides nothing itself: it correlates a call with its answer, and a call that is not answered in time is
 * refused (the session did not admit it).
 */
public final class HostedAdmission implements telamin.fluxtion.audit.analyser.analyser.net.HostedCredentials.Decider {

    /** How long a hosted call waits for the session's verdict before it is refused. */
    public static final long DEFAULT_TIMEOUT_MS = 5_000;

    public static final String TIMEOUT_TEXT = "the analyser did not admit the Claude Code call in time, so it was refused";

    private final Consumer<Object> post;
    private final long timeoutMs;
    private final AtomicLong calls = new AtomicLong();
    private final Map<Long, CompletableFuture<Optional<String>>> waiting = new ConcurrentHashMap<>();

    /** @param post delivers a fact to the session, on its thread */
    public HostedAdmission(Consumer<Object> post, long timeoutMs) {
        this.post = post;
        this.timeoutMs = timeoutMs;
    }

    /** Ask the session whether a call of {@code generation} may run now: null admits it, else the refusal. Blocks. */
    public String admit(long generation) {
        return admit(generation, null);
    }

    /** Scorecard R1: the same, naming the call's verb in the fact. */
    @Override
    public String admit(long generation, String verb) {
        return ask(generation, call -> new SessionEvents.LocalAgentCallAdmissionRequested(call, generation, verb));
    }

    /**
     * M74 P3 (OD-74.17): present a hosted DESTRUCTIVE call to the session, which admits it only by consuming the
     * person's grant for this exact call. Blocks, bounded, like {@link #admit}.
     */
    @Override
    public String present(long generation, String verb, String inputDigest, String toolUseIdDigest) {
        return ask(generation, call -> new SessionEvents.HostedCallPresented(call, generation, verb, inputDigest,
                toolUseIdDigest));
    }

    /** Scorecard R1: the server's own refusal, reported to the session as a fact; nothing waits for it. */
    @Override
    public void refused(long generation, String verb, String reason) {
        post.accept(new SessionEvents.HostedCallRefusedByServer(generation, verb, reason));
    }

    /** Owner 2026-10-18: the server wrote a report without an approval — a fact for the session; nothing waits for it. */
    @Override
    public void reportWritten(long generation, String name, String createdAt) {
        post.accept(new SessionEvents.HostedReportWritten(generation, name, createdAt));
    }

    private String ask(long generation, java.util.function.LongFunction<Object> fact) {
        long call = calls.incrementAndGet();
        CompletableFuture<Optional<String>> answer = new CompletableFuture<>();
        waiting.put(call, answer);
        boolean posted = false;
        try {
            post.accept(fact.apply(call));
            posted = true;
            return answer.get(timeoutMs, TimeUnit.MILLISECONDS).orElse(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return abandoned(call, generation, posted);
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            return abandoned(call, generation, posted);
        } finally {
            waiting.remove(call);
        }
    }

    /**
     * Review B4: the call gave up. If its fact reached the session, the session may still admit it — so the session is
     * told, and decides what an admission of a call that never ran means.
     */
    private String abandoned(long call, long generation, boolean posted) {
        if (posted) {
            try {
                post.accept(new SessionEvents.HostedCallAbandoned(call, generation));
            } catch (RuntimeException ignored) {
                // the session is gone: there is nothing left that could admit it
            }
        }
        return TIMEOUT_TEXT;
    }

    /** Perform the node's verdict: complete the waiting call (a call that already gave up is ignored). */
    public SessionEvents.Result answer(SessionEffects.AnswerHostedCallEffect e) {
        CompletableFuture<Optional<String>> f = waiting.get(e.call());
        if (f != null) f.complete(Optional.ofNullable(e.refusal()));
        return new SessionEvents.LocalAgentEffectStarted(e.opId(), 0, f == null ? "answerHostedCall:gone" : "answerHostedCall");
    }
}
