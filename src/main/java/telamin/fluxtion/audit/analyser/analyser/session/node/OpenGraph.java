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
 * <p>The close that answers a {@code CloseGraphEffect} is a result that proves it happened. An open, and a close
 * made outside a transition, are facts (M44.4a): graphs open synchronously from many surfaces, and each one
 * reports what happened through a single hook on the Topology panel.
 */
public class OpenGraph implements EventLogSource {

    private final OperationGate gate;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private boolean open;
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
        boolean wasOpen = open;
        open = false;
        graphPath = null;
        source = null;
        declaredNodeIds = java.util.Set.of();
        nodeTypes = java.util.List.of();
        auditLog.info("openGraph", "none").info("via", "GraphClosed");
        return wasOpen;
    }

    /** M44.4a: a graph is now the one on screen. Dirty only when it is a different graph. */
    @OnEventHandler
    public boolean onGraphOpened(SessionEvents.GraphOpened event) {
        boolean moved = !open || !java.util.Objects.equals(graphPath, event.graphPath())
                || !java.util.Objects.equals(source, event.source())
                || !declaredNodeIds.equals(event.declaredNodeIds()) || !nodeTypes.equals(event.nodeTypes());
        // A reader-supplied graph has no file, and is still a graph: GraphObserved carried a null path for it, so
        // the processor believed no graph was open while one was on screen. Openness is its own fact now.
        open = true;
        graphPath = event.graphPath();
        source = event.source();
        declaredNodeIds = event.declaredNodeIds();
        nodeTypes = event.nodeTypes();
        auditLog.info("openGraph", graphPath == null ? "(no file)" : graphPath).info("via", "GraphOpened").info("source", source);
        return moved;
    }

    /** M44.4a: the graph left the screen outside a transition; a no-op, recorded, when a result already closed it. */
    @OnEventHandler
    public boolean onGraphCleared(SessionEvents.GraphCleared event) {
        if (!open) {
            auditLog.info("noOp", "GraphCleared").info("reason", "no graph open");
            return false;
        }
        open = false;
        graphPath = null;
        source = null;
        declaredNodeIds = java.util.Set.of();
        nodeTypes = java.util.List.of();
        auditLog.info("openGraph", "none").info("via", "GraphCleared");
        return true;
    }

    public boolean isOpen() {
        return open;
    }

    public String graphPath() {
        return graphPath;
    }

    public String source() {
        return source;
    }

    /** All node ids the graph declares (including framework nodes) — raw, so a verdict is computed and not handed over. */
    public java.util.Set<String> declaredNodeIds() {
        return declaredNodeIds;
    }

    /** Every node's simple type name; how audit installation is read. */
    public java.util.List<String> nodeTypes() {
        return nodeTypes;
    }
}
