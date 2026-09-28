package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnTrigger;

import java.util.Objects;

/**
 * Spike round 3 — a NARROW trigger parent: the open log's lifecycle, without its record count.
 *
 * <p>{@link OpenLog} is dirty on every Follow append, because its count moved. Nodes that care only whether a log is
 * open, which generation it is, or what is known about its file were woken by every append anyway, each writing an
 * invocation line that says nothing. They now hold {@code OpenLog} as a {@code @NoTriggerReference} (read as data) and
 * are triggered by this node, which reports a change only when one of those four things moved.
 *
 * <p>It is not free: it runs on every append itself, and writes one line. The saving is one line per consumer, so it
 * pays from the second consumer on. It has three: {@link IdentityBanner}, {@link LogEvidence} and
 * {@link PairingQualifier}.
 */
public class LogLifecycle {

    private final OpenLog openLog;

    // Not final: node-local state (a final field is constructor-mapped by the generator).
    private boolean open;
    private long generation = -1;
    private String identity;
    private String identityReason;

    public LogLifecycle(OpenLog openLog) {
        this.openLog = openLog;
    }

    /** True only when open, generation, identity or its reason moved — an append alone is not a lifecycle change. */
    @OnTrigger
    public boolean onOpenLogChanged() {
        boolean nowOpen = openLog.isOpen();
        long nowGeneration = openLog.generation();
        String nowIdentity = openLog.identity();
        String nowReason = openLog.identityReason();
        if (nowOpen == open && nowGeneration == generation && Objects.equals(nowIdentity, identity)
                && Objects.equals(nowReason, identityReason)) {
            return false;
        }
        open = nowOpen;
        generation = nowGeneration;
        identity = nowIdentity;
        identityReason = nowReason;
        return true;
    }
}
