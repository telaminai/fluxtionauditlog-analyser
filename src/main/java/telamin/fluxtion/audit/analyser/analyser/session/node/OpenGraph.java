package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * Whether a topology graph is open, which, and where it came from
 * ({@code OPENED} / {@code DECLARED} / {@code INFERRED}).
 *
 * <p>Same two-input shape as {@link OpenLog}, for the same reason: the close is a result that proves
 * something happened, the open is an observation only until the slice that moves graph opening.
 */
public class OpenGraph implements EventLogSource {

    private final OperationGate gate;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private String graphPath;
    private String source;

    /** Held here for the same reason as {@code OpenLog}'s: derivation reads state, once per cycle. */
    private java.util.Set<String> declaredNodeIds = java.util.Set.of();
    private java.util.List<String> nodeTypes = java.util.List.of();

    public OpenGraph(OperationGate gate) {
        this.gate = gate;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onGraphClosed(SessionEvents.GraphClosed event) {
        if (!gate.accepted()) {
            return false;
        }
        boolean wasOpen = graphPath != null;
        graphPath = null;
        source = null;
        declaredNodeIds = java.util.Set.of();
        nodeTypes = java.util.List.of();
        auditLog.info("openGraph", "none").info("via", "GraphClosed");
        return wasOpen;
    }

    @OnEventHandler
    public boolean onGraphObserved(SessionEvents.GraphObserved event) {
        String wasPath = graphPath;
        java.util.Set<String> wasIds = declaredNodeIds;
        graphPath = event.open() ? event.graphPath() : null;
        source = event.open() ? event.source() : null;
        declaredNodeIds = event.open() ? event.declaredNodeIds() : java.util.Set.of();
        nodeTypes = event.open() ? event.nodeTypes() : java.util.List.of();
        auditLog.info("openGraph", event.open() ? event.graphPath() : "none").info("via", "observation");
        return !java.util.Objects.equals(wasPath, graphPath) || !wasIds.equals(declaredNodeIds);
    }

    public boolean isOpen() {
        return graphPath != null;
    }

    public String graphPath() {
        return graphPath;
    }

    public String source() {
        return source;
    }

    /** The authored node ids the graph declares — raw, so a verdict is computed and not handed over. */
    public java.util.Set<String> declaredNodeIds() {
        return declaredNodeIds;
    }

    /** Every node's simple type name; how audit installation is read. */
    public java.util.List<String> nodeTypes() {
        return nodeTypes;
    }
}
