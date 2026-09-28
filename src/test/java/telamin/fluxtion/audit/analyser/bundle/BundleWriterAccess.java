package telamin.fluxtion.audit.analyser.bundle;

import java.util.concurrent.CountDownLatch;

/** Test access to {@link BundleWriter}'s hold on the file work, for tests in other packages (EB.F9). */
public final class BundleWriterAccess {
    private BundleWriterAccess() {
    }

    /** From now on, every capture's file work waits, on its own thread, until {@code release} counts down. */
    public static void holdWritesUntil(CountDownLatch release, CountDownLatch reached) {
        BundleWriter.beforeCopy = () -> {
            reached.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
    }

    public static void stopHolding() {
        BundleWriter.beforeCopy = () -> { };
    }
}
