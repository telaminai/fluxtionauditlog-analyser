package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Ending a local agent without orphaning its MCP child (spec §4.7, [P0-36]): SIGTERM cleans up the CLI's MCP child,
 * but SIGKILL orphans it — re-parented, so it is no longer a descendant once the parent is gone. So the descendants are
 * snapshotted FIRST, every member of the tree is asked to terminate, and after the grace period the survivors of the
 * snapshot are killed.
 */
public final class ProcessTree {

    private ProcessTree() {
    }

    /** @return the handles that had to be killed forcibly (for the record and the tests) */
    public static List<ProcessHandle> terminate(ProcessHandle root, Duration grace) {
        List<ProcessHandle> tree = new ArrayList<>();
        tree.add(root);
        root.descendants().forEach(tree::add);           // the snapshot, before anything is signalled
        for (ProcessHandle h : tree) h.destroy();         // SIGTERM to every member
        long deadline = System.nanoTime() + grace.toNanos();
        for (ProcessHandle h : tree) {
            long left = deadline - System.nanoTime();
            if (left <= 0) break;
            try {
                h.onExit().get(left, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ignored) {
                // timed out or failed to wait: the survivor is killed below
            }
        }
        List<ProcessHandle> killed = new ArrayList<>();
        for (ProcessHandle h : tree) {
            if (h.isAlive()) {
                h.destroyForcibly();
                killed.add(h);
            }
        }
        return killed;
    }
}
