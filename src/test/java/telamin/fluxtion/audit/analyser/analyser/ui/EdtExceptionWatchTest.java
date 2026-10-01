package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class EdtExceptionWatchTest {
    private static Thread.UncaughtExceptionHandler handler() throws Exception {
        AtomicReference<Thread.UncaughtExceptionHandler> current = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> current.set(Thread.currentThread().getUncaughtExceptionHandler()));
        return current.get();
    }

    private static void failLater(Throwable failure) throws Exception {
        SwingUtilities.invokeLater(() -> {
            if (failure instanceof Error error) throw error;
            throw (RuntimeException) failure;
        });
        SwingUtilities.invokeAndWait(() -> { });
    }

    @Test
    void unexpectedFailureMakesTheWatchFailAndRestoresTheHandler() throws Exception {
        var original = handler();
        var watch = new EdtExceptionWatch();
        failLater(new IllegalStateException("DEMO unexpected EDT failure"));
        var failure = assertThrows(AssertionError.class, watch::close, "anUnexpectedEdtExceptionMustFailTheTest");
        assertTrue(failure.getMessage().contains("DEMO unexpected EDT failure"));
        assertSame(original, handler(), "anAssertionFailureMustStillRestoreTheHandler");
    }

    @Test
    void anExpectedFaultDoesNotHideAnotherFailure() throws Exception {
        var watch = new EdtExceptionWatch();
        failLater(new IllegalStateException("DEMO injected fault"));
        watch.expect(IllegalStateException.class, getClass().getName(), "anExpectedFaultDoesNotHideAnotherFailure");
        failLater(new IllegalArgumentException("DEMO unrelated fault"));
        var failure = assertThrows(AssertionError.class, watch::close);
        assertTrue(failure.getMessage().contains("DEMO unrelated fault"));
        assertFalse(failure.getMessage().contains("DEMO injected fault"));
    }

    @Test
    void aMismatchedExpectedFaultRemainsAnUnexpectedFailure() throws Exception {
        var watch = new EdtExceptionWatch();
        failLater(new IllegalArgumentException("DEMO wrong failure"));
        assertThrows(AssertionError.class, () -> watch.expect(IllegalStateException.class, getClass().getName(),
                "aMismatchedExpectedFaultRemainsAnUnexpectedFailure"));
        assertThrows(AssertionError.class, watch::close, "aRejectedExpectationMustNotConsumeTheFault");
    }

    @Test
    void noFaultLeavesTheOriginalHandlerInPlace() throws Exception {
        var original = handler();
        try (var watch = new EdtExceptionWatch()) {
            SwingUtilities.invokeAndWait(() -> { });
        }
        assertSame(original, handler());
    }
}
