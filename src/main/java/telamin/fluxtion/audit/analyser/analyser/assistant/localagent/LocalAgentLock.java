package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Optional;

/**
 * The session lock (spec §4.8, [P0-20]): two processes on one session id fork its history silently, so an analyser
 * holds {@code ~/.fluxtion-analyser/local-agent/<session-id>.lock} exclusively for as long as it runs that session.
 * Released on stop, on exit and — because it is an operating-system lock — when the analyser crashes. A terminal's
 * {@code claude --resume} takes no lock, so it is invisible here; the header warns about it instead.
 */
public final class LocalAgentLock implements AutoCloseable {

    private final FileChannel channel;
    private final FileLock lock;
    private final Path path;

    private LocalAgentLock(FileChannel channel, FileLock lock, Path path) {
        this.channel = channel;
        this.lock = lock;
        this.path = path;
    }

    /** The lock directory: {@code ~/.fluxtion-analyser/local-agent}. */
    public static Path directory(Path userHome) {
        return userHome.resolve(".fluxtion-analyser").resolve("local-agent");
    }

    /** Take the lock for {@code sessionId}, or empty when another holder (another analyser) has it. */
    public static Optional<LocalAgentLock> tryAcquire(Path dir, String sessionId) throws IOException {
        if (sessionId == null || !sessionId.matches("[0-9a-fA-F-]{8,64}")) {
            throw new IOException("not a session id");
        }
        privateDirectory(dir);
        Path file = dir.resolve(sessionId + ".lock");
        FileChannel ch = FileChannel.open(file, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        try {
            FileLock l = ch.tryLock();
            if (l == null) {
                ch.close();
                return Optional.empty();
            }
            return Optional.of(new LocalAgentLock(ch, l, file));
        } catch (OverlappingFileLockException e) {     // this JVM already holds it: another window of the same run
            ch.close();
            return Optional.empty();
        }
    }

    static void privateDirectory(Path dir) throws IOException {
        if (Files.isDirectory(dir)) return;
        try {
            Files.createDirectories(dir, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        } catch (UnsupportedOperationException e) {
            Files.createDirectories(dir);
        }
    }

    public Path path() {
        return path;
    }

    public boolean held() {
        return lock.isValid();
    }

    @Override
    public void close() {
        try {
            if (lock.isValid()) lock.release();
        } catch (IOException ignored) {
            // best effort: closing the channel releases it too
        }
        try {
            channel.close();
        } catch (IOException ignored) {
            // nothing more to release
        }
    }
}
