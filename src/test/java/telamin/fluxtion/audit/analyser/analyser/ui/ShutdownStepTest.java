package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one piece of shutdown that is worth a headless test: {@link MainFrame#step} isolates each quit
 * action. Swing itself isn't unit-tested here (CI is headless) and this doesn't test Swing — it only
 * loads the class and calls a static helper, no frame is ever constructed.
 *
 * <p>Guards a real failure: a shutdown step threw, the exception escaped {@code windowClosing}, and the
 * app could no longer be closed at all.
 */
class ShutdownStepTest {

    @Test
    void aFailingStepDoesNotPropagate() {
        assertDoesNotThrow(() -> MainFrame.step(() -> {
            throw new IllegalStateException("boom");
        }));
    }

    @Test
    void evenAnErrorIsAbsorbed() {
        // the real one was NoClassDefFoundError — an Error, so catching Exception would not have helped
        assertDoesNotThrow(() -> MainFrame.step(() -> {
            throw new NoClassDefFoundError("some/Class");
        }));
    }

    @Test
    void laterStepsStillRunAfterAnEarlierFailure() {
        List<String> done = new ArrayList<>();
        MainFrame.step(() -> done.add("first"));
        MainFrame.step(() -> {
            throw new RuntimeException("middle blew up");
        });
        MainFrame.step(() -> done.add("last"));
        assertEquals(List.of("first", "last"), done, "a failure must not cost the remaining steps");
    }

    @Test
    void aSucceedingStepRunsNormally() {
        List<String> done = new ArrayList<>();
        MainFrame.step(() -> done.add("ran"));
        assertEquals(List.of("ran"), done);
    }
}
