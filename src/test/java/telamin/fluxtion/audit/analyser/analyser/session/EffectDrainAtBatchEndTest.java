package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The effect drain moved from a loop in {@link SessionDriver} to {@code @OnBatchEnd} on
 * {@code EffectQueue} — Fluxtion's own transaction boundary rather than a reimplementation of one.
 *
 * <p>Every replay test in this package passed unchanged across that move, which proves the behaviour
 * did not regress and proves nothing about <em>why the new shape is safe</em>. These are the properties
 * the old driver got by construction and the new one has to earn.
 */
class EffectDrainAtBatchEndTest {

    private static final String A = "/projects/alpha.properties";

    private static void open(SessionDriver driver, String path, TransitionKind kind) {
        driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(), path, kind, "test"));
    }

    // ------------------------------------------------------------------ the ordering claim itself

    @Test
    @DisplayName("the decision's audit record has ALREADY published when the adapter is called")
    void decidedThenRecordedThenActed() {
        SessionAuditSink sink = new SessionAuditSink();
        List<Integer> recordsVisibleAtPerformTime = new ArrayList<>();

        SessionDriver driver = new SessionDriver(effect -> {
            recordsVisibleAtPerformTime.add(sink.records().size());
            return new SessionEvents.StatusShown(effect.opId(), "ok");
        }, sink);

        int beforeAnything = sink.records().size();
        open(driver, A, TransitionKind.STARTUP_ACTIVATION);

        assertFalse(recordsVisibleAtPerformTime.isEmpty(), "precondition: an effect was performed");
        // This is the whole reason the drain is not inside @OnTrigger or @AfterEvent. onEvent() ends in
        // afterEvent(), which publishes the cycle's record; batchEnd() cannot be entered until it has
        // returned. So the record of DECIDING an irreversible act provably exists before the act.
        assertTrue(recordsVisibleAtPerformTime.get(0) > beforeAnything,
                "the adapter ran with " + recordsVisibleAtPerformTime.get(0) + " records published and "
                        + beforeAnything + " before the request — the decision must be on the record "
                        + "BEFORE it is carried out, or an effect that throws costs the evidence too");
    }

    @Test
    @DisplayName("the drain leaves a BatchEnd lifecycle record — the boundary is visible in the log")
    void theTransactionBoundaryIsAudited() {
        SessionAuditSink sink = new SessionAuditSink();
        SessionDriver driver = new SessionDriver(new FakeSessionAdapter().withProfile(A), sink);
        open(driver, A, TransitionKind.STARTUP_ACTIVATION);

        assertFalse(sink.matching("BatchEnd").isEmpty(),
                "a reader must be able to see where the transaction closed and the effects ran; the "
                        + "old external drain happened entirely outside the record");
    }

    // ------------------------------------------------------------------ the adapter as a service

    @Test
    @DisplayName("the adapter arrives by @ServiceRegistered, not by construction")
    void theAdapterIsInjectedAsAService() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A);
        SessionDriver driver = new SessionDriver(adapter);

        open(driver, A, TransitionKind.STARTUP_ACTIVATION);

        // Nothing constructs EffectQueue with an adapter — the generated processor constructs its own
        // nodes. If the service route were broken this list would be empty and every effect would have
        // failed as "no adapter registered", so this assertion is the wiring test.
        assertFalse(adapter.performed.isEmpty(),
                "the registered service reached EffectQueue and the effects were performed");
    }

    // ------------------------------------------------------------------ the new failure mode

    @Test
    @DisplayName("a protocol violation reaches the caller AND does not wedge the processor")
    void aViolationDoesNotLeaveProcessingStuckOn() {
        SessionDriver[] holder = new SessionDriver[1];
        boolean[] reentrant = {true};
        SessionDriver driver = new SessionDriver(effect -> {
            if (reentrant[0]) {
                holder[0].submit(new SessionEvents.LogObserved(true, "/logs/other.yaml", "DECLARED"));
            }
            return new SessionEvents.StatusShown(effect.opId(), "ok");
        });
        holder[0] = driver;

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> open(driver, A, TransitionKind.EXPLICIT_SWITCH));
        assertTrue(thrown.getMessage().contains("single-in-flight"), thrown.getMessage());

        // THE POINT. The generated batchEnd() sets processing=true with no try/finally, so a throw from
        // inside the @OnBatchEnd method would leave the flag set forever: every later event would be
        // queued as re-entrant and never dispatched, and the application would go quiet with no error.
        // EffectQueue therefore catches it and stashes it; the driver rethrows once batchEnd has
        // returned. This asserts the processor still dispatches afterwards.
        reentrant[0] = false;
        driver.submit(new SessionEvents.LogObserved(true, "/logs/after.yaml", "DECLARED"));
        assertTrue(driver.processor().openLog.isOpen(),
                "an event submitted after the violation must still be dispatched — if this fails the "
                        + "processor was left mid-cycle and nothing would ever run again");
    }

    @Test
    @DisplayName("an effect failure is still an ordinary result, not a violation")
    void anOrdinaryFailureIsNotFatal() {
        SessionDriver driver = new SessionDriver(effect -> {
            throw new IllegalArgumentException("disk full");
        });
        open(driver, A, TransitionKind.STARTUP_ACTIVATION);

        assertFalse(driver.auditSink().matching("disk full").isEmpty(),
                "a failing adapter produces EffectFailed and the record says why");
        assertTrue(driver.auditSink().matching("EffectOutcome").stream()
                        .anyMatch(r -> r.contains("success") && r.contains("false")),
                "and it lands in EffectOutcomes as a FAILED outcome rather than vanishing — an effect "
                        + "with no row is the hole in the record this milestone exists to close. The "
                        + "first version of this assertion was a compound assertFalse(a && b) that "
                        + "could not fail, and did not: it passed against a key this record does not "
                        + "carry.");
    }

    // ------------------------------------------------------------------ the loop

    @Test
    @DisplayName("effects that cascade settle across several batchEnd rounds")
    void aCascadeSettles() {
        FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile(A);
        SessionDriver driver = new SessionDriver(adapter);

        open(driver, A, TransitionKind.STARTUP_ACTIVATION);
        driver.submit(new SessionEvents.GraphObserved(true, "/graphs/other.graphml", "OPENED",
                java.util.Set.of("supermarketTill"), List.of("EventLogManager")));
        // A log that the open graph does not describe: the arrival decides a close, the close produces
        // a result, the result produces a warning. More than one round of batchEnd, all inside one
        // submit(), and the queue must be empty when submit returns.
        driver.submit(new SessionEvents.LogObserved(true, "/logs/run.yaml", "DECLARED",
                java.util.Set.of("priceListener", "quotePublisher"), 2, 2, "TRACE"));

        assertTrue(adapter.graphClosed, "the cascade completed rather than stalling after one round");
        assertNotNull(adapter.lastWarning);
        assertTrue(driver.processor().effectQueue.isEmpty(),
                "submit() must not return with work still queued — that would leave an effect to be "
                        + "performed by whatever event happened to arrive next");
    }

    @Test
    @DisplayName("results arrive in the order the effects were performed")
    void resultsKeepTheirOrder() {
        FakeSessionAdapter adapter = new FakeSessionAdapter()
                .withProfile(A).withProfile("/projects/beta.properties");
        SessionDriver driver = new SessionDriver(adapter);

        open(driver, A, TransitionKind.STARTUP_ACTIVATION);
        driver.submit(new SessionEvents.LogObserved(true, "/logs/run.yaml", "DECLARED"));
        driver.submit(new SessionEvents.GraphObserved(true, "/graphs/run.graphml", "OPENED"));
        adapter.forget();

        open(driver, "/projects/beta.properties", TransitionKind.EXPLICIT_SWITCH);

        // Re-dispatch uses processAsNewEventCycle, which queues to the BACK of the callback stack.
        // processReentrantEvent would push to the FRONT and reverse a multi-effect batch — the close
        // of the graph would be seen before the close of the log that provoked it.
        List<String> order = new ArrayList<>();
        for (SessionEffects e : adapter.performed) {
            order.add(SessionDriver.name(e));
        }
        assertTrue(order.indexOf("closeLog") < order.indexOf("closeGraph"),
                "the decision asks for the log first; the results must not come back reversed: " + order);
    }
}
