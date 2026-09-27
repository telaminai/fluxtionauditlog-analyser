package telamin.fluxtion.audit.analyser.analyser.session.view;

import java.util.ArrayList;
import java.util.List;

/**
 * View-model spike: the backends registered for ONE element. The adapter hands each render to all of them, in
 * registration order, and reports which drew it — the way effect adapters are registered, and nothing more.
 */
public final class ViewBackends<V> {

    private final List<ViewBackend<V>> backends = new ArrayList<>();

    public ViewBackends<V> register(ViewBackend<V> backend) {
        backends.add(backend);
        return this;
    }

    /** Draw the view on every backend; the names of those that drew it. */
    public List<String> render(V view) {
        List<String> drew = new ArrayList<>(backends.size());
        for (ViewBackend<V> b : backends) {
            b.render(view);
            drew.add(b.name());
        }
        return List.copyOf(drew);
    }
}
