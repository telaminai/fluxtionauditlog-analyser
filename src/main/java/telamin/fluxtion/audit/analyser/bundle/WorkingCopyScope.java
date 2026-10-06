package telamin.fluxtion.audit.analyser.bundle;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Leases for file resources used by one window. The session supplies the live paths. */
public final class WorkingCopyScope implements AutoCloseable {
    private final Map<Path, WorkingCopyOwnership.Lease> leases = new HashMap<>();
    private boolean closed;

    public synchronized void hold(Path path) throws IOException {
        if (closed) throw new IOException("the analyser window is closed");
        add(WorkingCopyOwnership.forPath(path));
    }

    public synchronized void add(WorkingCopyOwnership.Lease lease) {
        if (lease == null) return;
        if (closed) { lease.close(); return; }
        var previous = leases.put(lease.directory(), lease);
        if (previous != null && previous != lease) previous.close();
    }

    /** Called after a settled snapshot, never while a file open is pending. */
    public synchronized boolean retain(List<Path> livePaths) {
        var canonical = livePaths.stream().map(path -> {
            try { return path.toRealPath(); }
            catch (IOException missing) { return path.toAbsolutePath().normalize(); }
        }).toList();
        boolean released = false;
        var iterator = leases.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (canonical.stream().anyMatch(path -> path.startsWith(entry.getKey()))) continue;
            entry.getValue().close();
            iterator.remove();
            released = true;
        }
        return released;
    }

    @Override public synchronized void close() {
        closed = true;
        leases.values().forEach(WorkingCopyOwnership.Lease::close);
        leases.clear();
    }
}
