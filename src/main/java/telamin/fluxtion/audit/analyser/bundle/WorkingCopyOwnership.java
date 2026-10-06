package telamin.fluxtion.audit.analyser.bundle;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * File-resource ownership, not session state. A shared lock lasts as long as any reader uses a copy;
 * cleanup needs the exclusive lock. The parent lock serialises acquiring a lease with deletion, so
 * a reader cannot lock an already unlinked marker. Unknown/legacy/foreign-host copies are kept.
 * Cooperating analysers only: this is not a sandbox against someone replacing local files.
 */
public final class WorkingCopyOwnership {
    private WorkingCopyOwnership() { }
    static final String MARKER = ".working-copy-owner";
    private static final String HOST = BundleWriter.HOST;
    private static final Map<Path, Held> HELD = new HashMap<>();
    private static final class Held {
        final FileChannel channel;
        final FileLock lock;
        int references = 1;
        Held(FileChannel channel, FileLock lock) { this.channel = channel; this.lock = lock; }
    }

    public static final class Lease implements AutoCloseable {
        private final Path directory;
        private boolean closed;
        private Lease(Path directory) { this.directory = directory; }
        public Path directory() { return directory; }
        @Override public void close() {
            synchronized (WorkingCopyOwnership.class) {
                if (closed) return;
                closed = true;
                Held held = HELD.get(directory);
                if (held != null && --held.references == 0) {
                    HELD.remove(directory);
                    try { held.channel.close(); } catch (IOException ignored) { }
                }
            }
        }
    }

    /** Claim a fresh, private extraction before writing any bundle member. */
    static synchronized Lease create(Path directory) throws IOException {
        Path real = directory.toRealPath();
        return coordinated(real.getParent(), () -> {
            Files.writeString(real.resolve(MARKER), HOST, StandardOpenOption.CREATE_NEW);
            return acquire(real);
        });
    }

    /** Acquire before reading a managed profile, log or graph. Legacy copies cannot be reaped. */
    public static synchronized Lease forPath(Path input) throws IOException {
        if (input == null) return null;
        Path root = EvidenceBundle.workingCopiesRoot();
        if (!Files.isDirectory(root)) return null;
        Path realRoot = root.toRealPath();
        // Resolve the nearest existing ancestor too: a deleted member must not create a new copy.
        Path real = input.toAbsolutePath().normalize();
        if (Files.exists(real)) real = real.toRealPath();
        if (!real.startsWith(realRoot) || real.equals(realRoot)) return null;
        Path directory = realRoot.resolve(realRoot.relativize(real).getName(0));
        return coordinated(realRoot, () -> {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS))
                throw new IOException("the bundle working copy is no longer available; open the bundle again");
            if (!Files.exists(directory.resolve(MARKER), LinkOption.NOFOLLOW_LINKS)) return null;
            return acquire(directory);
        });
    }

    private static Lease acquire(Path directory) throws IOException {
        Held held = HELD.get(directory);
        if (held != null) {
            held.references++;
            return new Lease(directory);
        }
        Path marker = directory.resolve(MARKER);
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("the working copy ownership marker is not a regular file");
        FileChannel channel = FileChannel.open(marker, StandardOpenOption.READ, StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        try {
            FileLock lock = channel.tryLock(0, Long.MAX_VALUE, true);
            if (lock == null) throw new IOException("the working copy is being cleaned; open the bundle again");
            HELD.put(directory, new Held(channel, lock));
            return new Lease(directory);
        } catch (IOException | RuntimeException error) {
            channel.close();
            throw error;
        }
    }

    /** Only direct managed children of the configured root can be removed. Never follow a link. */
    static boolean reap(Path candidate) { return reap(EvidenceBundle.workingCopiesRoot(), candidate); }

    static synchronized boolean reap(Path configuredRoot, Path candidate) {
        try {
            Path root = configuredRoot.toRealPath();
            Path at = candidate.toAbsolutePath().normalize();
            if (!Files.isDirectory(at, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(at)) return false;
            at = at.toRealPath();
            if (!root.equals(at.getParent()) || !at.getFileName().toString().startsWith("bundle-")) return false;
            Path directory = at;
            return coordinated(root, () -> {
                // Before opening ANY descriptor: closing another descriptor could drop our POSIX lock.
                if (HELD.containsKey(directory)) return false;
                Path marker = directory.resolve(MARKER);
                if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS) || HOST.isEmpty()) return false;
                try (FileChannel channel = FileChannel.open(marker, StandardOpenOption.READ,
                        StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                    FileLock lock = channel.tryLock();
                    if (lock == null) return false;
                    try (lock) {
                        if (channel.size() > 1024) return false;
                        ByteBuffer text = ByteBuffer.allocate((int) channel.size());
                        while (text.hasRemaining() && channel.read(text) >= 0) { }
                        text.flip();
                        if (!HOST.equals(StandardCharsets.UTF_8.decode(text).toString())) return false;
                        try (var paths = Files.walk(directory)) {
                            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                                Files.deleteIfExists(path);
                        }
                        return true;
                    }
                }
            });
        } catch (IOException | RuntimeException uncertain) {
            return false;
        }
    }

    @FunctionalInterface private interface IO<T> { T run() throws IOException; }
    private static <T> T coordinated(Path root, IO<T> action) throws IOException {
        Path coordinator = root.resolve(".working-copies-lock");
        try (FileChannel channel = FileChannel.open(coordinator, StandardOpenOption.CREATE,
                StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
            try (FileLock lock = channel.tryLock()) {
                if (lock == null) throw new IOException("working copy ownership is busy; retry the open or cleanup");
                return action.run();
            }
        }
    }
}
