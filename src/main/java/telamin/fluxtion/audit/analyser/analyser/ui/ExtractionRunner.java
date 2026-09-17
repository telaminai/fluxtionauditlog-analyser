package telamin.fluxtion.audit.analyser.analyser.ui;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The seam a graph's extraction runs through (M65, acceptance 3 / pass-3 F4): {@code Background.run}'s
 * three-argument shape, so the production default is {@code Background::run} and a headless test can park
 * the work and deliver it on its own thread. Lives in {@code ui} beside {@link GraphPanel}; {@code core} is
 * unchanged.
 */
interface ExtractionRunner {
    <T> void run(Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError);
}
