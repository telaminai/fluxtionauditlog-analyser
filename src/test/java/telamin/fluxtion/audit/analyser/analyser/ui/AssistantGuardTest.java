package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * OA-1 (spec §5, "an effect must not race between checking a ticket and performing a UI mutation"): the assistant's guard
 * is evaluated INSIDE the event-thread task a verb runs, so a turn that ended before the task ran changes nothing in it.
 */
class AssistantGuardTest {

    @Test
    @DisplayName("an ended turn's guard stops the event-thread task before its body runs")
    void anEndedTurnChangesNothing() throws Exception {
        ActionExecutor executor = new ActionExecutor(() -> null, () -> null, null, null, null);
        AtomicBoolean ran = new AtomicBoolean();
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            ActionExecutor.bindGuard(() -> false);          // the turn this worker acts for has ended
            try {
                executor.onEdt(() -> {
                    ran.set(true);
                    return null;
                });
            } catch (Throwable t) {
                thrown.set(t);
            } finally {
                ActionExecutor.bindGuard(null);
            }
        });
        worker.start();
        worker.join(10_000);
        assertFalse(ran.get(), "the Swing mutation did not run");
        assertInstanceOf(ActionExecutor.Superseded.class, thrown.get(), "the verb is told why");
    }

    @Test
    @DisplayName("a current turn's guard lets the task run, and an unbound worker (the bridge) is never consulted")
    void aCurrentTurnRuns() throws Exception {
        ActionExecutor executor = new ActionExecutor(() -> null, () -> null, null, null, null);
        AtomicBoolean ranGuarded = new AtomicBoolean(), ranUnbound = new AtomicBoolean();
        Thread guarded = new Thread(() -> {
            ActionExecutor.bindGuard(() -> true);
            try {
                executor.onEdt(() -> { ranGuarded.set(true); return null; });
            } finally {
                ActionExecutor.bindGuard(null);
            }
        });
        Thread unbound = new Thread(() -> executor.onEdt(() -> { ranUnbound.set(true); return null; }));
        guarded.start();
        unbound.start();
        guarded.join(10_000);
        unbound.join(10_000);
        assertTrue(ranGuarded.get());
        assertTrue(ranUnbound.get());
    }
}
