package telamin.fluxtion.audit.analyser.analyser.core;

import javax.swing.SwingUtilities;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared background executor + EDT marshalling (spec §3, §5). All parsing/IO/LLM work runs here so
 * the Swing EDT stays responsive; results are delivered back on the EDT.
 */
public final class Background {

    private static final ExecutorService POOL = Executors.newCachedThreadPool(new ThreadFactory() {
        private final AtomicInteger n = new AtomicInteger();
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "analyser-bg-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    });

    private Background() {
    }

    /**
     * Runs {@code work} off the EDT; on success delivers the result to {@code onSuccess} on the EDT,
     * on failure delivers the throwable to {@code onError} on the EDT.
     *
     * @return the submitted task, so an explicitly cancellable UI can interrupt its own work
     */
    public static <T> Future<?> run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        return POOL.submit(() -> {
            try {
                T result = work.get();
                SwingUtilities.invokeLater(() -> onSuccess.accept(result));
            } catch (Throwable t) {
                SwingUtilities.invokeLater(() -> onError.accept(t));
            }
        });
    }

    public static void shutdown() {
        POOL.shutdownNow();
    }
}
