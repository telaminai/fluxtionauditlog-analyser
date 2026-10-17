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
public final class HostedAdmission {

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
        long call = calls.incrementAndGet();
        CompletableFuture<Optional<String>> answer = new CompletableFuture<>();
        waiting.put(call, answer);
        try {
            post.accept(new SessionEvents.LocalAgentCallAdmissionRequested(call, generation));
            return answer.get(timeoutMs, TimeUnit.MILLISECONDS).orElse(null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return TIMEOUT_TEXT;
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            return TIMEOUT_TEXT;
        } finally {
            waiting.remove(call);
        }
    }

    /** Perform the node's verdict: complete the waiting call (a call that already gave up is ignored). */
    public SessionEvents.Result answer(SessionEffects.AnswerHostedCallEffect e) {
        CompletableFuture<Optional<String>> f = waiting.get(e.call());
        if (f != null) f.complete(Optional.ofNullable(e.refusal()));
        return new SessionEvents.LocalAgentEffectStarted(e.opId(), 0, f == null ? "answerHostedCall:gone" : "answerHostedCall");
    }
}
