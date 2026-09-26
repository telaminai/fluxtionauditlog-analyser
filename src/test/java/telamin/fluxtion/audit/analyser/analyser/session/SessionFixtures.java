package telamin.fluxtion.audit.analyser.analyser.session;

import java.util.List;
import java.util.Set;

/**
 * M44.4a: how a replay test puts a log or graph on the session, now that the observations are gone.
 *
 * <p>A graph is a FACT, submitted as it happens. A log arrives only as the RESULT of an open the processor asked for,
 * so opening one here is the real route: the request, answered {@code Pending}, then the landed {@code LogOpened}
 * with the same opId. The tests used to write {@code LogObserved(open=true)} for this, which put a log on the session
 * by a path the application no longer has.
 */
final class SessionFixtures {

    private SessionFixtures() {
    }

    static SessionEvents.GraphOpened graph(String path, String source, Set<String> declared, List<String> types) {
        return new SessionEvents.GraphOpened(path, source, declared, types);
    }

    static SessionEvents.GraphOpened graph(String path) {
        return graph(path, "OPENED", Set.of(), List.of());
    }

    /** Open {@code path} by request and landed result; the adapter is made to answer Pending for the request only. */
    static SessionEvents.LogOpened openLog(SessionDriver d, FakeSessionAdapter adapter, String path, String provenance,
                                           Set<String> ids, int sampled, int total, String level) {
        boolean was = adapter.pendingOpens;
        adapter.pendingOpens = true;
        long opId = d.nextOpId();
        try {
            d.submit(new SessionEvents.OpenLogRequested(opId, path, null, provenance, false));
        } finally {
            adapter.pendingOpens = was;
        }
        SessionEvents.LogOpened landed = new SessionEvents.LogOpened(opId, path, provenance, ids, sampled, total, level);
        d.submit(landed);
        return landed;
    }

    static SessionEvents.LogOpened openLog(SessionDriver d, FakeSessionAdapter adapter, String path) {
        return openLog(d, adapter, path, "DECLARED", Set.of(), 0, 0, null);
    }
}
