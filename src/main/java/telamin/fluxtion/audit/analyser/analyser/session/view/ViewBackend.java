package telamin.fluxtion.audit.analyser.analyser.session.view;

/**
 * View-model spike: something that draws a view model — the Swing status bar, a recorder in a test, an HTML page.
 *
 * <p>A backend PERFORMS; it never decides. The session has already decided that this view is current and that it
 * changed; a backend that compared, skipped or re-derived would move that decision back out of the processor.
 */
public interface ViewBackend<V> {

    /** The backend's name, as the {@code ViewRendered} result records it. */
    String name();

    void render(V view);
}
