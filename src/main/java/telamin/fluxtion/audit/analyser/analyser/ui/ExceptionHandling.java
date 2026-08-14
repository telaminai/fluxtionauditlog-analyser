package telamin.fluxtion.audit.analyser.analyser.ui;

/**
 * Routes uncaught exceptions to the console so nothing fails silently.
 *
 * <p>Non-EDT threads go through a default {@link Thread.UncaughtExceptionHandler}. EDT-dispatch
 * exceptions are already printed to {@code System.err} by the JVM's {@code EventDispatchThread}, so
 * we deliberately do <b>not</b> push a custom {@code EventQueue} — doing so can interfere with the
 * macOS Swing paint/validate pipeline (missed repaints / uncommitted layout) for no added benefit.
 */
public final class ExceptionHandling {

    private ExceptionHandling() {
    }

    /** Installs the handler. Call once at startup. */
    public static void install() {
        Thread.setDefaultUncaughtExceptionHandler((thread, t) -> report(thread.getName(), t));
    }

    private static void report(String thread, Throwable t) {
        System.err.println("[uncaught on " + thread + "] " + t);
        t.printStackTrace(System.err);
    }
}
