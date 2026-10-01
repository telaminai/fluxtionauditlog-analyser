package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.SwingUtilities;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** Accounts for asynchronous EDT faults that otherwise print to stderr without failing a frame test. */
final class EdtExceptionWatch implements AutoCloseable {
    private final ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();
    private final Thread thread;
    private final Thread.UncaughtExceptionHandler previous;

    EdtExceptionWatch() throws Exception {
        AtomicReference<Thread> owner = new AtomicReference<>();
        AtomicReference<Thread.UncaughtExceptionHandler> handler = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            owner.set(Thread.currentThread());
            handler.set(Thread.currentThread().getUncaughtExceptionHandler());
            Thread.currentThread().setUncaughtExceptionHandler((t, failure) -> failures.add(failure));
        });
        thread = owner.get();
        previous = handler.get();
    }

    /** Consume exactly one explicitly injected fault, only after its type and failing call are verified. */
    <T extends Throwable> T expect(Class<T> type, String className, String method) {
        Throwable failure = failures.peek();
        T expected = assertInstanceOf(type, failure, "theInjectedEdtFailureWasObserved");
        assertTrue(java.util.Arrays.stream(expected.getStackTrace()).anyMatch(frame ->
                        frame.getClassName().equals(className) && frame.getMethodName().equals(method)),
                "the injected failure must come from " + className + "." + method);
        failures.remove();
        return expected;
    }

    @Override
    public void close() throws Exception {
        // Drain work already queued before the barrier. Restore the original handler even when the assertion fails.
        SwingUtilities.invokeAndWait(() -> thread.setUncaughtExceptionHandler(previous));
        List<Throwable> unaccounted = List.copyOf(failures);
        assertTrue(unaccounted.isEmpty(), () -> "uncaughtEdtFailure: " + unaccounted);
    }
}
