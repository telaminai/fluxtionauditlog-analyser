package telamin.fluxtion.audit.analyser.bundle;

import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * A second PROCESS asking whether a capture's owner lock is free — the question another analyser's reaper asks.
 * Prints {@code free} or {@code held}. It is the only vantage point from which the defect in
 * {@code reapCorpses} is visible: the holding JVM's own {@code FileLock.isValid()} still says true after its
 * lock has been released out from under it, because the JDK's lock table is not the OS's.
 */
public final class CaptureLockTaker {
    private CaptureLockTaker() {
    }

    public static void main(String[] args) throws Exception {
        try (FileChannel ch = FileChannel.open(Path.of(args[0]), StandardOpenOption.WRITE)) {
            FileLock lock = ch.tryLock();
            System.out.println(lock == null ? "held" : "free");
            System.out.flush();
            if (lock != null) lock.release();
        }
    }
}
